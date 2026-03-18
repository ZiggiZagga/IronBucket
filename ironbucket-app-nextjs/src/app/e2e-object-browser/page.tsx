'use client';

import { Suspense, useEffect, useMemo, useState, type ChangeEvent } from 'react';
import { useSearchParams } from 'next/navigation';

type Bucket = {
  name: string;
};

type BrowserObject = {
  key: string;
  size: number;
  lastModified?: string;
};

function E2eObjectBrowserContent() {
  const searchParams = useSearchParams();
  const [actor, setActor] = useState('alice');
  const [authReady, setAuthReady] = useState(false);
  const [authError, setAuthError] = useState('');
  const [loadingBuckets, setLoadingBuckets] = useState(false);
  const [loadingObjects, setLoadingObjects] = useState(false);
  const [buckets, setBuckets] = useState<Bucket[]>([]);
  const [objects, setObjects] = useState<BrowserObject[]>([]);
  const [selectedBucket, setSelectedBucket] = useState<string>('');
  const [bootstrapBucket, setBootstrapBucket] = useState<string>('');
  const [draftSearch, setDraftSearch] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [draftSortDirection, setDraftSortDirection] = useState<'asc' | 'desc'>('asc');
  const [appliedSortDirection, setAppliedSortDirection] = useState<'asc' | 'desc'>('asc');
  const [pendingUploadFile, setPendingUploadFile] = useState<File | null>(null);
  const [statusMessage, setStatusMessage] = useState('');
  const bucketFromQuery = searchParams.get('bucket') ?? '';
  const activeBucket = selectedBucket || bootstrapBucket;

  const sortedObjects = useMemo(() => {
    const clone = [...objects];
    clone.sort((a, b) => a.key.localeCompare(b.key));
    if (appliedSortDirection === 'desc') {
      clone.reverse();
    }
    return clone;
  }, [objects, appliedSortDirection]);

  const postJson = async <TPayload, TResponse>(url: string, payload: TPayload): Promise<TResponse> => {
    const response = await fetch(url, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json'
      },
      body: JSON.stringify(payload)
    });

    const data = (await response.json()) as TResponse & { error?: string; details?: string };
    if (!response.ok) {
      throw new Error(data.details || data.error || `Request failed with ${response.status}`);
    }

    return data;
  };

  const refreshBuckets = async () => {
    setLoadingBuckets(true);
    try {
      const payload = await postJson<{ actor: string; action: 'listBuckets' }, { buckets?: Array<{ name?: string }> }>(
        '/api/e2e/object-browser-ops',
        { actor, action: 'listBuckets' }
      );

      const nextBuckets = (payload.buckets ?? [])
        .map((bucket) => ({ name: bucket?.name ?? '' }))
        .filter((bucket) => bucket.name.length > 0);
      setBuckets(nextBuckets);
    } finally {
      setLoadingBuckets(false);
    }
  };

  const refreshObjects = async (bucket: string, query: string) => {
    if (!bucket) {
      setObjects([]);
      return;
    }

    setLoadingObjects(true);
    try {
      const payload = await postJson<
        { actor: string; action: 'listObjects'; bucket: string; query: string },
        { objects?: BrowserObject[] }
      >('/api/e2e/object-browser-ops', {
        actor,
        action: 'listObjects',
        bucket,
        query
      });

      setObjects(payload.objects ?? []);
    } finally {
      setLoadingObjects(false);
    }
  };

  useEffect(() => {
    window.localStorage.setItem('ironbucket.e2e.actor', actor);
  }, [actor]);

  useEffect(() => {
    let cancelled = false;

    const bootstrapActorSession = async () => {
      setAuthReady(false);
      setAuthError('');

      try {
        const response = await fetch('/api/e2e/actor-token', {
          method: 'POST',
          headers: {
            'Content-Type': 'application/json'
          },
          body: JSON.stringify({ actor })
        });

        const payload = (await response.json()) as { accessToken?: string; error?: string; details?: string };
        if (!response.ok || !payload.accessToken) {
          throw new Error(payload.details || payload.error || `Failed to create actor session (${response.status})`);
        }

        if (!cancelled) {
          setAuthReady(true);
        }
      } catch (error) {
        if (!cancelled) {
          setAuthError(error instanceof Error ? error.message : String(error));
        }
      }
    };

    bootstrapActorSession().catch(() => undefined);

    return () => {
      cancelled = true;
    };
  }, [actor]);

  useEffect(() => {
    if (!selectedBucket) {
      const storedBucket = window.localStorage.getItem('ironbucket.e2e.bucket') ?? '';
      if (storedBucket) {
        setBootstrapBucket(storedBucket);
        setSelectedBucket(storedBucket);
      }
      return;
    }

    window.localStorage.setItem('ironbucket.e2e.bucket', selectedBucket);
  }, [selectedBucket]);

  useEffect(() => {
    if (!authReady) {
      return;
    }

    refreshBuckets().catch((error) => {
      setAuthError(error instanceof Error ? error.message : String(error));
    });
  }, [actor, authReady]);

  useEffect(() => {
    if (!selectedBucket && buckets.length > 0) {
      setSelectedBucket(buckets[0].name);
    }
  }, [buckets, selectedBucket]);

  useEffect(() => {
    if (bucketFromQuery) {
      setBootstrapBucket(bucketFromQuery);
    }

    if (bucketFromQuery && selectedBucket !== bucketFromQuery) {
      setSelectedBucket(bucketFromQuery);
    }
  }, [bucketFromQuery, selectedBucket]);

  useEffect(() => {
    if (!authReady) {
      return;
    }

    if (!activeBucket) {
      return;
    }

    refreshObjects(activeBucket, appliedSearch).catch((error) => {
      setStatusMessage(error instanceof Error ? error.message : String(error));
    });
  }, [activeBucket, actor, appliedSearch, appliedSortDirection, authReady]);

  const applySearch = () => {
    setAppliedSearch(draftSearch);
  };

  const applySort = () => {
    setAppliedSortDirection(draftSortDirection);
  };

  const handleDownload = async (key: string) => {
    if (!activeBucket) {
      return;
    }

    await postJson<
      { actor: string; action: 'downloadObject'; bucket: string; key: string },
      { url?: string }
    >('/api/e2e/object-browser-ops', {
      actor,
      action: 'downloadObject',
      bucket: activeBucket,
      key
    });

    setStatusMessage(`Download URL ready for ${key}`);
  };

  const handleDelete = async (key: string) => {
    if (!activeBucket) {
      return;
    }

    await postJson<
      { actor: string; action: 'deleteObject'; bucket: string; key: string },
      { deleted?: boolean }
    >('/api/e2e/object-browser-ops', {
      actor,
      action: 'deleteObject',
      bucket: activeBucket,
      key
    });

    setStatusMessage(`Deleted ${key}`);
    await refreshObjects(activeBucket, appliedSearch);
  };

  const handleUploadSelection = (event: ChangeEvent<HTMLInputElement>) => {
    setPendingUploadFile(event.target.files?.[0] ?? null);
  };

  const handleUpload = async () => {
    if (!pendingUploadFile || !activeBucket) {
      return;
    }

    const content = await pendingUploadFile.text();
    await postJson<
      { actor: string; bucket: string; key: string; content: string; contentType: string },
      { verified?: boolean; key?: string }
    >('/api/e2e/live-upload', {
      actor,
      bucket: activeBucket,
      key: pendingUploadFile.name,
      content,
      contentType: pendingUploadFile.type || 'text/plain'
    });

    setStatusMessage(`Upload successful: ${pendingUploadFile.name}`);
    setPendingUploadFile(null);
    await refreshObjects(activeBucket, appliedSearch);
  };

  return (
    <section className="space-y-6">
      <header className="space-y-2">
        <h1 className="text-2xl font-bold">Object Browser Baseline Scenario</h1>
        <p className="text-sm text-gray-600">
          Baseline aligned with object-browser core flows: bucket browse, object list, search, sort, upload,
          download, delete, and trace headers.
        </p>
      </header>

      <div className="space-y-2">
        <label htmlFor="actor-select" className="block text-sm font-medium">
          Active user
        </label>
        <select
          id="actor-select"
          aria-label="Active user"
          value={actor}
          onChange={(event) => setActor(event.target.value)}
          className="border rounded px-3 py-2"
        >
          <option value="alice">alice</option>
          <option value="bob">bob</option>
        </select>
      </div>

      <div>
        <h2 className="text-lg font-semibold mb-2">Buckets</h2>
        {!authReady ? (
          <p className="text-sm text-gray-500">Preparing actor session...</p>
        ) : authError ? (
          <p className="text-sm text-red-600">Actor session failed: {authError}</p>
        ) : loadingBuckets ? (
          <p className="text-sm text-gray-500">Loading buckets...</p>
        ) : (
          <div className="flex gap-2 flex-wrap">
            {buckets.map((bucket) => (
              <button
                key={bucket.name}
                type="button"
                className={`px-3 py-2 border rounded ${selectedBucket === bucket.name ? 'bg-black text-white' : ''}`}
                onClick={() => setSelectedBucket(bucket.name)}
              >
                {bucket.name}
              </button>
            ))}
            {activeBucket && !buckets.some((bucket) => bucket.name === activeBucket) ? (
              <button
                key={activeBucket}
                type="button"
                className="px-3 py-2 border rounded bg-black text-white"
                onClick={() => setSelectedBucket(activeBucket)}
              >
                {activeBucket}
              </button>
            ) : null}
          </div>
        )}
      </div>

      <div className="space-y-3 border rounded p-4">
        <div className="grid gap-3 md:grid-cols-2">
          <div>
            <label htmlFor="search-objects" className="block text-sm font-medium mb-1">
              Search objects
            </label>
            <input
              id="search-objects"
              aria-label="Search objects"
              value={draftSearch}
              onChange={(event) => setDraftSearch(event.target.value)}
              className="border rounded px-3 py-2 w-full"
              placeholder="type object key"
            />
          </div>
          <div>
            <label htmlFor="sort-order" className="block text-sm font-medium mb-1">
              Sort order
            </label>
            <select
              id="sort-order"
              aria-label="Sort order"
              value={draftSortDirection}
              onChange={(event) => setDraftSortDirection(event.target.value as 'asc' | 'desc')}
              className="border rounded px-3 py-2 w-full"
            >
              <option value="asc">asc</option>
              <option value="desc">desc</option>
            </select>
          </div>
        </div>

        <div className="flex gap-2">
          <button type="button" onClick={applySearch} className="border rounded px-3 py-2">
            Apply search
          </button>
          <button type="button" onClick={applySort} className="border rounded px-3 py-2">
            Apply sort
          </button>
        </div>
      </div>

      {activeBucket && (
        <section>
          <h2>Upload File</h2>
          <p>Bucket: {activeBucket}</p>
          <div>Drag and drop files here</div>

          <label htmlFor="upload-file-input">Choose file</label>
          <input id="upload-file-input" aria-label="Choose file" type="file" onChange={handleUploadSelection} />

          <button type="button" onClick={handleUpload}>Upload</button>
        </section>
      )}

      <div>
        <h2 className="text-lg font-semibold mb-2">Objects</h2>
        {loadingObjects ? (
          <p className="text-sm text-gray-500">Loading objects...</p>
        ) : sortedObjects.length === 0 ? (
          <p className="text-sm text-gray-500">No objects found.</p>
        ) : (
          <ul className="space-y-2">
            {sortedObjects.map((objectItem) => (
              <li key={objectItem.key} className="border rounded px-3 py-2 flex items-center justify-between gap-2">
                <span>{objectItem.key}</span>
                <div className="flex gap-2">
                  <button
                    type="button"
                    onClick={() => handleDownload(objectItem.key)}
                    className="border rounded px-2 py-1"
                  >
                    Download {objectItem.key}
                  </button>
                  <button
                    type="button"
                    onClick={() => handleDelete(objectItem.key)}
                    className="border rounded px-2 py-1"
                  >
                    Delete {objectItem.key}
                  </button>
                </div>
              </li>
            ))}
          </ul>
        )}
      </div>

      {statusMessage && <p className="text-sm font-medium">{statusMessage}</p>}
    </section>
  );
}

export default function E2eObjectBrowserPage() {
  return (
    <Suspense fallback={<section className="space-y-6"><p className="text-sm text-gray-500">Loading object browser...</p></section>}>
      <E2eObjectBrowserContent />
    </Suspense>
  );
}
