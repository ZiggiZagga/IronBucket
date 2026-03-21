import {
  ApolloClient,
  InMemoryCache,
  HttpLink,
  from
} from '@apollo/client';
import { onError } from '@apollo/client/link/error';
import { setContext } from '@apollo/client/link/context';
import { RetryLink } from '@apollo/client/link/retry';

const GRAPHQL_ENDPOINT = process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT ?? 'https://localhost:8080/graphql';

const errorLink = onError(({ graphQLErrors, networkError, operation }) => {
  if (graphQLErrors) {
    graphQLErrors.forEach(({ message, locations, path, extensions }) => {
      console.error(
        `[GraphQL error in ${operation.operationName}]: ${message}`,
        { locations, path, extensions }
      );
    });
  }
  if (networkError) {
    console.error(`[Network error]: ${networkError.message}`, networkError);
  }
});

const retryLink = new RetryLink({
  delay: {
    initial: 300,
    max: 5000,
    jitter: true
  },
  attempts: {
    max: 3,
    retryIf: (error) => {
      if ((error as any).statusCode && (error as any).statusCode >= 400 && (error as any).statusCode < 500) {
        return false;
      }
      return !!error;
    }
  }
});

function generateTraceparent(): string {
  const hex = (length: number) => {
    if (typeof crypto !== 'undefined' && typeof crypto.getRandomValues === 'function') {
      const bytes = new Uint8Array(length / 2);
      crypto.getRandomValues(bytes);
      return Array.from(bytes, (byte) => byte.toString(16).padStart(2, '0')).join('');
    }

    let out = '';
    for (let i = 0; i < length; i += 1) {
      out += Math.floor(Math.random() * 16).toString(16);
    }
    return out;
  };

  const traceId = hex(32);
  const spanId = hex(16);
  return `00-${traceId}-${spanId}-01`;
}

const tracingLink = setContext((_, previousContext) => {
  const traceparent = generateTraceparent();
  const actor =
    typeof window !== 'undefined'
      ? window.localStorage.getItem('ironbucket.e2e.actor') || 'anonymous'
      : 'anonymous';
  const e2eAccessToken =
    typeof window !== 'undefined'
      ? window.localStorage.getItem('ironbucket.e2e.accessToken') || ''
      : '';

  return {
    headers: {
      ...previousContext.headers,
      traceparent,
      'x-ironbucket-actor': actor,
      ...(e2eAccessToken ? { Authorization: `Bearer ${e2eAccessToken}` } : {})
    }
  };
});

const httpLink = new HttpLink({
  uri: GRAPHQL_ENDPOINT,
  credentials: 'include'
});

export const apolloClient = new ApolloClient({
  link: from([errorLink, retryLink, tracingLink, httpLink]),
  cache: new InMemoryCache(),
  defaultOptions: {
    watchQuery: {
      errorPolicy: 'all'
    },
    query: {
      errorPolicy: 'all'
    }
  }
});
