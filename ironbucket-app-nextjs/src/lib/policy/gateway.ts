import { callTenantGraphql } from '@/lib/tenant/gateway';
import type { PolicyDraft, PolicyRecord } from '@/features/policy-management/types';

function toPolicyRecord(payload: {
  id?: string;
  tenantId?: string;
  principal?: string;
  resource?: string;
  action?: string;
  effect?: string;
  version?: number;
  enabled?: boolean;
}): PolicyRecord {
  return {
    id: payload.id ?? '',
    tenantId: payload.tenantId ?? '',
    principal: payload.principal ?? '',
    resource: payload.resource ?? '',
    action: payload.action ?? '',
    effect: payload.effect === 'DENY' ? 'DENY' : 'ALLOW',
    version: payload.version ?? 1,
    enabled: payload.enabled ?? true,
    source: 'graphql',
    lastUpdatedAt: new Date().toISOString()
  };
}

export async function listPolicies(actorHeader: string | null, tenantId?: string) {
  const response = await callTenantGraphql(
    actorHeader,
    tenantId
      ? `query SearchPolicies($query: String) { searchPolicies(query: $query) { id tenantId principal resource action effect version enabled } }`
      : `query ListPolicies { listPolicies { id tenantId principal resource action effect version enabled } }`,
    tenantId ? { query: tenantId } : {}
  );

  const entries = (tenantId ? response.data?.searchPolicies : response.data?.listPolicies) ?? [];
  return (entries as Array<Record<string, unknown>>).map((entry) =>
    toPolicyRecord(entry as {
      id?: string;
      tenantId?: string;
      principal?: string;
      resource?: string;
      action?: string;
      effect?: string;
      version?: number;
      enabled?: boolean;
    })
  );
}

export async function createPolicy(actorHeader: string | null, draft: PolicyDraft) {
  const response = await callTenantGraphql(
    actorHeader,
    `mutation CreatePolicy($input: PolicyInput!) {
      createPolicy(input: $input) { id tenantId principal resource action effect version enabled }
    }`,
    { input: draft }
  );

  return toPolicyRecord(response.data?.createPolicy ?? {});
}

export async function updatePolicy(actorHeader: string | null, policyId: string, draft: PolicyDraft) {
  const response = await callTenantGraphql(
    actorHeader,
    `mutation UpdatePolicy($id: ID!, $input: PolicyInput!) {
      updatePolicy(id: $id, input: $input) { id tenantId principal resource action effect version enabled }
    }`,
    { id: policyId, input: draft }
  );

  return toPolicyRecord(response.data?.updatePolicy ?? {});
}

export async function getPolicy(actorHeader: string | null, policyId: string) {
  const response = await callTenantGraphql(
    actorHeader,
    `query GetPolicyById($id: ID!) {
      getPolicyById(id: $id) { id tenantId principal resource action effect version enabled }
    }`,
    { id: policyId }
  );

  const payload = response.data?.getPolicyById;
  if (!payload?.id) {
    return null;
  }

  return toPolicyRecord(payload);
}

export async function deletePolicy(actorHeader: string | null, policyId: string) {
  const response = await callTenantGraphql(
    actorHeader,
    `mutation DeletePolicy($id: ID!) { deletePolicy(id: $id) }`,
    { id: policyId }
  );

  return response.data?.deletePolicy === true;
}

export async function validatePolicy(actorHeader: string | null, draft: PolicyDraft) {
  const response = await callTenantGraphql(
    actorHeader,
    `mutation ValidatePolicy($input: PolicyInput!) { validatePolicy(input: $input) { valid errors } }`,
    { input: draft }
  );

  const payload = response.data?.validatePolicy as { valid?: boolean; errors?: string[] } | undefined;
  return {
    valid: payload?.valid === true,
    errors: payload?.errors ?? []
  };
}

export async function evaluatePolicy(
  actorHeader: string | null,
  input: { tenantId: string; principal: string; resource: string; action: string }
) {
  const response = await callTenantGraphql(
    actorHeader,
    `query EvaluatePolicy($input: PolicyEvaluationInput!) { evaluatePolicy(input: $input) { allow reason } }`,
    { input }
  );

  const payload = response.data?.evaluatePolicy as { allow?: boolean; reason?: string } | undefined;
  return {
    allow: payload?.allow === true,
    reason: payload?.reason ?? 'No reason returned from policy service.'
  };
}
