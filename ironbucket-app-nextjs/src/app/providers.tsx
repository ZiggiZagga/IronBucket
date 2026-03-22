'use client';

import React from 'react';
import { ApolloProvider } from '@apollo/client';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from 'next-themes';
import { NuqsAdapter } from 'nuqs/adapters/next/app';
import { AppSessionProvider } from '@/components/auth/session-provider';
import { AppToastProvider } from '@/components/ui/toast';
import { apolloClient } from '../lib/apollo';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 30_000,
      refetchOnWindowFocus: false
    }
  }
});

export function Providers({ children }: { children: React.ReactNode }) {
  return (
    <NuqsAdapter>
      <ThemeProvider attribute="class" defaultTheme="dark" enableSystem>
        <QueryClientProvider client={queryClient}>
          <ApolloProvider client={apolloClient}>
            <AppSessionProvider>
              <AppToastProvider>{children}</AppToastProvider>
            </AppSessionProvider>
          </ApolloProvider>
        </QueryClientProvider>
      </ThemeProvider>
    </NuqsAdapter>
  );
}
