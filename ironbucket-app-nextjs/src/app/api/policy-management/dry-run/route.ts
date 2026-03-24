import { NextRequest, NextResponse } from 'next/server';
import { evaluatePolicy } from '@/lib/policy/gateway';
import type { PolicyDryRunInput, PolicyDryRunResult } from '@/features/policy-management/types';

export async function POST(req: NextRequest) {
  const actor = req.headers.get('x-ironbucket-actor');
  const body = (await req.json()) as { tenantId: string; input: PolicyDryRunInput };

  if (!body.tenantId || !body.input?.user || !body.input.resource || !body.input.action) {
    return NextResponse.json({ error: 'tenantId and dry run input (user/resource/action) are required' }, { status: 400 });
  }

  try {
    const evaluated = await evaluatePolicy(actor, {
      tenantId: body.tenantId,
      principal: body.input.user,
      resource: body.input.resource,
      action: body.input.action
    });

    const result: PolicyDryRunResult = {
      allow: evaluated.allow,
      reason: evaluated.reason,
      matchedRules: [`${body.input.user}:${body.input.action}`]
    };

    return NextResponse.json(result);
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to dry run policy', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}
