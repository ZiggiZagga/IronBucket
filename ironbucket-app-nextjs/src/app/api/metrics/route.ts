import { NextResponse } from 'next/server';
import { getPrometheusContentType, getPrometheusMetrics, observeApiRequest } from '@/lib/observability/metrics';

export async function GET() {
  const started = performance.now();
  const metrics = await getPrometheusMetrics();
  const durationMs = performance.now() - started;

  observeApiRequest('/api/metrics', 'GET', 200, durationMs);

  return new NextResponse(metrics, {
    status: 200,
    headers: {
      'Content-Type': getPrometheusContentType()
    }
  });
}
