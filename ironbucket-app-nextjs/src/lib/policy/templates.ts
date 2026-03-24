import type { PolicyDraft } from '@/features/policy-management/types';

export const policyTemplates: Array<{ id: string; name: string; description: string; draft: PolicyDraft }> = [
  {
    id: 'tenant-admin-full-access',
    name: 'Tenant Admin Full Access',
    description: 'Allows admin principals full S3 actions within a tenant bucket namespace.',
    draft: {
      tenantId: 'tenant-a',
      principal: 'admin',
      resource: 'tenant-a-*',
      action: 's3:*',
      effect: 'ALLOW',
      enabled: true
    }
  },
  {
    id: 'auditor-read-only',
    name: 'Auditor Read Only',
    description: 'Allows read-only audit access for auditor principals.',
    draft: {
      tenantId: 'tenant-a',
      principal: 'auditor',
      resource: 'tenant-a-audit-*',
      action: 's3:GetObject',
      effect: 'ALLOW',
      enabled: true
    }
  },
  {
    id: 'developer-upload-only',
    name: 'Developer Upload Only',
    description: 'Allows developer principals to upload without delete permission.',
    draft: {
      tenantId: 'tenant-a',
      principal: 'developer',
      resource: 'tenant-a-dev-*',
      action: 's3:PutObject',
      effect: 'ALLOW',
      enabled: true
    }
  }
];

export const policySchemaAutocomplete = [
  'tenantId',
  'principal',
  'resource',
  'action',
  'effect',
  'enabled',
  'version'
];
