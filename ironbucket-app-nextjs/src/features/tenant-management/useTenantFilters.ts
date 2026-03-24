'use client';

import { parseAsString, useQueryState } from 'nuqs';

export function useTenantFilters() {
  const [query, setQuery] = useQueryState('q', parseAsString.withDefault(''));
  const [status, setStatus] = useQueryState('status', parseAsString.withDefault('ALL'));

  return {
    query,
    status,
    setQuery,
    setStatus
  };
}
