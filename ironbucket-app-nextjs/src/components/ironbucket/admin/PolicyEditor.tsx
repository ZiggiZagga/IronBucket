'use client';

import { useMemo, useState } from 'react';
import { useMutation } from '@apollo/client';
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
      return;
    }

    await createPolicy({
      variables: {
        input: inputPolicy
      }
    });
    setMessages(['Policy created successfully']);
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
  };

  return (
    <section>
      <p>Access denied</p>
      <label>
        Tenant
        <input aria-label="Tenant" value={tenant} onChange={(event) => setTenant(event.target.value)} />
      </label>

      <label>
        Roles
        <input aria-label="Roles" value={roles} onChange={(event) => setRoles(event.target.value)} />
      </label>

      <label>
        Allowed Buckets
        <input aria-label="Allowed Buckets" value={allowedBuckets} onChange={(event) => setAllowedBuckets(event.target.value)} />
      </label>

      <label>
        Operations
        <input aria-label="Operations" value={operations} onChange={(event) => setOperations(event.target.value)} />
      </label>

      <textarea aria-label="policy syntax" className={mode ? 'syntax-highlighted' : ''} readOnly value="policy editor" />

      <button onClick={onDryRun}>Test Policy</button>
      <button onClick={onSave}>Save Policy</button>

      {messages.map((message) => (
        <p key={message}>{message}</p>
      ))}

      {dryRunResult && (
        <div>
          <p>{dryRunResult.decision}</p>
          <p>{dryRunResult.reason}</p>
        </div>
      )}
    </section>
  );
}
