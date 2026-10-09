'use client';

/**
 * Application providers: Apollo over the same-origin BFF (/api/graphql; the session cookie authenticates,
 * no token reaches the browser) and the shared snackbar.
 */
import { useMemo, type ReactNode } from 'react';
import { ThemeProvider } from 'next-themes';
import { ApolloProvider } from '@apollo/client/react';
import { SnackbarProvider } from '@pml.tickets/shared';
import { createBffGraphQLClient } from '@pml.tickets/shared/api/graphql';

export default function Providers({ children, nonce }: { children: ReactNode; nonce?: string }) {
  const client = useMemo(
    () =>
      createBffGraphQLClient({
        // The session ended (idle, absolute, back-channel or revocation): sign in again.
        onAuthError: () => {
          window.location.href = '/login';
        },
      }),
    []
  );
  return (
    <ThemeProvider attribute="class" defaultTheme="system" enableSystem storageKey="pml-admin-theme" nonce={nonce} themes={['light', 'dark']}>
      <SnackbarProvider>
        <ApolloProvider client={client}>{children}</ApolloProvider>
      </SnackbarProvider>
    </ThemeProvider>
  );
}
