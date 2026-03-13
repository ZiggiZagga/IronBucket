'use client';

import React from 'react';
import { ApolloProvider } from '@apollo/client';
import { NuqsAdapter } from 'nuqs/adapters/next/app';
import { apolloClient } from '../lib/apollo';

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <NuqsAdapter>
      <ApolloProvider client={apolloClient}>{children}</ApolloProvider>
    </NuqsAdapter>
  );
}
