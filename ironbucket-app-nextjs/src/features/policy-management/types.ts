export type PolicyEffect = 'ALLOW' | 'DENY';

export type PolicyDraft = {
  tenantId: string;
  principal: string;
  resource: string;
  action: string;
  effect: PolicyEffect;
  enabled?: boolean;
};

export type PolicyRecord = {
  id: string;
  tenantId: string;
  principal: string;
  resource: string;
  action: string;
  effect: PolicyEffect;
  version: number;
  enabled: boolean;
  source: 'graphql' | 'ui-pending-sync';
  lastUpdatedAt: string;
};

export type PolicyVersionRecord = {
  policyId: string;
  version: number;
  policyJson: string;
  author: string;
  createdAt: string;
};

export type PolicyValidationResult = {
  valid: boolean;
  schemaErrors: string[];
  semanticErrors: string[];
  warnings: string[];
};

export type PolicyDryRunInput = {
  user: string;
  resource: string;
  action: string;
};

export type PolicyDryRunResult = {
  allow: boolean;
  reason: string;
  matchedRules: string[];
};

export type PolicyGitopsState = {
  policyId: string;
  branch: string;
  pullStatus: 'idle' | 'pulled';
  pushStatus: 'idle' | 'pushed';
  pulledAt?: string;
  pushedAt?: string;
};
