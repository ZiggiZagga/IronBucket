export type TenantStatus = 'ACTIVE' | 'SUSPENDED' | 'ARCHIVED';

export type TenantRecord = {
  id: string;
  name: string;
  status: TenantStatus;
  metadata: Record<string, string>;
  createdAt?: string;
  updatedAt?: string;
};

export type TenantMembershipRole = 'admin' | 'auditor' | 'developer' | 'viewer';

export type TenantMembership = {
  id: string;
  userId: string;
  displayName: string;
  email?: string;
  role: TenantMembershipRole;
  attributes: Record<string, string>;
  source: 'keycloak' | 'ui-pending-sync';
  createdAt: string;
};

export type CreateTenantPayload = {
  name: string;
  status: TenantStatus;
  metadata: Record<string, string>;
};

export type UpdateTenantPayload = {
  name?: string;
  status?: TenantStatus;
  metadata?: Record<string, string>;
};

export type CreateMembershipPayload = {
  userId: string;
  displayName: string;
  email?: string;
  role: TenantMembershipRole;
  attributes: Record<string, string>;
};

export type UpdateMembershipPayload = {
  role?: TenantMembershipRole;
  attributes?: Record<string, string>;
};
