'use client';

import { useState } from 'react';
import { useActorBucket } from '@/hooks/useActorBucket';
import { useScenarioAction } from '@/hooks/useScenarioAction';

type UploadResult = {
  actor: string;
  bucket: string;
  key: string;
  verified: boolean;
  roundtripSize: number;
  timestamp: string;
};

export default function E2eUploadLivePage() {
  const { actor, setActor, bucket } = useActorBucket('files');
  const [file, setFile] = useState<File | null>(null);
  const scenario = useScenarioAction<UploadResult>();

  const onUpload = async () => {
    scenario.setStatus('');
    scenario.setError('');
    scenario.setResult(null);

    if (!file) {
      scenario.setError('Select a file first.');
      return;
    }

    try {
      const uploadResult = await scenario.run(async () => {
        const content = await file.text();
        const key = `${actor}-live-ui-${Date.now()}-${file.name}`;

        const response = await fetch('/api/e2e/live-upload', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({
            actor,
            key,
            content,
            contentType: file.type || 'text/plain'
          })
        });

        const payload = await response.json();
        if (!response.ok) {
          throw new Error(payload?.details || payload?.error || `Upload failed with ${response.status}`);
        }

        return payload as UploadResult;
      });

      if (uploadResult.verified) {
        scenario.setStatus(`Live upload verified for ${uploadResult.key}`);
      } else {
        scenario.setError(`Upload completed but round-trip verification failed for ${uploadResult.key}`);
      }
    } catch (uploadError) {
      scenario.setError(uploadError instanceof Error ? uploadError.message : String(uploadError));
    }
  };

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">Live UI Upload Persistence Scenario</h1>
        <p className="text-sm text-gray-600">
          Upload via UI to live Sentinel-Gear path and verify read-back persistence in the same flow.
        </p>
      </header>

      <div className="space-y-2">
        <label htmlFor="live-actor" className="block text-sm font-medium">
          Active user
        </label>
        <select
          id="live-actor"
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
        <label htmlFor="live-upload-file" className="block text-sm font-medium">
          Upload file
        </label>
        <input
          id="live-upload-file"
          data-testid="live-upload-file"
          type="file"
          onChange={(event) => setFile(event.target.files?.[0] ?? null)}
        />

        <button
          type="button"
          onClick={onUpload}
          disabled={scenario.isRunning}
          className="border rounded px-3 py-2"
        >
          {scenario.isRunning ? 'Uploading...' : 'Upload live'}
        </button>
      </div>

      {scenario.status && <p className="text-sm font-medium text-green-700">{scenario.status}</p>}
      {scenario.error && <p className="text-sm font-medium text-red-700">{scenario.error}</p>}

      {scenario.result && (
        <div className="border rounded p-4 text-sm" data-testid="live-upload-result">
          <p>actor: {scenario.result.actor}</p>
          <p>bucket: {scenario.result.bucket}</p>
          <p>key: {scenario.result.key}</p>
          <p>verified: {String(scenario.result.verified)}</p>
          <p>roundtripSize: {scenario.result.roundtripSize}</p>
          <p>timestamp: {scenario.result.timestamp}</p>
        </div>
      )}
    </section>
  );
}