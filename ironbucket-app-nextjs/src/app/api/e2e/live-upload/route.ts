import { NextRequest, NextResponse } from 'next/server';

type UploadRequest = {
  actor?: string;
  key?: string;
  content?: string;
  contentType?: string;
};

const ACTOR_CREDENTIALS: Record<string, { username: string; password: string }> = {
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

export async function POST(req: NextRequest) {
  const requestBody = (await req.json()) as UploadRequest;
  const actor = (requestBody.actor ?? 'alice').toLowerCase();
  const key = requestBody.key ?? '';
  const content = requestBody.content ?? '';
  const contentType = requestBody.contentType ?? 'text/plain';

  if (!ACTOR_CREDENTIALS[actor]) {
    return NextResponse.json({ error: `Unsupported actor '${actor}'` }, { status: 400 });
  }
  if (!key) {
    return NextResponse.json({ error: 'Missing object key' }, { status: 400 });
  }

  const bucket = `default-${actor}-files`;

  try {
    const token = await fetchAccessToken(actor);

    const uploadGraphqlResponse = await graphqlCall(token, {
      query: `
        mutation UploadObject($jwtToken: String!, $bucket: String!, $key: String!, $content: String!, $contentType: String) {
          uploadObject(jwtToken: $jwtToken, bucket: $bucket, key: $key, content: $content, contentType: $contentType) {
            key
            bucket
            size
          }
        }
      `,
      variables: {
        jwtToken: token,
        bucket,
        key,
        content,
        contentType
      }
    });

    const uploaded = uploadGraphqlResponse?.data?.uploadObject;
    if (!uploaded || uploaded.key !== key) {
      return NextResponse.json(
        {
          error: 'GraphQL upload did not return expected object metadata',
          details: JSON.stringify(uploadGraphqlResponse)
        },
        { status: 502 }
      );
    }

    const listedGraphqlResponse = await graphqlCall(token, {
      query: `
        query ListObjects($jwtToken: String!, $bucket: String!, $query: String) {
          listObjects(jwtToken: $jwtToken, bucket: $bucket, query: $query) {
            key
            size
          }
        }
      `,
      variables: {
        jwtToken: token,
        bucket,
        query: key
      }
    });

    const listed = listedGraphqlResponse?.data?.listObjects ?? [];
    const found = Array.isArray(listed) && listed.some((item: { key?: string }) => item?.key === key);

    return NextResponse.json({
      actor,
      bucket,
      key,
      verified: found,
      roundtripSize: content.length,
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    return NextResponse.json(
      {
        error: 'Live upload flow failed on gateway GraphQL path',
        details: error instanceof Error ? error.message : String(error)
      },
      { status: 500 }
    );
  }
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