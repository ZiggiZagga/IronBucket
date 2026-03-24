'use client';

import Link from 'next/link';
import { useEffect, useMemo, useState } from 'react';
import type { ChangeEvent } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, CheckCircle2, GitBranch, Play, Save } from 'lucide-react';
import { useAppSession } from '@/components/auth/session-provider';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { CodeEditor } from '@/components/ui/code-editor';
import { FormField, FormHint, FormLabel } from '@/components/ui/form-field';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { Skeleton } from '@/components/ui/skeleton';
import { useAppToast } from '@/components/ui/toast';
import {
  apiCreatePolicy,
  apiDiffPolicyVersions,
  apiDryRunPolicy,
  apiGetPolicy,
  apiListPolicyVersions,
  apiReadGitopsState,
  apiRunGitopsAction,
  apiUpdatePolicy,
  apiValidatePolicy
} from '@/lib/api/policy-client';
import { policySchemaAutocomplete, policyTemplates } from '@/lib/policy/templates';
import { insertSchemaKey, parsePolicySource, serializePolicyDraft } from '@/lib/policy/serialization';
import { canManageAdminResources, canReadControlPlane } from '@/lib/auth/rbac';
import type { PolicyDraft, PolicyRecord } from '../types';

function toPolicyDraft(policy: PolicyRecord | null, tenantId: string): PolicyDraft {
  if (!policy) {
    return {
      tenantId,
      principal: 'developer',
      resource: `${tenantId}-*`,
      action: 's3:GetObject',
      effect: 'ALLOW',
      enabled: true
    };
  }

  return {
    tenantId: policy.tenantId,
    principal: policy.principal,
    resource: policy.resource,
    action: policy.action,
    effect: policy.effect,
    enabled: policy.enabled
  };
}

function toHumanReadableSummary(draft: PolicyDraft) {
  const effect = draft.effect === 'ALLOW' ? 'allows' : 'denies';
  const enabled = draft.enabled === false ? 'disabled' : 'enabled';
  return `This policy ${effect} ${draft.principal} to run ${draft.action} on ${draft.resource} in tenant ${draft.tenantId}. Current state: ${enabled}.`;
}

function toRegoPreview(draft: PolicyDraft) {
  const effect = draft.effect === 'ALLOW' ? 'true' : 'false';

  return [
    'package ironbucket.policy',
    '',
    '# Beginner-friendly OPA preview generated from the form',
    'default allow := false',
    '',
    'allow if {',
    `  input.tenant == "${draft.tenantId}"`,
    `  input.user == "${draft.principal}"`,
    `  input.action == "${draft.action}"`,
    `  glob.match("${draft.resource}", ["*"], input.resource)`,
    `  ${effect}`,
    '}'
  ].join('\n');
}

export function PolicyEditorPage({ tenantId, policyId }: { tenantId: string; policyId?: string }) {
  const queryClient = useQueryClient();
  const { session } = useAppSession();
  const { pushToast } = useAppToast();
  const actor = session?.user.username;
  const userRoles = session?.user.roles ?? [];
  const canWritePolicy = canManageAdminResources(userRoles);
  const canReadPolicy = canReadControlPlane(userRoles);

  const policyQuery = useQuery({
    queryKey: ['policy-detail', policyId, actor],
    enabled: Boolean(policyId),
    queryFn: () => apiGetPolicy(String(policyId), { actor })
  });

  const [draft, setDraft] = useState<PolicyDraft>(() => toPolicyDraft(null, tenantId));
  const [editorMode, setEditorMode] = useState<'json' | 'yaml'>('json');
  const [rawPolicy, setRawPolicy] = useState(serializePolicyDraft(toPolicyDraft(null, tenantId), 'json'));
  const [dryRunUser, setDryRunUser] = useState('alice');
  const [dryRunResource, setDryRunResource] = useState(`${tenantId}-example-bucket`);
  const [dryRunAction, setDryRunAction] = useState('s3:GetObject');
  const [selectedLeftVersion, setSelectedLeftVersion] = useState('');
  const [selectedRightVersion, setSelectedRightVersion] = useState('');
  const [diffOutput, setDiffOutput] = useState('');
  const [lastValidation, setLastValidation] = useState<{
    valid: boolean;
    schemaErrors: string[];
    semanticErrors: string[];
    warnings: string[];
  } | null>(null);

  useEffect(() => {
    if (policyQuery.data) {
      const nextDraft = toPolicyDraft(policyQuery.data, tenantId);
      setDraft(nextDraft);
      setRawPolicy(serializePolicyDraft(nextDraft, editorMode));
    }
  }, [editorMode, policyQuery.data, tenantId]);

  const versionsQuery = useQuery({
    queryKey: ['policy-versions', policyId, actor],
    enabled: Boolean(policyId),
    queryFn: () => apiListPolicyVersions(String(policyId), { actor })
  });

  const gitopsQuery = useQuery({
    queryKey: ['policy-gitops', policyId, actor],
    enabled: Boolean(policyId),
    queryFn: () => apiReadGitopsState(String(policyId), { actor })
  });

  const saveMutation = useMutation({
    mutationFn: (payload: PolicyDraft) =>
      policyId ? apiUpdatePolicy(policyId, payload, { actor }) : apiCreatePolicy(payload, { actor }),
    onSuccess: (saved: PolicyRecord) => {
      queryClient.invalidateQueries({ queryKey: ['policy-list', tenantId, actor] });
      queryClient.invalidateQueries({ queryKey: ['policy-detail', policyId, actor] });
      queryClient.invalidateQueries({ queryKey: ['policy-versions', policyId, actor] });
      const nextDraft = toPolicyDraft(saved, tenantId);
      setDraft(nextDraft);
      setRawPolicy(serializePolicyDraft(nextDraft, editorMode));
      pushToast({ title: 'Policy saved', description: `Policy ${saved.id} saved successfully.`, variant: 'success' });
    },
    onError: (error: unknown) => {
      pushToast({ title: 'Save failed', description: error instanceof Error ? error.message : String(error), variant: 'error' });
    }
  });

  const validateMutation = useMutation({
    mutationFn: (payload: PolicyDraft) => apiValidatePolicy(payload, { actor }),
    onSuccess: (result: { valid: boolean; schemaErrors: string[]; semanticErrors: string[]; warnings: string[] }) => {
      setLastValidation(result);
      const issues = [...result.schemaErrors, ...result.semanticErrors];
      pushToast({
        title: result.valid ? 'Policy validation passed' : 'Policy validation failed',
        description: result.valid ? 'Schema and semantic checks passed.' : issues.join(' | '),
        variant: result.valid ? 'success' : 'error'
      });
    }
  });

  const dryRunMutation = useMutation({
    mutationFn: () => apiDryRunPolicy(
      draft.tenantId,
      {
        user: dryRunUser,
        resource: dryRunResource,
        action: dryRunAction
      },
      { actor }
    )
  });

  const gitopsMutation = useMutation({
    mutationFn: ({ action, branch }: { action: 'pull' | 'push'; branch: string }) => apiRunGitopsAction(String(policyId), action, branch, { actor }),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['policy-gitops', policyId, actor] });
    }
  });

  const canSave = useMemo(() => {
    try {
      parsePolicySource(rawPolicy, editorMode);
      return true;
    } catch {
      return false;
    }
  }, [editorMode, rawPolicy]);

  const humanSummary = useMemo(() => toHumanReadableSummary(draft), [draft]);
  const regoPreview = useMemo(() => toRegoPreview(draft), [draft]);
  const gitopsStatusHint = useMemo(() => {
    if (!policyId) {
      return 'GitOps actions are enabled after the first save, because a policy ID is required.';
    }

    return 'Policy is saved, GitOps pull/push can now be triggered.';
  }, [policyId]);

  if (!session) {
    return (
      <section className="space-y-6" data-testid="policy-editor-page">
        <Card>
          <CardHeader>
            <CardTitle>Sign in required</CardTitle>
            <CardDescription>Policy editing is available after authentication.</CardDescription>
          </CardHeader>
        </Card>
      </section>
    );
  }

  if (!canReadPolicy) {
    return (
      <section className="space-y-6" data-testid="policy-editor-page">
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
    <section className="space-y-6" data-testid="policy-editor-page" aria-busy={policyQuery.isLoading}>
      {policyQuery.isError ? (
        <Card>
          <CardHeader>
            <CardTitle>Could not load policy</CardTitle>
            <CardDescription>
              {policyQuery.error instanceof Error ? policyQuery.error.message : 'An unexpected error occurred while loading policy details.'}
            </CardDescription>
          </CardHeader>
          <CardContent>
            <Button type="button" variant="secondary" onClick={() => policyQuery.refetch()}>Retry</Button>
          </CardContent>
        </Card>
      ) : null}

      <Card>
        <CardHeader>
          <div className="flex flex-wrap items-center justify-between gap-3">
            <div>
              <CardTitle>Policy Editor</CardTitle>
              <CardDescription>
                YAML/JSON editing, validation, dry-run, version history, diff, and GitOps actions in one tenant-aware workspace.
              </CardDescription>
            </div>
            <Button asChild variant="ghost">
              <Link href={`/policies/${tenantId}`}>
                <ArrowLeft className="h-4 w-4" />
                Back to policy list
              </Link>
            </Button>
          </div>
        </CardHeader>
        <CardContent className="grid gap-4 xl:grid-cols-2">
          <FormField>
            <FormLabel htmlFor="policy-template">Policy template</FormLabel>
            <Select
              id="policy-template"
              defaultValue=""
              disabled={!canWritePolicy}
              onChange={(event: ChangeEvent<HTMLSelectElement>) => {
                const template = policyTemplates.find((entry) => entry.id === event.target.value);
                if (!template) {
                  return;
                }
                const next = { ...template.draft, tenantId };
                setDraft(next);
                setRawPolicy(serializePolicyDraft(next, editorMode));
              }}
            >
              <option value="">Choose template...</option>
              {policyTemplates.map((template) => (
                <option key={template.id} value={template.id}>{template.name}</option>
              ))}
            </Select>
            <FormHint>Templates provide starter structures for common RBAC scenarios.</FormHint>
          </FormField>

          <FormField>
            <FormLabel htmlFor="policy-json-mode">Editor mode</FormLabel>
            <Select
              id="policy-json-mode"
              value={editorMode}
              disabled={!canWritePolicy}
              onChange={(event: ChangeEvent<HTMLSelectElement>) => {
                const nextMode = event.target.value as 'json' | 'yaml';
                setEditorMode(nextMode);
                setRawPolicy(serializePolicyDraft(draft, nextMode));
              }}
            >
              <option value="json">JSON</option>
              <option value="yaml">YAML</option>
            </Select>
            <FormHint>Switch between JSON and YAML while preserving the same underlying policy model.</FormHint>
          </FormField>
        </CardContent>
      </Card>

      <div className="grid gap-5 xl:grid-cols-[1.2fr_0.8fr]">
        <Card>
          <CardHeader>
            <CardTitle>Policy Source</CardTitle>
            <CardDescription>Schema hints support faster editing with field-level autocomplete buttons.</CardDescription>
          </CardHeader>
          <CardContent className="space-y-4">
            <CodeEditor
              title={editorMode === 'yaml' ? 'Policy YAML' : 'Policy JSON'}
              language={editorMode}
              value={rawPolicy}
              readOnly={!canWritePolicy}
              onChange={(event: ChangeEvent<HTMLTextAreaElement>) => {
                setRawPolicy(event.target.value);
                try {
                  const parsed = parsePolicySource(event.target.value, editorMode);
                  setDraft(parsed);
                } catch {
                  // Keep raw editor state while source is invalid.
                }
              }}
              suggestions={policySchemaAutocomplete}
              onInsertSuggestion={canWritePolicy ? (value: string) => {
                const next = insertSchemaKey(rawPolicy, value, editorMode);
                setRawPolicy(next);
                try {
                  setDraft(parsePolicySource(next, editorMode));
                } catch {
                  // Keep draft unchanged until source is valid again.
                }
              } : undefined}
              aria-label="Policy source"
            />
            <div className="flex flex-wrap gap-3">
              <Button
                type="button"
                variant="secondary"
                disabled={!canWritePolicy || !canSave || validateMutation.isPending}
                onClick={() => validateMutation.mutate(draft)}
              >
                <CheckCircle2 className="h-4 w-4" />
                Save + validate
              </Button>
              <Button
                type="button"
                disabled={!canWritePolicy || !canSave || saveMutation.isPending}
                onClick={() => saveMutation.mutate(draft)}
              >
                <Save className="h-4 w-4" />
                Save policy
              </Button>
            </div>

            {!canWritePolicy ? (
              <p className="text-xs text-amber-300" role="status">
                Read-only mode: write actions require an admin role.
              </p>
            ) : null}

            {lastValidation ? (
              <div className="rounded-2xl border border-[color:var(--border)] bg-[color:var(--panel-strong)] p-3 text-sm">
                <p className="font-semibold">Validation summary</p>
                <p className="mt-1 text-xs text-[color:var(--muted-foreground)]">
                  {lastValidation.valid ? 'No blocking issues.' : 'Fix the items below, then validate again.'}
                </p>

                {lastValidation.schemaErrors.length > 0 ? (
                  <div className="mt-3">
                    <p className="text-xs font-semibold uppercase tracking-[0.12em] text-rose-500">Schema issues</p>
                    <ul className="mt-1 list-disc pl-5 text-xs">
                      {lastValidation.schemaErrors.map((entry) => (
                        <li key={`schema-${entry}`}>{entry}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                {lastValidation.semanticErrors.length > 0 ? (
                  <div className="mt-3">
                    <p className="text-xs font-semibold uppercase tracking-[0.12em] text-rose-500">Semantic issues</p>
                    <ul className="mt-1 list-disc pl-5 text-xs">
                      {lastValidation.semanticErrors.map((entry) => (
                        <li key={`semantic-${entry}`}>{entry}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}

                {lastValidation.warnings.length > 0 ? (
                  <div className="mt-3">
                    <p className="text-xs font-semibold uppercase tracking-[0.12em] text-amber-500">Warnings</p>
                    <ul className="mt-1 list-disc pl-5 text-xs">
                      {lastValidation.warnings.map((entry) => (
                        <li key={`warning-${entry}`}>{entry}</li>
                      ))}
                    </ul>
                  </div>
                ) : null}
              </div>
            ) : null}
          </CardContent>
        </Card>

        <div className="space-y-5">
          <Card>
            <CardHeader>
              <CardTitle>OPA Beginner Guide</CardTitle>
              <CardDescription>Human-readable explanation first, then generated Rego preview for easier learning.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="rounded-2xl border border-[color:var(--border)] bg-[color:var(--panel-strong)] p-3 text-sm">
                <p>{humanSummary}</p>
              </div>
              <CodeEditor
                title="Generated OPA Rego Preview"
                language="rego"
                value={regoPreview}
                readOnly
                aria-label="OPA Rego preview"
              />
              <div className="rounded-2xl border border-[color:var(--border)] p-3 text-xs text-[color:var(--muted-foreground)]">
                Tip: `input.user`, `input.action`, and `input.resource` in Rego map to Dry Run fields below.
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Dry Run Simulation</CardTitle>
              <CardDescription>Input user, resource and action to simulate allow/deny decision.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <Input aria-label="Dry run user" value={dryRunUser} onChange={(event: ChangeEvent<HTMLInputElement>) => setDryRunUser(event.target.value)} />
              <Input aria-label="Dry run resource" value={dryRunResource} onChange={(event: ChangeEvent<HTMLInputElement>) => setDryRunResource(event.target.value)} />
              <Input aria-label="Dry run action" value={dryRunAction} onChange={(event: ChangeEvent<HTMLInputElement>) => setDryRunAction(event.target.value)} />
              <Button type="button" variant="secondary" onClick={() => dryRunMutation.mutate()}>
                <Play className="h-4 w-4" />
                Run dry run
              </Button>
              {dryRunMutation.isPending ? <Skeleton className="h-14 w-full" /> : null}
              {dryRunMutation.data ? (
                <div className="rounded-2xl border border-[color:var(--border)] bg-[color:var(--panel-strong)] p-3 text-sm">
                  <p><strong>Decision:</strong> {dryRunMutation.data.allow ? 'allow' : 'deny'}</p>
                  <p><strong>Reason:</strong> {dryRunMutation.data.reason}</p>
                </div>
              ) : null}
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>GitOps Integration</CardTitle>
              <CardDescription>Push and pull state for policy-as-code workflow mapping.</CardDescription>
            </CardHeader>
            <CardContent className="space-y-3">
              <div className="rounded-2xl border border-[color:var(--border)] p-3 text-sm">
                <p><strong>Branch:</strong> {gitopsQuery.data?.branch ?? 'main'}</p>
                <p><strong>Pull:</strong> {gitopsQuery.data?.pullStatus ?? 'idle'}</p>
                <p><strong>Push:</strong> {gitopsQuery.data?.pushStatus ?? 'idle'}</p>
                <p className="mt-2 text-xs text-[color:var(--muted-foreground)]">{gitopsStatusHint}</p>
              </div>
              <div className="flex gap-2">
                <Button
                  type="button"
                  variant="secondary"
                  disabled={!canWritePolicy || !policyId || gitopsMutation.isPending}
                  onClick={() => gitopsMutation.mutate({ action: 'pull', branch: gitopsQuery.data?.branch ?? 'main' })}
                >
                  <GitBranch className="h-4 w-4" />
                  Pull policies
                </Button>
                <Button
                  type="button"
                  disabled={!canWritePolicy || !policyId || gitopsMutation.isPending}
                  onClick={() => gitopsMutation.mutate({ action: 'push', branch: gitopsQuery.data?.branch ?? 'main' })}
                >
                  <GitBranch className="h-4 w-4" />
                  Push policies
                </Button>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>

      <Card>
        <CardHeader>
          <CardTitle>Policy Version History</CardTitle>
          <CardDescription>Read-only version timeline with compare view.</CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          <div className="grid gap-4 md:grid-cols-2">
            <FormField>
              <FormLabel htmlFor="diff-left-version">Left version</FormLabel>
              <Select id="diff-left-version" value={selectedLeftVersion} onChange={(event: ChangeEvent<HTMLSelectElement>) => setSelectedLeftVersion(event.target.value)}>
                <option value="">Select version...</option>
                {(versionsQuery.data ?? []).map((entry) => (
                  <option key={`left-${entry.version}`} value={String(entry.version)}>v{entry.version}</option>
                ))}
              </Select>
            </FormField>
            <FormField>
              <FormLabel htmlFor="diff-right-version">Right version</FormLabel>
              <Select id="diff-right-version" value={selectedRightVersion} onChange={(event: ChangeEvent<HTMLSelectElement>) => setSelectedRightVersion(event.target.value)}>
                <option value="">Select version...</option>
                {(versionsQuery.data ?? []).map((entry) => (
                  <option key={`right-${entry.version}`} value={String(entry.version)}>v{entry.version}</option>
                ))}
              </Select>
            </FormField>
          </div>

          <div className="flex flex-wrap gap-3">
            <Button
              type="button"
              variant="secondary"
              disabled={!policyId || !selectedLeftVersion || !selectedRightVersion}
              onClick={async () => {
                const result = await apiDiffPolicyVersions(String(policyId), Number(selectedLeftVersion), Number(selectedRightVersion), { actor });
                setDiffOutput(result.unifiedDiff);
              }}
            >
              Compare versions
            </Button>
          </div>

          <div className="grid gap-3">
            {versionsQuery.isLoading ? (
              <Skeleton className="h-16 w-full" />
            ) : (versionsQuery.data ?? []).length === 0 ? (
              <p className="text-sm text-[color:var(--muted-foreground)]">No version history yet.</p>
            ) : (
              (versionsQuery.data ?? []).map((entry) => (
                <div key={entry.version} className="rounded-2xl border border-[color:var(--border)] p-3 text-sm">
                  <div className="flex flex-wrap items-center justify-between gap-2">
                    <p className="font-semibold">Version {entry.version}</p>
                    <Badge variant="neutral">{entry.author}</Badge>
                  </div>
                  <p className="mt-1 text-xs text-[color:var(--muted-foreground)]">{entry.createdAt}</p>
                </div>
              ))
            )}
          </div>

          {diffOutput ? (
            <CodeEditor
              title="Policy Version Diff"
              language="diff"
              value={diffOutput}
              readOnly
              aria-label="Policy diff viewer"
            />
          ) : null}
        </CardContent>
      </Card>
    </section>
  );
}
