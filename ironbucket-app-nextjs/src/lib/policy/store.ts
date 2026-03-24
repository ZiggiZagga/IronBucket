import type { PolicyGitopsState, PolicyVersionRecord } from '@/features/policy-management/types';

const versionStore = new Map<string, PolicyVersionRecord[]>();
const gitopsStore = new Map<string, PolicyGitopsState>();

export function getPolicyVersions(policyId: string): PolicyVersionRecord[] {
  return versionStore.get(policyId) ?? [];
}

export function appendPolicyVersion(record: PolicyVersionRecord) {
  const current = getPolicyVersions(record.policyId);
  versionStore.set(record.policyId, [...current, record]);
}

export function getPolicyVersion(policyId: string, version: number): PolicyVersionRecord | null {
  return getPolicyVersions(policyId).find((entry) => entry.version === version) ?? null;
}

export function getGitopsState(policyId: string): PolicyGitopsState {
  return (
    gitopsStore.get(policyId) ?? {
      policyId,
      branch: 'main',
      pullStatus: 'idle',
      pushStatus: 'idle'
    }
  );
}

export function setGitopsState(state: PolicyGitopsState) {
  gitopsStore.set(state.policyId, state);
}
