'use client';

import { gql, useLazyQuery, useMutation, useQuery } from '@apollo/client';
import { useEffect, useMemo, useState } from 'react';
import UploadDialog from '@/components/ironbucket/UploadDialog';
import { GET_BUCKETS } from '@/graphql/ironbucket-queries';

type Bucket = {
  name: string;
  creationDate: string;
  ownerTenant: string;
};

type BrowserObject = {
  key: string;
  size: number;
  lastModified: string;
};

const LIST_OBJECTS = gql`
  query ListObjects($bucket: String!, $query: String, $sortBy: String, $sortDirection: String) {
    listObjects(bucket: $bucket, query: $query, sortBy: $sortBy, sortDirection: $sortDirection) {
      key
      size
      lastModified
    }
  }
`;

const DELETE_OBJECT = gql`
  mutation DeleteObject($bucket: String!, $key: String!) {
    deleteObject(bucket: $bucket, key: $key)
  }
`;

const DOWNLOAD_OBJECT = gql`
  mutation DownloadObject($bucket: String!, $key: String!) {
    downloadObject(bucket: $bucket, key: $key) {
      url
    }
  }
`;

export default function E2eObjectBrowserPage() {
  const [actor, setActor] = useState('alice');
  const [selectedBucket, setSelectedBucket] = useState<string>('');
  const [draftSearch, setDraftSearch] = useState('');
  const [appliedSearch, setAppliedSearch] = useState('');
  const [draftSortDirection, setDraftSortDirection] = useState<'asc' | 'desc'>('asc');
  const [appliedSortDirection, setAppliedSortDirection] = useState<'asc' | 'desc'>('asc');
  const [statusMessage, setStatusMessage] = useState('');

  const { data: bucketData, loading: loadingBuckets } = useQuery(GET_BUCKETS);

  const [fetchObjects, { data: objectData, loading: loadingObjects }] = useLazyQuery(LIST_OBJECTS, {
    fetchPolicy: 'no-cache'
  });

  const [deleteObject] = useMutation(DELETE_OBJECT);
  const [downloadObject] = useMutation(DOWNLOAD_OBJECT);

  const buckets: Bucket[] = useMemo(() => bucketData?.listBuckets ?? [], [bucketData]);
  const objects: BrowserObject[] = useMemo(() => objectData?.listObjects ?? [], [objectData]);

  useEffect(() => {
    window.localStorage.setItem('ironbucket.e2e.actor', actor);
  }, [actor]);

  useEffect(() => {
    if (!selectedBucket && buckets.length > 0) {
      setSelectedBucket(buckets[0].name);
    }
  }, [buckets, selectedBucket]);

  useEffect(() => {
    if (!selectedBucket) {
      return;
    }

    fetchObjects({
      variables: {
        bucket: selectedBucket,
        query: appliedSearch,
        sortBy: 'key',
        sortDirection: appliedSortDirection
      }
    });
  }, [selectedBucket, appliedSearch, appliedSortDirection, fetchObjects]);

  const applySearch = () => {
    setAppliedSearch(draftSearch);
  };

  const applySort = () => {
    setAppliedSortDirection(draftSortDirection);
  };

  const handleDownload = async (key: string) => {
    if (!selectedBucket) {
      return;
    }

    await downloadObject({
      variables: {
        bucket: selectedBucket,
        key
      }
    });

    setStatusMessage(`Download URL ready for ${key}`);
  };

  const handleDelete = async (key: string) => {
    if (!selectedBucket) {
      return;
    }

    await deleteObject({
      variables: {
        bucket: selectedBucket,
        key
      }
    });

    setStatusMessage(`Deleted ${key}`);
    await fetchObjects({
      variables: {
        bucket: selectedBucket,
        query: appliedSearch,
        sortBy: 'key',
        sortDirection: appliedSortDirection
      }
    });
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
        {loadingBuckets ? (
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

      {selectedBucket && (
        <UploadDialog
          bucket={selectedBucket}
          onClose={() => undefined}
          autoClose={false}
          onSuccess={(result) => {
            setStatusMessage(`Upload successful: ${result.key}`);
            fetchObjects({
              variables: {
                bucket: selectedBucket,
                query: appliedSearch,
                sortBy: 'key',
                sortDirection: appliedSortDirection
              }
            });
          }}
        />
      )}

      <div>
        <h2 className="text-lg font-semibold mb-2">Objects</h2>
        {loadingObjects ? (
          <p className="text-sm text-gray-500">Loading objects...</p>
        ) : objects.length === 0 ? (
          <p className="text-sm text-gray-500">No objects found.</p>
        ) : (
          <ul className="space-y-2">
            {objects.map((objectItem) => (
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
