import { NextRequest, NextResponse } from 'next/server';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

type ObjectBrowserOpsRequest = {
  actor?: string;
  bucket?: string;
  key?: string;
  query?: string;
  action?: 'listBuckets' | 'listObjects' | 'downloadObject' | 'deleteObject';
};

export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/object-browser-ops';
  const traceparent = req.headers.get('traceparent') ?? undefined;
  const correlationId = resolveCorrelationId(req.headers);
  const requestBody = (await req.json()) as ObjectBrowserOpsRequest;
  const action = requestBody.action;
  const actor = resolveActor(requestBody.actor);

  if (!action) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(NextResponse.json({ error: 'Missing action' }, { status: 400 }), correlationId);
  }

  if (!actor) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }

  try {
    const token = await fetchActorAccessToken(actor);
    const gatewayOptions = { traceparent, actor, correlationId };

    if (action === 'listBuckets') {
      const response = await callGatewayGraphql(
        token,
        {
          query: `
            query ListBuckets($jwtToken: String!) {
              listBuckets(jwtToken: $jwtToken) {
                name
              }
            }
          `,
          variables: {
            jwtToken: token
          }
        },
        gatewayOptions
      );

      const buckets = response?.data?.listBuckets ?? [];
      const durationMs = performance.now() - started;
      observeApiRequest(route, 'POST', 200, durationMs);
      return withCorrelationHeaders(NextResponse.json({ actor, action, buckets }), correlationId);
    }

    const bucket = (requestBody.bucket ?? '').trim();
    if (!bucket) {
      const durationMs = performance.now() - started;
      observeApiRequest(route, 'POST', 400, durationMs);
      return withCorrelationHeaders(NextResponse.json({ error: 'Missing bucket' }, { status: 400 }), correlationId);
    }

    if (action === 'listObjects') {
      const response = await callGatewayGraphql(
        token,
        {
          query: `
            query ListObjects($jwtToken: String!, $bucket: String!, $query: String) {
              listObjects(jwtToken: $jwtToken, bucket: $bucket, query: $query) {
                key
                size
                lastModified
              }
            }
          `,
          variables: {
            jwtToken: token,
            bucket,
            query: requestBody.query ?? ''
          }
        },
        gatewayOptions
      );

      const objects = response?.data?.listObjects ?? [];
      const durationMs = performance.now() - started;
      observeApiRequest(route, 'POST', 200, durationMs);
      return withCorrelationHeaders(NextResponse.json({ actor, action, bucket, objects }), correlationId);
    }

    const key = (requestBody.key ?? '').trim();
    if (!key) {
      const durationMs = performance.now() - started;
      observeApiRequest(route, 'POST', 400, durationMs);
      return withCorrelationHeaders(NextResponse.json({ error: 'Missing object key' }, { status: 400 }), correlationId);
    }

    if (action === 'downloadObject') {
      const response = await callGatewayGraphql(
        token,
        {
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
        },
        gatewayOptions
      );

      const url = response?.data?.downloadObject?.url ?? '';
      const durationMs = performance.now() - started;
      observeApiRequest(route, 'POST', 200, durationMs);
      return withCorrelationHeaders(NextResponse.json({ actor, action, bucket, key, url }), correlationId);
    }

    const response = await callGatewayGraphql(
      token,
      {
        query: `
          mutation DeleteObject($jwtToken: String!, $bucket: String!, $key: String!) {
            deleteObject(jwtToken: $jwtToken, bucket: $bucket, key: $key)
          }
        `,
        variables: {
          jwtToken: token,
          bucket,
          key
        }
      },
      gatewayOptions
    );

    const deleted = response?.data?.deleteObject === true;
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    return withCorrelationHeaders(NextResponse.json({ actor, action, bucket, key, deleted }), correlationId);
  } catch (error) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('Object browser e2e operation failed.', {
      route,
      status: 500,
      actor,
      action,
      traceparent,
      correlationId,
      durationMs,
      error: error instanceof Error ? error.message : String(error)
    });

    return withCorrelationHeaders(
      NextResponse.json(
        {
          error: 'Object browser operation failed',
          details: error instanceof Error ? error.message : String(error)
        },
        { status: 500 }
      ),
      correlationId
    );
  }
}
