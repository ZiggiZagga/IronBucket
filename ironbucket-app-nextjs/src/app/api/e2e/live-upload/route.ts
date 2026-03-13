import { NextRequest, NextResponse } from 'next/server';
import { execFile } from 'node:child_process';
import { promisify } from 'node:util';

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
const CLIENT_ID = process.env.E2E_OIDC_CLIENT_ID ?? 'dev-client';
const CLIENT_SECRET = process.env.E2E_OIDC_CLIENT_SECRET ?? 'dev-secret';
const execFileAsync = promisify(execFile);

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

function isBucketAlreadyExists(payload: string): boolean {
  return payload.includes('BucketAlreadyOwnedByYou') || payload.includes('BucketAlreadyExists');
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

    const createBucketResponse = await fetch(`${SENTINEL_URL}/s3/bucket/${bucket}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`
      }
    });

    if (!(createBucketResponse.status === 200 || createBucketResponse.status === 201)) {
      const payload = await createBucketResponse.text();
      if (!(isBucketAlreadyExists(payload) || createBucketResponse.status === 500)) {
        return NextResponse.json(
          {
            error: `Bucket create failed (${createBucketResponse.status})`,
            details: payload
          },
          { status: 502 }
        );
      }
    }

    const uploadResponse = await fetch(`${SENTINEL_URL}/s3/object/${bucket}/${encodeURIComponent(key)}`, {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': contentType
      },
      body: content
    });

    if (!uploadResponse.ok) {
      const uploadDetails = await uploadResponse.text();
      return NextResponse.json(
        {
          error: `Upload failed (${uploadResponse.status})`,
          details: uploadDetails
        },
        { status: 502 }
      );
    }

    const getResponse = await fetch(`${SENTINEL_URL}/s3/object/${bucket}/${encodeURIComponent(key)}`, {
      method: 'GET',
      headers: {
        Authorization: `Bearer ${token}`
      }
    });

    if (!getResponse.ok) {
      const getDetails = await getResponse.text();
      return NextResponse.json(
        {
          error: `Read-back failed (${getResponse.status})`,
          details: getDetails
        },
        { status: 502 }
      );
    }

    const roundtrip = await getResponse.text();

    return NextResponse.json({
      actor,
      bucket,
      key,
      verified: roundtrip === content,
      roundtripSize: roundtrip.length,
      timestamp: new Date().toISOString()
    });
  } catch (_error) {
    try {
      const dockerResult = await runLiveFlowViaDockerNetwork(actor, key, content, contentType);
      return NextResponse.json(dockerResult);
    } catch (dockerError) {
      return NextResponse.json(
        {
          error: 'Live upload flow failed',
          details: dockerError instanceof Error ? dockerError.message : String(dockerError)
        },
        { status: 500 }
      );
    }
  }
}

async function detectSteelHammerNetwork(): Promise<string> {
  const { stdout } = await execFileAsync('docker', [
    'inspect',
    '--format',
    '{{range $k, $v := .NetworkSettings.Networks}}{{println $k}}{{end}}',
    'steel-hammer-sentinel-gear'
  ]);

  const network = stdout.split(/\r?\n/).map((line) => line.trim()).find(Boolean);
  if (!network) {
    throw new Error('Unable to detect docker network from steel-hammer-sentinel-gear');
  }
  return network;
}

async function dockerCurl(network: string, args: string[]): Promise<string> {
  const cmd = ['run', '--rm', '--network', network, 'curlimages/curl:8.12.1', ...args];
  const { stdout } = await execFileAsync('docker', cmd, { maxBuffer: 10 * 1024 * 1024 });
  return stdout;
}

async function runLiveFlowViaDockerNetwork(
  actor: string,
  key: string,
  content: string,
  contentType: string
): Promise<{ actor: string; bucket: string; key: string; verified: boolean; roundtripSize: number; timestamp: string }> {
  const credentials = ACTOR_CREDENTIALS[actor];
  if (!credentials) {
    throw new Error(`Unsupported actor: ${actor}`);
  }

  const network = await detectSteelHammerNetwork();
  const bucket = `default-${actor}-files`;

  const tokenRaw = await dockerCurl(network, [
    '-sS',
    '-X',
    'POST',
    'http://steel-hammer-keycloak:7081/realms/dev/protocol/openid-connect/token',
    '-H',
    'Content-Type: application/x-www-form-urlencoded',
    '--data',
    `client_id=${CLIENT_ID}`,
    '--data',
    `client_secret=${CLIENT_SECRET}`,
    '--data',
    `username=${credentials.username}`,
    '--data',
    `password=${credentials.password}`,
    '--data',
    'grant_type=password',
    '--data',
    'scope=openid profile email roles'
  ]);

  const tokenJson = JSON.parse(tokenRaw) as { access_token?: string };
  if (!tokenJson.access_token) {
    throw new Error('Docker network token request did not return access_token');
  }
  const token = tokenJson.access_token;

  const bucketCreateRaw = await dockerCurl(network, [
    '-sS',
    '-w',
    '\n%{http_code}',
    '-X',
    'POST',
    `http://steel-hammer-sentinel-gear:8080/s3/bucket/${bucket}`,
    '-H',
    `Authorization: Bearer ${token}`
  ]);

  const bucketCreateCode = bucketCreateRaw.split(/\r?\n/).pop() ?? '000';
  const bucketCreateBody = bucketCreateRaw.split(/\r?\n/).slice(0, -1).join('\n');

  if (!(
    bucketCreateCode === '200' ||
    bucketCreateCode === '201' ||
    bucketCreateCode === '500' ||
    isBucketAlreadyExists(bucketCreateBody)
  )) {
    throw new Error(`Docker network bucket create failed (${bucketCreateCode}): ${bucketCreateBody}`);
  }

  const uploadRaw = await dockerCurl(network, [
    '-sS',
    '-w',
    '\n%{http_code}',
    '-X',
    'POST',
    `http://steel-hammer-sentinel-gear:8080/s3/object/${bucket}/${encodeURIComponent(key)}`,
    '-H',
    `Authorization: Bearer ${token}`,
    '-H',
    `Content-Type: ${contentType}`,
    '--data-raw',
    content
  ]);

  const uploadCode = uploadRaw.split(/\r?\n/).pop() ?? '000';
  if (uploadCode !== '200') {
    throw new Error(`Docker network upload failed (${uploadCode})`);
  }

  const getRaw = await dockerCurl(network, [
    '-sS',
    '-w',
    '\n%{http_code}',
    '-X',
    'GET',
    `http://steel-hammer-sentinel-gear:8080/s3/object/${bucket}/${encodeURIComponent(key)}`,
    '-H',
    `Authorization: Bearer ${token}`
  ]);

  const getCode = getRaw.split(/\r?\n/).pop() ?? '000';
  const getBody = getRaw.split(/\r?\n/).slice(0, -1).join('\n');

  if (getCode !== '200') {
    throw new Error(`Docker network read-back failed (${getCode})`);
  }

  return {
    actor,
    bucket,
    key,
    verified: getBody === content,
    roundtripSize: getBody.length,
    timestamp: new Date().toISOString()
  };
}