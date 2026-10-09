'use client';

/**
 * Application providers: theme (light/dark/system via next-themes), snackbar, and Apollo.
 * Apollo talks to the same-origin BFF (`/api/graphql`); the browser never holds a token.
 */

import { useMemo, type ReactNode } from 'react';
import { ThemeProvider } from 'next-themes';
import { ApolloProvider } from '@apollo/client/react';
import { createBffGraphQLClient } from '@pml.tickets/shared/api/graphql/client';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';

function BffApolloProvider({ children }: { children: ReactNode }) {
  const client = useMemo(
    () =>
      createBffGraphQLClient({
        // The BFF answered 401 SESSION_ENDED: run the full logout (server session + Keycloak SSO).
        onAuthError: () => {
          window.location.href = '/logout';
        },
      }),
    []
  );
  return <ApolloProvider client={client}>{children}</ApolloProvider>;
}

export default function Providers({ children, nonce }: { children: ReactNode; nonce?: string }) {
  return (
    <ThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem
      storageKey="pml-organizer-theme"
      nonce={nonce}
      themes={['light', 'dark']}
    >
      <SnackbarProvider>
        <BffApolloProvider>{children}</BffApolloProvider>
      </SnackbarProvider>
    </ThemeProvider>
  );
}
