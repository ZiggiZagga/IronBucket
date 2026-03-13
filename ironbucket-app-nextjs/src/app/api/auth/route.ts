import { NextRequest, NextResponse } from 'next/server';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

export async function POST(req: NextRequest) {
  const started = performance.now();
  const traceparent = req.headers.get('traceparent') ?? undefined;
  const correlationId = resolveCorrelationId(req.headers);
  const { username, password } = await req.json();

  if (!username || !password) {
    const durationMs = performance.now() - started;
    observeApiRequest('/api/auth', 'POST', 400, durationMs);
    logger.warn('Authentication request rejected due to missing credentials.', {
      route: '/api/auth',
      status: 400,
      traceparent,
      correlationId
    });
    return withCorrelationHeaders(
      NextResponse.json({ error: 'Username and password required' }, { status: 400 }),
      correlationId
    );
  }
  try {
    const { discovery, ClientSecretPost, genericGrantRequest } = await import('openid-client');
    const issuerUrl = process.env.KEYCLOAK_ISSUER_URL || 'https://localhost:7082/realms/ironbucket-lab';
    const clientId = process.env.KEYCLOAK_CLIENT_ID || 'sentinel-gear-app';
    const clientSecret = process.env.KEYCLOAK_CLIENT_SECRET || 'sentinel-gear-app-secret';
    const config = await discovery(new URL(issuerUrl), clientId, clientSecret, ClientSecretPost(clientSecret), { execute: [] });
    const tokenSet = await genericGrantRequest(
      config,
      'password',
      { username, password, scope: 'openid' }
    );

    const durationMs = performance.now() - started;
    observeApiRequest('/api/auth', 'POST', 200, durationMs);
    logger.info('Authentication request completed successfully.', {
      route: '/api/auth',
      status: 200,
      traceparent,
      correlationId,
      durationMs
    });

    return withCorrelationHeaders(
      NextResponse.json({
        token: tokenSet.id_token || tokenSet.access_token,
        accessToken: tokenSet.access_token,
        idToken: tokenSet.id_token,
      }),
      correlationId
    );
  } catch (err: any) {
    const durationMs = performance.now() - started;
    observeApiRequest('/api/auth', 'POST', 401, durationMs);
    logger.error('Authentication request failed.', {
      route: '/api/auth',
      status: 401,
      traceparent,
      correlationId,
      durationMs,
      error: err?.message
    });
    return withCorrelationHeaders(
      NextResponse.json({ error: 'Authentication failed', details: err.message }, { status: 401 }),
      correlationId
    );
  }
}
