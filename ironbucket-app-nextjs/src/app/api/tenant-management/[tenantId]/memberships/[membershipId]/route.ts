import { NextRequest, NextResponse } from 'next/server';
import type { TenantMembershipRole } from '@/features/tenant-management/types';
import { listMemberships, writeMemberships } from '@/lib/tenant/store';

const allowedRoles: TenantMembershipRole[] = ['admin', 'auditor', 'developer', 'viewer'];

function resolveRole(value: unknown): TenantMembershipRole | null {
  if (typeof value === 'string' && allowedRoles.includes(value as TenantMembershipRole)) {
    return value as TenantMembershipRole;
  }

  return null;
}

export async function PATCH(
  req: NextRequest,
  { params }: { params: Promise<{ tenantId: string; membershipId: string }> }
) {
  const { tenantId, membershipId } = await params;
  const body = (await req.json()) as {
    role?: string;
    attributes?: Record<string, string>;
  };

  const role = body.role ? resolveRole(body.role) : null;
  if (body.role && !role) {
    return NextResponse.json({ error: 'Unsupported role value' }, { status: 400 });
  }

  const current = listMemberships(tenantId);
  const index = current.findIndex((membership) => membership.id === membershipId);
  if (index < 0) {
    return NextResponse.json({ error: 'Membership not found' }, { status: 404 });
  }

  const updated = {
    ...current[index],
    ...(role ? { role } : {}),
    ...(body.attributes ? { attributes: body.attributes } : {})
  };

  const next = [...current];
  next[index] = updated;
  writeMemberships(tenantId, next);
  return NextResponse.json(updated);
}

export async function DELETE(
  _: NextRequest,
  { params }: { params: Promise<{ tenantId: string; membershipId: string }> }
) {
  const { tenantId, membershipId } = await params;
  const current = listMemberships(tenantId);
  const next = current.filter((membership) => membership.id !== membershipId);
  writeMemberships(tenantId, next);
  return NextResponse.json({ deleted: next.length !== current.length });
}
