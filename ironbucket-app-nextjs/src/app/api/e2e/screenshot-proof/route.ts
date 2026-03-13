import { NextRequest, NextResponse } from 'next/server';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';

type ScreenshotProofRequest = {
  actor?: string;
  screenshotBase64?: string;
  mimeType?: string;
};

export async function POST(req: NextRequest) {
  const requestBody = (await req.json()) as ScreenshotProofRequest;
  const actor = resolveActor(requestBody.actor);
  const screenshotBase64 = requestBody.screenshotBase64 ?? '';
  const mimeType = requestBody.mimeType ?? 'image/png';

  if (!actor) {
    return NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 });
  }

  if (!screenshotBase64) {
    return NextResponse.json({ error: 'Missing screenshotBase64' }, { status: 400 });
  }

  const bucket = `default-${actor}-proofs`;
  const key = `${actor}-ui-e2e-proof-${Date.now()}.png.b64`;

  try {
    const token = await fetchActorAccessToken(actor);

    await callGatewayGraphql(token, {
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

    await callGatewayGraphql(token, {
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

    const listResponse = await callGatewayGraphql(token, {
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

    const downloadResponse = await callGatewayGraphql(token, {
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

