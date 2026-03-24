import { NextRequest, NextResponse } from 'next/server';
import { getPolicyVersions } from '@/lib/policy/store';

export async function GET(_: NextRequest, { params }: { params: Promise<{ policyId: string }> }) {
  const { policyId } = await params;
  return NextResponse.json(getPolicyVersions(policyId));
}
