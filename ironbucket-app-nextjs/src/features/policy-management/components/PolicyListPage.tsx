'use client';

import Link from 'next/link';
import { useMemo, useState } from 'react';
import type { ChangeEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowRight, FileStack, Plus, Search, Trash2 } from 'lucide-react';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { FormField, FormLabel } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { Skeleton } from '@/components/ui/skeleton';
import { Table, TableBody, TableCell, TableHead, TableHeaderCell, TableRow } from '@/components/ui/table';
import { useAppToast } from '@/components/ui/toast';
import { useAppSession } from '@/components/auth/session-provider';
import { apiDeletePolicy, apiListPolicies } from '@/lib/api/policy-client';
import { canManageAdminResources, canReadControlPlane } from '@/lib/auth/rbac';
import type { PolicyRecord } from '../types';

export function PolicyListPage({ tenantId }: { tenantId: string }) {
  const queryClient = useQueryClient();
  const { session } = useAppSession();
  const { pushToast } = useAppToast();
  const actor = session?.user.username;
  const userRoles = session?.user.roles ?? [];
  const canManagePolicies = canManageAdminResources(userRoles);
  const canReadPolicies = canReadControlPlane(userRoles);
  const [query, setQuery] = useState('');

  const policyQuery = useQuery({
    queryKey: ['policy-list', tenantId, actor],
    queryFn: () => apiListPolicies(tenantId, { actor })
  });

  const deleteMutation = useMutation({
    mutationFn: (policyId: string) => apiDeletePolicy(policyId, { actor }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policy-list', tenantId, actor] });
      pushToast({ title: 'Policy deleted', description: 'Policy deletion request completed.', variant: 'info' });
    }
  });

  const filtered = useMemo(() => {
    const input = query.trim().toLowerCase();
    const list = policyQuery.data ?? [];
    if (!input) {
      return list;
    }

    return list.filter((policy: PolicyRecord & { versionHistoryCount?: number }) =>
      policy.principal.toLowerCase().includes(input)
      || policy.action.toLowerCase().includes(input)
      || policy.resource.toLowerCase().includes(input)
      || policy.id.toLowerCase().includes(input)
    );
  }, [policyQuery.data, query]);

  if (!session) {
    return (
      <section className="space-y-6" data-testid="policy-list-page">
        <Card>
          <CardHeader>
            <CardTitle>Sign in required</CardTitle>
            <CardDescription>Policy management is available after authentication.</CardDescription>
          </CardHeader>
        </Card>
      </section>
    );
  }

  if (!canReadPolicies) {
    return (
      <section className="space-y-6" data-testid="policy-list-page">
        <Card>
          <CardHeader>
            <CardTitle>Access restricted</CardTitle>
            <CardDescription>Your role set does not include policy read permissions.</CardDescription>
          </CardHeader>
        </Card>
      </section>
    );
  }

  return (
    <section className="space-y-6" data-testid="policy-list-page" aria-busy={policyQuery.isLoading}>
      <Card>
        <CardHeader>
          <CardTitle className="flex items-center gap-2">
            <FileStack className="h-5 w-5" />
            Policy List
          </CardTitle>
          <CardDescription>
            Tenant-scoped policy inventory with version awareness and navigation into the editor workspace.
          </CardDescription>
        </CardHeader>
        <CardContent className="grid gap-4 md:grid-cols-[1fr_auto]">
          <FormField>
            <FormLabel htmlFor="policy-search">Find policy</FormLabel>
            <div className="relative">
              <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-[color:var(--muted-foreground)]" />
              <Input
                id="policy-search"
                value={query}
                  onChange={(event: ChangeEvent<HTMLInputElement>) => setQuery(event.target.value)}
                className="pl-9"
                placeholder="principal, action, resource or id"
              />
            </div>
          </FormField>
          <div className="flex items-end">
            {canManagePolicies ? (
              <Button asChild>
                <Link href={`/policies/${tenantId}/new`}>
                  <Plus className="h-4 w-4" />
                  New policy
                </Link>
              </Button>
            ) : (
              <Button type="button" disabled aria-label="New policy requires admin role">
                <Plus className="h-4 w-4" />
                New policy
              </Button>
            )}
          </div>
        </CardContent>
      </Card>

      {policyQuery.isError ? (
        <Card>
          <CardHeader>
            <CardTitle>Could not load policies</CardTitle>
            <CardDescription>
              {policyQuery.error instanceof Error ? policyQuery.error.message : 'An unexpected error occurred while loading policies.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button type="button" variant="secondary" onClick={() => policyQuery.refetch()}>Retry</Button>
          </CardContent>
        </Card>
      ) : null}

      <Table>
        <TableHead>
          <TableRow>
            <TableHeaderCell>Principal</TableHeaderCell>
            <TableHeaderCell>Resource</TableHeaderCell>
            <TableHeaderCell>Action</TableHeaderCell>
            <TableHeaderCell>Effect</TableHeaderCell>
            <TableHeaderCell>Version</TableHeaderCell>
            <TableHeaderCell className="text-right">Actions</TableHeaderCell>
          </TableRow>
        </TableHead>
        <TableBody>
          {policyQuery.isLoading ? (
            Array.from({ length: 4 }).map((_, index) => (
              <TableRow key={`policy-loading-${index}`}>
                <TableCell colSpan={6}><Skeleton className="h-10 w-full" /></TableCell>
              </TableRow>
            ))
          ) : filtered.length === 0 ? (
            <TableRow>
              <TableCell colSpan={6}>
                <p className="font-medium">No policies found.</p>
                <p className="mt-1 text-xs text-[color:var(--muted-foreground)]">Adjust search or create a policy if your role has write access.</p>
              </TableCell>
            </TableRow>
          ) : (
            filtered.map((policy) => (
              <TableRow key={policy.id} data-testid={`policy-row-${policy.id}`}>
                <TableCell>
                  <p className="font-semibold">{policy.principal}</p>
                  <p className="text-xs text-[color:var(--muted-foreground)]">{policy.id}</p>
                </TableCell>
                <TableCell>{policy.resource}</TableCell>
                <TableCell>{policy.action}</TableCell>
                <TableCell>
                  <Badge variant={policy.effect === 'ALLOW' ? 'success' : 'warning'}>{policy.effect}</Badge>
                </TableCell>
                <TableCell>
                  <p>v{policy.version}</p>
                  <p className="text-xs text-[color:var(--muted-foreground)]">history: {policy.versionHistoryCount ?? 0}</p>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex flex-wrap justify-end gap-2">
                    <Button asChild variant="secondary" size="sm">
                      <Link href={`/policies/${tenantId}/${policy.id}`}>
                        Open
                        <ArrowRight className="h-4 w-4" />
                      </Link>
                    </Button>
                    <Button
                      variant="danger"
                      size="sm"
                      type="button"
                      aria-label={`Delete policy ${policy.id}`}
                      disabled={!canManagePolicies || deleteMutation.isPending}
                      onClick={() => {
                        if (window.confirm(`Delete policy ${policy.id}?`)) {
                          deleteMutation.mutate(policy.id);
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
