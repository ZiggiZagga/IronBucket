import { PolicyListPage } from '@/features/policy-management/components/PolicyListPage';

export default async function TenantPolicyListPage({ params }: { params: Promise<{ tenantId: string }> }) {
  const { tenantId } = await params;
  return <PolicyListPage tenantId={tenantId} />;
}
