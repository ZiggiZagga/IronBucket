'use client';

import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, Plus, ShieldCheck, Trash2 } from 'lucide-react';
import Link from 'next/link';
import { useAppSession } from '@/components/auth/session-provider';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { FormField, FormHint, FormLabel } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeaderCell, TableRow } from '@/components/ui/table';
import { useAppToast } from '@/components/ui/toast';
import {
  apiCreateMembership,
  apiDeleteMembership,
  apiGetTenant,
  apiListMemberships,
  apiUpdateMembership
} from '@/lib/api/ironbucket-client';
import type { CreateMembershipPayload, TenantMembershipRole } from './types';

const roles: TenantMembershipRole[] = ['admin', 'auditor', 'developer', 'viewer'];

function parseAttributes(text: string) {
  return text
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)
    .reduce<Record<string, string>>((acc, line) => {
      const [key, ...rest] = line.split('=');
      if (!key?.trim() || rest.length === 0) {
        return acc;
      }
      acc[key.trim()] = rest.join('=').trim();
      return acc;
    }, {});
}

export function TenantDetailPage({ tenantId }: { tenantId: string }) {
  const queryClient = useQueryClient();
  const { session } = useAppSession();
  const { pushToast } = useAppToast();
  const actor = session?.user.username;

  const roleSet = new Set(session?.user.roles ?? []);
  const canManageMembership = roleSet.has('admin') || roleSet.has('adminrole');

  const [form, setForm] = useState<CreateMembershipPayload>({
    userId: '',
    displayName: '',
    email: '',
    role: 'developer',
    attributes: {}
  });
  const [attributesText, setAttributesText] = useState('department=engineering');

  const tenantQuery = useQuery({
    queryKey: ['tenant', tenantId, actor],
    queryFn: () => apiGetTenant(tenantId, { actor })
  });

  const membershipQuery = useQuery({
    queryKey: ['tenant-memberships', tenantId, actor],
    queryFn: () => apiListMemberships(tenantId, { actor })
  });

  const createMembership = useMutation({
    mutationFn: (payload: CreateMembershipPayload) => apiCreateMembership(tenantId, payload, { actor }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenant-memberships', tenantId, actor] });
      setForm({ userId: '', displayName: '', email: '', role: 'developer', attributes: {} });
      setAttributesText('department=engineering');
      pushToast({ title: 'Membership added', description: 'Tenant member was added to local membership registry.', variant: 'success' });
    },
    onError: (error) => {
      pushToast({ title: 'Add member failed', description: error instanceof Error ? error.message : String(error), variant: 'error' });
    }
  });

  const updateMembership = useMutation({
    mutationFn: ({ membershipId, role }: { membershipId: string; role: TenantMembershipRole }) =>
      apiUpdateMembership(tenantId, membershipId, { role }, { actor }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenant-memberships', tenantId, actor] });
      pushToast({ title: 'Role updated', description: 'Membership role assignment updated.', variant: 'info' });
    }
  });

  const deleteMembership = useMutation({
    mutationFn: (membershipId: string) => apiDeleteMembership(tenantId, membershipId, { actor }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenant-memberships', tenantId, actor] });
      pushToast({ title: 'Membership removed', description: 'Member removed from tenant.', variant: 'info' });
    }
  });

  const sessionRoleSummary = useMemo(() => (session?.user.roles ?? []).join(', ') || 'none', [session?.user.roles]);

  return (
    <section className="space-y-6" data-testid="tenant-detail-page">
      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <CardTitle className="flex items-center gap-2">
                <ShieldCheck className="h-5 w-5" />
                {tenantQuery.data?.name ?? 'Tenant detail'}
              </CardTitle>
              <CardDescription>
                Membership and role assignment workspace for tenant {tenantId}.
              </CardDescription>
            </div>
            <Button asChild variant="ghost" type="button">
              <Link href="/tenants">
                <ArrowLeft className="h-4 w-4" />
                Back to tenant list
              </Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-3">
          <div className="rounded-2xl border border-[color:var(--border)] p-4">
            <p className="text-xs uppercase tracking-[0.16em] text-[color:var(--muted-foreground)]">Status</p>
            <p className="mt-2"><Badge variant={tenantQuery.data?.status === 'ACTIVE' ? 'success' : 'neutral'}>{tenantQuery.data?.status ?? 'unknown'}</Badge></p>
          </div>
          <div className="rounded-2xl border border-[color:var(--border)] p-4">
            <p className="text-xs uppercase tracking-[0.16em] text-[color:var(--muted-foreground)]">Session roles</p>
            <p className="mt-2 text-sm">{sessionRoleSummary}</p>
          </div>
          <div className="rounded-2xl border border-[color:var(--border)] p-4">
            <p className="text-xs uppercase tracking-[0.16em] text-[color:var(--muted-foreground)]">RBAC gate</p>
            <p className="mt-2 text-sm">{canManageMembership ? 'Write access enabled' : 'Read-only mode'}</p>
          </div>
        </CardContent>
      </Card>

      <Card>
        <CardHeader>
          <CardTitle>Add tenant member</CardTitle>
          <CardDescription>
            Keycloak attribute display is included on each membership row. Membership persistence is currently UI-side pending backend sync.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <form
            className="grid gap-4 md:grid-cols-2"
            onSubmit={(event) => {
              event.preventDefault();
              createMembership.mutate({
                ...form,
                attributes: parseAttributes(attributesText)
              });
            }}
          >
            <FormField>
              <FormLabel htmlFor="membership-user-id">User ID</FormLabel>
              <Input
                id="membership-user-id"
                value={form.userId}
                onChange={(event) => setForm((current) => ({ ...current, userId: event.target.value }))}
                placeholder="alice"
                required
              />
            </FormField>
            <FormField>
              <FormLabel htmlFor="membership-display-name">Display name</FormLabel>
              <Input
                id="membership-display-name"
                value={form.displayName}
                onChange={(event) => setForm((current) => ({ ...current, displayName: event.target.value }))}
                placeholder="Alice Operator"
                required
              />
            </FormField>
            <FormField>
              <FormLabel htmlFor="membership-email">Email</FormLabel>
              <Input
                id="membership-email"
                value={form.email}
                onChange={(event) => setForm((current) => ({ ...current, email: event.target.value }))}
                placeholder="alice@ironbucket.dev"
              />
            </FormField>
            <FormField>
              <FormLabel htmlFor="membership-role">Role</FormLabel>
              <Select
                id="membership-role"
                value={form.role}
                onChange={(event) => setForm((current) => ({ ...current, role: event.target.value as TenantMembershipRole }))}
              >
                {roles.map((role) => (
                  <option key={role} value={role}>{role}</option>
                ))}
              </Select>
            </FormField>
            <FormField className="md:col-span-2">
              <FormLabel htmlFor="membership-attributes">User attributes (key=value per line)</FormLabel>
              <textarea
                id="membership-attributes"
                value={attributesText}
                onChange={(event) => setAttributesText(event.target.value)}
                className="flex min-h-[110px] w-full rounded-2xl border border-[color:var(--border)] bg-[color:var(--panel-strong)] px-4 py-3 text-sm"
              />
              <FormHint>Example: department=engineering, location=berlin</FormHint>
            </FormField>
            <div className="md:col-span-2">
              <Button type="submit" disabled={!canManageMembership || createMembership.isPending}>
                <Plus className="h-4 w-4" />
                {createMembership.isPending ? 'Adding...' : 'Add member'}
              </Button>
            </div>
          </form>
        </CardContent>
      </Card>

      <Table>
        <TableHead>
          <TableRow>
            <TableHeaderCell>User</TableHeaderCell>
            <TableHeaderCell>Role</TableHeaderCell>
            <TableHeaderCell>Attributes</TableHeaderCell>
            <TableHeaderCell>Source</TableHeaderCell>
            <TableHeaderCell className="text-right">Actions</TableHeaderCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {membershipQuery.isLoading ? (
            <TableRow>
              <TableCell colSpan={5}>Loading memberships...</TableCell>
            </TableRow>
          ) : (membershipQuery.data ?? []).length === 0 ? (
            <TableRow>
              <TableCell colSpan={5}>No members assigned yet.</TableCell>
            </TableRow>
          ) : (
            (membershipQuery.data ?? []).map((membership) => (
              <TableRow key={membership.id} data-testid={`membership-row-${membership.id}`}>
                <TableCell>
                  <p className="font-semibold">{membership.displayName}</p>
                  <p className="text-xs text-[color:var(--muted-foreground)]">{membership.userId}{membership.email ? ` · ${membership.email}` : ''}</p>
                </TableCell>
                <TableCell>
                  <Select
                    aria-label={`Role for ${membership.userId}`}
                    value={membership.role}
                    disabled={!canManageMembership || updateMembership.isPending}
                    onChange={(event) => {
                      updateMembership.mutate({
                        membershipId: membership.id,
                        role: event.target.value as TenantMembershipRole
                      });
                    }}
                  >
                    {roles.map((role) => (
                      <option key={role} value={role}>{role}</option>
                    ))}
                  </Select>
                </TableCell>
                <TableCell>
                  <p className="line-clamp-2 text-xs text-[color:var(--muted-foreground)]">
                    {Object.entries(membership.attributes).length === 0
                      ? 'No attributes'
                      : Object.entries(membership.attributes).map(([key, value]) => `${key}=${value}`).join(', ')}
                  </p>
                </TableCell>
                <TableCell>
                  <Badge variant="neutral">{membership.source}</Badge>
                </TableCell>
                <TableCell className="text-right">
                  <Button
                    variant="danger"
                    size="sm"
                    type="button"
                    disabled={!canManageMembership || deleteMembership.isPending}
                    onClick={() => deleteMembership.mutate(membership.id)}
                  >
                    <Trash2 className="h-4 w-4" />
                    Remove
                  </Button>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </section>
  );
}
