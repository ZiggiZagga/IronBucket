import { NextRequest, NextResponse } from 'next/server';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

type UploadRequest = {
  actor?: string;
  key?: string;
  content?: string;
  contentType?: string;
};

export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/live-upload';
  const traceparent = req.headers.get('traceparent') ?? undefined;
  const correlationId = resolveCorrelationId(req.headers);
  const requestBody = (await req.json()) as UploadRequest;
  const actor = resolveActor(requestBody.actor);
  const key = requestBody.key ?? '';
  const content = requestBody.content ?? '';
  const contentType = requestBody.contentType ?? 'text/plain';

  if (!actor) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }
  if (!key) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(NextResponse.json({ error: 'Missing object key' }, { status: 400 }), correlationId);
  }

  const bucket = `default-${actor}-files`;

  try {
    const token = await fetchActorAccessToken(actor);

    const uploadGraphqlResponse = await callGatewayGraphql(token, {
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
    }, { traceparent, actor, correlationId });

    const uploaded = uploadGraphqlResponse?.data?.uploadObject;
    if (!uploaded || uploaded.key !== key) {
      return withCorrelationHeaders(NextResponse.json(
        {
          error: 'GraphQL upload did not return expected object metadata',
          details: JSON.stringify(uploadGraphqlResponse)
        },
        { status: 502 }
      ), correlationId);
    }

    const listedGraphqlResponse = await callGatewayGraphql(token, {
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
    }, { traceparent, actor, correlationId });

    const listed = listedGraphqlResponse?.data?.listObjects ?? [];
    const found = Array.isArray(listed) && listed.some((item: { key?: string }) => item?.key === key);

    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('Live upload verification completed.', {
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
      verified: found,
      roundtripSize: content.length,
      timestamp: new Date().toISOString()
    }), correlationId);
  } catch (error) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('Live upload verification failed.', {
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
        error: 'Live upload flow failed on gateway GraphQL path',
        details: error instanceof Error ? error.message : String(error)
      },
      { status: 500 }
    ), correlationId);
  }
}