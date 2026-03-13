import { ACTOR_CREDENTIALS, E2E_RUNTIME, type SupportedActor } from './runtime';

type GraphqlPayload = {
  query: string;
  variables?: Record<string, unknown>;
};

type GraphqlCallOptions = {
  actor?: SupportedActor;
  traceparent?: string;
};

export async function fetchActorAccessToken(actor: SupportedActor): Promise<string> {
  const credentials = ACTOR_CREDENTIALS[actor];
  if (!credentials) {
    throw new Error(`Unsupported actor: ${actor}`);
  }

  const body = new URLSearchParams({
    client_id: E2E_RUNTIME.clientId,
    client_secret: E2E_RUNTIME.clientSecret,
    grant_type: 'password',
    scope: 'openid profile email roles',
    username: credentials.username,
    password: credentials.password
  });

  const tokenResponse = await fetch(E2E_RUNTIME.tokenUrl, {
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

export async function callGatewayGraphql(
  token: string,
  payload: GraphqlPayload,
  options: GraphqlCallOptions = {}
): Promise<{ data?: any; errors?: unknown[] }> {
  const headers: Record<string, string> = {
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`
  };

  if (options.traceparent) {
    headers.traceparent = options.traceparent;
  }

  if (options.actor) {
    headers['x-ironbucket-actor'] = options.actor;
  }

  const response = await fetch(E2E_RUNTIME.gatewayGraphqlUrl, {
    method: 'POST',
    headers,
    body: JSON.stringify(payload)
  });

  const body = (await response.json()) as { data?: any; errors?: unknown[] };
  if (!response.ok) {
    throw new Error(`GraphQL HTTP ${response.status}: ${JSON.stringify(body)}`);
  }

  if (body.errors) {
    throw new Error(`GraphQL errors: ${JSON.stringify(body.errors)}`);
  }

  return body;
}
