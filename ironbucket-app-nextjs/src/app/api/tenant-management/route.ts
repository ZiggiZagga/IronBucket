import { NextRequest, NextResponse } from 'next/server';
import { listTenants, callTenantGraphql } from '@/lib/tenant/gateway';
import { writeTenantMetadata } from '@/lib/tenant/store';
import type { TenantStatus } from '@/features/tenant-management/types';

function toStatus(value: unknown): TenantStatus {
  if (value === 'ACTIVE' || value === 'SUSPENDED' || value === 'ARCHIVED') {
    return value;
  }

  return 'ACTIVE';
}

export async function GET(req: NextRequest) {
  try {
    const tenants = await listTenants(req.headers.get('x-ironbucket-actor'));
    return NextResponse.json(tenants);
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to list tenants', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}

export async function POST(req: NextRequest) {
  const actor = req.headers.get('x-ironbucket-actor');
  const body = (await req.json()) as {
    name?: string;
    status?: string;
    metadata?: Record<string, string>;
  };

  if (!body.name?.trim()) {
    return NextResponse.json({ error: 'Tenant name is required' }, { status: 400 });
  }

  try {
    const status = toStatus(body.status);
    const response = await callTenantGraphql(
      actor,
      `mutation CreateTenant($input: TenantInput!) { createTenant(input: $input) { id name status } }`,
      { input: { name: body.name.trim(), status } }
    );

    const created = response.data?.createTenant as { id?: string; name?: string; status?: string } | undefined;
    if (!created?.id || !created.name) {
      return NextResponse.json({ error: 'Tenant service did not return a valid tenant' }, { status: 502 });
    }

    writeTenantMetadata(created.id, body.metadata ?? {});
    return NextResponse.json({
      id: created.id,
      name: created.name,
      status: toStatus(created.status),
      metadata: body.metadata ?? {}
    });
  } catch (error) {
    return NextResponse.json(
      { error: 'Failed to create tenant', details: error instanceof Error ? error.message : String(error) },
      { status: 500 }
    );
  }
}
