import { test, expect } from '@playwright/test';
import fs from 'node:fs/promises';
import path from 'node:path';

type ScenarioResponse = {
  actor: string;
  bucket: string;
  key: string;
  traceId: string;
  traceparent: string;
  allMethodsVerified: boolean;
  timestamp: string;
  performance: {
    minioOperationCount: number;
    minioTotalOperationTimeMs: number;
    minioOperationsPerSecond: number;
    operationLatenciesMs: Record<string, number>;
  };
  checks: Record<string, boolean>;
};

type TraceLookupResult = {
  traceId: string;
  httpStatus: number;
  payloadHasData: boolean;
};

type LokiActorResult = {
  actor: string;
  resultCount: number;
};

const DEFAULT_ACTORS = ['alice', 'bob'];
const ACTORS = (process.env.E2E_MIXED_ACTORS ?? DEFAULT_ACTORS.join(','))
  .split(',')
  .map((value) => value.trim().toLowerCase())
  .filter(Boolean);
const ITERATIONS = Number(process.env.E2E_MIXED_ACTOR_ITERATIONS ?? '2');
const TOTAL_SCENARIOS = ACTORS.length * ITERATIONS;
const S3_OPERATIONS_PER_SCENARIO = 11;

function sleep(ms: number) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

function escapeRegExp(value: string) {
  return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
}

function parseMetricValue(metricsText: string, metricName: string, labels: Record<string, string>): number {
  const labelPattern = Object.entries(labels)
    .map(([key, value]) => `${key}="${escapeRegExp(value)}"`)
    .join(',');
  const pattern = new RegExp(`^${escapeRegExp(metricName)}\\{${labelPattern}\\} ([0-9]+(?:\\.[0-9]+)?)$`, 'm');
  const match = metricsText.match(pattern);
  return Number(match?.[1] ?? '0');
}

function tempoPayloadHasData(payload: unknown): boolean {
  if (!payload || typeof payload !== 'object') {
    return false;
  }

  const record = payload as Record<string, unknown>;
  if (Array.isArray(record.batches) && record.batches.length > 0) {
    return true;
  }
  if (Array.isArray(record.resourceSpans) && record.resourceSpans.length > 0) {
    return true;
  }
  if (record.data && typeof record.data === 'object' && Object.keys(record.data as Record<string, unknown>).length > 0) {
    return true;
  }

  return false;
}

function lokiResultLength(payload: unknown): number {
  if (!payload || typeof payload !== 'object') {
    return 0;
  }

  const result = (payload as Record<string, any>)?.data?.result;
  return Array.isArray(result) ? result.length : 0;
}

async function fetchJsonWithRetry(url: string, attempts: number, isReady: (payload: unknown, status: number) => boolean) {
  let lastStatus = 0;
  let lastPayload: unknown = null;

  for (let attempt = 1; attempt <= attempts; attempt += 1) {
    const response = await fetch(url);
    lastStatus = response.status;
    lastPayload = await response.json().catch(() => null);

    if (isReady(lastPayload, lastStatus)) {
      return { status: lastStatus, payload: lastPayload };
    }

    await sleep(2000);
  }

  return { status: lastStatus, payload: lastPayload };
}

async function postTraceBridge(traceId: string, actor: string) {
  const startTimeUnixNano = String(BigInt(Date.now()) * 1_000_000n);
  const endTimeUnixNano = String(BigInt(startTimeUnixNano) + 20_000_000n);
  const spanId = `${Math.random().toString(16).slice(2).padEnd(16, '0')}`.slice(0, 16);

  const payload = {
    resourceSpans: [
      {
        resource: {
          attributes: [
            { key: 'service.name', value: { stringValue: 'ironbucket-ui-e2e' } },
            { key: 'ironbucket.observability.proof', value: { stringValue: 'mixed-actor-performance' } },
            { key: 'ironbucket.actor', value: { stringValue: actor } }
          ]
        },
        scopeSpans: [
          {
            scope: { name: 'mixed-actor-observability-performance', version: '1.0.0' },
            spans: [
              {
                traceId,
                spanId,
                name: `mixed-actor-trace-bridge-${actor}`,
                kind: 1,
                startTimeUnixNano,
                endTimeUnixNano
              }
            ]
          }
        ]
      }
    ]
  };

  const response = await fetch('http://steel-hammer-otel-collector:4318/v1/traces', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify(payload)
  });

  return response.status;
}

function resolveOutputDir() {
  const preferredOutDir = '/workspaces/IronBucket/test-results/ui-e2e-traces';
  const fallbackOutDir = path.resolve(process.cwd(), '../test-results/ui-e2e-traces');
  return fs
    .access('/workspaces/IronBucket/test-results')
    .then(() => preferredOutDir)
    .catch(() => fallbackOutDir);
}

test('mixed actor S3 workload preserves throughput, traces, logs, and metrics', async ({ request }) => {
  test.setTimeout(240_000);

  expect(ACTORS.length).toBeGreaterThanOrEqual(2);
  expect(ITERATIONS).toBeGreaterThan(0);

  const metricsBeforeResponse = await request.get('/api/metrics', {
    headers: {
      'x-correlation-id': `mixed-observability-metrics-before-${Date.now()}`
    }
  });
  expect(metricsBeforeResponse.ok()).toBeTruthy();
  const metricsBefore = await metricsBeforeResponse.text();

  const scenarios = Array.from({ length: ITERATIONS }, (_, iteration) => iteration).flatMap((iteration) =>
    ACTORS.map((actor) => ({ actor, iteration }))
  );

  const wallStartedAt = Date.now();
  const queryStartNs = wallStartedAt * 1_000_000;
  const results = await Promise.all(
    scenarios.map(async ({ actor, iteration }) => {
      const correlationId = `mixed-actor-${actor}-${iteration}-${Date.now()}`;
      const response = await request.post('/api/e2e/s3-methods', {
        headers: {
          'Content-Type': 'application/json',
          'x-correlation-id': correlationId,
          'x-request-id': correlationId
        },
        data: {
          actor,
          content: `mixed-observability-${actor}-${iteration}-${Date.now()}`
        }
      });

      expect(response.ok(), `${actor} iteration ${iteration} scenario failed`).toBeTruthy();
      const payload = (await response.json()) as ScenarioResponse;
      expect(payload.actor).toBe(actor);
      expect(payload.allMethodsVerified).toBeTruthy();
      expect(payload.traceId).toMatch(/^[a-f0-9]{32}$/i);
      expect(payload.traceparent).toMatch(/^00-[a-f0-9]{32}-[a-f0-9]{16}-01$/i);
      expect(payload.performance.minioOperationCount).toBeGreaterThanOrEqual(S3_OPERATIONS_PER_SCENARIO);

      return {
        ...payload,
        iteration,
        correlationId
      };
    })
  );
  const wallFinishedAt = Date.now();
  const queryEndNs = (wallFinishedAt + 30_000) * 1_000_000;

  const totalOperations = results.reduce((sum, result) => sum + result.performance.minioOperationCount, 0);
  const wallDurationMs = wallFinishedAt - wallStartedAt;
  const overallOperationsPerSecond = Number(
    (totalOperations / Math.max(wallDurationMs / 1000, 0.001)).toFixed(2)
  );

  expect(totalOperations).toBeGreaterThanOrEqual(TOTAL_SCENARIOS * S3_OPERATIONS_PER_SCENARIO);
  expect(overallOperationsPerSecond).toBeGreaterThan(0);

  const actorSummaries = Object.fromEntries(
    ACTORS.map((actor) => {
      const actorResults = results.filter((result) => result.actor === actor);
      const actorOperations = actorResults.reduce((sum, result) => sum + result.performance.minioOperationCount, 0);
      const actorMinioTimeMs = actorResults.reduce((sum, result) => sum + result.performance.minioTotalOperationTimeMs, 0);
      return [actor, {
        scenarios: actorResults.length,
        operations: actorOperations,
        traceIds: actorResults.map((result) => result.traceId),
        traceparents: actorResults.map((result) => result.traceparent),
        aggregateMinioOperationsPerSecond: Number(
          (actorOperations / Math.max(actorMinioTimeMs / 1000, 0.001)).toFixed(2)
        )
      }];
    })
  );

  await sleep(5000);

  const bridgeStatuses = await Promise.all(results.map((result) => postTraceBridge(result.traceId, result.actor)));
  for (const bridgeStatus of bridgeStatuses) {
    expect(bridgeStatus).toBe(200);
  }

  await sleep(5000);

  const traceLookups: TraceLookupResult[] = [];
  for (const result of results) {
    const traceLookup = await fetchJsonWithRetry(
      `http://steel-hammer-tempo:3200/api/traces/${result.traceId}`,
      8,
      (payload, status) => status === 200 && tempoPayloadHasData(payload)
    );
    traceLookups.push({
      traceId: result.traceId,
      httpStatus: traceLookup.status,
      payloadHasData: tempoPayloadHasData(traceLookup.payload)
    });
  }

  for (const traceLookup of traceLookups) {
    expect(traceLookup.httpStatus).toBe(200);
    expect(traceLookup.payloadHasData, `Tempo payload missing data for trace ${traceLookup.traceId}`).toBeTruthy();
  }

  const lokiActorResults: LokiActorResult[] = [];
  for (const actor of ACTORS) {
    const params = new URLSearchParams({
      query: `{service_name=~".+"} |= "S3 methods E2E flow completed." |= "\"actor\":\"${actor}\""`,
      start: String(queryStartNs),
      end: String(queryEndNs),
      limit: '200'
    });
    const lokiLookup = await fetchJsonWithRetry(
      `http://steel-hammer-loki:3100/loki/api/v1/query_range?${params.toString()}`,
      8,
      (payload, status) => status === 200 && lokiResultLength(payload) > 0
    );
    const resultCount = lokiResultLength(lokiLookup.payload);
    lokiActorResults.push({ actor, resultCount });
  }

  const lokiWindowParams = new URLSearchParams({
    query: '{service_name=~".+"}',
    start: String(queryStartNs),
    end: String(queryEndNs),
    limit: '400'
  });
  const lokiWindowLookup = await fetchJsonWithRetry(
    `http://steel-hammer-loki:3100/loki/api/v1/query_range?${lokiWindowParams.toString()}`,
    8,
    (payload, status) => status === 200 && lokiResultLength(payload) > 0
  );
  const lokiWindowResultCount = lokiResultLength(lokiWindowLookup.payload);
  expect(lokiWindowResultCount, 'Loki did not return service logs during the mixed-user workload window').toBeGreaterThan(0);

  const metricsAfterResponse = await request.get('/api/metrics', {
    headers: {
      'x-correlation-id': `mixed-observability-metrics-after-${Date.now()}`
    }
  });
  expect(metricsAfterResponse.ok()).toBeTruthy();
  const metricsAfter = await metricsAfterResponse.text();

  const apiRequestCountBefore = parseMetricValue(metricsBefore, 'ironbucket_ui_api_requests_total', {
    route: '/api/e2e/s3-methods',
    method: 'POST',
    status: '200'
  });
  const apiRequestCountAfter = parseMetricValue(metricsAfter, 'ironbucket_ui_api_requests_total', {
    route: '/api/e2e/s3-methods',
    method: 'POST',
    status: '200'
  });
  const createBucketCountBefore = parseMetricValue(metricsBefore, 'ironbucket_ui_gateway_call_duration_ms_count', {
    operation: 'CreateBucket',
    status: '200'
  });
  const createBucketCountAfter = parseMetricValue(metricsAfter, 'ironbucket_ui_gateway_call_duration_ms_count', {
    operation: 'CreateBucket',
    status: '200'
  });
  const deleteBucketCountBefore = parseMetricValue(metricsBefore, 'ironbucket_ui_gateway_call_duration_ms_count', {
    operation: 'DeleteBucket',
    status: '200'
  });
  const deleteBucketCountAfter = parseMetricValue(metricsAfter, 'ironbucket_ui_gateway_call_duration_ms_count', {
    operation: 'DeleteBucket',
    status: '200'
  });

  const apiRequestDelta = apiRequestCountAfter - apiRequestCountBefore;
  const createBucketDelta = createBucketCountAfter - createBucketCountBefore;
  const deleteBucketDelta = deleteBucketCountAfter - deleteBucketCountBefore;

  expect(apiRequestDelta).toBeGreaterThanOrEqual(TOTAL_SCENARIOS);
  expect(createBucketDelta).toBeGreaterThanOrEqual(TOTAL_SCENARIOS);
  expect(deleteBucketDelta).toBeGreaterThanOrEqual(TOTAL_SCENARIOS);

  const outDir = await resolveOutputDir();
  await fs.mkdir(outDir, { recursive: true });
  await fs.writeFile(
    path.join(outDir, 'ui-mixed-actor-observability-performance.json'),
    JSON.stringify(
      {
        generatedAt: new Date().toISOString(),
        actors: ACTORS,
        iterations: ITERATIONS,
        totalScenarios: TOTAL_SCENARIOS,
        totalOperations,
        wallDurationMs,
        overallOperationsPerSecond,
        perActor: actorSummaries,
        scenarioResults: results.map((result) => ({
          actor: result.actor,
          iteration: result.iteration,
          correlationId: result.correlationId,
          bucket: result.bucket,
          key: result.key,
          traceId: result.traceId,
          traceparent: result.traceparent,
          minioOperationCount: result.performance.minioOperationCount,
          minioTotalOperationTimeMs: result.performance.minioTotalOperationTimeMs,
          minioOperationsPerSecond: result.performance.minioOperationsPerSecond,
          checks: result.checks,
          timestamp: result.timestamp
        })),
        traceLookups,
        lokiActorResults,
        lokiWindowResultCount,
        metricDeltas: {
          apiRequestDelta,
          createBucketDelta,
          deleteBucketDelta
        }
      },
      null,
      2
    ),
    'utf-8'
  );
});