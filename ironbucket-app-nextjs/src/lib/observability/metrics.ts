import {
  collectDefaultMetrics,
  Counter,
  Histogram,
  Registry
} from 'prom-client';

type MetricState = {
  registry: Registry;
  apiRequestsTotal: Counter<string>;
  apiRequestDurationMs: Histogram<string>;
  gatewayCallDurationMs: Histogram<string>;
};

const GLOBAL_KEY = '__ironbucketNextMetrics';

function initMetrics(): MetricState {
  const globalState = globalThis as typeof globalThis & {
    [GLOBAL_KEY]?: MetricState;
  };

  if (globalState[GLOBAL_KEY]) {
    return globalState[GLOBAL_KEY];
  }

  const registry = new Registry();
  collectDefaultMetrics({ register: registry, prefix: 'ironbucket_ui_' });

  const apiRequestsTotal = new Counter({
    name: 'ironbucket_ui_api_requests_total',
    help: 'Total number of handled API requests in the Next.js UI backend.',
    labelNames: ['route', 'method', 'status'],
    registers: [registry]
  });

  const apiRequestDurationMs = new Histogram({
    name: 'ironbucket_ui_api_request_duration_ms',
    help: 'API request duration in milliseconds.',
    labelNames: ['route', 'method', 'status'],
    buckets: [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000],
    registers: [registry]
  });

  const gatewayCallDurationMs = new Histogram({
    name: 'ironbucket_ui_gateway_call_duration_ms',
    help: 'Gateway GraphQL call duration in milliseconds.',
    labelNames: ['operation', 'status'],
    buckets: [10, 25, 50, 100, 250, 500, 1000, 2500, 5000],
    registers: [registry]
  });

  const metricState: MetricState = {
    registry,
    apiRequestsTotal,
    apiRequestDurationMs,
    gatewayCallDurationMs
  };

  globalState[GLOBAL_KEY] = metricState;
  return metricState;
}

const state = initMetrics();

export function observeApiRequest(route: string, method: string, status: number, durationMs: number) {
  const labels = { route, method, status: String(status) };
  state.apiRequestsTotal.inc(labels);
  state.apiRequestDurationMs.observe(labels, durationMs);
}

export function observeGatewayCall(operation: string, status: number, durationMs: number) {
  const labels = { operation, status: String(status) };
  state.gatewayCallDurationMs.observe(labels, durationMs);
}

export function getPrometheusMetrics() {
  return state.registry.metrics();
}

export function getPrometheusContentType() {
  return state.registry.contentType;
}
