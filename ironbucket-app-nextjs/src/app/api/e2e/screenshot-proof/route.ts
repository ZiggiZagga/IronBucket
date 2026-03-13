import { NextRequest, NextResponse } from 'next/server';

type ScreenshotProofRequest = {
  actor?: string;
  screenshotBase64?: string;
  mimeType?: string;
};

type ActorCredentials = {
  username: string;
  password: string;
};

const ACTOR_CREDENTIALS: Record<string, ActorCredentials> = {
  alice: { username: 'alice', password: 'aliceP@ss' },
  bob: { username: 'bob', password: 'bobP@ss' }
};

const TOKEN_URL =
  process.env.E2E_KEYCLOAK_TOKEN_URL ??
  'http://127.0.0.1:7081/realms/dev/protocol/openid-connect/token';
const SENTINEL_URL = process.env.E2E_SENTINEL_URL ?? 'http://127.0.0.1:8080';
const GATEWAY_GRAPHQL_URL = process.env.E2E_GATEWAY_GRAPHQL_URL ?? `${SENTINEL_URL}/graphql`;
const CLIENT_ID = process.env.E2E_OIDC_CLIENT_ID ?? 'dev-client';
const CLIENT_SECRET = process.env.E2E_OIDC_CLIENT_SECRET ?? 'dev-secret';

export async function POST(req: NextRequest) {
  const requestBody = (await req.json()) as ScreenshotProofRequest;
  const actor = (requestBody.actor ?? 'alice').toLowerCase();
  const screenshotBase64 = requestBody.screenshotBase64 ?? '';
  const mimeType = requestBody.mimeType ?? 'image/png';

  if (!ACTOR_CREDENTIALS[actor]) {
    return NextResponse.json({ error: `Unsupported actor '${actor}'` }, { status: 400 });
  }

  if (!screenshotBase64) {
    return NextResponse.json({ error: 'Missing screenshotBase64' }, { status: 400 });
  }

  const bucket = `default-${actor}-proofs`;
  const key = `${actor}-ui-e2e-proof-${Date.now()}.png.b64`;

  try {
    const token = await fetchAccessToken(actor);

    await graphqlCall(token, {
      query: `
        mutation CreateBucket($jwtToken: String!, $bucketName: String!, $ownerTenant: String!) {
          createBucket(jwtToken: $jwtToken, bucketName: $bucketName, ownerTenant: $ownerTenant) {
            name
          }
        }
      `,
      variables: {
        jwtToken: token,
        bucketName: bucket,
        ownerTenant: actor
      }
    });

    await graphqlCall(token, {
      query: `
        mutation UploadObject($jwtToken: String!, $bucket: String!, $key: String!, $content: String!, $contentType: String) {
          uploadObject(jwtToken: $jwtToken, bucket: $bucket, key: $key, content: $content, contentType: $contentType) {
            key
            bucket
          }
        }
      `,
      variables: {
        jwtToken: token,
        bucket,
        key,
        content: screenshotBase64,
        contentType: 'text/plain'
      }
    });

    const listResponse = await graphqlCall(token, {
      query: `
        query ListObjects($jwtToken: String!, $bucket: String!, $query: String) {
          listObjects(jwtToken: $jwtToken, bucket: $bucket, query: $query) {
            key
          }
        }
      `,
      variables: {
        jwtToken: token,
        bucket,
        query: key
      }
    });

    const listed = listResponse?.data?.listObjects ?? [];
    const found = Array.isArray(listed) && listed.some((item: { key?: string }) => item?.key === key);

    const downloadResponse = await graphqlCall(token, {
      query: `
        mutation DownloadObject($jwtToken: String!, $bucket: String!, $key: String!) {
          downloadObject(jwtToken: $jwtToken, bucket: $bucket, key: $key) {
            url
          }
        }
      `,
      variables: {
        jwtToken: token,
        bucket,
        key
      }
    });

    const downloadUrl = String(downloadResponse?.data?.downloadObject?.url ?? '');
    if (!downloadUrl) {
      throw new Error('downloadObject did not return a URL');
    }

    const downloadedBase64 = await fetchDownloadedBase64(downloadUrl, token);
    const previewDataUrl = `data:${mimeType};base64,${downloadedBase64}`;

    return NextResponse.json({
      actor,
      bucket,
      key,
      proofStored: found,
      downloadUrl,
      previewDataUrl,
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    return NextResponse.json(
      {
        error: 'Screenshot proof upload/download flow failed',
        details: error instanceof Error ? error.message : String(error)
      },
      { status: 500 }
    );
  }
}

async function fetchDownloadedBase64(downloadUrl: string, token: string): Promise<string> {
  const response = await fetch(downloadUrl, {
    headers: {
      Authorization: `Bearer ${token}`
    }
  });

  const bodyText = await response.text();
  if (!response.ok) {
    throw new Error(`Download fetch failed (${response.status}): ${bodyText}`);
  }

  const normalized = bodyText.trim();
  if (!normalized) {
    throw new Error('Downloaded screenshot payload is empty');
  }

  return normalized;
}

async function fetchAccessToken(actor: string): Promise<string> {
  const credentials = ACTOR_CREDENTIALS[actor];
  if (!credentials) {
    throw new Error(`Unsupported actor: ${actor}`);
  }

  const body = new URLSearchParams({
    client_id: CLIENT_ID,
    client_secret: CLIENT_SECRET,
    grant_type: 'password',
    scope: 'openid profile email roles',
    username: credentials.username,
    password: credentials.password
  });

  const tokenResponse = await fetch(TOKEN_URL, {
    method: 'POST',
    headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
    body
  });

  if (!tokenResponse.ok) {
    const details = await tokenResponse.text();
    throw new Error(`Token request failed (${tokenResponse.status}): ${details}`);
  }

  const tokenData = (await tokenResponse.json()) as { access_token?: string };
  if (!tokenData.access_token) {
    throw new Error('Token response did not include access_token');
  }

  return tokenData.access_token;
}

async function graphqlCall(token: string, payload: { query: string; variables?: Record<string, unknown> }) {
  const response = await fetch(GATEWAY_GRAPHQL_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`
    },
    body: JSON.stringify(payload)
  });

  const body = await response.json();
  if (!response.ok) {
    throw new Error(`GraphQL HTTP ${response.status}: ${JSON.stringify(body)}`);
  }
  if (body.errors) {
    throw new Error(`GraphQL errors: ${JSON.stringify(body.errors)}`);
  }

  return body;
}
