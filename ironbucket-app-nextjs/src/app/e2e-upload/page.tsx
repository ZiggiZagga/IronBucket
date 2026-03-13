'use client';

import { useEffect, useState } from 'react';
import UploadDialog from '@/components/ironbucket/UploadDialog';
import { useActorBucket } from '@/hooks/useActorBucket';

type UploadEvent = {
  actor: string;
  bucket: string;
  key: string;
  size: number;
  uploadedAt: string;
};

const ACTORS = ['alice', 'bob'] as const;

export default function UiUploadE2ePage() {
  const { actor: activeActor, setActor: setActiveActor, bucket } = useActorBucket('files');
  const [events, setEvents] = useState<UploadEvent[]>([]);

  useEffect(() => {
    window.localStorage.setItem('ironbucket.e2e.actor', activeActor);
  }, [activeActor]);

  return (
    <section className="space-y-6">
      <header>
        <h1 className="text-2xl font-bold">UI E2E Upload Scenario</h1>
        <p className="text-sm text-gray-600">
          Upload as Alice and Bob through the UI. Each GraphQL upload request carries traceparent + actor headers.
        </p>
      </header>

      <div className="space-y-2">
        <label htmlFor="actor-select" className="block text-sm font-medium">
          Active user
        </label>
        <select
          id="actor-select"
          value={activeActor}
          onChange={(event) => setActiveActor(event.target.value as 'alice' | 'bob')}
          className="border rounded px-3 py-2"
        >
          {ACTORS.map((actor) => (
            <option key={actor} value={actor}>
              {actor}
            </option>
          ))}
        </select>
        <p className="text-xs text-gray-500">Current bucket: {bucket}</p>
      </div>

      <UploadDialog
        bucket={bucket}
        onClose={() => undefined}
        autoClose={false}
        onSuccess={(result) => {
          setEvents((previous) => [
            {
              actor: activeActor,
              bucket: result.bucket,
              key: result.key,
              size: result.size,
              uploadedAt: new Date().toISOString()
            },
            ...previous
          ]);
        }}
      />

      <div>
        <h2 className="text-lg font-semibold mb-2">Upload timeline</h2>
        {events.length === 0 ? (
          <p className="text-sm text-gray-500">No uploads yet.</p>
        ) : (
          <ul className="space-y-2" data-testid="upload-events">
            {events.map((event, index) => (
              <li key={`${event.actor}-${event.key}-${index}`} className="text-sm border rounded px-3 py-2">
                {event.actor} uploaded {event.key} to {event.bucket} ({event.size} bytes)
              </li>
            ))}
          </ul>
        )}
      </div>
    </section>
  );
}
