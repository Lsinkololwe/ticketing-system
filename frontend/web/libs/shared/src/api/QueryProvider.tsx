'use client';

import { QueryClientProvider, type QueryClient } from '@tanstack/react-query';
import { useState, type ReactNode } from 'react';
import { createQueryClient } from './rest/query-client';

/**
 * TanStack Query provider for REST/BFF calls. Creates one client per mounted tree (never a module-level
 * singleton on the server, which would leak data between requests). GraphQL uses the apps' existing
 * `ApolloProvider` + `createGraphQLClient`; do not wrap Apollo again here.
 */
export function QueryProvider({ children, client }: { children: ReactNode; client?: QueryClient }) {
  const [qc] = useState(() => client ?? createQueryClient());
  return <QueryClientProvider client={qc}>{children}</QueryClientProvider>;
}
