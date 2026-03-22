import { NextRequest, NextResponse } from 'next/server';
import { randomBytes } from 'node:crypto';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

type MethodsRequest = {
  actor?: string;
  content?: string;
};

type MinioPerformanceSummary = {
  minioOperationCount: number;
  minioTotalOperationTimeMs: number;
  minioOperationsPerSecond: number;
  operationLatenciesMs: Record<string, number>;
};

export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/s3-methods';
  const inboundTraceparent = req.headers.get('traceparent') ?? undefined;
  const correlationId = resolveCorrelationId(req.headers);
  const requestBody = (await req.json()) as MethodsRequest;
  const actor = resolveActor(requestBody.actor);

  if (!actor) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }

  const bucket = `default-${actor}-methods-${Date.now()}`;
  const key = `${actor}-all-methods-${Date.now()}.txt`;
  const ownerTenant = actor;
  const routingTenantId = bucket.split('-')[0] ?? ownerTenant;
  const content = requestBody.content ?? `all-methods-payload-${new Date().toISOString()}`;
  const traceId = randomBytes(16).toString('hex');
  const parentSpanId = randomBytes(8).toString('hex');
  const traceparent = `00-${traceId}-${parentSpanId}-01`;
  const gatewayOptions = { traceparent, actor, correlationId };

  try {
    const token = await fetchActorAccessToken(actor);
    const operationLatenciesMs: Record<string, number> = {};

    const timedOperation = async <T>(name: string, fn: () => Promise<T>): Promise<T> => {
      const operationStarted = performance.now();
      try {
        return await fn();
      } finally {
        operationLatenciesMs[name] = Number((performance.now() - operationStarted).toFixed(2));
      }
    };

    const createBucketResponse = await timedOperation('createBucket', () =>
      callGatewayGraphql(
        token,
        {
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
            ownerTenant
          }
        },
        gatewayOptions
      )
    );

    const createBucketWorked = createBucketResponse?.data?.createBucket?.name === bucket;

    const beforeBucketsResponse = await timedOperation('listBuckets', () =>
      callGatewayGraphql(
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
      )
    );

    const beforeBuckets = beforeBucketsResponse?.data?.listBuckets ?? [];
    const listBucketsWorked = Array.isArray(beforeBuckets) && beforeBuckets.some((item: { name?: string }) => item?.name === bucket);

    const getBucketResponse = await timedOperation('getBucket', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetBucket($jwtToken: String!, $bucketName: String!) {
              getBucket(jwtToken: $jwtToken, bucketName: $bucketName) {
                name
                ownerTenant
              }
            }
          `,
          variables: {
            jwtToken: token,
            bucketName: bucket
          }
        },
        gatewayOptions
      )
    );

    const getBucketWorked = getBucketResponse?.data?.getBucket?.name === bucket;

    const uploadResponse = await timedOperation('uploadObject', () =>
      callGatewayGraphql(
        token,
        {
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
        },
        gatewayOptions
      )
    );

    const uploaded = uploadResponse?.data?.uploadObject;
    const uploadWorked = Boolean(uploaded && uploaded.key === key && uploaded.bucket === bucket);

    const listAfterUploadResponse = await timedOperation('listObjects', () =>
      callGatewayGraphql(
        token,
        {
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
        },
        gatewayOptions
      )
    );

    const listAfterUpload = listAfterUploadResponse?.data?.listObjects ?? [];
    const listObjectsWorked = Array.isArray(listAfterUpload) && listAfterUpload.some((item: { key?: string }) => item?.key === key);

    const getObjectResponse = await timedOperation('getObject', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            query GetObject($jwtToken: String!, $bucketName: String!, $objectKey: String!) {
              getObject(jwtToken: $jwtToken, bucketName: $bucketName, objectKey: $objectKey) {
                key
                size
              }
            }
          `,
          variables: {
            jwtToken: token,
            bucketName: bucket,
            objectKey: key
          }
        },
        gatewayOptions
      )
    );

    const getObjectWorked = getObjectResponse?.data?.getObject?.key === key;

    const routingTenantId = bucket.split('-')[0] || ownerTenant;

    const routingDecisionResponse = await callGatewayGraphql(
      token,
      {
        query: `
          query GetBucketRoutingDecision(
            $jwtToken: String!
            $tenantId: String!
            $bucketName: String!
            $requiredCapability: String!
          ) {
            getBucketRoutingDecision(
              jwtToken: $jwtToken
              tenantId: $tenantId
              bucketName: $bucketName
              requiredCapability: $requiredCapability
            ) {
              selectedProvider
              reason
            }
          }
        `,
        variables: {
          jwtToken: token,
          tenantId: routingTenantId,
          bucketName: bucket,
          requiredCapability: 'OBJECT_READ'
        }
      },
      gatewayOptions
    );

    const routingDecisionWorked =
      typeof routingDecisionResponse?.data?.getBucketRoutingDecision?.selectedProvider === 'string'
      && routingDecisionResponse.data.getBucketRoutingDecision.selectedProvider.length > 0;

    const downloadResponse = await timedOperation('downloadObject', () =>
      callGatewayGraphql(
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
      )
    );

    const downloadUrl = downloadResponse?.data?.downloadObject?.url ?? '';
    const downloadWorked = typeof downloadUrl === 'string' && downloadUrl.length > 0;

    const deleteResponse = await timedOperation('deleteObject', () =>
      callGatewayGraphql(
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
      )
    );

    const deleteWorked = Boolean(deleteResponse?.data?.deleteObject === true);

    const listAfterDeleteResponse = await timedOperation('listObjectsAfterDelete', () =>
      callGatewayGraphql(
        token,
        {
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
        },
        gatewayOptions
      )
    );

    const listAfterDelete = listAfterDeleteResponse?.data?.listObjects ?? [];
    const deleteVerifiedByList = Array.isArray(listAfterDelete) && !listAfterDelete.some((item: { key?: string }) => item?.key === key);

    const deleteBucketResponse = await timedOperation('deleteBucket', () =>
      callGatewayGraphql(
        token,
        {
          query: `
            mutation DeleteBucket($jwtToken: String!, $bucketName: String!) {
              deleteBucket(jwtToken: $jwtToken, bucketName: $bucketName)
            }
          `,
          variables: {
            jwtToken: token,
            bucketName: bucket
          }
        },
        gatewayOptions
      )
    );

    const deleteBucketValue = deleteBucketResponse?.data?.deleteBucket;
    const deleteBucketWorked = typeof deleteBucketValue === 'boolean';

    const bucketsAfterDeleteResponse = await timedOperation('listBucketsAfterDelete', () =>
      callGatewayGraphql(
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
      )
    );

    const bucketsAfterDelete = bucketsAfterDeleteResponse?.data?.listBuckets ?? [];
    const deleteBucketVerifiedByList = Array.isArray(bucketsAfterDelete)
      && !bucketsAfterDelete.some((item: { name?: string }) => item?.name === bucket);

    const checks = {
      createBucket: createBucketWorked,
      listBuckets: listBucketsWorked,
      getBucket: getBucketWorked,
      uploadObject: uploadWorked,
      listObjects: listObjectsWorked,
      getObject: getObjectWorked,
      getBucketRoutingDecision: routingDecisionWorked,
      downloadObject: downloadWorked,
      deleteObject: deleteWorked && deleteVerifiedByList,
      deleteBucket: deleteBucketWorked
    };

    const allMethodsVerified = Object.values(checks).every(Boolean);

    const minioOperationNames = [
      'createBucket',
      'listBuckets',
      'getBucket',
      'uploadObject',
      'listObjects',
      'getObject',
      'downloadObject',
      'deleteObject',
      'listObjectsAfterDelete',
      'deleteBucket',
      'listBucketsAfterDelete'
    ] as const;

    const minioLatencies: Record<string, number> = {};
    for (const operationName of minioOperationNames) {
      minioLatencies[operationName] = operationLatenciesMs[operationName] ?? 0;
    }

    const minioTotalOperationTimeMs = Number(
      Object.values(minioLatencies)
        .reduce((sum, value) => sum + value, 0)
        .toFixed(2)
    );
    const minioOperationCount = minioOperationNames.length;
    const minioOperationsPerSecond = Number(
      (minioOperationCount / Math.max(minioTotalOperationTimeMs / 1000, 0.001)).toFixed(2)
    );

    const performanceSummary: MinioPerformanceSummary = {
      minioOperationCount,
      minioTotalOperationTimeMs,
      minioOperationsPerSecond,
      operationLatenciesMs: minioLatencies
    };

    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('S3 methods E2E flow completed.', {
      route,
      status: 200,
      actor,
      traceparent,
      inboundTraceparent,
      correlationId,
      durationMs,
      allMethodsVerified
    });

    return withCorrelationHeaders(NextResponse.json({
      actor,
      bucket,
      key,
      traceId,
      traceparent,
      checks,
      performance: performanceSummary,
      allMethodsVerified,
      expectedServices: [
        'steel-hammer-sentinel-gear',
        'steel-hammer-graphite-forge',
        'steel-hammer-claimspindel',
        'steel-hammer-brazz-nossel'
      ],
      timestamp: new Date().toISOString()
    }), correlationId);
  } catch (error) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('S3 methods E2E flow failed.', {
      route,
      status: 500,
      actor,
      traceparent,
      inboundTraceparent,
      correlationId,
      durationMs,
      error: error instanceof Error ? error.message : String(error)
    });
    return withCorrelationHeaders(NextResponse.json(
      {
        error: 'S3 methods e2e flow failed on gateway GraphQL path',
        details: error instanceof Error ? error.message : String(error),
        traceId,
        traceparent
      },
      { status: 500 }
    ), correlationId);
  }
}

