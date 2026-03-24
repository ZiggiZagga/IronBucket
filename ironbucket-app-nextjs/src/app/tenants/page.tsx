import { Suspense } from 'react';
import { TenantManagementPage } from '@/features/tenant-management/TenantManagementPage';

export default function TenantsPage() {
  return (
    <Suspense fallback={<section className="rounded-[24px] border border-[color:var(--border)] bg-[color:var(--panel)] p-6 text-sm text-[color:var(--muted-foreground)]">Loading tenant management...</section>}>
      <TenantManagementPage />
    </Suspense>
  );
}
