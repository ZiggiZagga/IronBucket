import { NextRequest, NextResponse } from 'next/server';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { getPrometheusContentType, getPrometheusMetrics, observeApiRequest } from '@/lib/observability/metrics';

export async function GET(req: NextRequest) {
  const started = performance.now();
  const correlationId = resolveCorrelationId(req.headers);
  const metrics = await getPrometheusMetrics();
  const durationMs = performance.now() - started;

  observeApiRequest('/api/metrics', 'GET', 200, durationMs);

  return withCorrelationHeaders(new NextResponse(metrics, {
    status: 200,
    headers: {
      'Content-Type': getPrometheusContentType()
    }
  }), correlationId);
}
