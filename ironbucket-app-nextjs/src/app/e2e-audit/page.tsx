'use client';

import { useState } from 'react';
import { useScenarioAction } from '@/hooks/useScenarioAction';
import type { SupportedActor } from '@/lib/e2e/runtime';

type AuditChecks = {
  getAuditTrail: boolean;
  getAuditLogs: boolean;
  auditLogsAlias: boolean;
  filterAuditLogs: boolean;
  getAuditLogById: boolean;
  getPolicyStatistics: boolean;
  policyStatsAlias: boolean;
  getUserActivitySummary: boolean;
  userActivityAlias: boolean;
  getResourceAccessPatterns: boolean;
  resourceAccessAlias: boolean;
};

type AuditScenarioResult = {
  actor: string;
  tenantId: string;
  traceId: string;
  traceparent: string;
  checks: AuditChecks;
  allVerified: boolean;
  auditLogCount: number;
  timestamp: string;
};

const ACTORS: SupportedActor[] = ['alice', 'bob', 'charlie', 'dana', 'eve'];

export default function E2eAuditPage() {
  const [actor, setActor] = useState<SupportedActor>('alice');
  const scenario = useScenarioAction<AuditScenarioResult>();

  const runScenario = async () => {
    scenario.setStatus('');
    scenario.setError('');
    scenario.setResult(null);

    try {
      const result = await scenario.run(async () => {
        const response = await fetch('/api/e2e/audit', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ actor })
        });
        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload?.details ?? payload?.error ?? `Scenario failed with ${response.status}`);
        }
        return payload as AuditScenarioResult;
      });

      if (result.allVerified) {
        scenario.setStatus(`All Audit GraphQL methods verified for actor: ${result.actor}`);
      } else {
        const failed = Object.entries(result.checks)
          .filter(([, v]) => !v)
          .map(([k]) => k)
          .join(', ');
        scenario.setError(`Audit scenario completed with failures: ${failed}`);
      }
    } catch (err) {
      scenario.setError(err instanceof Error ? err.message : String(err));
    }
  };

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">Audit &amp; Stats E2E Scenario</h1>
        <p className="text-sm text-gray-600">
          Exercises all Audit and Stats GraphQL queries: getAuditTrail, getAuditLogs,
          auditLogs (alias), filterAuditLogs, getAuditLogById, getPolicyStatistics,
          policyStats (alias), getUserActivitySummary, userActivity (alias),
          getResourceAccessPatterns, resourceAccess (alias).
          Audit data comes from real Loki logs via LGTM stack.
        </p>
      </header>

      <div className="space-y-2">
        <label htmlFor="audit-actor" className="block text-sm font-medium">Active user</label>
        <select
          id="audit-actor"
          aria-label="Active user"
          value={actor}
          onChange={(e) => setActor(e.target.value as SupportedActor)}
          className="border rounded px-3 py-2"
        >
          {ACTORS.map((a) => <option key={a} value={a}>{a}</option>)}
        </select>
      </div>

      <button
        type="button"
        onClick={runScenario}
        disabled={scenario.isRunning}
        className="border rounded px-4 py-2"
      >
        {scenario.isRunning ? 'Running...' : 'Run full Audit & Stats scenario'}
      </button>

      {scenario.status && (
        <p className="text-sm font-medium text-green-700">{scenario.status}</p>
      )}
      {scenario.error && (
        <p className="text-sm font-medium text-red-700">{scenario.error}</p>
      )}

      {scenario.result && (
        <div className="border rounded p-4 text-sm space-y-1" data-testid="audit-scenario-result">
          <p>actor: {scenario.result.actor}</p>
          <p>tenantId: {scenario.result.tenantId}</p>
          <p>traceId: {scenario.result.traceId}</p>
          <p>allVerified: {String(scenario.result.allVerified)}</p>
          <p>auditLogCount: {scenario.result.auditLogCount}</p>
          {Object.entries(scenario.result.checks).map(([key, value]) => (
            <p key={key}>{key}: {String(value)}</p>
          ))}
          <p>timestamp: {scenario.result.timestamp}</p>
        </div>
      )}
    </section>
  );
}
