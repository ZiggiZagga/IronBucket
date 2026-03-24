import { NextRequest, NextResponse } from 'next/server';
import { appendPolicyVersion, getPolicyVersions } from '@/lib/policy/store';
import { createPolicy, listPolicies } from '@/lib/policy/gateway';
import type { PolicyDraft } from '@/features/policy-management/types';

export async function GET(req: NextRequest) {
  const actor = req.headers.get('x-ironbucket-actor');
  const tenantId = req.nextUrl.searchParams.get('tenantId') ?? undefined;

  try {
    const policies = await listPolicies(actor, tenantId);
    const withVersionCount = policies.map((policy) => ({
      ...policy,
      versionHistoryCount: getPolicyVersions(policy.id).length
    }));
    return NextResponse.json(withVersionCount);
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to list policies', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}

export async function POST(req: NextRequest) {
  const actor = req.headers.get('x-ironbucket-actor');
  const body = (await req.json()) as PolicyDraft;

  if (!body.tenantId || !body.principal || !body.resource || !body.action) {
    return NextResponse.json({ error: 'tenantId, principal, resource and action are required' }, { status: 400 });
  }

  try {
    const created = await createPolicy(actor, body);
    appendPolicyVersion({
      policyId: created.id,
      version: created.version,
      policyJson: JSON.stringify(created, null, 2),
      author: actor ?? 'anonymous',
      createdAt: new Date().toISOString()
    });

    return NextResponse.json(created);
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to create policy', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}
