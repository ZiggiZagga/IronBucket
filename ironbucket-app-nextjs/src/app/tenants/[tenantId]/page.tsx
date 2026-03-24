import { TenantDetailPage } from '@/features/tenant-management/TenantDetailPage';

export default async function TenantByIdPage({ params }: { params: Promise<{ tenantId: string }> }) {
  const { tenantId } = await params;

  return <TenantDetailPage tenantId={tenantId} />;
}
