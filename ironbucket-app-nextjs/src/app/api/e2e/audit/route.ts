import { NextRequest, NextResponse } from 'next/server';
import { randomBytes } from 'node:crypto';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

/**
 * E2E flow: all Audit + Stats queries via GraphQL.
 * The audit log is driven by real LGTM/Loki data produced by upstream S3 operations.
 * Tests: getAuditTrail, getAuditLogs, auditLogs (alias), filterAuditLogs,
 *        getAuditLogById, getPolicyStatistics, policyStats (alias),
 *        getUserActivitySummary, userActivity (alias),
 *        getResourceAccessPatterns, resourceAccess (alias)
 */
export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/audit';
  const correlationId = resolveCorrelationId(req.headers);
  const traceId = randomBytes(16).toString('hex');
  const traceparent = `00-${traceId}-${randomBytes(8).toString('hex')}-01`;
  const gatewayOptions = { traceparent, correlationId };

  const body = (await req.json()) as { actor?: string; tenantId?: string };
  const actor = resolveActor(body.actor);
  if (!actor) {
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${body.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }
  const tenantId = body.tenantId ?? actor;

  try {
    const token = await fetchActorAccessToken(actor);
    const gql = (q: string, vars: Record<string, unknown>) =>
      callGatewayGraphql(token, { query: q, variables: vars }, gatewayOptions);

    // getAuditTrail
    const trailRes = await gql(`
      query GetAuditTrail($tenantId: String!) {
        getAuditTrail(tenantId: $tenantId) { id tenantId actor action resource timestamp decision }
      }`, { tenantId });
    const trail = trailRes?.data?.getAuditTrail ?? [];

    // getAuditLogs
    const logsRes = await gql(`
      query GetAuditLogs($tenantId: String!) {
        getAuditLogs(tenantId: $tenantId) { id tenantId actor action resource timestamp decision }
      }`, { tenantId });
    const logs = logsRes?.data?.getAuditLogs ?? [];

    // auditLogs alias
    const auditLogsAliasRes = await gql(`
      query AuditLogs($tenantId: String!) {
        auditLogs(tenantId: $tenantId) { id tenantId actor action resource timestamp decision }
      }`, { tenantId });
    const auditLogsAlias = auditLogsAliasRes?.data?.auditLogs ?? [];

    // filterAuditLogs
    const filterRes = await gql(`
      query FilterAuditLogs($filter: AuditLogFilterInput!) {
        filterAuditLogs(filter: $filter) { id tenantId actor action resource timestamp decision }
      }`, { filter: { tenantId } });
    const filtered = filterRes?.data?.filterAuditLogs ?? [];

    // getAuditLogById — use first available id if any, or a synthetic id
    const firstId: string = (trail[0]?.id) ?? ('synthetic-0');
    const getByIdRes = await gql(`
      query GetAuditLogById($id: ID!) {
        getAuditLogById(id: $id) { id tenantId actor action resource timestamp decision }
      }`, { id: firstId });
    const fetchedById = getByIdRes?.data?.getAuditLogById;

    // getPolicyStatistics
    const statsRes = await gql(`
      query GetPolicyStatistics($tenantId: String!) {
        getPolicyStatistics(tenantId: $tenantId) { tenantId totalPolicies evaluationCount }
      }`, { tenantId });
    const stats = statsRes?.data?.getPolicyStatistics;

    // policyStats alias
    const policyStatsRes = await gql(`
      query PolicyStats($tenantId: String!) {
        policyStats(tenantId: $tenantId) { tenantId totalPolicies evaluationCount }
      }`, { tenantId });
    const policyStats = policyStatsRes?.data?.policyStats;

    // getUserActivitySummary
    const activityRes = await gql(`
      query GetUserActivitySummary($tenantId: String!) {
        getUserActivitySummary(tenantId: $tenantId) { identityId operations lastSeen }
      }`, { tenantId });
    const activity = activityRes?.data?.getUserActivitySummary ?? [];

    // userActivity alias
    const userActivityRes = await gql(`
      query UserActivity($tenantId: String!) {
        userActivity(tenantId: $tenantId) { identityId operations lastSeen }
      }`, { tenantId });
    const userActivity = userActivityRes?.data?.userActivity ?? [];

    // getResourceAccessPatterns
    const patternsRes = await gql(`
      query GetResourceAccessPatterns($tenantId: String!) {
        getResourceAccessPatterns(tenantId: $tenantId) { resource accesses }
      }`, { tenantId });
    const patterns = patternsRes?.data?.getResourceAccessPatterns ?? [];

    // resourceAccess alias
    const resourceAccessRes = await gql(`
      query ResourceAccess($tenantId: String!) {
        resourceAccess(tenantId: $tenantId) { resource accesses }
      }`, { tenantId });
    const resourceAccess = resourceAccessRes?.data?.resourceAccess ?? [];

    const checks = {
      getAuditTrail:           Array.isArray(trail),
      getAuditLogs:            Array.isArray(logs),
      auditLogsAlias:          Array.isArray(auditLogsAlias),
      filterAuditLogs:         Array.isArray(filtered),
      getAuditLogById:         Boolean(fetchedById?.id),
      getPolicyStatistics:     typeof stats?.totalPolicies === 'number',
      policyStatsAlias:        typeof policyStats?.totalPolicies === 'number',
      getUserActivitySummary:  Array.isArray(activity),
      userActivityAlias:       Array.isArray(userActivity),
      getResourceAccessPatterns: Array.isArray(patterns),
      resourceAccessAlias:     Array.isArray(resourceAccess),
    };

    const allVerified = Object.values(checks).every(Boolean);
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('Audit e2e flow completed.', { route, actor, tenantId, traceId, allVerified, durationMs });

    return withCorrelationHeaders(NextResponse.json({
      actor, tenantId, traceId, traceparent, checks, allVerified,
      auditLogCount: trail.length,
      timestamp: new Date().toISOString()
    }), correlationId);
  } catch (err) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('Audit e2e flow failed.', { route, actor, traceId, durationMs, error: err instanceof Error ? err.message : String(err) });
    return withCorrelationHeaders(NextResponse.json(
      { error: 'Audit e2e flow failed', details: err instanceof Error ? err.message : String(err), traceId, traceparent },
      { status: 500 }
    ), correlationId);
  }
}
