import {
  ApolloClient,
  InMemoryCache,
  HttpLink,
  from
} from '@apollo/client';
import { onError } from '@apollo/client/link/error';
import { RetryLink } from '@apollo/client/link/retry';

const GRAPHQL_ENDPOINT = process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT ?? 'http://localhost:8080/graphql';

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

const httpLink = new HttpLink({
  uri: GRAPHQL_ENDPOINT,
  credentials: 'include'
});

export const apolloClient = new ApolloClient({
  link: from([errorLink, retryLink, httpLink]),
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
