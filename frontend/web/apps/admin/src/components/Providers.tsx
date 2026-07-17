'use client';

/**
 * Application Providers
 *
 * Provides:
 * - Theme (Radix UI + next-themes with system preference support)
 * - Authentication (Better Auth with Keycloak)
 * - GraphQL (Apollo Client with automatic token injection)
 *
 * Theme Configuration:
 * - Supports: light, dark, system
 * - Persists choice in localStorage (admin-theme)
 * - System preference detection enabled
 *
 * Better Auth handles session management via Redis, with tokens
 * available for both Server and Client Components.
 */

import { ReactNode, useMemo, useCallback } from 'react';
import { Theme } from '@radix-ui/themes';
import { ThemeProvider as NextThemeProvider } from 'next-themes';
import { ApolloProvider } from '@apollo/client/react';
import { createGraphQLClient } from '@pml.tickets/shared';
import { useSession, getAccessToken } from '@/lib/auth/client';

interface ProvidersProps {
  children: ReactNode;
}

/**
 * Apollo Provider with Better Auth Integration
 *
 * Gets access token from Better Auth session for GraphQL requests.
 * Token is automatically refreshed by Better Auth.
 */
function ApolloProviderWithAuth({ children }: { children: ReactNode }) {
  // Get session state from Better Auth
  const { isPending } = useSession();

  // Token getter for Apollo Client.
  //
  // Backend resource servers (via the API Gateway) validate a Keycloak JWT, so
  // we must forward the Keycloak *access token* — NOT the opaque Better Auth
  // session token (sending that yields "Invalid JWT serialization: Missing dot
  // delimiter(s)" at the gateway). Better Auth's native getAccessToken pulls the
  // stored Keycloak token for the linked account and refreshes it when expired.
  const tokenGetter = useCallback((): Promise<string | null> => {
    return getAccessToken();
  }, []);

  // Create Apollo client with token getter
  // Client is recreated only when tokenGetter changes (which is stable)
  const apolloClient = useMemo(
    () =>
      createGraphQLClient({
        tokenGetter,
        // On session expiry, run the full logout (clears Better Auth session +
        // Keycloak SSO) via the /logout route instead of leaving a stale session.
        onAuthError: () => {
          window.location.href = '/logout';
        },
      }),
    [tokenGetter]
  );

  // Show loading state while checking authentication
  // This prevents flash of unauthenticated content
  if (isPending) {
    return (
      <div className="flex items-center justify-center min-h-screen">
        <div className="animate-spin rounded-full h-8 w-8 border-b-2 border-brand-500" />
      </div>
    );
  }

  return (
    <ApolloProvider client={apolloClient}>
      {children}
    </ApolloProvider>
  );
}

/**
 * Radix Theme Wrapper
 *
 * Syncs Radix UI Theme appearance with next-themes.
 * Uses 'inherit' to let next-themes control the appearance via CSS class.
 */
function RadixThemeWrapper({ children }: { children: ReactNode }) {
  return (
    <Theme
      accentColor="teal"
      grayColor="slate"
      radius="medium"
      scaling="100%"
      // Use 'inherit' to let next-themes control via class attribute
      // This prevents flash of wrong theme on initial load
      appearance="inherit"
    >
      {children}
    </Theme>
  );
}

/**
 * Root Providers Component
 *
 * Provider hierarchy:
 * 1. NextThemeProvider - Dark/light/system mode with persistence
 * 2. Radix Theme - UI components (inherits from next-themes)
 * 3. ApolloProviderWithAuth - GraphQL with auth
 *
 * Theme Options:
 * - 'light' - Light mode
 * - 'dark' - Dark mode
 * - 'system' - Follow system preference
 */
export default function Providers({ children }: ProvidersProps) {
  return (
    <NextThemeProvider
      attribute="class"
      defaultTheme="system"
      enableSystem={true}
      storageKey="pml-admin-theme"
      themes={['light', 'dark', 'system']}
      disableTransitionOnChange={false}
    >
      <RadixThemeWrapper>
        <ApolloProviderWithAuth>
          {children}
        </ApolloProviderWithAuth>
      </RadixThemeWrapper>
    </NextThemeProvider>
  );
}
