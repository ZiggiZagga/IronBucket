import { NextRequest, NextResponse } from 'next/server';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

type ScreenshotProofRequest = {
  actor?: string;
  screenshotBase64?: string;
  mimeType?: string;
};

export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/screenshot-proof';
  const traceparent = req.headers.get('traceparent') ?? undefined;
  const correlationId = resolveCorrelationId(req.headers);
  const requestBody = (await req.json()) as ScreenshotProofRequest;
  const actor = resolveActor(requestBody.actor);
  const screenshotBase64 = requestBody.screenshotBase64 ?? '';
  const mimeType = requestBody.mimeType ?? 'image/png';

  if (!actor) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }

  if (!screenshotBase64) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(
      NextResponse.json({ error: 'Missing screenshotBase64' }, { status: 400 }),
      correlationId
    );
  }

  const bucket = `default-${actor}-proofs`;
  const key = `${actor}-ui-e2e-proof-${Date.now()}.png.b64`;

  try {
    const token = await fetchActorAccessToken(actor);
    const gatewayOptions = { actor, traceparent, correlationId };

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
    }, gatewayOptions);

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
    }, gatewayOptions);

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
    }, gatewayOptions);

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
    }, gatewayOptions);

    const downloadUrl = String(downloadResponse?.data?.downloadObject?.url ?? '');
    if (!downloadUrl) {
      throw new Error('downloadObject did not return a URL');
    }

    const downloadedBase64 = await fetchDownloadedBase64(downloadUrl, token);
    const previewDataUrl = `data:${mimeType};base64,${downloadedBase64}`;

    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('Screenshot proof flow completed.', {
      route,
      status: 200,
      actor,
      traceparent,
      correlationId,
      durationMs
    });

    return withCorrelationHeaders(NextResponse.json({
      actor,
      bucket,
      key,
      proofStored: found,
      downloadUrl,
      previewDataUrl,
      timestamp: new Date().toISOString()
    }), correlationId);
  } catch (error) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('Screenshot proof flow failed.', {
      route,
      status: 500,
      actor,
      traceparent,
      correlationId,
      durationMs,
      error: error instanceof Error ? error.message : String(error)
    });
    return withCorrelationHeaders(NextResponse.json(
      {
        error: 'Screenshot proof upload/download flow failed',
        details: error instanceof Error ? error.message : String(error)
      },
      { status: 500 }
    ), correlationId);
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

