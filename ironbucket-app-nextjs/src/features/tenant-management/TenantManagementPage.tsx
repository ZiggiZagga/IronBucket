'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { Edit3, Plus, Search, Trash2, UserCog } from 'lucide-react';
import { useAppSession } from '@/components/auth/session-provider';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Dialog, DialogContent, DialogDescription, DialogHeader, DialogTitle, DialogTrigger } from '@/components/ui/dialog';
import { FormError, FormField, FormHint, FormLabel } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Table, TableBody, TableCell, TableHead, TableHeaderCell, TableRow } from '@/components/ui/table';
import { useAppToast } from '@/components/ui/toast';
import {
  apiCreateTenant,
  apiDeleteTenant,
  apiListTenants,
  apiUpdateTenant
} from '@/lib/api/ironbucket-client';
import type { CreateTenantPayload, TenantRecord, TenantStatus } from './types';
import { useTenantFilters } from './useTenantFilters';

const tenantStatuses: TenantStatus[] = ['ACTIVE', 'SUSPENDED', 'ARCHIVED'];

function parseMetadata(input: string) {
  return input
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

export function TenantManagementPage() {
  const queryClient = useQueryClient();
  const { session } = useAppSession();
  const { pushToast } = useAppToast();
  const { query, setQuery, status, setStatus } = useTenantFilters();

  const actor = session?.user.username;
  const roleSet = new Set(session?.user.roles ?? []);
  const canManageTenants = roleSet.has('admin') || roleSet.has('adminrole');

  const [createOpen, setCreateOpen] = useState(false);
  const [createForm, setCreateForm] = useState<CreateTenantPayload>({
    name: '',
    status: 'ACTIVE',
    metadata: {}
  });
  const [createMetadataText, setCreateMetadataText] = useState('environment=dev');
  const [createError, setCreateError] = useState('');

  const [editingTenant, setEditingTenant] = useState<TenantRecord | null>(null);
  const [editName, setEditName] = useState('');
  const [editStatus, setEditStatus] = useState<TenantStatus>('ACTIVE');
  const [editMetadataText, setEditMetadataText] = useState('');

  const tenantQuery = useQuery({
    queryKey: ['tenants', actor],
    queryFn: () => apiListTenants({ actor })
  });

  const createMutation = useMutation({
    mutationFn: (payload: CreateTenantPayload) => apiCreateTenant(payload, { actor }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants', actor] });
      pushToast({ title: 'Tenant created', description: 'New tenant was created via tenant service.', variant: 'success' });
      setCreateOpen(false);
      setCreateForm({ name: '', status: 'ACTIVE', metadata: {} });
      setCreateMetadataText('environment=dev');
      setCreateError('');
    },
    onError: (error) => {
      setCreateError(error instanceof Error ? error.message : String(error));
    }
  });

  const updateMutation = useMutation({
    mutationFn: ({ tenantId, payload }: { tenantId: string; payload: Partial<TenantRecord> }) =>
      apiUpdateTenant(
        tenantId,
        {
          name: payload.name,
          status: payload.status,
          metadata: payload.metadata
        },
        { actor }
      ),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants', actor] });
      pushToast({ title: 'Tenant updated', description: 'Tenant metadata and status were updated.', variant: 'success' });
      setEditingTenant(null);
    },
    onError: (error) => {
      pushToast({ title: 'Update failed', description: error instanceof Error ? error.message : String(error), variant: 'error' });
    }
  });

  const deleteMutation = useMutation({
    mutationFn: (tenantId: string) => apiDeleteTenant(tenantId, { actor }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['tenants', actor] });
      pushToast({ title: 'Tenant deleted', description: 'Tenant deletion completed with safety confirmation.', variant: 'info' });
    },
    onError: (error) => {
      pushToast({ title: 'Delete failed', description: error instanceof Error ? error.message : String(error), variant: 'error' });
    }
  });

  const filteredTenants = useMemo(() => {
    const list = tenantQuery.data ?? [];
    return list.filter((tenant) => {
      const queryOk = !query || tenant.name.toLowerCase().includes(query.toLowerCase()) || tenant.id.includes(query);
      const statusOk = status === 'ALL' || tenant.status === status;
      return queryOk && statusOk;
    });
  }, [query, status, tenantQuery.data]);

  return (
    <section className="space-y-6" data-testid="tenant-management-page">
      <Card>
        <CardHeader>
          <CardTitle>Tenant Management</CardTitle>
          <CardDescription>
            Tenant CRUD surface mapped to IronBucket tenant GraphQL service with URL-state filtering via nuqs.
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-3 md:grid-cols-[1fr_220px_auto]">
            <FormField>
              <FormLabel htmlFor="tenant-search">Search tenants</FormLabel>
              <div className="relative">
                <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--muted-foreground)]" />
                <Input
                  id="tenant-search"
                  value={query}
                  onChange={(event) => setQuery(event.target.value)}
                  className="pl-9"
                  placeholder="name or tenant id"
                />
              </div>
            </FormField>
            <FormField>
              <FormLabel htmlFor="tenant-status-filter">Status filter</FormLabel>
              <Select id="tenant-status-filter" value={status} onChange={(event) => setStatus(event.target.value)}>
                <option value="ALL">All statuses</option>
                {tenantStatuses.map((option) => (
                  <option key={option} value={option}>{option}</option>
                ))}
              </Select>
            </FormField>
            <div className="flex items-end">
              <Dialog open={createOpen} onOpenChange={setCreateOpen}>
                <DialogTrigger asChild>
                  <Button type="button" className="w-full md:w-auto" disabled={!canManageTenants}>
                    <Plus className="h-4 w-4" />
                    Create tenant
                  </Button>
                </DialogTrigger>
                <DialogContent>
                  <DialogHeader>
                    <DialogTitle>Create tenant</DialogTitle>
                    <DialogDescription>Creates a tenant in the GraphQL tenant service and stores metadata in the UI layer.</DialogDescription>
                  </DialogHeader>
                  <form
                    className="mt-5 space-y-4"
                    onSubmit={(event) => {
                      event.preventDefault();
                      const metadata = parseMetadata(createMetadataText);
                      createMutation.mutate({ ...createForm, metadata });
                    }}
                  >
                    <FormField>
                      <FormLabel htmlFor="tenant-create-name">Tenant name</FormLabel>
                      <Input
                        id="tenant-create-name"
                        value={createForm.name}
                        onChange={(event) => setCreateForm((current) => ({ ...current, name: event.target.value }))}
                        placeholder="acme-corp"
                        required
                      />
                    </FormField>
                    <FormField>
                      <FormLabel htmlFor="tenant-create-status">Status</FormLabel>
                      <Select
                        id="tenant-create-status"
                        value={createForm.status}
                        onChange={(event) => setCreateForm((current) => ({ ...current, status: event.target.value as TenantStatus }))}
                      >
                        {tenantStatuses.map((option) => (
                          <option key={option} value={option}>{option}</option>
                        ))}
                      </Select>
                    </FormField>
                    <FormField>
                      <FormLabel htmlFor="tenant-create-metadata">Metadata (one key=value per line)</FormLabel>
                      <textarea
                        id="tenant-create-metadata"
                        value={createMetadataText}
                        onChange={(event) => setCreateMetadataText(event.target.value)}
                        className="flex min-h-[120px] w-full rounded-2xl border border-[color:var(--border)] bg-[color:var(--panel-strong)] px-4 py-3 text-sm"
                      />
                      <FormHint>Example: owner=platform-team</FormHint>
                    </FormField>
                    {createError ? <FormError>{createError}</FormError> : null}
                    <Button type="submit" className="w-full" disabled={createMutation.isPending || !canManageTenants}>
                      {createMutation.isPending ? 'Creating...' : 'Create tenant'}
                    </Button>
                  </form>
                </DialogContent>
              </Dialog>
            </div>
          </div>
          {!canManageTenants ? (
            <p className="text-sm text-amber-300">
              Current session is read-only for tenant write actions. Sign in with an admin role to create, update, or delete tenants.
            </p>
          ) : null}
        </CardContent>
      </Card>

      <Table>
        <TableHead>
          <TableRow>
            <TableHeaderCell>Tenant</TableHeaderCell>
            <TableHeaderCell>Status</TableHeaderCell>
            <TableHeaderCell>Metadata</TableHeaderCell>
            <TableHeaderCell className="text-right">Actions</TableHeaderCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {tenantQuery.isLoading ? (
            <TableRow>
              <TableCell colSpan={4}>Loading tenants...</TableCell>
            </TableRow>
          ) : filteredTenants.length === 0 ? (
            <TableRow>
              <TableCell colSpan={4}>No tenants match the current filter.</TableCell>
            </TableRow>
          ) : (
            filteredTenants.map((tenant) => (
              <TableRow key={tenant.id} data-testid={`tenant-row-${tenant.id}`}>
                <TableCell>
                  <p className="font-semibold">{tenant.name}</p>
                  <p className="text-xs text-[color:var(--muted-foreground)]">{tenant.id}</p>
                </TableCell>
                <TableCell>
                  <Badge variant={tenant.status === 'ACTIVE' ? 'success' : 'neutral'}>{tenant.status}</Badge>
                </TableCell>
                <TableCell>
                  <p className="line-clamp-2 text-xs text-[color:var(--muted-foreground)]">
                    {Object.entries(tenant.metadata).length === 0
                      ? 'No metadata'
                      : Object.entries(tenant.metadata).map(([key, value]) => `${key}=${value}`).join(', ')}
                  </p>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-2">
                    <Button asChild variant="ghost" size="sm" type="button">
                      <Link href={`/tenants/${tenant.id}`}>
                        <UserCog className="h-4 w-4" />
                        Members
                      </Link>
                    </Button>
                    <Dialog
                      open={editingTenant?.id === tenant.id}
                      onOpenChange={(open) => {
                        if (!open) {
                          setEditingTenant(null);
                        }
                      }}
                    >
                      <DialogTrigger asChild>
                        <Button
                          variant="secondary"
                          size="sm"
                          type="button"
                          disabled={!canManageTenants}
                          onClick={() => {
                            setEditingTenant(tenant);
                            setEditName(tenant.name);
                            setEditStatus(tenant.status);
                            setEditMetadataText(
                              Object.entries(tenant.metadata)
                                .map(([key, value]) => `${key}=${value}`)
                                .join('\n')
                            );
                          }}
                        >
                          <Edit3 className="h-4 w-4" />
                          Edit
                        </Button>
                      </DialogTrigger>
                      <DialogContent>
                        <DialogHeader>
                          <DialogTitle>Update tenant</DialogTitle>
                          <DialogDescription>Update display metadata and service status for this tenant.</DialogDescription>
                        </DialogHeader>
                        <form
                          className="mt-5 space-y-4"
                          onSubmit={(event) => {
                            event.preventDefault();
                            updateMutation.mutate({
                              tenantId: tenant.id,
                              payload: {
                                name: editName,
                                status: editStatus,
                                metadata: parseMetadata(editMetadataText)
                              }
                            });
                          }}
                        >
                          <FormField>
                            <FormLabel htmlFor="tenant-edit-name">Tenant name</FormLabel>
                            <Input id="tenant-edit-name" value={editName} onChange={(event) => setEditName(event.target.value)} required />
                          </FormField>
                          <FormField>
                            <FormLabel htmlFor="tenant-edit-status">Status</FormLabel>
                            <Select id="tenant-edit-status" value={editStatus} onChange={(event) => setEditStatus(event.target.value as TenantStatus)}>
                              {tenantStatuses.map((option) => (
                                <option key={option} value={option}>{option}</option>
                              ))}
                            </Select>
                          </FormField>
                          <FormField>
                            <FormLabel htmlFor="tenant-edit-metadata">Metadata (key=value)</FormLabel>
                            <textarea
                              id="tenant-edit-metadata"
                              value={editMetadataText}
                              onChange={(event) => setEditMetadataText(event.target.value)}
                              className="flex min-h-[120px] w-full rounded-2xl border border-[color:var(--border)] bg-[color:var(--panel-strong)] px-4 py-3 text-sm"
                            />
                          </FormField>
                          <Button type="submit" className="w-full" disabled={updateMutation.isPending || !canManageTenants}>
                            {updateMutation.isPending ? 'Saving...' : 'Save changes'}
                          </Button>
                        </form>
                      </DialogContent>
                    </Dialog>
                    <Button
                      variant="danger"
                      size="sm"
                      type="button"
                      disabled={!canManageTenants || deleteMutation.isPending}
                      onClick={() => {
                        const confirmed = window.confirm(`Delete tenant ${tenant.name}? This cannot be undone.`);
                        if (confirmed) {
                          deleteMutation.mutate(tenant.id);
                        }
                      }}
                    >
                      <Trash2 className="h-4 w-4" />
                      Delete
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))
          )}
        </TableBody>
      </Table>
    </section>
  );
}
