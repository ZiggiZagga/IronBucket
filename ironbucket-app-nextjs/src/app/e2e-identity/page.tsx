'use client';

import { useState } from 'react';
import { useScenarioAction } from '@/hooks/useScenarioAction';
import type { SupportedActor } from '@/lib/e2e/runtime';

type IdentityChecks = {
  createIdentity: boolean;
  addUser: boolean;
  getIdentityById: boolean;
  identityAlias: boolean;
  getIdentity: boolean;
  listIdentities: boolean;
  getUserPermissions: boolean;
  updateIdentity: boolean;
  updateUser: boolean;
  deleteIdentity: boolean;
  removeUser: boolean;
};

type IdentityScenarioResult = {
  actor: string;
  tenantId: string;
  traceId: string;
  traceparent: string;
  checks: IdentityChecks;
  allVerified: boolean;
  timestamp: string;
};

const ACTORS: SupportedActor[] = ['alice', 'bob', 'charlie', 'dana', 'eve'];

export default function E2eIdentityPage() {
  const [actor, setActor] = useState<SupportedActor>('alice');
  const scenario = useScenarioAction<IdentityScenarioResult>();

  const runScenario = async () => {
    scenario.setStatus('');
    scenario.setError('');
    scenario.setResult(null);

    try {
      const result = await scenario.run(async () => {
        const response = await fetch('/api/e2e/identity', {
          method: 'POST',
          headers: { 'Content-Type': 'application/json' },
          body: JSON.stringify({ actor })
        });
        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload?.details ?? payload?.error ?? `Scenario failed with ${response.status}`);
        }
        return payload as IdentityScenarioResult;
      });

      if (result.allVerified) {
        scenario.setStatus(`All Identity GraphQL methods verified for actor: ${result.actor}`);
      } else {
        const failed = Object.entries(result.checks)
          .filter(([, v]) => !v)
          .map(([k]) => k)
          .join(', ');
        scenario.setError(`Identity scenario completed with failures: ${failed}`);
      }
    } catch (err) {
      scenario.setError(err instanceof Error ? err.message : String(err));
    }
  };

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">Identity CRUD E2E Scenario</h1>
        <p className="text-sm text-gray-600">
          Exercises all Identity queries and mutations: createIdentity, addUser, getIdentity,
          identity, getIdentityById, listIdentities, getUserPermissions, updateIdentity,
          updateUser, deleteIdentity, removeUser.
        </p>
      </header>

      <div className="space-y-2">
        <label htmlFor="identity-actor" className="block text-sm font-medium">Active user</label>
        <select
          id="identity-actor"
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
        {scenario.isRunning ? 'Running...' : 'Run full Identity scenario'}
      </button>

      {scenario.status && (
        <p className="text-sm font-medium text-green-700">{scenario.status}</p>
      )}
      {scenario.error && (
        <p className="text-sm font-medium text-red-700">{scenario.error}</p>
      )}

      {scenario.result && (
        <div className="border rounded p-4 text-sm space-y-1" data-testid="identity-scenario-result">
          <p>actor: {scenario.result.actor}</p>
          <p>tenantId: {scenario.result.tenantId}</p>
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
