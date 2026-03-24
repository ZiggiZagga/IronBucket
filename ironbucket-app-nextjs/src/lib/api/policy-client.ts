import type {
  PolicyDraft,
  PolicyDryRunInput,
  PolicyDryRunResult,
  PolicyRecord,
  PolicyValidationResult,
  PolicyVersionRecord
} from '@/features/policy-management/types';

type ApiOptions = { actor?: string };

function actorHeaders(options?: ApiOptions): HeadersInit {
  if (!options?.actor) {
    return {};
  }

  return { 'x-ironbucket-actor': options.actor };
}

async function readJson<T>(response: Response): Promise<T> {
  const payload = (await response.json()) as T & { error?: string; details?: string };
  if (!response.ok) {
    throw new Error(payload.details ?? payload.error ?? `Policy API request failed (${response.status})`);
  }
  return payload as T;
}

export async function apiListPolicies(tenantId: string, options?: ApiOptions) {
  const response = await fetch(`/api/policy-management?tenantId=${encodeURIComponent(tenantId)}`, {
    method: 'GET',
    headers: actorHeaders(options),
    cache: 'no-store'
  });
  return readJson<Array<PolicyRecord & { versionHistoryCount?: number }>>(response);
}

export async function apiCreatePolicy(payload: PolicyDraft, options?: ApiOptions) {
  const response = await fetch('/api/policy-management', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify(payload)
  });
  return readJson<PolicyRecord>(response);
}

export async function apiGetPolicy(policyId: string, options?: ApiOptions) {
  const response = await fetch(`/api/policy-management/${policyId}`, {
    method: 'GET',
    headers: actorHeaders(options),
    cache: 'no-store'
  });
  return readJson<PolicyRecord>(response);
}

export async function apiUpdatePolicy(policyId: string, payload: PolicyDraft, options?: ApiOptions) {
  const response = await fetch(`/api/policy-management/${policyId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify(payload)
  });
  return readJson<PolicyRecord>(response);
}

export async function apiDeletePolicy(policyId: string, options?: ApiOptions) {
  const response = await fetch(`/api/policy-management/${policyId}`, {
    method: 'DELETE',
    headers: actorHeaders(options)
  });
  return readJson<{ deleted: boolean }>(response);
}

export async function apiValidatePolicy(payload: PolicyDraft, options?: ApiOptions) {
  const response = await fetch('/api/policy-management/validate', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify(payload)
  });
  return readJson<PolicyValidationResult>(response);
}

export async function apiDryRunPolicy(tenantId: string, input: PolicyDryRunInput, options?: ApiOptions) {
  const response = await fetch('/api/policy-management/dry-run', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify({ tenantId, input })
  });
  return readJson<PolicyDryRunResult>(response);
}

export async function apiListPolicyVersions(policyId: string, options?: ApiOptions) {
  const response = await fetch(`/api/policy-management/${policyId}/versions`, {
    method: 'GET',
    headers: actorHeaders(options),
    cache: 'no-store'
  });
  return readJson<PolicyVersionRecord[]>(response);
}

export async function apiDiffPolicyVersions(policyId: string, leftVersion: number, rightVersion: number, options?: ApiOptions) {
  const response = await fetch('/api/policy-management/diff', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify({ policyId, leftVersion, rightVersion })
  });
  return readJson<{ unifiedDiff: string }>(response);
}

export async function apiReadGitopsState(policyId: string, options?: ApiOptions) {
  const response = await fetch(`/api/policy-management/gitops?policyId=${encodeURIComponent(policyId)}`, {
    method: 'GET',
    headers: actorHeaders(options),
    cache: 'no-store'
  });
  return readJson<{
    policyId: string;
    branch: string;
    pullStatus: 'idle' | 'pulled';
    pushStatus: 'idle' | 'pushed';
    pulledAt?: string;
    pushedAt?: string;
  }>(response);
}

export async function apiRunGitopsAction(
  policyId: string,
  action: 'pull' | 'push',
  branch: string,
  options?: ApiOptions
) {
  const response = await fetch('/api/policy-management/gitops', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify({ policyId, action, branch })
  });
  return readJson<{
    policyId: string;
    branch: string;
    pullStatus: 'idle' | 'pulled';
    pushStatus: 'idle' | 'pushed';
    pulledAt?: string;
    pushedAt?: string;
  }>(response);
}
