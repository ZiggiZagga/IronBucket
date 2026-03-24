import { dump, load } from 'js-yaml';
import type { PolicyDraft } from '@/features/policy-management/types';

export type PolicySourceMode = 'json' | 'yaml';

const DEFAULT_DRAFT: PolicyDraft = {
  tenantId: '',
  principal: '',
  resource: '',
  action: '',
  effect: 'ALLOW',
  enabled: true
};

function ensurePolicyDraft(value: unknown): PolicyDraft {
  const parsed = typeof value === 'object' && value !== null ? (value as Record<string, unknown>) : {};

  return {
    tenantId: typeof parsed.tenantId === 'string' ? parsed.tenantId : DEFAULT_DRAFT.tenantId,
    principal: typeof parsed.principal === 'string' ? parsed.principal : DEFAULT_DRAFT.principal,
    resource: typeof parsed.resource === 'string' ? parsed.resource : DEFAULT_DRAFT.resource,
    action: typeof parsed.action === 'string' ? parsed.action : DEFAULT_DRAFT.action,
    effect: parsed.effect === 'DENY' ? 'DENY' : 'ALLOW',
    enabled: typeof parsed.enabled === 'boolean' ? parsed.enabled : true
  };
}

export function serializePolicyDraft(draft: PolicyDraft, mode: PolicySourceMode): string {
  if (mode === 'yaml') {
    return dump(draft, { lineWidth: 120, noRefs: true }).trimEnd();
  }

  return JSON.stringify(draft, null, 2);
}

export function parsePolicySource(raw: string, mode: PolicySourceMode): PolicyDraft {
  if (mode === 'yaml') {
    return ensurePolicyDraft(load(raw));
  }

  return ensurePolicyDraft(JSON.parse(raw));
}

export function insertSchemaKey(raw: string, key: string, mode: PolicySourceMode): string {
  try {
    const draft = parsePolicySource(raw, mode) as PolicyDraft & Record<string, unknown>;
    if (!(key in draft)) {
      draft[key] = '';
    }
    return serializePolicyDraft(draft, mode);
  } catch {
    if (mode === 'yaml') {
      return `${raw.trimEnd()}\n${key}: ""`;
    }

    return `${raw.trimEnd()}\n"${key}": ""`;
  }
}
