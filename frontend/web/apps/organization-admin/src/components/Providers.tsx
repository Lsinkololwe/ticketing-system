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
 * - Persists choice in localStorage (organizer-theme)
 * - System preference detection enabled
 * - Emerald/Teal accent color for organization portal
 *
 * Better Auth handles session management via Redis, with tokens
 * available for both Server and Client Components.
 */

import { ReactNode, useMemo, useCallback } from 'react';
import { Theme } from '@radix-ui/themes';
import { ThemeProvider as NextThemeProvider } from 'next-themes';
import { ApolloProvider } from '@apollo/client/react';
import { createGraphQLClient } from '@pml.tickets/shared';
import { getAccessToken } from '@/lib/auth/client';
import { ToastContextProvider } from '@/components/ui';

interface ProvidersProps {
  children: ReactNode;
}

/**
 * Apollo Provider with Better Auth Integration
 *
 * Gets OAuth access token from Better Auth for GraphQL requests.
 * Token is used in Authorization header for backend authentication.
 *
 * IMPORTANT: We don't block rendering during session check.
 * Server-side auth (DAL) handles protection - client is for UX only.
 */
function ApolloProviderWithAuth({ children }: { children: ReactNode }) {
  // Token getter for Apollo Client (called on each request)
  // Uses Better Auth's getAccessToken to get the Keycloak JWT
  const tokenGetter = useCallback(async (): Promise<string | null> => {
    try {
      const token = await getAccessToken();

      if (process.env.NODE_ENV === 'development' && token) {
        console.log('[Apollo] Got access token:', token.substring(0, 20) + '...');
      }

      return token;
    } catch (error) {
      console.error('[Apollo] Failed to get token:', error);
      return null;
    }
  }, []);

  // Create Apollo client with token getter (stable reference)
  const apolloClient = useMemo(
    () => createGraphQLClient({ tokenGetter }),
    [tokenGetter]
  );

  // Don't block rendering - server layout handles auth protection
  // Apollo queries will wait for session naturally via tokenGetter
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
 * Emerald/Teal accent for organization portal branding.
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
 * 3. ToastContextProvider - Toast notifications (Radix UI Toast)
 * 4. ApolloProviderWithAuth - GraphQL with auth (Apollo Client handles caching)
 *
 * ## Session Expiration Handling
 *
 * Session expiration is handled at multiple layers (no client-side monitor needed):
 * - **Server-side**: `verifySession()` in layouts redirects to /logout
 * - **Apollo Client**: ErrorLink redirects to /logout on 401/UNAUTHENTICATED
 * - **Tab focus**: Better Auth's `refetchOnWindowFocus` re-validates session
 *
 * Note: Organization data is fetched via Apollo Client hooks directly.
 * No separate context is needed since Apollo Client provides caching.
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
      storageKey="pml-organizer-theme"
      themes={['light', 'dark', 'system']}
      disableTransitionOnChange={false}
    >
      <RadixThemeWrapper>
        <ToastContextProvider>
          <ApolloProviderWithAuth>
            {children}
          </ApolloProviderWithAuth>
        </ToastContextProvider>
      </RadixThemeWrapper>
    </NextThemeProvider>
  );
}
