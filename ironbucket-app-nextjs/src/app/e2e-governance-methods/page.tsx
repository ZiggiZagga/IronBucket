'use client';

import { useScenarioAction } from '@/hooks/useScenarioAction';
import { useState } from 'react';

type GovernanceChecks = {
  getPolicy: boolean;
  searchPolicies: boolean;
  createTenant: boolean;
  addTenant: boolean;
  listTenants: boolean;
  getTenant: boolean;
  tenant: boolean;
  getTenantById: boolean;
  updateTenant: boolean;
  createIdentity: boolean;
  addUser: boolean;
  listIdentities: boolean;
  identity: boolean;
  getIdentityById: boolean;
  getIdentity: boolean;
  updateIdentity: boolean;
  updateUser: boolean;
  removeUser: boolean;
  getUserPermissions: boolean;
  createPolicy: boolean;
  addPolicy: boolean;
  listPolicies: boolean;
  getPolicyById: boolean;
  updatePolicy: boolean;
  evaluatePolicy: boolean;
  validatePolicy: boolean;
  deletePolicy: boolean;
  getPolicyStatistics: boolean;
  policyStats: boolean;
  getUserActivitySummary: boolean;
  userActivity: boolean;
  getResourceAccessPatterns: boolean;
  resourceAccess: boolean;
  getAuditTrail: boolean;
  getAuditLogs: boolean;
  auditLogs: boolean;
  filterAuditLogs: boolean;
  getAuditLogById: boolean;
  auditLogSubscription: boolean;
  onAuditLog: boolean;
  deleteIdentity: boolean;
  deleteTenant: boolean;
};

type GovernanceScenarioResult = {
  actor: string;
  tenantId: string;
  identityId: string;
  policyId: string;
  traceId: string;
  traceparent: string;
  checks: GovernanceChecks;
  operationLatenciesMs: Record<string, number>;
  allMethodsVerified: boolean;
  timestamp: string;
};

export default function E2eGovernanceMethodsPage() {
  const [actor, setActor] = useState<'alice' | 'bob'>('alice');
  const scenario = useScenarioAction<GovernanceScenarioResult>();

  const runScenario = async () => {
    scenario.setStatus('');
    scenario.setError('');
    scenario.setResult(null);

    try {
      const scenarioResult = await scenario.run(async () => {
        const response = await fetch('/api/e2e/governance-methods', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ actor })
        });

        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload?.details || payload?.error || `Scenario failed with ${response.status}`);
        }

        return payload as GovernanceScenarioResult;
      });

      if (scenarioResult.allMethodsVerified) {
        scenario.setStatus(`All governance GraphQL methods verified for tenant ${scenarioResult.tenantId}`);
      } else {
        scenario.setError(`One or more governance method checks failed for tenant ${scenarioResult.tenantId}`);
      }
    } catch (scenarioError) {
      scenario.setError(scenarioError instanceof Error ? scenarioError.message : String(scenarioError));
    }
  };

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">Governance GraphQL Method Coverage Scenario</h1>
        <p className="text-sm text-gray-600">
          Executes Policy, Identity, Tenant, and Audit operations end-to-end through Sentinel GraphQL gateway.
        </p>
      </header>

      <div className="space-y-2">
        <label htmlFor="governance-actor" className="block text-sm font-medium">
          Active user
        </label>
        <select
          id="governance-actor"
          aria-label="Active user"
          value={actor}
          onChange={(event) => setActor(event.target.value as 'alice' | 'bob')}
          className="border rounded px-3 py-2"
        >
          <option value="alice">alice</option>
          <option value="bob">bob</option>
        </select>
      </div>

      <div className="space-y-3 border rounded p-4">
        <button
          type="button"
          onClick={runScenario}
          disabled={scenario.isRunning}
          className="border rounded px-3 py-2"
        >
          {scenario.isRunning ? 'Running...' : 'Run full governance method scenario'}
        </button>
      </div>

      {scenario.status && <p className="text-sm font-medium text-green-700">{scenario.status}</p>}
      {scenario.error && <p className="text-sm font-medium text-red-700">{scenario.error}</p>}

      {scenario.result && (
        <div className="border rounded p-4 text-sm space-y-1" data-testid="governance-methods-result">
          <p>actor: {scenario.result.actor}</p>
          <p>tenantId: {scenario.result.tenantId}</p>
          <p>identityId: {scenario.result.identityId}</p>
          <p>policyId: {scenario.result.policyId}</p>
          <p>traceId: {scenario.result.traceId}</p>
          <p>traceparent: {scenario.result.traceparent}</p>
          <p>allMethodsVerified: {String(scenario.result.allMethodsVerified)}</p>
          <p>getPolicy: {String(scenario.result.checks.getPolicy)}</p>
          <p>searchPolicies: {String(scenario.result.checks.searchPolicies)}</p>
          <p>createTenant: {String(scenario.result.checks.createTenant)}</p>
          <p>addTenant: {String(scenario.result.checks.addTenant)}</p>
          <p>listTenants: {String(scenario.result.checks.listTenants)}</p>
          <p>getTenant: {String(scenario.result.checks.getTenant)}</p>
          <p>tenant: {String(scenario.result.checks.tenant)}</p>
          <p>getTenantById: {String(scenario.result.checks.getTenantById)}</p>
          <p>updateTenant: {String(scenario.result.checks.updateTenant)}</p>
          <p>createIdentity: {String(scenario.result.checks.createIdentity)}</p>
          <p>addUser: {String(scenario.result.checks.addUser)}</p>
          <p>listIdentities: {String(scenario.result.checks.listIdentities)}</p>
          <p>identity: {String(scenario.result.checks.identity)}</p>
          <p>getIdentityById: {String(scenario.result.checks.getIdentityById)}</p>
          <p>getIdentity: {String(scenario.result.checks.getIdentity)}</p>
          <p>updateIdentity: {String(scenario.result.checks.updateIdentity)}</p>
          <p>updateUser: {String(scenario.result.checks.updateUser)}</p>
          <p>removeUser: {String(scenario.result.checks.removeUser)}</p>
          <p>getUserPermissions: {String(scenario.result.checks.getUserPermissions)}</p>
          <p>createPolicy: {String(scenario.result.checks.createPolicy)}</p>
          <p>addPolicy: {String(scenario.result.checks.addPolicy)}</p>
          <p>listPolicies: {String(scenario.result.checks.listPolicies)}</p>
          <p>getPolicyById: {String(scenario.result.checks.getPolicyById)}</p>
          <p>updatePolicy: {String(scenario.result.checks.updatePolicy)}</p>
          <p>evaluatePolicy: {String(scenario.result.checks.evaluatePolicy)}</p>
          <p>validatePolicy: {String(scenario.result.checks.validatePolicy)}</p>
          <p>deletePolicy: {String(scenario.result.checks.deletePolicy)}</p>
          <p>getPolicyStatistics: {String(scenario.result.checks.getPolicyStatistics)}</p>
          <p>policyStats: {String(scenario.result.checks.policyStats)}</p>
          <p>getUserActivitySummary: {String(scenario.result.checks.getUserActivitySummary)}</p>
          <p>userActivity: {String(scenario.result.checks.userActivity)}</p>
          <p>getResourceAccessPatterns: {String(scenario.result.checks.getResourceAccessPatterns)}</p>
          <p>resourceAccess: {String(scenario.result.checks.resourceAccess)}</p>
          <p>getAuditTrail: {String(scenario.result.checks.getAuditTrail)}</p>
          <p>getAuditLogs: {String(scenario.result.checks.getAuditLogs)}</p>
          <p>auditLogs: {String(scenario.result.checks.auditLogs)}</p>
          <p>filterAuditLogs: {String(scenario.result.checks.filterAuditLogs)}</p>
          <p>getAuditLogById: {String(scenario.result.checks.getAuditLogById)}</p>
          <p>auditLogSubscription: {String(scenario.result.checks.auditLogSubscription)}</p>
          <p>onAuditLog: {String(scenario.result.checks.onAuditLog)}</p>
          <p>deleteIdentity: {String(scenario.result.checks.deleteIdentity)}</p>
          <p>deleteTenant: {String(scenario.result.checks.deleteTenant)}</p>
          {Object.entries(scenario.result.operationLatenciesMs).map(([operation, value]) => (
            <p key={operation}>operation.{operation}.ms: {value.toFixed(2)}</p>
          ))}
          <p>timestamp: {scenario.result.timestamp}</p>
        </div>
      )}
    </section>
  );
}
