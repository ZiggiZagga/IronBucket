import { randomUUID } from 'node:crypto';
import { NodeSDK } from '@opentelemetry/sdk-node';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import {
  collectDefaultMetrics,
  Counter,
  Histogram,
  Registry
} from 'prom-client';
import type { Request, Response, NextFunction } from 'express';

type MetricsState = {
  registry: Registry;
  requestsTotal: Counter<string>;
  requestDurationMs: Histogram<string>;
};

const SERVICE_NAME = process.env.OTEL_SERVICE_NAME ?? 'ironbucket-app';
const GLOBAL_METRICS_KEY = '__ironbucketAppMetrics';
const GLOBAL_OTEL_KEY = '__ironbucketAppOtelStarted';
const REQUEST_ID_HEADER = 'x-request-id';
const CORRELATION_ID_HEADER = 'x-correlation-id';

function log(level: 'info' | 'warn' | 'error', message: string, context: Record<string, unknown> = {}) {
  const payload = {
    ts: new Date().toISOString(),
    level,
    service: SERVICE_NAME,
    message,
    ...context
  };

  const line = JSON.stringify(payload);
  if (level === 'error') {
    console.error(line);
    return;
  }

  if (level === 'warn') {
    console.warn(line);
    return;
  }

  console.log(line);
}

function resolveTraceExporterUrl(): string {
  const explicit = process.env.OTEL_EXPORTER_OTLP_TRACES_ENDPOINT;
  if (explicit) {
    return explicit;
  }

  const generic = process.env.OTEL_EXPORTER_OTLP_ENDPOINT;
  if (generic) {
    if (generic.endsWith('/v1/traces')) {
      return generic;
    }

    if (generic.endsWith(':4317')) {
      return `${generic.replace(':4317', ':4318')}/v1/traces`;
    }

    if (generic.endsWith(':4318')) {
      return `${generic}/v1/traces`;
    }

    return `${generic}/v1/traces`;
  }

  return 'http://localhost:4318/v1/traces';
}

export async function startObservability(): Promise<void> {
  const globalState = globalThis as typeof globalThis & { [GLOBAL_OTEL_KEY]?: boolean };
  if (globalState[GLOBAL_OTEL_KEY]) {
    return;
  }

  const traceExporter = new OTLPTraceExporter({
    url: resolveTraceExporterUrl()
  });

  const sdk = new NodeSDK({
    serviceName: SERVICE_NAME,
    traceExporter,
    instrumentations: [getNodeAutoInstrumentations()]
  });

  try {
    await sdk.start();
    globalState[GLOBAL_OTEL_KEY] = true;
    log('info', 'OpenTelemetry initialized.', {
      otlpTraceEndpoint: resolveTraceExporterUrl()
    });
  } catch (error) {
    log('error', 'OpenTelemetry initialization failed.', {
      error: error instanceof Error ? error.message : String(error)
    });
  }
}

function initMetrics(): MetricsState {
  const globalState = globalThis as typeof globalThis & {
    [GLOBAL_METRICS_KEY]?: MetricsState;
  };

  if (globalState[GLOBAL_METRICS_KEY]) {
    return globalState[GLOBAL_METRICS_KEY];
  }

  const registry = new Registry();
  collectDefaultMetrics({ register: registry, prefix: 'ironbucket_ui_' });

  const requestsTotal = new Counter({
    name: 'ironbucket_ui_http_requests_total',
    help: 'Total number of incoming HTTP requests.',
    labelNames: ['method', 'route', 'status'],
    registers: [registry]
  });

  const requestDurationMs = new Histogram({
    name: 'ironbucket_ui_http_request_duration_ms',
    help: 'HTTP request duration in milliseconds.',
    labelNames: ['method', 'route', 'status'],
    buckets: [5, 10, 25, 50, 100, 250, 500, 1000, 2500, 5000],
    registers: [registry]
  });

  const state: MetricsState = {
    registry,
    requestsTotal,
    requestDurationMs
  };

  globalState[GLOBAL_METRICS_KEY] = state;
  return state;
}

const metrics = initMetrics();

export function requestObservabilityMiddleware(req: Request, res: Response, next: NextFunction) {
  const started = process.hrtime.bigint();
  const correlationId = req.header(CORRELATION_ID_HEADER) ?? req.header(REQUEST_ID_HEADER) ?? randomUUID();
  res.setHeader(REQUEST_ID_HEADER, correlationId);
  res.setHeader(CORRELATION_ID_HEADER, correlationId);

  res.on('finish', () => {
    const durationMs = Number(process.hrtime.bigint() - started) / 1_000_000;
    const route = req.route?.path ?? req.path;
    const labels = {
      method: req.method,
      route,
      status: String(res.statusCode)
    };

    metrics.requestsTotal.inc(labels);
    metrics.requestDurationMs.observe(labels, durationMs);

    log('info', 'HTTP request completed.', {
      method: req.method,
      route,
      status: res.statusCode,
      durationMs,
      requestId: correlationId,
      correlationId,
      traceId: req.header('x-b3-traceid') ?? undefined
    });
  });

  next();
}

export async function metricsSnapshot(): Promise<string> {
  return metrics.registry.metrics();
}

export function metricsContentType(): string {
  return metrics.registry.contentType;
}

export function logUnhandledError(err: unknown, req: Request) {
  const correlationId = req.header(CORRELATION_ID_HEADER) ?? req.header(REQUEST_ID_HEADER) ?? randomUUID();
  log('error', 'Unhandled request error.', {
    method: req.method,
    route: req.path,
    correlationId,
    traceId: req.header('x-b3-traceid') ?? undefined,
    error: err instanceof Error ? err.message : String(err)
  });
}
