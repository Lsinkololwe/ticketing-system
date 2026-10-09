'use client';

import { ApolloProvider } from '@apollo/client/react';
import { useMemo, type ReactNode } from 'react';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';
import { BuyerSessionProvider, type BuyerSession } from '@/lib/auth/session-context';
import { createBffGraphQLClient } from '@pml.tickets/shared/api';
import { ThemeProvider } from '@/lib/theme';

/**
 * Apollo talks to the same-origin `/api/graphql` route; the server attaches the token.
 * On an auth failure the buyer is sent to this app's `/auth` route.
 */
export default function Providers({ children, session }: { children: ReactNode; session: BuyerSession }) {
  const client = useMemo(() => createBffGraphQLClient({ onAuthError: () => window.location.assign('/auth') }), []);
  return (
    <BuyerSessionProvider session={session}>
      <ApolloProvider client={client}>
        <ThemeProvider>
          <SnackbarProvider>{children}</SnackbarProvider>
        </ThemeProvider>
      </ApolloProvider>
    </BuyerSessionProvider>
  );
}
