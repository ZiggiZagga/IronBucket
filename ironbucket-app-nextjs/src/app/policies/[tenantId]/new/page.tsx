import { PolicyEditorPage } from '@/features/policy-management/components/PolicyEditorPage';

export default async function NewPolicyPage({ params }: { params: Promise<{ tenantId: string }> }) {
  const { tenantId } = await params;
  return <PolicyEditorPage tenantId={tenantId} />;
}
