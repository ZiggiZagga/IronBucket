'use client';

import { Suspense, useEffect, useMemo, useState, type ChangeEvent } from 'react';
import { useSearchParams } from 'next/navigation';
import { Badge } from '@/components/ui/badge';
import { Button } from '@/components/ui/button';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import { Select } from '@/components/ui/select';
import { useAppToast } from '@/components/ui/toast';
import { formatDateTime } from '@/lib/utils';

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
  const { pushToast } = useAppToast();
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
    pushToast({
      title: 'Download prepared',
      description: `Signed URL generated for ${key}.`,
      variant: 'success'
    });
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
    pushToast({
      title: 'Object deleted',
      description: `${key} removed from ${activeBucket}.`,
      variant: 'info'
    });
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
    pushToast({
      title: 'Upload successful',
      description: `${pendingUploadFile.name} stored in ${activeBucket}.`,
      variant: 'success'
    });
    setPendingUploadFile(null);
    await refreshObjects(activeBucket, appliedSearch);
  };

  return (
    <section className="space-y-6">
      <Card className="overflow-hidden border-white/10 bg-[linear-gradient(140deg,rgba(12,24,43,0.95),rgba(10,84,90,0.78),rgba(8,15,28,0.95))] text-white">
        <CardContent className="grid gap-6 px-6 py-7 md:px-8 lg:grid-cols-[1.2fr_0.8fr]">
          <div className="space-y-3">
            <Badge variant="success">Live storage path</Badge>
            <header className="space-y-2">
              <h1 className="text-3xl font-semibold tracking-tight">Object Browser Baseline Scenario</h1>
              <p className="max-w-2xl text-sm text-slate-300 md:text-base">
                Baseline aligned with object-browser core flows: bucket browse, object list, search, sort, upload,
                download, delete, and trace headers.
              </p>
            </header>
          </div>
          <div className="grid gap-3 md:grid-cols-3 lg:grid-cols-1">
            <div className="rounded-[24px] border border-white/10 bg-white/8 p-4">
              <p className="text-xs uppercase tracking-[0.18em] text-slate-400">Active actor</p>
              <p className="mt-2 text-2xl font-semibold">{actor}</p>
            </div>
            <div className="rounded-[24px] border border-white/10 bg-white/8 p-4">
              <p className="text-xs uppercase tracking-[0.18em] text-slate-400">Bucket</p>
              <p className="mt-2 truncate text-lg font-semibold">{activeBucket || 'Waiting for selection'}</p>
            </div>
          </div>
        </CardContent>
      </Card>

      <div className="grid gap-4 xl:grid-cols-[0.85fr_1.15fr]">
        <Card>
          <CardHeader>
            <CardTitle>Session and bucket control</CardTitle>
            <CardDescription>
              Keep the runtime actor explicit. This page still preserves the selectors used by the existing Playwright proof.
            </CardDescription>
          </CardHeader>
          <CardContent className="space-y-5">
            <div className="space-y-2">
              <label htmlFor="actor-select" className="block text-sm font-medium text-[color:var(--foreground)]">
                Active user
              </label>
              <Select
                id="actor-select"
                aria-label="Active user"
                value={actor}
                onChange={(event) => setActor(event.target.value)}
              >
                <option value="alice">alice</option>
                <option value="bob">bob</option>
              </Select>
            </div>

            <div>
              <h2 className="mb-3 text-sm font-semibold uppercase tracking-[0.18em] text-[color:var(--muted-foreground)]">Buckets</h2>
              {!authReady ? (
                <p className="text-sm text-[color:var(--muted-foreground)]">Preparing actor session...</p>
              ) : authError ? (
                <p className="text-sm text-rose-300">Actor session failed: {authError}</p>
              ) : loadingBuckets ? (
                <p className="text-sm text-[color:var(--muted-foreground)]">Loading buckets...</p>
              ) : (
                <div className="flex gap-2 flex-wrap">
                  {buckets.map((bucket) => (
                    <button
                      key={bucket.name}
                      type="button"
                      className={`rounded-2xl border px-3 py-2 text-sm transition ${selectedBucket === bucket.name ? 'border-emerald-400/40 bg-emerald-500/15 text-emerald-100' : 'border-[color:var(--border)] bg-[color:var(--panel-strong)] text-[color:var(--foreground)]'}`}
                      onClick={() => setSelectedBucket(bucket.name)}
                    >
                      {bucket.name}
                    </button>
                  ))}
                  {activeBucket && !buckets.some((bucket) => bucket.name === activeBucket) ? (
                    <button
                      key={activeBucket}
                      type="button"
                      className="rounded-2xl border border-emerald-400/40 bg-emerald-500/15 px-3 py-2 text-sm text-emerald-100"
                      onClick={() => setSelectedBucket(activeBucket)}
                    >
                      {activeBucket}
                    </button>
                  ) : null}
                </div>
              )}
            </div>

            {activeBucket && (
              <div className="space-y-3 rounded-[28px] border border-[color:var(--border)] bg-[color:var(--panel-strong)] p-4">
                <div>
                  <h2 className="text-lg font-semibold">Upload File</h2>
                  <p className="text-sm text-[color:var(--muted-foreground)]">Bucket: {activeBucket}</p>
                </div>
                <div className="rounded-[24px] border border-dashed border-[color:var(--border)] bg-[color:var(--panel)] px-4 py-6 text-sm text-[color:var(--muted-foreground)]">
                  Drag and drop files here
                </div>
                <label htmlFor="upload-file-input" className="block text-sm font-medium text-[color:var(--foreground)]">
                  Choose file
                </label>
                <Input id="upload-file-input" aria-label="Choose file" type="file" onChange={handleUploadSelection} />
                <Button type="button" onClick={handleUpload} disabled={!pendingUploadFile}>
                  Upload
                </Button>
              </div>
            )}
          </CardContent>
        </Card>

        <div className="space-y-4">
          <Card>
            <CardHeader>
              <CardTitle>Object search and sort</CardTitle>
              <CardDescription>
                Search, sort, and refresh against the active bucket while retaining existing labels and action names.
              </CardDescription>
            </CardHeader>
            <CardContent className="space-y-4">
              <div className="grid gap-3 md:grid-cols-2">
                <div>
                  <label htmlFor="search-objects" className="mb-1 block text-sm font-medium text-[color:var(--foreground)]">
                    Search objects
                  </label>
                  <Input
                    id="search-objects"
                    aria-label="Search objects"
                    value={draftSearch}
                    onChange={(event) => setDraftSearch(event.target.value)}
                    placeholder="type object key"
                  />
                </div>
                <div>
                  <label htmlFor="sort-order" className="mb-1 block text-sm font-medium text-[color:var(--foreground)]">
                    Sort order
                  </label>
                  <Select
                    id="sort-order"
                    aria-label="Sort order"
                    value={draftSortDirection}
                    onChange={(event) => setDraftSortDirection(event.target.value as 'asc' | 'desc')}
                  >
                    <option value="asc">asc</option>
                    <option value="desc">desc</option>
                  </Select>
                </div>
              </div>

              <div className="flex gap-2">
                <Button type="button" variant="secondary" onClick={applySearch}>
                  Apply search
                </Button>
                <Button type="button" variant="secondary" onClick={applySort}>
                  Apply sort
                </Button>
              </div>
            </CardContent>
          </Card>

          <Card>
            <CardHeader>
              <CardTitle>Objects</CardTitle>
              <CardDescription>
                Actions remain named for test compatibility while the presentation moves to a denser operator layout.
              </CardDescription>
            </CardHeader>
            <CardContent>
              {loadingObjects ? (
                <p className="text-sm text-[color:var(--muted-foreground)]">Loading objects...</p>
              ) : sortedObjects.length === 0 ? (
                <p className="text-sm text-[color:var(--muted-foreground)]">No objects found.</p>
              ) : (
                <ul className="space-y-3">
                  {sortedObjects.map((objectItem) => (
                    <li key={objectItem.key} className="flex flex-col gap-3 rounded-[24px] border border-[color:var(--border)] bg-[color:var(--panel-strong)] px-4 py-4 md:flex-row md:items-center md:justify-between">
                      <div className="min-w-0">
                        <p className="truncate text-sm font-semibold text-[color:var(--foreground)]">{objectItem.key}</p>
                        <p className="text-xs text-[color:var(--muted-foreground)]">
                          {objectItem.size} bytes
                          {objectItem.lastModified ? ` • ${formatDateTime(objectItem.lastModified)}` : ''}
                        </p>
                      </div>
                      <div className="flex gap-2">
                        <Button
                          type="button"
                          variant="secondary"
                          size="sm"
                          onClick={() => handleDownload(objectItem.key)}
                        >
                          Download {objectItem.key}
                        </Button>
                        <Button
                          type="button"
                          variant="danger"
                          size="sm"
                          onClick={() => handleDelete(objectItem.key)}
                        >
                          Delete {objectItem.key}
                        </Button>
                      </div>
                    </li>
                  ))}
                </ul>
              )}
            </CardContent>
          </Card>
        </div>
      </div>

      {statusMessage ? (
        <div className="rounded-2xl border border-emerald-400/20 bg-emerald-500/10 px-4 py-3 text-sm font-medium text-emerald-200">{statusMessage}</div>
      ) : null}
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
