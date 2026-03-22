'use client';

import { useState } from 'react';
import { useScenarioAction } from '@/hooks/useScenarioAction';
import type { SupportedActor } from '@/lib/e2e/runtime';

type TenantChecks = {
  createTenant: boolean;
  addTenant: boolean;
  getTenantById: boolean;
  getTenant: boolean;
  tenantAlias: boolean;
  listTenants: boolean;
  updateTenant: boolean;
  deleteTenant: boolean;
};

type TenantScenarioResult = {
  actor: string;
  traceId: string;
  traceparent: string;
  checks: TenantChecks;
  allVerified: boolean;
  timestamp: string;
};

const ACTORS: SupportedActor[] = ['alice', 'bob', 'charlie', 'dana', 'eve'];

export default function E2eTenantPage() {
  const [actor, setActor] = useState<SupportedActor>('alice');
  const scenario = useScenarioAction<TenantScenarioResult>();

  const runScenario = async () => {
    scenario.setStatus('');
    scenario.setError('');
    scenario.setResult(null);

    try {
      const result = await scenario.run(async () => {
        const response = await fetch('/api/e2e/tenant', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ actor })
        });
        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload?.details ?? payload?.error ?? `Scenario failed with ${response.status}`);
        }
        return payload as TenantScenarioResult;
      });

      if (result.allVerified) {
        scenario.setStatus(`All Tenant GraphQL methods verified for actor: ${result.actor}`);
      } else {
        const failed = Object.entries(result.checks)
          .filter(([, v]) => !v)
          .map(([k]) => k)
          .join(', ');
        scenario.setError(`Tenant scenario completed with failures: ${failed}`);
      }
    } catch (err) {
      scenario.setError(err instanceof Error ? err.message : String(err));
    }
  };

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">Tenant CRUD E2E Scenario</h1>
        <p className="text-sm text-gray-600">
          Exercises all Tenant queries and mutations: createTenant, addTenant, getTenant,
          tenant (alias), getTenantById, listTenants, updateTenant, deleteTenant.
        </p>
      </header>

      <div className="space-y-2">
        <label htmlFor="tenant-actor" className="block text-sm font-medium">Active user</label>
        <select
          id="tenant-actor"
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
        {scenario.isRunning ? 'Running...' : 'Run full Tenant scenario'}
      </button>

      {scenario.status && (
        <p className="text-sm font-medium text-green-700">{scenario.status}</p>
      )}
      {scenario.error && (
        <p className="text-sm font-medium text-red-700">{scenario.error}</p>
      )}

      {scenario.result && (
        <div className="border rounded p-4 text-sm space-y-1" data-testid="tenant-scenario-result">
          <p>actor: {scenario.result.actor}</p>
          <p>traceId: {scenario.result.traceId}</p>
          <p>allVerified: {String(scenario.result.allVerified)}</p>
          {Object.entries(scenario.result.checks).map(([key, value]) => (
            <p key={key}>{key}: {String(value)}</p>
          ))}
          <p>timestamp: {scenario.result.timestamp}</p>
        </div>
      )}
    </section>
  );
}
