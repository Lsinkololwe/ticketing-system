'use client';

/**
 * Keycloak Provider for React Applications
 *
 * Provides authentication context using keycloak-js adapter.
 * Handles initialization, token refresh, and logout.
 *
 * Usage:
 * ```tsx
 * <KeycloakProvider config={{ url, realm, clientId }}>
 *   <App />
 * </KeycloakProvider>
 * ```
 */

import Keycloak from 'keycloak-js';
import {
  createContext,
  useContext,
  useEffect,
  useState,
  useCallback,
  useRef,
  type ReactNode,
} from 'react';
import type { KeycloakConfig, KeycloakRole } from './keycloak-config';
import { TOKEN_CONFIG } from './keycloak-config';

/**
 * User information extracted from Keycloak token
 */
export interface KeycloakUser {
  id: string;
  email?: string;
  emailVerified?: boolean;
  name?: string;
  givenName?: string;
  familyName?: string;
  preferredUsername?: string;
  phoneNumber?: string;
  phoneVerified?: boolean;
  roles: string[];
  /** Custom claim: backend user ID */
  userId?: string;
  /** Custom claim: user type (CUSTOMER, ORGANIZER, etc.) */
  userType?: string;
}

/**
 * Keycloak context value
 */
export interface KeycloakContextValue {
  /** The Keycloak instance */
  keycloak: Keycloak | null;
  /** Whether Keycloak has been initialized */
  initialized: boolean;
  /** Whether initialization is in progress */
  loading: boolean;
  /** Whether the user is authenticated */
  authenticated: boolean;
  /** Current access token (for API calls) */
  token: string | undefined;
  /** Current user information */
  user: KeycloakUser | null;
  /** Error during initialization */
  error: Error | null;
  /** Initiate login flow */
  login: (options?: Keycloak.KeycloakLoginOptions) => Promise<void>;
  /** Initiate logout flow */
  logout: (options?: Keycloak.KeycloakLogoutOptions) => Promise<void>;
  /** Refresh the token if needed */
  updateToken: (minValidity?: number) => Promise<boolean>;
  /** Check if user has a specific role */
  hasRole: (role: KeycloakRole | string) => boolean;
  /** Check if user has any of the specified roles */
  hasAnyRole: (roles: (KeycloakRole | string)[]) => boolean;
  /** Get fresh token (refreshes if needed) */
  getToken: () => Promise<string | null>;
}

const KeycloakContext = createContext<KeycloakContextValue | null>(null);

/**
 * Keycloak initialization options
 */
export interface KeycloakInitOptions {
  /** How to check for existing session on load */
  onLoad?: 'check-sso' | 'login-required';
  /** URL for silent SSO check (required for check-sso) */
  silentCheckSsoRedirectUri?: string;
  /** Enable PKCE (recommended, default: true) */
  pkceMethod?: 'S256' | false;
  /** Check login iframe (default: false for better CSP compatibility) */
  checkLoginIframe?: boolean;
  /** Fallback URI after silent SSO fails */
  silentCheckSsoFallback?: boolean;
  /** Redirect URI after login */
  redirectUri?: string;
}

export interface KeycloakProviderProps {
  children: ReactNode;
  /** Keycloak server configuration */
  config: KeycloakConfig;
  /** Initialization options */
  initOptions?: KeycloakInitOptions;
  /** Callback when authentication state changes */
  onAuthStateChange?: (authenticated: boolean, user: KeycloakUser | null) => void;
  /** Callback when token is refreshed */
  onTokenRefresh?: (token: string) => void;
  /** Callback on error */
  onError?: (error: Error) => void;
}

/**
 * Extract user information from Keycloak token
 */
function extractUser(keycloak: Keycloak): KeycloakUser | null {
  if (!keycloak.tokenParsed) {
    return null;
  }

  const token = keycloak.tokenParsed as Record<string, unknown>;

  // Extract roles from realm_access
  const realmRoles = (token.realm_access as { roles?: string[] })?.roles || [];
  // Also check 'authorities' custom claim (from realm config)
  const authorities = (token.authorities as string[]) || [];
  const roles = [...new Set([...realmRoles, ...authorities])];

  return {
    id: token.sub as string,
    email: token.email as string | undefined,
    emailVerified: token.email_verified as boolean | undefined,
    name: token.name as string | undefined,
    givenName: token.given_name as string | undefined,
    familyName: token.family_name as string | undefined,
    preferredUsername: token.preferred_username as string | undefined,
    phoneNumber: token.phone_number as string | undefined,
    phoneVerified: token.phone_number_verified as boolean | undefined,
    roles,
    userId: token.userId as string | undefined,
    userType: token.userType as string | undefined,
  };
}

export function KeycloakProvider({
  children,
  config,
  initOptions,
  onAuthStateChange,
  onTokenRefresh,
  onError,
}: KeycloakProviderProps) {
  const [keycloak] = useState(() => new Keycloak(config));
  const [initialized, setInitialized] = useState(false);
  const [loading, setLoading] = useState(true);
  const [authenticated, setAuthenticated] = useState(false);
  const [user, setUser] = useState<KeycloakUser | null>(null);
  const [error, setError] = useState<Error | null>(null);

  // Refs for callbacks to avoid effect dependencies
  const onAuthStateChangeRef = useRef(onAuthStateChange);
  const onTokenRefreshRef = useRef(onTokenRefresh);
  const onErrorRef = useRef(onError);

  useEffect(() => {
    onAuthStateChangeRef.current = onAuthStateChange;
    onTokenRefreshRef.current = onTokenRefresh;
    onErrorRef.current = onError;
  }, [onAuthStateChange, onTokenRefresh, onError]);

  // Token refresh interval
  const refreshIntervalRef = useRef<NodeJS.Timeout | null>(null);

  useEffect(() => {
    const init = async () => {
      try {
        // Default initialization options
        const defaultOptions: KeycloakInitOptions = {
          onLoad: 'check-sso',
          pkceMethod: 'S256',
          checkLoginIframe: false,
        };

        // Only set silentCheckSsoRedirectUri if explicitly provided
        // Don't auto-generate it as Keycloak's CSP may block the iframe
        const mergedOptions = {
          ...defaultOptions,
          ...initOptions,
        };

        // Remove silentCheckSsoRedirectUri if not explicitly set to avoid CSP issues
        if (!initOptions?.silentCheckSsoRedirectUri) {
          delete mergedOptions.silentCheckSsoRedirectUri;
        }

        // Initialize Keycloak
        const auth = await keycloak.init(mergedOptions);

        setAuthenticated(auth);

        if (auth) {
          const extractedUser = extractUser(keycloak);
          setUser(extractedUser);
          onAuthStateChangeRef.current?.(true, extractedUser);
        } else {
          onAuthStateChangeRef.current?.(false, null);
        }

        // Set up token refresh handler
        keycloak.onTokenExpired = () => {
          keycloak
            .updateToken(TOKEN_CONFIG.minValiditySeconds)
            .then((refreshed) => {
              if (refreshed && keycloak.token) {
                onTokenRefreshRef.current?.(keycloak.token);
              }
            })
            .catch((err) => {
              console.error('Token refresh failed:', err);
              // Token refresh failed - logout user
              keycloak.logout();
            });
        };

        // Set up auth success handler
        keycloak.onAuthSuccess = () => {
          const extractedUser = extractUser(keycloak);
          setAuthenticated(true);
          setUser(extractedUser);
          onAuthStateChangeRef.current?.(true, extractedUser);
        };

        // Set up auth logout handler
        keycloak.onAuthLogout = () => {
          setAuthenticated(false);
          setUser(null);
          onAuthStateChangeRef.current?.(false, null);
        };

        // Set up periodic token refresh check
        refreshIntervalRef.current = setInterval(
          async () => {
            if (keycloak.authenticated) {
              try {
                const refreshed = await keycloak.updateToken(
                  TOKEN_CONFIG.refreshBufferSeconds
                );
                if (refreshed && keycloak.token) {
                  onTokenRefreshRef.current?.(keycloak.token);
                }
              } catch (err) {
                console.warn('Periodic token refresh failed:', err);
              }
            }
          },
          TOKEN_CONFIG.checkIntervalMs
        );

        setInitialized(true);
      } catch (err) {
        const error =
          err instanceof Error ? err : new Error('Keycloak initialization failed');
        console.error('Keycloak initialization error:', error);
        setError(error);
        onErrorRef.current?.(error);
        setInitialized(true);
      } finally {
        setLoading(false);
      }
    };

    init();

    return () => {
      if (refreshIntervalRef.current) {
        clearInterval(refreshIntervalRef.current);
      }
    };
  }, [keycloak, initOptions]);

  const login = useCallback(
    async (options?: Keycloak.KeycloakLoginOptions) => {
      try {
        await keycloak.login({
          redirectUri: typeof window !== 'undefined' ? window.location.href : undefined,
          ...options,
        });
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Login failed');
        setError(error);
        onErrorRef.current?.(error);
        throw error;
      }
    },
    [keycloak]
  );

  const logout = useCallback(
    async (options?: Keycloak.KeycloakLogoutOptions) => {
      try {
        await keycloak.logout({
          redirectUri: typeof window !== 'undefined' ? window.location.origin : undefined,
          ...options,
        });
      } catch (err) {
        const error = err instanceof Error ? err : new Error('Logout failed');
        setError(error);
        onErrorRef.current?.(error);
        throw error;
      }
    },
    [keycloak]
  );

  const updateToken = useCallback(
    async (minValidity = TOKEN_CONFIG.minValiditySeconds) => {
      try {
        return await keycloak.updateToken(minValidity);
      } catch (err) {
        console.error('Token update failed:', err);
        return false;
      }
    },
    [keycloak]
  );

  const hasRole = useCallback(
    (role: KeycloakRole | string): boolean => {
      return user?.roles.includes(role) ?? false;
    },
    [user]
  );

  const hasAnyRole = useCallback(
    (roles: (KeycloakRole | string)[]): boolean => {
      return roles.some((role) => user?.roles.includes(role));
    },
    [user]
  );

  const getToken = useCallback(async (): Promise<string | null> => {
    if (!keycloak.authenticated) {
      return null;
    }

    try {
      // Refresh if needed (5 second buffer)
      await keycloak.updateToken(5);
      return keycloak.token || null;
    } catch (err) {
      console.error('Failed to get token:', err);
      return null;
    }
  }, [keycloak]);

  return (
    <KeycloakContext.Provider
      value={{
        keycloak,
        initialized,
        loading,
        authenticated,
        token: keycloak.token,
        user,
        error,
        login,
        logout,
        updateToken,
        hasRole,
        hasAnyRole,
        getToken,
      }}
    >
      {children}
    </KeycloakContext.Provider>
  );
}

/**
 * Hook to access Keycloak authentication context
 *
 * @throws Error if used outside of KeycloakProvider
 */
export function useKeycloak(): KeycloakContextValue {
  const context = useContext(KeycloakContext);
  if (!context) {
    throw new Error('useKeycloak must be used within a KeycloakProvider');
  }
  return context;
}

/**
 * Hook to access authentication state only (safe to use in components that may be outside provider)
 */
export function useAuth(): Pick<
  KeycloakContextValue,
  'authenticated' | 'user' | 'loading' | 'login' | 'logout' | 'hasRole' | 'hasAnyRole'
> {
  const context = useContext(KeycloakContext);

  // Return default values if outside provider
  if (!context) {
    return {
      authenticated: false,
      user: null,
      loading: false,
      login: async () => {
        console.warn('useAuth: No KeycloakProvider found');
      },
      logout: async () => {
        console.warn('useAuth: No KeycloakProvider found');
      },
      hasRole: () => false,
      hasAnyRole: () => false,
    };
  }

  return {
    authenticated: context.authenticated,
    user: context.user,
    loading: context.loading,
    login: context.login,
    logout: context.logout,
    hasRole: context.hasRole,
    hasAnyRole: context.hasAnyRole,
  };
}
