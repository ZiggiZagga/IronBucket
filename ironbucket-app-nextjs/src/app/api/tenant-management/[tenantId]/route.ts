import { NextRequest, NextResponse } from 'next/server';
import { callTenantGraphql, getTenantById } from '@/lib/tenant/gateway';
import { deleteMemberships, deleteTenantMetadata, readTenantMetadata, writeTenantMetadata } from '@/lib/tenant/store';
import type { TenantStatus } from '@/features/tenant-management/types';

function toStatus(value: unknown): TenantStatus {
  if (value === 'ACTIVE' || value === 'SUSPENDED' || value === 'ARCHIVED') {
    return value;
  }

  return 'ACTIVE';
}

export async function GET(req: NextRequest, { params }: { params: Promise<{ tenantId: string }> }) {
  const { tenantId } = await params;

  try {
    const tenant = await getTenantById(req.headers.get('x-ironbucket-actor'), tenantId);
    if (!tenant) {
      return NextResponse.json({ error: 'Tenant not found' }, { status: 404 });
    }

    return NextResponse.json(tenant);
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to fetch tenant', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}

export async function PATCH(req: NextRequest, { params }: { params: Promise<{ tenantId: string }> }) {
  const actor = req.headers.get('x-ironbucket-actor');
  const { tenantId } = await params;
  const body = (await req.json()) as {
    name?: string;
    status?: TenantStatus;
    metadata?: Record<string, string>;
  };

  try {
    const current = await getTenantById(actor, tenantId);
    if (!current) {
      return NextResponse.json({ error: 'Tenant not found' }, { status: 404 });
    }

    const nextName = body.name?.trim() || current.name;
    const nextStatus = toStatus(body.status ?? current.status);

    const response = await callTenantGraphql(
      actor,
      `mutation UpdateTenant($id: ID!, $input: TenantInput!) {
        updateTenant(id: $id, input: $input) { id name status }
      }`,
      {
        id: tenantId,
        input: {
          name: nextName,
          status: nextStatus
        }
      }
    );

    const updated = response.data?.updateTenant as { id?: string; name?: string; status?: string } | undefined;
    if (!updated?.id || !updated.name) {
      return NextResponse.json({ error: 'Tenant service did not return a valid tenant update' }, { status: 502 });
    }

    const mergedMetadata = { ...readTenantMetadata(tenantId), ...(body.metadata ?? {}) };
    writeTenantMetadata(tenantId, mergedMetadata);

    return NextResponse.json({
      id: updated.id,
      name: updated.name,
      status: toStatus(updated.status),
      metadata: mergedMetadata
    });
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to update tenant', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}

export async function DELETE(req: NextRequest, { params }: { params: Promise<{ tenantId: string }> }) {
  const actor = req.headers.get('x-ironbucket-actor');
  const { tenantId } = await params;

  try {
    const response = await callTenantGraphql(
      actor,
      `mutation DeleteTenant($id: ID!) { deleteTenant(id: $id) }`,
      { id: tenantId }
    );

    const deleted = response.data?.deleteTenant === true;
    if (deleted) {
      deleteTenantMetadata(tenantId);
      deleteMemberships(tenantId);
    }

    return NextResponse.json({ deleted });
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to delete tenant', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}
