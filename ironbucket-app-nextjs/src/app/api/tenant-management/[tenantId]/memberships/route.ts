import { randomUUID } from 'node:crypto';
import { NextRequest, NextResponse } from 'next/server';
import type { TenantMembership, TenantMembershipRole } from '@/features/tenant-management/types';
import { listMemberships, writeMemberships } from '@/lib/tenant/store';

const allowedRoles: TenantMembershipRole[] = ['admin', 'auditor', 'developer', 'viewer'];

function resolveRole(value: unknown): TenantMembershipRole {
  if (typeof value === 'string' && allowedRoles.includes(value as TenantMembershipRole)) {
    return value as TenantMembershipRole;
  }

  return 'viewer';
}

export async function GET(_: NextRequest, { params }: { params: Promise<{ tenantId: string }> }) {
  const { tenantId } = await params;
  return NextResponse.json(listMemberships(tenantId));
}

export async function POST(req: NextRequest, { params }: { params: Promise<{ tenantId: string }> }) {
  const { tenantId } = await params;
  const body = (await req.json()) as {
    userId?: string;
    displayName?: string;
    email?: string;
    role?: string;
    attributes?: Record<string, string>;
  };

  if (!body.userId?.trim() || !body.displayName?.trim()) {
    return NextResponse.json({ error: 'userId and displayName are required' }, { status: 400 });
  }

  const nextMembership: TenantMembership = {
    id: randomUUID(),
    userId: body.userId.trim(),
    displayName: body.displayName.trim(),
    email: body.email?.trim(),
    role: resolveRole(body.role),
    attributes: body.attributes ?? {},
    source: 'ui-pending-sync',
    createdAt: new Date().toISOString()
  };

  const current = listMemberships(tenantId);
  writeMemberships(tenantId, [...current, nextMembership]);
  return NextResponse.json(nextMembership);
}
