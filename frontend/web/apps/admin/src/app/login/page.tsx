'use client';

/**
 * Admin Login Page
 *
 * Enterprise login for the MyTicket Zambia Admin Portal.
 *
 * This is a thin interstitial: it checks the session and redirects to Keycloak
 * (the real sign-in form) via SSO. It is deliberately minimal — a single clean
 * card, no marketing panel — so it never flashes heavy content before redirect.
 *
 * Authentication via Better Auth with Keycloak OIDC. Sessions stored in Redis.
 *
 * Branding rules (MyTicketZM design system):
 * - Admin identity = deep teal (--accent-*), slate gray, Inter. Jade/emerald is
 *   reserved for money semantics only and must NOT be used as a brand color.
 * - No hardcoded hex, no invented logo mark (plain wordmark) — everything flows
 *   through the Radix teal/slate scales so light (primary) and dark propagate.
 * - Light is the primary scheme (Trust & Authority).
 */

import { useEffect, useCallback, Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Box, Flex, Text, Button, Heading, Spinner, Callout } from '@radix-ui/themes';
import { Lock } from 'iconoir-react';
import { useSession, signInWithKeycloak } from '@/lib/auth/client';

// =============================================================================
// SHARED PRIMITIVES
// =============================================================================

/** Plain wordmark — no logo mark exists; the design system uses the wordmark. */
function Wordmark() {
  return (
    <Flex direction="column" align="start" gap="1">
      {/* Micro uppercase eyebrow — matches `.auth-logo-sub` in the Keycloak
          admin theme this page redirects to. */}
      <Text className="ds-label" as="p" style={{ color: 'var(--accent-11)' }}>
        Admin Portal
      </Text>
      <Heading size="6" style={{ color: 'var(--gray-12)', letterSpacing: '-0.02em' }}>
        MyTicket <span style={{ color: 'var(--accent-11)' }}>Zambia</span>
      </Heading>
    </Flex>
  );
}

// =============================================================================
// LOADING STATES
// =============================================================================

function LoadingScreen({ message }: { message: string }) {
  return (
    <Box
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: 'var(--color-background)',
      }}
    >
      <Flex direction="column" align="center" gap="4">
        <Spinner size="3" />
        <Text size="2" style={{ color: 'var(--gray-11)' }}>{message}</Text>
      </Flex>
    </Box>
  );
}

// =============================================================================
// MAIN LOGIN CONTENT
// =============================================================================

function LoginContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { data: session, isPending } = useSession();
  const [isSigningIn, setIsSigningIn] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const callbackUrl = searchParams.get('callbackUrl') || '/dashboard';
  const authError = searchParams.get('error');

  // Redirect if already authenticated
  useEffect(() => {
    if (session?.user) {
      router.replace(callbackUrl);
    }
  }, [session, router, callbackUrl]);

  // Handle login
  const handleLogin = useCallback(async () => {
    try {
      setIsSigningIn(true);
      setError(null);
      await signInWithKeycloak(callbackUrl);
    } catch (err) {
      console.error('Sign in failed:', err);
      setError('Failed to initiate sign in. Please try again.');
      setIsSigningIn(false);
    }
  }, [callbackUrl]);

  // Loading states
  if (isPending) {
    return <LoadingScreen message="Checking authentication..." />;
  }

  if (session?.user) {
    return <LoadingScreen message="Redirecting to dashboard..." />;
  }

  return (
    <Box
      style={{
        minHeight: '100vh',
        backgroundColor: 'var(--color-background)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '40px 20px',
      }}
    >
      <Box style={{ width: '100%', maxWidth: 420 }}>
        <Box
          p="6"
          style={{
            backgroundColor: 'var(--color-panel-solid)',
            borderRadius: 'var(--radius-6)',
            border: '1px solid var(--gray-a5)',
            boxShadow: 'var(--shadow-4)',
          }}
        >
          <Box mb="5">
            <Wordmark />
          </Box>

          <Flex direction="column" gap="5">
            <Box>
              <Heading size="4" mb="2" style={{ color: 'var(--gray-12)' }}>
                Sign in to Admin
              </Heading>
              <Text size="2" style={{ color: 'var(--gray-11)' }}>
                You will be taken to the single sign-on page to continue.
              </Text>
            </Box>

            {/* Error Message */}
            {(error || authError) && (
              <Callout.Root color="red" size="1">
                <Callout.Text>
                  {error || authError === 'OAuthAccountNotLinked'
                    ? 'This email is already associated with another account.'
                    : 'Authentication failed. Please try again.'}
                </Callout.Text>
              </Callout.Root>
            )}

            {/* SSO hand-off. `color` is omitted so the button inherits the
                theme accent (teal) — hardcoding "teal" would survive a rebrand
                and silently diverge from the rest of the portal. */}
            <Button
              size="3"
              data-testid="admin-login-sso-button"
              onClick={handleLogin}
              disabled={isSigningIn}
              style={{
                width: '100%',
                height: 48,
                cursor: isSigningIn ? 'not-allowed' : 'pointer',
              }}
            >
              {isSigningIn ? (
                <Flex align="center" gap="2">
                  <Spinner size="1" />
                  <span>Redirecting&hellip;</span>
                </Flex>
              ) : (
                <Flex align="center" gap="2">
                  <Lock style={{ width: 18, height: 18 }} />
                  <span>Sign in with SSO</span>
                </Flex>
              )}
            </Button>

            {/* Help Link */}
            <Text size="1" align="center" style={{ color: 'var(--gray-11)' }}>
              Having trouble signing in?{' '}
              <a
                href="mailto:support@pml.tickets"
                style={{
                  color: 'var(--accent-11)',
                  textDecoration: 'none',
                  cursor: 'pointer',
                }}
              >
                Contact support
              </a>
            </Text>
          </Flex>
        </Box>

        {/* Footer */}
        <Text
          size="1"
          align="center"
          mt="5"
          style={{ color: 'var(--gray-10)', display: 'block' }}
          suppressHydrationWarning
        >
          &copy; {new Date().getFullYear()} MyTicket Zambia. All rights reserved.
        </Text>
      </Box>
    </Box>
  );
}

// =============================================================================
// EXPORT
// =============================================================================

export default function LoginPage() {
  return (
    <Suspense fallback={<LoadingScreen message="Loading..." />}>
      <LoginContent />
    </Suspense>
  );
}
