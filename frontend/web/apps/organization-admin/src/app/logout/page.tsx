'use client';

/**
 * Logout Page
 *
 * Handles complete logout from:
 * - Better Auth (local session in MongoDB/Redis)
 * - Keycloak SSO session
 *
 * This page is used for:
 * - User-initiated logout (clicking "Sign Out")
 * - Session expiration (server-side redirect when session expires)
 * - Keycloak post_logout_redirect_uri callback
 *
 * CRITICAL: Always logs out from BOTH Better Auth AND Keycloak to prevent
 * the scenario where user is logged out of the app but still has Keycloak SSO.
 */

import { useEffect, useState } from 'react';
import { Box, Text } from '@radix-ui/themes';
import { signOutComplete } from '@/lib/auth/client';

/**
 * Clear all session cookies directly
 * Defense-in-depth to ensure cookie cache is cleared
 */
function clearAllSessionCookies() {
  const cookiePrefix = 'pml_org';
  const cookiesToClear = [
    `${cookiePrefix}.session_token`,
    `${cookiePrefix}.session_data`, // Cookie cache - CRITICAL
  ];

  cookiesToClear.forEach((name) => {
    // Clear with various combinations to ensure removal
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/`;
    document.cookie = `${name}=; expires=Thu, 01 Jan 1970 00:00:00 GMT; path=/; domain=${window.location.hostname}`;
    // Also try without domain
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

        // 1. Clear cookies FIRST to prevent any race conditions
        clearAllSessionCookies();

        setStatus('Logging out from Keycloak...');

        // 2. Call signOutComplete which:
        //    - Calls Better Auth signOut (may fail if session already expired - that's OK)
        //    - Redirects to Keycloak end_session_endpoint
        //    - Keycloak redirects back to /login
        await signOutComplete();

        // Note: signOutComplete does a window.location redirect to Keycloak,
        // so the code below only runs if there's an error
      } catch (error) {
        console.error('[Logout] Error during logout:', error);
        // Still clear cookies and redirect to login even on error
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
