'use client';

import { useMemo, useState } from 'react';

type UploadResult = {
  actor: string;
  bucket: string;
  key: string;
  verified: boolean;
  roundtripSize: number;
  timestamp: string;
};

export default function E2eUploadLivePage() {
  const [actor, setActor] = useState<'alice' | 'bob'>('alice');
  const [file, setFile] = useState<File | null>(null);
  const [status, setStatus] = useState<string>('');
  const [error, setError] = useState<string>('');
  const [result, setResult] = useState<UploadResult | null>(null);
  const [isSubmitting, setIsSubmitting] = useState(false);

  const bucket = useMemo(() => `default-${actor}-files`, [actor]);

  const onUpload = async () => {
    setStatus('');
    setError('');
    setResult(null);

    if (!file) {
      setError('Select a file first.');
      return;
    }

    setIsSubmitting(true);

    try {
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

      const uploadResult = payload as UploadResult;
      setResult(uploadResult);

      if (uploadResult.verified) {
        setStatus(`Live upload verified for ${uploadResult.key}`);
      } else {
        setError(`Upload completed but round-trip verification failed for ${uploadResult.key}`);
      }
    } catch (uploadError) {
      setError(uploadError instanceof Error ? uploadError.message : String(uploadError));
    } finally {
      setIsSubmitting(false);
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
          disabled={isSubmitting}
          className="border rounded px-3 py-2"
        >
          {isSubmitting ? 'Uploading...' : 'Upload live'}
        </button>
      </div>

      {status && <p className="text-sm font-medium text-green-700">{status}</p>}
      {error && <p className="text-sm font-medium text-red-700">{error}</p>}

      {result && (
        <div className="border rounded p-4 text-sm" data-testid="live-upload-result">
          <p>actor: {result.actor}</p>
          <p>bucket: {result.bucket}</p>
          <p>key: {result.key}</p>
          <p>verified: {String(result.verified)}</p>
          <p>roundtripSize: {result.roundtripSize}</p>
          <p>timestamp: {result.timestamp}</p>
        </div>
      )}
    </section>
  );
}