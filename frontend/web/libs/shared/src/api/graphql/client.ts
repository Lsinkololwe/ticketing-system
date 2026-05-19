// Apollo Client Configuration for Apollo Federation
// Single unified endpoint through Apollo Router
import {
  ApolloClient,
  InMemoryCache,
  ApolloLink,
  split,
  CombinedGraphQLErrors,
  NetworkStatus,
} from '@apollo/client';
import { HttpLink } from '@apollo/client/link/http';
import { ErrorLink } from '@apollo/client/link/error';
import { setContext } from '@apollo/client/link/context';
import { GraphQLWsLink } from '@apollo/client/link/subscriptions';
import { getMainDefinition } from '@apollo/client/utilities';
import { createClient } from 'graphql-ws';
import type { GraphQLError } from 'graphql';

// ============================================
// Apollo Federation Endpoint Configuration
// ============================================
// Single unified endpoint: Apollo Router (via API Gateway)
// This endpoint provides access to all services through federation
const GRAPHQL_URI = process.env.NEXT_PUBLIC_GRAPHQL_ENDPOINT || 'http://localhost:8080/graphql';
const GRAPHQL_WS_URI = process.env.NEXT_PUBLIC_GRAPHQL_WS_ENDPOINT || 'ws://localhost:8080/ws';

// Token getter function type - must be provided by the app
export type TokenGetter = () => Promise<string | null>;

// Client configuration interface
export interface GraphQLClientConfig {
  uri?: string;
  wsUri?: string;
  headers?: Record<string, string>;
  /** Required: Function to get the access token (e.g., from Keycloak) */
  tokenGetter: TokenGetter;
}

/**
 * Create error handling link - Apollo Client 4.x API
 */
const createErrorLink = () =>
  new ErrorLink(({ error, operation }) => {
    if (CombinedGraphQLErrors.is(error)) {
      for (const err of error.errors) {
        const errorCode = err.extensions?.code;

        // Redirect to logout on authentication errors
        if (errorCode === 'UNAUTHENTICATED' && typeof window !== 'undefined') {
          window.location.href = '/logout';
          return;
        }

        // Log errors in development
        if (process.env.NODE_ENV !== 'production') {
          console.error('[GraphQL error]', {
            message: err.message,
            code: errorCode,
            path: err.path,
            operation: operation?.operationName,
          });
        }
      }
    } else if (error) {
      // Network or other errors
      const statusCode = 'statusCode' in error ? (error as { statusCode?: number }).statusCode : undefined;

      // Handle 401 errors
      if (statusCode === 401 && typeof window !== 'undefined') {
        window.location.href = '/logout';
        return;
      }

      if (process.env.NODE_ENV !== 'production') {
        console.error('[Network error]', {
          message: error.message,
          operation: operation?.operationName,
        });
      }
    }
  });

/**
 * Create authentication link
 */
const createAuthLink = (tokenGetter: TokenGetter) =>
  setContext(async (_, { headers }) => {
    const token = await tokenGetter();
    return {
      headers: {
        ...headers,
        ...(token ? { Authorization: `Bearer ${token}` } : {}),
      },
    };
  });

/**
 * Create WebSocket link for subscriptions
 */
const createWsLink = (wsUri: string, tokenGetter: TokenGetter): GraphQLWsLink | null => {
  if (typeof window === 'undefined') return null;

  return new GraphQLWsLink(
    createClient({
      url: wsUri,
      connectionParams: async () => {
        const token = await tokenGetter();
        return token ? { authorization: `Bearer ${token}` } : {};
      },
      retryAttempts: 5,
      shouldRetry: () => true,
    })
  );
};

/**
 * Create Apollo Client for Apollo Federation
 * Single client connects to Apollo Router which handles all federation
 *
 * @param config - Configuration including required tokenGetter
 * @throws Error if tokenGetter is not provided
 */
export const createGraphQLClient = (config: GraphQLClientConfig) => {
  if (!config.tokenGetter) {
    throw new Error('tokenGetter is required for createGraphQLClient');
  }

  const httpUri = config.uri || GRAPHQL_URI;
  const wsUri = config.wsUri || GRAPHQL_WS_URI;
  const tokenGetter = config.tokenGetter;

  const httpLink = new HttpLink({
    uri: httpUri,
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      'apollographql-client-name': 'event-ticketing-admin',
      'apollographql-client-version': '1.0.0',
      ...(config?.headers || {}),
    },
  });

  const isClientSide = typeof window !== 'undefined';
  const authLink = isClientSide ? createAuthLink(tokenGetter) : null;
  const wsLink = isClientSide ? createWsLink(wsUri, tokenGetter) : null;

  // Build link chain
  const httpLinkChain = authLink
    ? ApolloLink.from([createErrorLink(), authLink, httpLink])
    : ApolloLink.from([createErrorLink(), httpLink]);

  // Split between WebSocket (subscriptions) and HTTP (queries/mutations)
  const link = wsLink
    ? split(
        ({ query }) => {
          const definition = getMainDefinition(query);
          return (
            definition.kind === 'OperationDefinition' &&
            definition.operation === 'subscription'
          );
        },
        wsLink,
        httpLinkChain
      )
    : httpLinkChain;

  return new ApolloClient({
    link,
    cache: new InMemoryCache({
      typePolicies: {
        Event: { keyFields: ['id'] },
        Ticket: { keyFields: ['id'] },
        User: { keyFields: ['id'] },
        Location: { keyFields: ['id'] },
        EventCategory: { keyFields: ['id'] },
      },
    }),
    defaultOptions: {
      watchQuery: { errorPolicy: 'all', fetchPolicy: 'cache-and-network' },
      query: { errorPolicy: 'all', fetchPolicy: 'cache-first' },
      mutate: { errorPolicy: 'all' },
    },
  });
};

// ============================================
// No default client - must be created with tokenGetter
// ============================================
// Use createGraphQLClient({ tokenGetter }) in your app's Providers component
// Example:
// const { getToken } = useKeycloak();
// const client = createGraphQLClient({ tokenGetter: getToken });

// ============================================
// Error Utilities
// ============================================

export type ApolloErrorType = 'CORS' | 'NETWORK' | 'AUTHENTICATION' | 'AUTHORIZATION' | 'VALIDATION' | 'NOT_FOUND' | 'SERVER' | 'GRAPHQL' | 'UNKNOWN';

export function categorizeApolloError(error: Error | unknown): ApolloErrorType {
  if (!(error instanceof Error)) return 'UNKNOWN';

  const message = error.message?.toLowerCase() || '';

  if (message.includes('cors') || message.includes('cross-origin')) return 'CORS';
  if (message.includes('network') || message.includes('failed to fetch')) return 'NETWORK';
  if (message.includes('unauthorized') || message.includes('401')) return 'AUTHENTICATION';
  if (message.includes('forbidden') || message.includes('403')) return 'AUTHORIZATION';
  if (message.includes('not found') || message.includes('404')) return 'NOT_FOUND';
  if (message.includes('server') || message.includes('500')) return 'SERVER';
  if (message.includes('validation') || message.includes('invalid')) return 'VALIDATION';

  return 'GRAPHQL';
}

export function getApolloErrorMessage(error: Error | unknown): string {
  const errorType = categorizeApolloError(error);

  const messages: Record<ApolloErrorType, string> = {
    CORS: 'Connection blocked by security policy. Please contact support.',
    NETWORK: 'Unable to connect to the server. Please check your connection.',
    AUTHENTICATION: 'Your session has expired. Please log in again.',
    AUTHORIZATION: 'You do not have permission to perform this action.',
    VALIDATION: 'Please check your input and try again.',
    NOT_FOUND: 'The requested resource was not found.',
    SERVER: 'A server error occurred. Please try again later.',
    GRAPHQL: error instanceof Error ? error.message : 'A GraphQL error occurred.',
    UNKNOWN: 'An unexpected error occurred. Please try again.',
  };

  return messages[errorType];
}

export const handleGraphQLError = (error: GraphQLError): string => {
  const errorCode = error.extensions?.code;

  switch (errorCode) {
    case 'UNAUTHENTICATED':
      return 'Please log in to continue';
    case 'FORBIDDEN':
      return 'You do not have permission to perform this action';
    case 'NOT_FOUND':
      return 'The requested resource was not found';
    case 'VALIDATION_ERROR':
    case 'BAD_USER_INPUT':
      return 'Please check your input and try again';
    default:
      return error.message || 'An unexpected error occurred.';
  }
};

// ============================================
// Apollo Error Interfaces & Utilities
// ============================================

export interface ApolloErrorInterface {
  graphQLErrors?: Array<{ message: string; extensions?: { code?: string } }>;
  networkError?: Error | null;
  message: string;
}

/**
 * Check if network is in a loading state
 */
export function isNetworkLoading(networkStatus: NetworkStatus): boolean {
  return (
    networkStatus === NetworkStatus.loading ||
    networkStatus === NetworkStatus.setVariables ||
    networkStatus === NetworkStatus.fetchMore
  );
}

/**
 * Check if network is refetching
 */
export function isRefetching(networkStatus: NetworkStatus): boolean {
  return networkStatus === NetworkStatus.refetch;
}

/**
 * Extract user-friendly error message from Apollo error
 */
export function extractErrorMessage(error: ApolloErrorInterface | undefined): string | null {
  if (!error) return null;
  if (error.graphQLErrors?.length) {
    return error.graphQLErrors[0].message;
  }
  if (error.networkError) {
    return error.networkError.message;
  }
  return error.message;
}

/**
 * Filter GraphQL errors to exclude validation errors (field undefined)
 * These are typically schema mismatches that shouldn't be shown to users
 */
export function filterGraphQLErrors(error: ApolloErrorInterface): ApolloErrorInterface | undefined {
  if (!error) return undefined;

  // Filter out field undefined errors (validation errors)
  const filteredGraphQLErrors = error.graphQLErrors?.filter((err) => {
    const message = err.message || '';
    // Exclude errors that mention "Field" and "undefined" (schema validation errors)
    const isFieldUndefinedError = message.includes('Field') && message.includes('undefined');
    return !isFieldUndefinedError;
  });

  // If all GraphQL errors were filtered out, check if there are network errors
  if (!filteredGraphQLErrors || filteredGraphQLErrors.length === 0) {
    if (error.networkError) {
      return error;
    }
    return undefined;
  }

  return {
    ...error,
    graphQLErrors: filteredGraphQLErrors,
  } as ApolloErrorInterface;
}

/**
 * Get user-friendly error message from Apollo error
 * Filters out technical validation errors
 */
export function getUserFriendlyErrorMessage(error: ApolloErrorInterface | undefined): string | null {
  if (!error) return null;

  const filteredError = filterGraphQLErrors(error);
  if (!filteredError) return null;

  return getApolloErrorMessage(filteredError);
}

/**
 * Check if a GraphQL error is authentication-related
 */
export function isGraphQLAuthError(error: ApolloErrorInterface): boolean {
  return (
    error.graphQLErrors?.some(
      (err) =>
        err.message.includes('Unauthorized') ||
        err.message.includes('Authentication') ||
        err.message.includes('Forbidden') ||
        err.message.includes('Invalid token') ||
        err.extensions?.code === 'UNAUTHENTICATED'
    ) ?? false
  );
}
