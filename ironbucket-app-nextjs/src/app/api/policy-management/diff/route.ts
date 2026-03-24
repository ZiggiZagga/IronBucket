import { NextRequest, NextResponse } from 'next/server';
import { getPolicyVersion } from '@/lib/policy/store';
import { buildUnifiedLineDiff } from '@/lib/policy/diff';

export async function POST(req: NextRequest) {
  const body = (await req.json()) as { policyId?: string; leftVersion?: number; rightVersion?: number };
  if (!body.policyId || !body.leftVersion || !body.rightVersion) {
    return NextResponse.json({ error: 'policyId, leftVersion and rightVersion are required' }, { status: 400 });
  }

  const left = getPolicyVersion(body.policyId, body.leftVersion);
  const right = getPolicyVersion(body.policyId, body.rightVersion);

  if (!left || !right) {
    return NextResponse.json({ error: 'One or both versions not found' }, { status: 404 });
  }

  return NextResponse.json({
    policyId: body.policyId,
    leftVersion: body.leftVersion,
    rightVersion: body.rightVersion,
    unifiedDiff: buildUnifiedLineDiff(left.policyJson, right.policyJson)
  });
}
