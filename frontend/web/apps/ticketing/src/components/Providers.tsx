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
import { getRadixThemeProps } from '@pml.tickets/shared/lib/theme';
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

    return createGraphQLClient({ tokenGetter });
  }, [getToken, authenticated]);

  return <ApolloProvider client={apolloClient}>{children}</ApolloProvider>;
}

export default function Providers({ children }: ProvidersProps) {
  const themeProps = getRadixThemeProps();

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
          <ThemeProvider attribute="class" defaultTheme="light" enableSystem>
            <Theme {...themeProps}>
              {children}
            </Theme>
          </ThemeProvider>
        </ApolloProviderWithAuth>
      </QueryClientProvider>
    </KeycloakProvider>
  );
}
