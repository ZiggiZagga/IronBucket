import { NextRequest, NextResponse } from 'next/server';
import { randomBytes } from 'node:crypto';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

type MethodsRequest = {
  actor?: string;
  content?: string;
};

export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/s3-methods';
  const inboundTraceparent = req.headers.get('traceparent') ?? undefined;
  const requestBody = (await req.json()) as MethodsRequest;
  const actor = resolveActor(requestBody.actor);

  if (!actor) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 });
  }

  const bucket = `default-${actor}-methods-${Date.now()}`;
  const key = `${actor}-all-methods-${Date.now()}.txt`;
  const ownerTenant = actor;
  const content = requestBody.content ?? `all-methods-payload-${new Date().toISOString()}`;
  const traceId = randomBytes(16).toString('hex');
  const parentSpanId = randomBytes(8).toString('hex');
  const traceparent = `00-${traceId}-${parentSpanId}-01`;

  try {
    const token = await fetchActorAccessToken(actor);

    const createBucketResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const createBucketWorked = createBucketResponse?.data?.createBucket?.name === bucket;

    const beforeBucketsResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const beforeBuckets = beforeBucketsResponse?.data?.listBuckets ?? [];
    const listBucketsWorked = Array.isArray(beforeBuckets) && beforeBuckets.some((item: { name?: string }) => item?.name === bucket);

    const getBucketResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const getBucketWorked = getBucketResponse?.data?.getBucket?.name === bucket;

    const uploadResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const uploaded = uploadResponse?.data?.uploadObject;
    const uploadWorked = Boolean(uploaded && uploaded.key === key && uploaded.bucket === bucket);

    const listAfterUploadResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const listAfterUpload = listAfterUploadResponse?.data?.listObjects ?? [];
    const listObjectsWorked = Array.isArray(listAfterUpload) && listAfterUpload.some((item: { key?: string }) => item?.key === key);

    const getObjectResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const getObjectWorked = getObjectResponse?.data?.getObject?.key === key;

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
          tenantId: ownerTenant,
          bucketName: bucket,
          requiredCapability: 'OBJECT_READ'
        }
      },
      { traceparent, actor }
    );

    const routingDecisionWorked =
      typeof routingDecisionResponse?.data?.getBucketRoutingDecision?.selectedProvider === 'string'
      && routingDecisionResponse.data.getBucketRoutingDecision.selectedProvider.length > 0;

    const downloadResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const downloadUrl = downloadResponse?.data?.downloadObject?.url ?? '';
    const downloadWorked = typeof downloadUrl === 'string' && downloadUrl.length > 0;

    const deleteResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const deleteWorked = Boolean(deleteResponse?.data?.deleteObject === true);

    const listAfterDeleteResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const listAfterDelete = listAfterDeleteResponse?.data?.listObjects ?? [];
    const deleteVerifiedByList = Array.isArray(listAfterDelete) && !listAfterDelete.some((item: { key?: string }) => item?.key === key);

    const deleteBucketResponse = await callGatewayGraphql(
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
      { traceparent, actor }
    );

    const deleteBucketValue = deleteBucketResponse?.data?.deleteBucket;
    const deleteBucketWorked = typeof deleteBucketValue === 'boolean';

    const bucketsAfterDeleteResponse = await callGatewayGraphql(
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
      { traceparent, actor }
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

    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('S3 methods E2E flow completed.', {
      route,
      status: 200,
      actor,
      traceparent,
      inboundTraceparent,
      durationMs,
      allMethodsVerified
    });

    return NextResponse.json({
      actor,
      bucket,
      key,
      traceId,
      traceparent,
      checks,
      allMethodsVerified,
      expectedServices: [
        'steel-hammer-sentinel-gear',
        'steel-hammer-graphite-forge',
        'steel-hammer-claimspindel',
        'steel-hammer-brazz-nossel'
      ],
      timestamp: new Date().toISOString()
    });
  } catch (error) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('S3 methods E2E flow failed.', {
      route,
      status: 500,
      actor,
      traceparent,
      inboundTraceparent,
      durationMs,
      error: error instanceof Error ? error.message : String(error)
    });
    return NextResponse.json(
      {
        error: 'S3 methods e2e flow failed on gateway GraphQL path',
        details: error instanceof Error ? error.message : String(error),
        traceId,
        traceparent
      },
      { status: 500 }
    );
  }
}

