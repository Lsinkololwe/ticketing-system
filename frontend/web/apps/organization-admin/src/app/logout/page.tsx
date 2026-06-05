'use client';

/**
 * Logout Page
 *
 * Handles logout redirects from:
 * - Keycloak post_logout_redirect_uri
 * - Direct navigation to /logout
 * - Better Auth signOut redirect
 *
 * CRITICAL: Ensures all session cookies (including cookie cache) are cleared
 * before redirecting to login page.
 */

import { useEffect, useState } from 'react';
import { Box, Text } from '@radix-ui/themes';
import { signOut } from '@/lib/auth/client';

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
        setStatus('Clearing session...');

        // 1. Clear cookies FIRST to prevent any race conditions
        clearAllSessionCookies();

        // 2. Call Better Auth signOut to invalidate server-side session
        try {
          await signOut();
          console.log('[Logout] Server session cleared');
        } catch (error) {
          // Session might already be cleared, that's fine
          console.log('[Logout] signOut error (may be already cleared):', error);
        }

        // 3. Clear cookies AGAIN after signOut for defense-in-depth
        clearAllSessionCookies();

        setStatus('Redirecting to login...');

        // 4. Small delay to ensure cookies are cleared, then redirect
        await new Promise((resolve) => setTimeout(resolve, 100));

        // 5. Use window.location for hard redirect (clears any cached state)
        window.location.href = '/login';
      } catch (error) {
        console.error('[Logout] Error during logout:', error);
        // Still try to redirect even on error
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
