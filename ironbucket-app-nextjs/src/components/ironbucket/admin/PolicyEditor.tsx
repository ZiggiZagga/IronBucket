'use client';

import { useMemo, useState } from 'react';
import { useMutation } from '@apollo/client';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { CodeEditor } from '@/components/ui/code-editor';
import { Input } from '@/components/ui/input';
import { useAppToast } from '@/components/ui/toast';
import { CREATE_POLICY, DRY_RUN_POLICY, UPDATE_POLICY } from '../../../graphql/ironbucket-mutations';

type Policy = {
  id?: string;
  tenant: string;
  roles: string[];
  allowedBuckets: string[];
  allowedPrefixes: string[];
  operations: string[];
};

type DryRunResult = {
  decision: string;
  matchedRules: string[];
  reason: string;
};

type PolicyEditorProps = {
  policy?: Policy;
  mode?: 'yaml' | 'json';
};

export default function PolicyEditor({ policy, mode }: PolicyEditorProps) {
  const { pushToast } = useAppToast();
  const [tenant, setTenant] = useState(policy?.tenant ?? '');
  const [roles, setRoles] = useState((policy?.roles ?? []).join(','));
  const [allowedBuckets, setAllowedBuckets] = useState((policy?.allowedBuckets ?? []).join(','));
  const [operations, setOperations] = useState((policy?.operations ?? []).join(','));
  const [messages, setMessages] = useState<string[]>([]);
  const [dryRunResult, setDryRunResult] = useState<DryRunResult | null>(null);

  const [createPolicy] = useMutation(CREATE_POLICY);
  const [updatePolicy] = useMutation(UPDATE_POLICY);
  const [dryRunPolicy] = useMutation(DRY_RUN_POLICY);

  const inputPolicy = useMemo(() => {
    const parsedRoles = roles.split(',').map((value) => value.trim()).filter(Boolean);
    const parsedBuckets = allowedBuckets.split(',').map((value) => value.trim()).filter(Boolean);
    const parsedOperations = operations.split(',').map((value) => value.trim()).filter(Boolean);

    return {
      id: policy?.id ?? 'policy-1',
      tenant: tenant || 'test-tenant',
      roles: parsedRoles.length > 0 ? parsedRoles : ['admin'],
      allowedBuckets: parsedBuckets.length > 0 ? parsedBuckets : ['*'],
      allowedPrefixes: ['*'],
      operations: parsedOperations.length > 0 ? parsedOperations : ['s3:*']
    };
  }, [allowedBuckets, operations, policy?.id, roles, tenant]);

  const editorValue = useMemo(() => JSON.stringify(inputPolicy, null, 2), [inputPolicy]);

  const validate = () => {
    const nextMessages: string[] = [];

    if (!tenant.trim()) {
      nextMessages.push('Tenant is required');
    }

    if (!roles.trim()) {
      nextMessages.push('At least one role is required');
    }

    if (allowedBuckets && !allowedBuckets.split(',').every((name) => name.trim() === '*' || /^[a-z0-9][a-z0-9.-]*[a-z0-9]$/.test(name.trim()))) {
      nextMessages.push('Invalid bucket name');
    }

    if (operations && !operations.split(',').every((operationName) => /^s3:[A-Za-z*]+$/.test(operationName.trim()))) {
      nextMessages.push('Invalid operation');
    }

    setMessages(nextMessages);
    return nextMessages.length === 0;
  };

  const onSave = async () => {
    if (!validate()) {
      return;
    }

    if (policy?.id) {
      await updatePolicy({
        variables: {
          id: policy.id,
          input: inputPolicy
        }
      });
      setMessages(['Policy updated successfully']);
      pushToast({
        title: 'Policy updated',
        description: `Tenant ${inputPolicy.tenant} updated successfully.`,
        variant: 'success'
      });
      return;
    }

    await createPolicy({
      variables: {
        input: inputPolicy
      }
    });
    setMessages(['Policy created successfully']);
    pushToast({
      title: 'Policy created',
      description: `Tenant ${inputPolicy.tenant} created successfully.`,
      variant: 'success'
    });
  };

  const onDryRun = async () => {
    const result = await dryRunPolicy({
      variables: {
        policy: inputPolicy,
        operation: 's3:GetObject',
        resource: 'arn:aws:s3:::test-bucket/file.txt'
      }
    });
    setDryRunResult(result.data?.dryRunPolicy ?? null);
    pushToast({
      title: 'Policy dry run completed',
      description: 'Evaluation returned a decision and matched rules.',
      variant: 'info'
    });
  };

  return (
    <section className="space-y-6">
      <Card className="overflow-hidden border-white/10 bg-[linear-gradient(135deg,rgba(8,15,28,0.96),rgba(13,70,60,0.78),rgba(8,15,28,0.96))] text-white">
        <CardContent className="flex flex-col gap-4 px-6 py-7 md:flex-row md:items-end md:justify-between">
          <div className="space-y-3">
            <Badge variant="success">Policy Studio</Badge>
            <div>
              <h1 className="text-3xl font-semibold tracking-tight">Governance policy editor</h1>
              <p className="mt-2 max-w-2xl text-sm text-slate-300">
                Shared inputs, code-editor treatment, and GraphQL-backed save and dry-run actions in one view.
              </p>
            </div>
          </div>
          <div className="rounded-[24px] border border-white/10 bg-white/8 px-4 py-3 text-sm text-slate-200">
            <p>Mode: {mode ?? 'json'}</p>
            <p>Tenant: {inputPolicy.tenant}</p>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[0.9fr_1.1fr]">
        <Card>
          <CardHeader>
            <CardTitle>Policy inputs</CardTitle>
            <CardDescription>Fields stay simple, but the layout now supports real operator review and editing.</CardDescription>
          </CardHeader>
          <CardContent className="grid gap-4">
            <label className="space-y-2 text-sm font-medium text-[color:var(--foreground)]">
              Tenant
              <Input aria-label="Tenant" value={tenant} onChange={(event) => setTenant(event.target.value)} />
            </label>

            <label className="space-y-2 text-sm font-medium text-[color:var(--foreground)]">
              Roles
              <Input aria-label="Roles" value={roles} onChange={(event) => setRoles(event.target.value)} />
            </label>

            <label className="space-y-2 text-sm font-medium text-[color:var(--foreground)]">
              Allowed Buckets
              <Input aria-label="Allowed Buckets" value={allowedBuckets} onChange={(event) => setAllowedBuckets(event.target.value)} />
            </label>

            <label className="space-y-2 text-sm font-medium text-[color:var(--foreground)]">
              Operations
              <Input aria-label="Operations" value={operations} onChange={(event) => setOperations(event.target.value)} />
            </label>

            <div className="flex flex-wrap gap-3">
              <Button type="button" variant="secondary" onClick={onDryRun}>Test Policy</Button>
              <Button type="button" onClick={onSave}>Save Policy</Button>
            </div>

            {messages.length > 0 ? (
              <div className="space-y-2 rounded-[24px] border border-[color:var(--border)] bg-[color:var(--panel-strong)] p-4">
                {messages.map((message) => (
                  <p key={message} className="text-sm font-medium text-[color:var(--foreground)]">{message}</p>
                ))}
              </div>
            ) : null}

            {dryRunResult ? (
              <div className="rounded-[24px] border border-emerald-400/20 bg-emerald-500/10 p-4 text-sm text-emerald-100">
                <p className="font-semibold">{dryRunResult.decision}</p>
                <p className="mt-1">{dryRunResult.reason}</p>
              </div>
            ) : null}
          </CardContent>
        </Card>

        <Card>
          <CardHeader>
            <CardTitle>Policy source</CardTitle>
            <CardDescription>Shared code-editor presentation for JSON policy review and future YAML parity.</CardDescription>
          </CardHeader>
          <CardContent>
            <CodeEditor
              aria-label="policy syntax"
              className={mode ? 'syntax-highlighted' : ''}
              readOnly
              title="Policy source"
              language={mode ?? 'json'}
              value={editorValue}
            />
          </CardContent>
        </Card>
      </div>
    </section>
  );
}
