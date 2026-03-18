import { NextRequest, NextResponse } from 'next/server';
import { fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

type ActorTokenRequest = {
  actor?: string;
};

export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/actor-token';
  const traceparent = req.headers.get('traceparent') ?? undefined;
  const correlationId = resolveCorrelationId(req.headers);
  const requestBody = (await req.json()) as ActorTokenRequest;
  const actor = resolveActor(requestBody.actor);

  if (!actor) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 400, durationMs);
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${requestBody.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }

  try {
    const accessToken = await fetchActorAccessToken(actor);

    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('E2E actor token issued.', {
      route,
      status: 200,
      actor,
      traceparent,
      correlationId,
      durationMs
    });

    return withCorrelationHeaders(
      NextResponse.json({ actor, accessToken, timestamp: new Date().toISOString() }),
      correlationId
    );
  } catch (error) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('E2E actor token issuance failed.', {
      route,
      status: 500,
      actor,
      traceparent,
      correlationId,
      durationMs,
      error: error instanceof Error ? error.message : String(error)
    });

    return withCorrelationHeaders(
      NextResponse.json(
        {
          error: 'Unable to issue actor token',
          details: error instanceof Error ? error.message : String(error)
        },
        { status: 500 }
      ),
      correlationId
    );
  }
}
