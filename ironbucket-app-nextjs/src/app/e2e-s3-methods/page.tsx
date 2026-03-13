'use client';

import { useState } from 'react';
import { useActorBucket } from '@/hooks/useActorBucket';
import { useScenarioAction } from '@/hooks/useScenarioAction';

type MethodChecks = {
  createBucket: boolean;
  listBuckets: boolean;
  getBucket: boolean;
  uploadObject: boolean;
  listObjects: boolean;
  getObject: boolean;
  getBucketRoutingDecision: boolean;
  downloadObject: boolean;
  deleteObject: boolean;
  deleteBucket: boolean;
};

type ScenarioResult = {
  actor: string;
  bucket: string;
  key: string;
  traceId: string;
  traceparent: string;
  checks: MethodChecks;
  allMethodsVerified: boolean;
  expectedServices: string[];
  timestamp: string;
};

type ScreenshotProofResult = {
  actor: string;
  bucket: string;
  key: string;
  proofStored: boolean;
  downloadUrl: string;
  previewDataUrl: string;
  timestamp: string;
};

export default function E2eS3MethodsPage() {
  const { actor, setActor, bucket } = useActorBucket('methods');
  const scenario = useScenarioAction<ScenarioResult>();
  const [proofFile, setProofFile] = useState<File | null>(null);
  const proofScenario = useScenarioAction<ScreenshotProofResult>();

  const runScenario = async () => {
    scenario.setStatus('');
    scenario.setError('');
    scenario.setResult(null);

    try {
      const scenarioResult = await scenario.run(async () => {
        const response = await fetch('/api/e2e/s3-methods', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            actor,
            content: `all-methods-ui-content-${Date.now()}`
          })
        });

        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload?.details || payload?.error || `Scenario failed with ${response.status}`);
        }

        return payload as ScenarioResult;
      });

      if (scenarioResult.allMethodsVerified) {
        scenario.setStatus(`All S3 GraphQL methods verified for ${scenarioResult.key}`);
      } else {
        scenario.setError(`One or more method checks failed for ${scenarioResult.key}`);
      }
    } catch (scenarioError) {
      scenario.setError(scenarioError instanceof Error ? scenarioError.message : String(scenarioError));
    }
  };

  const uploadScreenshotProof = async () => {
    proofScenario.setStatus('');
    proofScenario.setError('');
    proofScenario.setResult(null);

    if (!proofFile) {
      proofScenario.setError('Select a screenshot file first.');
      return;
    }

    try {
      const nextProof = await proofScenario.run(async () => {
        const binary = await proofFile.arrayBuffer();
        const bytes = new Uint8Array(binary);
        let value = '';
        bytes.forEach((item) => {
          value += String.fromCharCode(item);
        });

        const screenshotBase64 = btoa(value);

        const response = await fetch('/api/e2e/screenshot-proof', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            actor,
            screenshotBase64,
            mimeType: proofFile.type || 'image/png'
          })
        });

        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload?.details || payload?.error || `Proof failed with ${response.status}`);
        }

        return payload as ScreenshotProofResult;
      });

      if (nextProof.proofStored) {
        proofScenario.setStatus(`Screenshot proof stored and downloaded: ${nextProof.key}`);
      } else {
        proofScenario.setError(`Screenshot proof was uploaded but not verified in list: ${nextProof.key}`);
      }
    } catch (uploadError) {
      proofScenario.setError(uploadError instanceof Error ? uploadError.message : String(uploadError));
    }
  };

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">S3 GraphQL Method Coverage Scenario</h1>
        <p className="text-sm text-gray-600">
          Executes all exposed object-browser GraphQL methods end-to-end and captures a shared trace identifier.
        </p>
      </header>

      <div className="space-y-2">
        <label htmlFor="s3-methods-actor" className="block text-sm font-medium">
          Active user
        </label>
        <select
          id="s3-methods-actor"
          aria-label="Active user"
          value={actor}
          onChange={(event) => setActor(event.target.value as 'alice' | 'bob')}
          className="border rounded px-3 py-2"
        >
          <option value="alice">alice</option>
          <option value="bob">bob</option>
        </select>
        <p className="text-xs text-gray-500">Target bucket: {bucket}</p>
      </div>

      <div className="space-y-3 border rounded p-4">
        <button
          type="button"
          onClick={runScenario}
          disabled={scenario.isRunning}
          className="border rounded px-3 py-2"
        >
          {scenario.isRunning ? 'Running...' : 'Run full S3 method scenario'}
        </button>
      </div>

      {scenario.status && <p className="text-sm font-medium text-green-700">{scenario.status}</p>}
      {scenario.error && <p className="text-sm font-medium text-red-700">{scenario.error}</p>}

      <div className="space-y-3 border rounded p-4">
        <h2 className="text-lg font-semibold">Screenshot Proof (MinIO)</h2>
        <p className="text-xs text-gray-500">
          Uploads a Playwright screenshot as proof artifact to MinIO, downloads it again, and renders preview.
        </p>
        <input
          type="file"
          accept="image/png,image/jpeg"
          data-testid="screenshot-proof-file"
          onChange={(event) => setProofFile(event.target.files?.[0] ?? null)}
        />
        <button
          type="button"
          data-testid="upload-screenshot-proof"
          onClick={uploadScreenshotProof}
          disabled={proofScenario.isRunning}
          className="border rounded px-3 py-2"
        >
          {proofScenario.isRunning ? 'Uploading proof...' : 'Upload screenshot proof'}
        </button>

        {proofScenario.status && <p className="text-sm font-medium text-green-700">{proofScenario.status}</p>}
        {proofScenario.error && <p className="text-sm font-medium text-red-700">{proofScenario.error}</p>}

        {proofScenario.result && (
          <div className="border rounded p-4 text-sm space-y-1" data-testid="screenshot-proof-result">
            <p>proofStored: {String(proofScenario.result.proofStored)}</p>
            <p>proofBucket: {proofScenario.result.bucket}</p>
            <p>proofKey: {proofScenario.result.key}</p>
            <p>proofDownloadUrl: {proofScenario.result.downloadUrl}</p>
            <img
              src={proofScenario.result.previewDataUrl}
              alt="Downloaded screenshot proof"
              data-testid="screenshot-proof-preview"
              className="max-w-full border rounded"
            />
          </div>
        )}
      </div>

      {scenario.result && (
        <div className="border rounded p-4 text-sm space-y-1" data-testid="s3-methods-result">
          <p>actor: {scenario.result.actor}</p>
          <p>bucket: {scenario.result.bucket}</p>
          <p>key: {scenario.result.key}</p>
          <p>traceId: {scenario.result.traceId}</p>
          <p>traceparent: {scenario.result.traceparent}</p>
          <p>allMethodsVerified: {String(scenario.result.allMethodsVerified)}</p>
          <p>createBucket: {String(scenario.result.checks.createBucket)}</p>
          <p>listBuckets: {String(scenario.result.checks.listBuckets)}</p>
          <p>getBucket: {String(scenario.result.checks.getBucket)}</p>
          <p>uploadObject: {String(scenario.result.checks.uploadObject)}</p>
          <p>listObjects: {String(scenario.result.checks.listObjects)}</p>
          <p>getObject: {String(scenario.result.checks.getObject)}</p>
          <p>getBucketRoutingDecision: {String(scenario.result.checks.getBucketRoutingDecision)}</p>
          <p>downloadObject: {String(scenario.result.checks.downloadObject)}</p>
          <p>deleteObject: {String(scenario.result.checks.deleteObject)}</p>
          <p>deleteBucket: {String(scenario.result.checks.deleteBucket)}</p>
          <p>expectedServices: {scenario.result.expectedServices.join(', ')}</p>
          <p>timestamp: {scenario.result.timestamp}</p>
        </div>
      )}
    </section>
  );
}
