'use client';

import { Theme } from '@radix-ui/themes';
import { ThemeProvider } from 'next-themes';
import { ApolloProvider } from '@apollo/client/react';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import {
  KeycloakProvider,
  useKeycloak,
  createGraphQLClient,
  type TokenGetter,
} from '@pml.tickets/shared';
import { useMemo, type ReactNode } from 'react';

const keycloakConfig = {
  url: process.env.NEXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8084',
  realm: process.env.NEXT_PUBLIC_KEYCLOAK_REALM || 'myticketzm',
  clientId: process.env.NEXT_PUBLIC_KEYCLOAK_CLIENT_ID || 'myticketzm-web',
};

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      gcTime: 5 * 60_000,
      retry: (failureCount, error) => {
        if (error instanceof Error && 'status' in error) {
          const status = (error as { status: number }).status;
          if (status >= 400 && status < 500) return false;
        }
        return failureCount < 3;
      },
      refetchOnWindowFocus: false,
    },
    mutations: {
      retry: 1,
    },
  },
});

interface ProvidersProps {
  children: React.ReactNode;
}

/**
 * Inner provider with access to Keycloak context
 * Creates Apollo client with Keycloak token getter
 */
function ApolloProviderWithAuth({ children }: { children: ReactNode }) {
  const { getToken, authenticated } = useKeycloak();

  const apolloClient = useMemo(() => {
    const tokenGetter: TokenGetter = async () => {
      if (!authenticated) return null;
      return getToken();
    };

    // On auth failure, send the user to THIS app's login route (`/auth`),
    // not the shared-lib default of `/login` (which 404s here).
    const onAuthError = () => {
      if (typeof window !== 'undefined') {
        window.location.href = '/auth';
      }
    };

    return createGraphQLClient({ tokenGetter, onAuthError });
  }, [getToken, authenticated]);

  return <ApolloProvider client={apolloClient}>{children}</ApolloProvider>;
}

export default function Providers({ children }: ProvidersProps) {
  return (
    <KeycloakProvider
      config={keycloakConfig}
      initOptions={{
        onLoad: 'check-sso',
        pkceMethod: 'S256',
        checkLoginIframe: false,
      }}
      onError={(error) => console.error('Keycloak error:', error)}
    >
      <QueryClientProvider client={queryClient}>
        <ApolloProviderWithAuth>
          {/* defaultTheme="system" matches the admin and organization-admin
              apps, and follows the customer's OS preference — an accessibility
              default we should not override. This is safe here because every
              surface now routes through design tokens that carry both light
              and dark values; the app no longer pins any literal white. */}
          <ThemeProvider attribute="class" defaultTheme="system" enableSystem>
            {/* MyTicketZM Design System — "ticketing" brand context:
                accent iris, gray slate, medium radius, solid panels.
                The pinned brand hex lives in design-tokens.css, never here. */}
            <Theme
              accentColor="iris"
              grayColor="slate"
              panelBackground="solid"
              radius="medium"
              scaling="100%"
            >
              {children}
            </Theme>
          </ThemeProvider>
        </ApolloProviderWithAuth>
      </QueryClientProvider>
    </KeycloakProvider>
  );
}
