import type { TenantMembership, TenantRecord } from '@/features/tenant-management/types';

const tenantMetaStore = new Map<string, Record<string, string>>();
const membershipStore = new Map<string, TenantMembership[]>();

export function readTenantMetadata(tenantId: string) {
  return tenantMetaStore.get(tenantId) ?? {};
}

export function writeTenantMetadata(tenantId: string, metadata: Record<string, string>) {
  tenantMetaStore.set(tenantId, metadata);
}

export function deleteTenantMetadata(tenantId: string) {
  tenantMetaStore.delete(tenantId);
}

export function enrichTenant(tenant: Omit<TenantRecord, 'metadata'>): TenantRecord {
  return {
    ...tenant,
    metadata: readTenantMetadata(tenant.id)
  };
}

export function listMemberships(tenantId: string): TenantMembership[] {
  return membershipStore.get(tenantId) ?? [];
}

export function writeMemberships(tenantId: string, memberships: TenantMembership[]) {
  membershipStore.set(tenantId, memberships);
}

export function deleteMemberships(tenantId: string) {
  membershipStore.delete(tenantId);
}
