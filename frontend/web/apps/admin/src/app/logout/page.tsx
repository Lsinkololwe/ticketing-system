'use client';

/**
 * Logout Page (Admin)
 *
 * Handles complete logout from:
 * - Better Auth (local session in MongoDB/Redis)
 * - Keycloak SSO session
 *
 * Reached from:
 * - User-initiated logout (the Header "Sign Out" item calls signOut() directly,
 *   but this route is the canonical full-logout destination)
 * - Session expiration — the shared Apollo client's `onAuthError` handler
 *   redirects here when a request fails with UNAUTHENTICATED / 401
 *   (see libs/shared/src/api/graphql/client.ts and components/Providers.tsx)
 * - Keycloak post_logout_redirect_uri callback
 *
 * CRITICAL: Always logs out from BOTH Better Auth AND Keycloak to avoid the
 * state where the user is signed out of the app but still has a live SSO session.
 */

import { useEffect, useState } from 'react';
import { Box, Text } from '@radix-ui/themes';
import { signOut } from '@/lib/auth/client';

/**
 * Clear admin session cookies directly (defense-in-depth so the cookie cache is
 * gone even if the network logout call fails). Mirrors the admin cookiePrefix
 * configured in lib/auth (`pml_admin`).
 */
function clearAllSessionCookies() {
  const cookiePrefix = 'pml_admin';
  const cookiesToClear = [
    `${cookiePrefix}.session_token`,
    `${cookiePrefix}.session_data`, // Cookie cache - CRITICAL
  ];

  cookiesToClear.forEach((name) => {
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; domain=${window.location.hostname}`;
    document.cookie = `${name}=; max-age=0; path=/`;
  });

  console.log('[Logout] All session cookies cleared');
}

export default function LogoutPage() {
  const [status, setStatus] = useState('Signing out...');

  useEffect(() => {
    async function handleLogout() {
      try {
        setStatus('Clearing local session...');

        // 1. Clear cookies FIRST to prevent race conditions.
        clearAllSessionCookies();

        setStatus('Logging out from Keycloak...');

        // 2. signOut() blacklists the JTI in Redis, clears the Better Auth
        //    session (may fail if already expired — that's fine), then redirects
        //    to the Keycloak end_session_endpoint, which returns to /login.
        await signOut();

        // signOut() performs a window.location redirect, so code below only runs
        // if it threw before redirecting.
      } catch (error) {
        console.error('[Logout] Error during logout:', error);
        clearAllSessionCookies();
        window.location.href = '/login';
      }
    }

    handleLogout();
  }, []);

  return (
    <Box
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--color-background)',
      }}
    >
      <Text size="3" style={{ color: 'var(--gray-11)' }}>
        {status}
      </Text>
    </Box>
  );
}
