'use client';

import { useMemo, useState } from 'react';
import { useQuery } from '@apollo/client';
import { GET_BUCKETS } from '../../graphql/ironbucket-queries';
import { useUserClaims } from '../../hooks/useUserClaims';

type Bucket = {
  name: string;
  creationDate: string;
  ownerTenant: string;
};

export default function BucketList() {
  const { data, loading, error, refetch } = useQuery(GET_BUCKETS);
  const { roles } = useUserClaims();
  const [search, setSearch] = useState('');
  const [showCreate, setShowCreate] = useState(false);

  const canCreate = roles.includes('admin') || roles.includes('s3:write');

  const buckets: Bucket[] = useMemo(() => {
    const list = data?.listBuckets ?? [];
    if (!search) {
      return list;
    }
    const query = search.toLowerCase();
    return list.filter((bucket: Bucket) => bucket.name.toLowerCase().includes(query));
  }, [data, search]);

  if (loading) {
    return <p>Loading buckets...</p>;
  }

  if (error) {
    return <p>Error loading buckets</p>;
  }

  return (
    <section>
      <div className="flex gap-2 items-center mb-3">
        <input
          placeholder="Search buckets"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
        />
        <button aria-label="refresh" onClick={() => refetch()}>↻</button>
        <button onClick={() => setShowCreate(true)} disabled={!canCreate}>Create Bucket</button>
      </div>

      {showCreate && (
        <div>
          <label>
            Bucket name
            <input aria-label="bucket name" />
          </label>
        </div>
      )}

      {buckets.length === 0 ? (
        <p>No buckets found</p>
      ) : (
        <ul>
          {buckets.map((bucket) => (
            <li key={bucket.name}>
              <button type="button">{bucket.name}</button>
              <span>read-write</span>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
