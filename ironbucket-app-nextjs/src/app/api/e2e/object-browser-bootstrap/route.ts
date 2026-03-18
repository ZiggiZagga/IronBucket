import { NextRequest, NextResponse } from 'next/server';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

type BootstrapRequest = {
  actor?: string;
  content?: string;
};

export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/object-browser-bootstrap';
  const traceparent = req.headers.get('traceparent') ?? undefined;
  const correlationId = resolveCorrelationId(req.headers);
  const requestBody = (await req.json()) as BootstrapRequest;
  const actor = resolveActor(requestBody.actor);

  if (!actor) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }

  const now = Date.now();
  const bucket = `default-${actor}-ob-${now}`;
  const key = `object-browser-seed-${now}.txt`;
  const ownerTenant = actor;
  const content = requestBody.content ?? `object-browser-bootstrap-${new Date().toISOString()}`;

  try {
    const token = await fetchActorAccessToken(actor);
    const gatewayOptions = { traceparent, actor, correlationId };

    const createBucketResponse = await callGatewayGraphql(token, {
      query: `
        mutation CreateBucket($jwtToken: String!, $bucketName: String!, $ownerTenant: String!) {
          createBucket(jwtToken: $jwtToken, bucketName: $bucketName, ownerTenant: $ownerTenant) {
            name
            ownerTenant
          }
        }
      `,
      variables: {
        jwtToken: token,
        bucketName: bucket,
        ownerTenant
      }
    }, gatewayOptions);

    const createdBucketName = createBucketResponse?.data?.createBucket?.name;
    if (createdBucketName !== bucket) {
      throw new Error(`createBucket returned unexpected bucket '${createdBucketName ?? 'null'}'`);
    }

    const uploadResponse = await callGatewayGraphql(token, {
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
        contentType: 'text/plain'
      }
    }, gatewayOptions);

    const uploadedKey = uploadResponse?.data?.uploadObject?.key;
    if (uploadedKey !== key) {
      throw new Error(`uploadObject returned unexpected key '${uploadedKey ?? 'null'}'`);
    }

    const listBucketsResponse = await callGatewayGraphql(token, {
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
    }, gatewayOptions);

    const listedBuckets: Array<{ name?: string }> = listBucketsResponse?.data?.listBuckets ?? [];
    const bucketNames = listedBuckets
      .map((entry) => entry?.name)
      .filter((name): name is string => typeof name === 'string');
    const bucketVisibleInListing = bucketNames.includes(bucket);

    const listObjectsResponse = await callGatewayGraphql(token, {
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
    }, gatewayOptions);

    const listedObjects: Array<{ key?: string }> = listObjectsResponse?.data?.listObjects ?? [];
    const objectVisibleInListing = listedObjects.some((entry) => entry?.key === key);

    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('Object browser bootstrap completed.', {
      route,
      status: 200,
      actor,
      bucket,
      key,
      bucketVisibleInListing,
      objectVisibleInListing,
      listedBucketCount: bucketNames.length,
      sampleBuckets: bucketNames.slice(0, 5),
      traceparent,
      correlationId,
      durationMs
    });

    return withCorrelationHeaders(NextResponse.json({
      actor,
      bucket,
      key,
      verified: bucketVisibleInListing && objectVisibleInListing,
      diagnostics: {
        listedBucketCount: bucketNames.length,
        sampleBuckets: bucketNames.slice(0, 5),
        bucketVisibleInListing,
        objectVisibleInListing
      },
      timestamp: new Date().toISOString()
    }), correlationId);
  } catch (error) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('Object browser bootstrap failed.', {
      route,
      status: 500,
      actor,
      traceparent,
      correlationId,
      durationMs,
      error: error instanceof Error ? error.message : String(error)
    });

    return withCorrelationHeaders(NextResponse.json({
      error: 'Object browser bootstrap failed',
      details: error instanceof Error ? error.message : String(error)
    }, { status: 500 }), correlationId);
  }
}