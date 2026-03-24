import { NextRequest, NextResponse } from 'next/server';
import { appendPolicyVersion } from '@/lib/policy/store';
import { deletePolicy, getPolicy, updatePolicy } from '@/lib/policy/gateway';
import type { PolicyDraft } from '@/features/policy-management/types';

export async function GET(req: NextRequest, { params }: { params: Promise<{ policyId: string }> }) {
  const actor = req.headers.get('x-ironbucket-actor');
  const { policyId } = await params;

  try {
    const policy = await getPolicy(actor, policyId);
    if (!policy) {
      return NextResponse.json({ error: 'Policy not found' }, { status: 404 });
    }

    return NextResponse.json(policy);
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to fetch policy', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}

export async function PATCH(req: NextRequest, { params }: { params: Promise<{ policyId: string }> }) {
  const actor = req.headers.get('x-ironbucket-actor');
  const { policyId } = await params;
  const body = (await req.json()) as PolicyDraft;

  try {
    const updated = await updatePolicy(actor, policyId, body);
    appendPolicyVersion({
      policyId,
      version: updated.version,
      policyJson: JSON.stringify(updated, null, 2),
      author: actor ?? 'anonymous',
      createdAt: new Date().toISOString()
    });

    return NextResponse.json(updated);
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to update policy', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}

export async function DELETE(req: NextRequest, { params }: { params: Promise<{ policyId: string }> }) {
  const actor = req.headers.get('x-ironbucket-actor');
  const { policyId } = await params;

  try {
    const deleted = await deletePolicy(actor, policyId);
    return NextResponse.json({ deleted });
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to delete policy', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}
