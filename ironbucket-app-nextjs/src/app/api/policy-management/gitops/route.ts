import { NextRequest, NextResponse } from 'next/server';
import { getGitopsState, setGitopsState } from '@/lib/policy/store';

export async function GET(req: NextRequest) {
  const policyId = req.nextUrl.searchParams.get('policyId');
  if (!policyId) {
    return NextResponse.json({ error: 'policyId is required' }, { status: 400 });
  }

  return NextResponse.json(getGitopsState(policyId));
}

export async function POST(req: NextRequest) {
  const body = (await req.json()) as { policyId?: string; action?: 'pull' | 'push'; branch?: string };
  if (!body.policyId || !body.action) {
    return NextResponse.json({ error: 'policyId and action are required' }, { status: 400 });
  }

  const current = getGitopsState(body.policyId);
  const now = new Date().toISOString();
  const next = {
    ...current,
    branch: body.branch ?? current.branch,
    ...(body.action === 'pull'
      ? { pullStatus: 'pulled' as const, pulledAt: now }
      : { pushStatus: 'pushed' as const, pushedAt: now })
  };

  setGitopsState(next);
  return NextResponse.json(next);
}
