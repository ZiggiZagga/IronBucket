export type ActorCredentials = {
  username: string;
  password: string;
};

export const ACTOR_CREDENTIALS = {
  alice: { username: 'alice', password: 'aliceP@ss' },
  bob: { username: 'bob', password: 'bobP@ss' }
} as const satisfies Record<string, ActorCredentials>;

export type SupportedActor = keyof typeof ACTOR_CREDENTIALS;

export const E2E_RUNTIME = {
  tokenUrl:
    process.env.E2E_KEYCLOAK_TOKEN_URL ??
    'http://127.0.0.1:7081/realms/dev/protocol/openid-connect/token',
  sentinelUrl: process.env.E2E_SENTINEL_URL ?? 'http://127.0.0.1:8080',
  gatewayGraphqlUrl:
    process.env.E2E_GATEWAY_GRAPHQL_URL
    ?? `${process.env.E2E_SENTINEL_URL ?? 'http://127.0.0.1:8080'}/graphql`,
  clientId: process.env.E2E_OIDC_CLIENT_ID ?? 'dev-client',
  clientSecret: process.env.E2E_OIDC_CLIENT_SECRET ?? 'dev-secret'
};

export function isSupportedActor(value: string): value is SupportedActor {
  return value in ACTOR_CREDENTIALS;
}

export function resolveActor(input: string | undefined, fallback: SupportedActor = 'alice'): SupportedActor | null {
  const normalized = (input ?? fallback).toLowerCase();
  if (isSupportedActor(normalized)) {
    return normalized;
  }

  return null;
}
