'use client';

import { useMemo, useState } from 'react';

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
  const [actor, setActor] = useState<'alice' | 'bob'>('alice');
  const [status, setStatus] = useState('');
  const [error, setError] = useState('');
  const [isRunning, setIsRunning] = useState(false);
  const [result, setResult] = useState<ScenarioResult | null>(null);
  const [proofFile, setProofFile] = useState<File | null>(null);
  const [proofResult, setProofResult] = useState<ScreenshotProofResult | null>(null);
  const [proofError, setProofError] = useState('');
  const [proofStatus, setProofStatus] = useState('');
  const [proofUploading, setProofUploading] = useState(false);

  const bucket = useMemo(() => `default-${actor}-files`, [actor]);

  const runScenario = async () => {
    setStatus('');
    setError('');
    setResult(null);
    setIsRunning(true);

    try {
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

      const scenarioResult = payload as ScenarioResult;
      setResult(scenarioResult);

      if (scenarioResult.allMethodsVerified) {
        setStatus(`All S3 GraphQL methods verified for ${scenarioResult.key}`);
      } else {
        setError(`One or more method checks failed for ${scenarioResult.key}`);
      }
    } catch (scenarioError) {
      setError(scenarioError instanceof Error ? scenarioError.message : String(scenarioError));
    } finally {
      setIsRunning(false);
    }
  };

  const uploadScreenshotProof = async () => {
    setProofStatus('');
    setProofError('');
    setProofResult(null);

    if (!proofFile) {
      setProofError('Select a screenshot file first.');
      return;
    }

    setProofUploading(true);

    try {
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

      const nextProof = payload as ScreenshotProofResult;
      setProofResult(nextProof);

      if (nextProof.proofStored) {
        setProofStatus(`Screenshot proof stored and downloaded: ${nextProof.key}`);
      } else {
        setProofError(`Screenshot proof was uploaded but not verified in list: ${nextProof.key}`);
      }
    } catch (uploadError) {
      setProofError(uploadError instanceof Error ? uploadError.message : String(uploadError));
    } finally {
      setProofUploading(false);
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
          disabled={isRunning}
          className="border rounded px-3 py-2"
        >
          {isRunning ? 'Running...' : 'Run full S3 method scenario'}
        </button>
      </div>

      {status && <p className="text-sm font-medium text-green-700">{status}</p>}
      {error && <p className="text-sm font-medium text-red-700">{error}</p>}

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
          disabled={proofUploading}
          className="border rounded px-3 py-2"
        >
          {proofUploading ? 'Uploading proof...' : 'Upload screenshot proof'}
        </button>

        {proofStatus && <p className="text-sm font-medium text-green-700">{proofStatus}</p>}
        {proofError && <p className="text-sm font-medium text-red-700">{proofError}</p>}

        {proofResult && (
          <div className="border rounded p-4 text-sm space-y-1" data-testid="screenshot-proof-result">
            <p>proofStored: {String(proofResult.proofStored)}</p>
            <p>proofBucket: {proofResult.bucket}</p>
            <p>proofKey: {proofResult.key}</p>
            <p>proofDownloadUrl: {proofResult.downloadUrl}</p>
            <img
              src={proofResult.previewDataUrl}
              alt="Downloaded screenshot proof"
              data-testid="screenshot-proof-preview"
              className="max-w-full border rounded"
            />
          </div>
        )}
      </div>

      {result && (
        <div className="border rounded p-4 text-sm space-y-1" data-testid="s3-methods-result">
          <p>actor: {result.actor}</p>
          <p>bucket: {result.bucket}</p>
          <p>key: {result.key}</p>
          <p>traceId: {result.traceId}</p>
          <p>traceparent: {result.traceparent}</p>
          <p>allMethodsVerified: {String(result.allMethodsVerified)}</p>
          <p>createBucket: {String(result.checks.createBucket)}</p>
          <p>listBuckets: {String(result.checks.listBuckets)}</p>
          <p>getBucket: {String(result.checks.getBucket)}</p>
          <p>uploadObject: {String(result.checks.uploadObject)}</p>
          <p>listObjects: {String(result.checks.listObjects)}</p>
          <p>getObject: {String(result.checks.getObject)}</p>
          <p>getBucketRoutingDecision: {String(result.checks.getBucketRoutingDecision)}</p>
          <p>downloadObject: {String(result.checks.downloadObject)}</p>
          <p>deleteObject: {String(result.checks.deleteObject)}</p>
          <p>deleteBucket: {String(result.checks.deleteBucket)}</p>
          <p>expectedServices: {result.expectedServices.join(', ')}</p>
          <p>timestamp: {result.timestamp}</p>
        </div>
      )}
    </section>
  );
}
