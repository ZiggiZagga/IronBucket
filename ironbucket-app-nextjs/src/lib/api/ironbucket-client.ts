import type {
  CreateMembershipPayload,
  CreateTenantPayload,
  TenantMembership,
  TenantRecord,
  UpdateMembershipPayload,
  UpdateTenantPayload
} from '@/features/tenant-management/types';

type ApiOptions = {
  actor?: string;
};

async function readJson<T>(response: Response): Promise<T> {
  const payload = (await response.json()) as T & { error?: string; details?: string };
  if (!response.ok) {
    throw new Error(payload.details ?? payload.error ?? `API request failed (${response.status})`);
  }
  return payload as T;
}

function actorHeaders(options?: ApiOptions): HeadersInit {
  if (!options?.actor) {
    return {};
  }

  return {
    'x-ironbucket-actor': options.actor
  };
}

export async function apiListTenants(options?: ApiOptions) {
  const response = await fetch('/api/tenant-management', {
    method: 'GET',
    headers: actorHeaders(options),
    cache: 'no-store'
  });
  return readJson<TenantRecord[]>(response);
}

export async function apiCreateTenant(payload: CreateTenantPayload, options?: ApiOptions) {
  const response = await fetch('/api/tenant-management', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify(payload)
  });
  return readJson<TenantRecord>(response);
}

export async function apiGetTenant(tenantId: string, options?: ApiOptions) {
  const response = await fetch(`/api/tenant-management/${tenantId}`, {
    method: 'GET',
    headers: actorHeaders(options),
    cache: 'no-store'
  });
  return readJson<TenantRecord>(response);
}

export async function apiUpdateTenant(tenantId: string, payload: UpdateTenantPayload, options?: ApiOptions) {
  const response = await fetch(`/api/tenant-management/${tenantId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify(payload)
  });
  return readJson<TenantRecord>(response);
}

export async function apiDeleteTenant(tenantId: string, options?: ApiOptions) {
  const response = await fetch(`/api/tenant-management/${tenantId}`, {
    method: 'DELETE',
    headers: actorHeaders(options)
  });
  return readJson<{ deleted: boolean }>(response);
}

export async function apiListMemberships(tenantId: string, options?: ApiOptions) {
  const response = await fetch(`/api/tenant-management/${tenantId}/memberships`, {
    method: 'GET',
    headers: actorHeaders(options),
    cache: 'no-store'
  });
  return readJson<TenantMembership[]>(response);
}

export async function apiCreateMembership(tenantId: string, payload: CreateMembershipPayload, options?: ApiOptions) {
  const response = await fetch(`/api/tenant-management/${tenantId}/memberships`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify(payload)
  });
  return readJson<TenantMembership>(response);
}

export async function apiUpdateMembership(
  tenantId: string,
  membershipId: string,
  payload: UpdateMembershipPayload,
  options?: ApiOptions
) {
  const response = await fetch(`/api/tenant-management/${tenantId}/memberships/${membershipId}`, {
    method: 'PATCH',
    headers: {
      'Content-Type': 'application/json',
      ...actorHeaders(options)
    },
    body: JSON.stringify(payload)
  });
  return readJson<TenantMembership>(response);
}

export async function apiDeleteMembership(tenantId: string, membershipId: string, options?: ApiOptions) {
  const response = await fetch(`/api/tenant-management/${tenantId}/memberships/${membershipId}`, {
    method: 'DELETE',
    headers: actorHeaders(options)
  });
  return readJson<{ deleted: boolean }>(response);
}
