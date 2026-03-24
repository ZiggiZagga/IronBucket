import { PolicyEditorPage } from '@/features/policy-management/components/PolicyEditorPage';

export default async function EditPolicyPage({ params }: { params: Promise<{ tenantId: string; policyId: string }> }) {
  const { tenantId, policyId } = await params;
  return <PolicyEditorPage tenantId={tenantId} policyId={policyId} />;
}
