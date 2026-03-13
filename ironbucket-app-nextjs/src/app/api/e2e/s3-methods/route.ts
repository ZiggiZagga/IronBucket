import { NextRequest, NextResponse } from 'next/server';
import { randomBytes } from 'node:crypto';

type MethodsRequest = {
  actor?: string;
  content?: string;
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
  const requestBody = (await req.json()) as MethodsRequest;
  const actor = (requestBody.actor ?? 'alice').toLowerCase();

  if (!ACTOR_CREDENTIALS[actor]) {
    return NextResponse.json({ error: `Unsupported actor '${actor}'` }, { status: 400 });
  }

  const bucket = `default-${actor}-methods-${Date.now()}`;
  const key = `${actor}-all-methods-${Date.now()}.txt`;
  const ownerTenant = actor;
  const content = requestBody.content ?? `all-methods-payload-${new Date().toISOString()}`;
  const traceId = randomBytes(16).toString('hex');
  const parentSpanId = randomBytes(8).toString('hex');
  const traceparent = `00-${traceId}-${parentSpanId}-01`;

  try {
    const token = await fetchAccessToken(actor);

    const createBucketResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const createBucketWorked = createBucketResponse?.data?.createBucket?.name === bucket;

    const beforeBucketsResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const beforeBuckets = beforeBucketsResponse?.data?.listBuckets ?? [];
    const listBucketsWorked = Array.isArray(beforeBuckets) && beforeBuckets.some((item: { name?: string }) => item?.name === bucket);

    const getBucketResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const getBucketWorked = getBucketResponse?.data?.getBucket?.name === bucket;

    const uploadResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const uploaded = uploadResponse?.data?.uploadObject;
    const uploadWorked = Boolean(uploaded && uploaded.key === key && uploaded.bucket === bucket);

    const listAfterUploadResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const listAfterUpload = listAfterUploadResponse?.data?.listObjects ?? [];
    const listObjectsWorked = Array.isArray(listAfterUpload) && listAfterUpload.some((item: { key?: string }) => item?.key === key);

    const getObjectResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const getObjectWorked = getObjectResponse?.data?.getObject?.key === key;

    const routingDecisionResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const routingDecisionWorked =
      typeof routingDecisionResponse?.data?.getBucketRoutingDecision?.selectedProvider === 'string'
      && routingDecisionResponse.data.getBucketRoutingDecision.selectedProvider.length > 0;

    const downloadResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const downloadUrl = downloadResponse?.data?.downloadObject?.url ?? '';
    const downloadWorked = typeof downloadUrl === 'string' && downloadUrl.length > 0;

    const deleteResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const deleteWorked = Boolean(deleteResponse?.data?.deleteObject === true);

    const listAfterDeleteResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const listAfterDelete = listAfterDeleteResponse?.data?.listObjects ?? [];
    const deleteVerifiedByList = Array.isArray(listAfterDelete) && !listAfterDelete.some((item: { key?: string }) => item?.key === key);

    const deleteBucketResponse = await graphqlCall(
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
      traceparent,
      actor
    );

    const deleteBucketValue = deleteBucketResponse?.data?.deleteBucket;
    const deleteBucketWorked = typeof deleteBucketValue === 'boolean';

    const bucketsAfterDeleteResponse = await graphqlCall(
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
      traceparent,
      actor
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

async function graphqlCall(
  token: string,
  payload: { query: string; variables?: Record<string, unknown> },
  traceparent: string,
  actor: string
) {
  const response = await fetch(GATEWAY_GRAPHQL_URL, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${token}`,
      traceparent,
      'x-ironbucket-actor': actor
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
