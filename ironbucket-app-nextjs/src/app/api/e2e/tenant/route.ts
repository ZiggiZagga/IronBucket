import { NextRequest, NextResponse } from 'next/server';
import { randomBytes } from 'node:crypto';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import { resolveCorrelationId, withCorrelationHeaders } from '@/lib/observability/correlation';
import { logger } from '@/lib/observability/logger';
import { observeApiRequest } from '@/lib/observability/metrics';

/**
 * E2E flow: full Tenant lifecycle via GraphQL.
 * Tests: createTenant / addTenant, getTenant / getTenantById / tenant (alias),
 *        listTenants, updateTenant, deleteTenant
 */
export async function POST(req: NextRequest) {
  const started = performance.now();
  const route = '/api/e2e/tenant';
  const correlationId = resolveCorrelationId(req.headers);
  const traceId = randomBytes(16).toString('hex');
  const traceparent = `00-${traceId}-${randomBytes(8).toString('hex')}-01`;
  const gatewayOptions = { traceparent, correlationId };

  const body = (await req.json()) as { actor?: string };
  const actor = resolveActor(body.actor);
  if (!actor) {
    return withCorrelationHeaders(
      NextResponse.json({ error: `Unsupported actor '${body.actor ?? ''}'` }, { status: 400 }),
      correlationId
    );
  }

  try {
    const token = await fetchActorAccessToken(actor);
    const gql = (q: string, vars: Record<string, unknown>) =>
      callGatewayGraphql(token, { query: q, variables: vars }, gatewayOptions);

    const stamp = Date.now();
    const tenantInput = { name: `e2e-tenant-${actor}-${stamp}`, status: 'ACTIVE' };

    // createTenant
    const createRes = await gql(`
      mutation CreateTenant($input: TenantInput!) {
        createTenant(input: $input) { id name status }
      }`, { input: tenantInput });
    const created = createRes?.data?.createTenant;
    const tenantId: string = created?.id ?? '';

    // addTenant (alias)
    const addRes = await gql(`
      mutation AddTenant($input: TenantInput!) {
        addTenant(input: $input) { id name status }
      }`, { input: { name: `e2e-tenant-${actor}-alt-${stamp}`, status: 'ACTIVE' } });
    const added = addRes?.data?.addTenant;
    const addedId: string = added?.id ?? '';

    // getTenantById
    const getByIdRes = tenantId ? await gql(`
      query GetTenantById($id: ID!) {
        getTenantById(id: $id) { id name status }
      }`, { id: tenantId }) : null;
    const fetchedById = getByIdRes?.data?.getTenantById;

    // getTenant alias
    const getTenantRes = tenantId ? await gql(`
      query GetTenant($id: ID!) {
        getTenant(id: $id) { id name status }
      }`, { id: tenantId }) : null;
    const fetchedByTenant = getTenantRes?.data?.getTenant;

    // tenant alias
    const tenantAliasRes = tenantId ? await gql(`
      query Tenant($id: ID!) {
        tenant(id: $id) { id name status }
      }`, { id: tenantId }) : null;
    const fetchedAlias = tenantAliasRes?.data?.tenant;

    // listTenants
    const listRes = await gql(`query { listTenants { id name status } }`, {});
    const listed = listRes?.data?.listTenants ?? [];

    // updateTenant
    const updateRes = tenantId ? await gql(`
      mutation UpdateTenant($id: ID!, $input: TenantInput!) {
        updateTenant(id: $id, input: $input) { id name status }
      }`, { id: tenantId, input: { name: `e2e-tenant-${actor}-${stamp}`, status: 'SUSPENDED' } }) : null;
    const updated = updateRes?.data?.updateTenant;

    // deleteTenant
    const deleteRes = tenantId ? await gql(`
      mutation DeleteTenant($id: ID!) {
        deleteTenant(id: $id)
      }`, { id: tenantId }) : null;
    const deleted = deleteRes?.data?.deleteTenant;

    if (addedId) {
      await gql(`mutation DeleteTenant($id: ID!) { deleteTenant(id: $id) }`, { id: addedId });
    }

    const checks = {
      createTenant:   Boolean(created?.id),
      addTenant:      Boolean(added?.id),
      getTenantById:  Boolean(fetchedById?.id),
      getTenant:      Boolean(fetchedByTenant?.id),
      tenantAlias:    Boolean(fetchedAlias?.id),
      listTenants:    Array.isArray(listed),
      updateTenant:   updated?.status === 'SUSPENDED',
      deleteTenant:   deleted === true,
    };

    const allVerified = Object.values(checks).every(Boolean);
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 200, durationMs);
    logger.info('Tenant e2e flow completed.', { route, actor, traceId, allVerified, durationMs });

    return withCorrelationHeaders(NextResponse.json({
      actor, traceId, traceparent, checks, allVerified,
      timestamp: new Date().toISOString()
    }), correlationId);
  } catch (err) {
    const durationMs = performance.now() - started;
    observeApiRequest(route, 'POST', 500, durationMs);
    logger.error('Tenant e2e flow failed.', { route, actor, traceId, durationMs, error: err instanceof Error ? err.message : String(err) });
    return withCorrelationHeaders(NextResponse.json(
      { error: 'Tenant e2e flow failed', details: err instanceof Error ? err.message : String(err), traceId, traceparent },
      { status: 500 }
    ), correlationId);
  }
}
