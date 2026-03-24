import { randomBytes } from 'node:crypto';
import { callGatewayGraphql, fetchActorAccessToken } from '@/lib/e2e/gateway-client';
import { resolveActor } from '@/lib/e2e/runtime';
import type { TenantRecord, TenantStatus } from '@/features/tenant-management/types';
import { enrichTenant } from './store';

function resolveTenantActor(actorHeader: string | null) {
  const resolved = resolveActor(actorHeader ?? undefined, 'alice');
  if (!resolved) {
    return 'alice' as const;
  }

  return resolved;
}

export async function callTenantGraphql(actorHeader: string | null, query: string, variables: Record<string, unknown>) {
  const actor = resolveTenantActor(actorHeader);
  const token = await fetchActorAccessToken(actor);
  const traceId = randomBytes(16).toString('hex');
  const traceparent = `00-${traceId}-${randomBytes(8).toString('hex')}-01`;
  return callGatewayGraphql(token, { query, variables }, { actor, traceparent });
}

function normalizeStatus(value: string | undefined): TenantStatus {
  if (value === 'ACTIVE' || value === 'SUSPENDED' || value === 'ARCHIVED') {
    return value;
  }

  return 'ACTIVE';
}

export async function listTenants(actorHeader: string | null): Promise<TenantRecord[]> {
  const response = await callTenantGraphql(
    actorHeader,
    `query ListTenants { listTenants { id name status } }`,
    {}
  );

  const tenants = (response.data?.listTenants ?? []) as Array<{ id: string; name: string; status?: string }>;
  return tenants.map((tenant) =>
    enrichTenant({
      id: tenant.id,
      name: tenant.name,
      status: normalizeStatus(tenant.status)
    })
  );
}

export async function getTenantById(actorHeader: string | null, tenantId: string): Promise<TenantRecord | null> {
  const response = await callTenantGraphql(
    actorHeader,
    `query GetTenantById($id: ID!) { getTenantById(id: $id) { id name status } }`,
    { id: tenantId }
  );

  const tenant = response.data?.getTenantById as { id: string; name: string; status?: string } | undefined;
  if (!tenant?.id) {
    return null;
  }

  return enrichTenant({
    id: tenant.id,
    name: tenant.name,
    status: normalizeStatus(tenant.status)
  });
}
