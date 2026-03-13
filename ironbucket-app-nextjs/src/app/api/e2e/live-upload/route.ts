import { NextRequest, NextResponse } from 'next/server';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';

type UploadRequest = {
  actor?: string;
  key?: string;
  content?: string;
  contentType?: string;
};

export async function POST(req: NextRequest) {
  const requestBody = (await req.json()) as UploadRequest;
  const actor = resolveActor(requestBody.actor);
  const key = requestBody.key ?? '';
  const content = requestBody.content ?? '';
  const contentType = requestBody.contentType ?? 'text/plain';

  if (!actor) {
    return NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 });
  }
  if (!key) {
    return NextResponse.json({ error: 'Missing object key' }, { status: 400 });
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