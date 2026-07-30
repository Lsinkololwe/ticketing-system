'use client';

/**
 * Organization Portal Login Page
 *
 * This page handles OAuth callback and auto-redirects to Keycloak.
 * Users don't typically see this page - they go directly to Keycloak.
 *
 * Scenarios:
 * - No session: Auto-redirect to Keycloak login
 * - Auth error: Show error message with retry button
 * - Just registered: Show success message, then redirect to Keycloak login
 * - Already authenticated: Redirect to dashboard
 *
 * Styling: MyTicketZM design system, Organization Admin context — Radix "teal"
 * accent + "slate" gray, Inter, medium radii, soft two-step shadows, hairline
 * alpha-gray borders. This is only a thin interstitial that redirects to the
 * real Keycloak form, so it is deliberately minimal — a single clean card, no
 * marketing infographic. No hardcoded colours, no invented logo mark (plain
 * wordmark), light + dark aware. Only look-and-feel changed — behaviour is unchanged.
 */

import { useEffect, useCallback, Suspense, useState, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Box, Flex, Text, Button, Heading, Callout } from '@radix-ui/themes';
import { Lock, UserPlus } from 'iconoir-react';
import { useSession, signInWithKeycloak, registerWithKeycloak } from '@/lib/auth/client';

// =============================================================================
// SHARED PRIMITIVES
// =============================================================================

/**
 * Plain wordmark — no logo file exists; the design system renders the brand as
 * text and never invents a mark. This matches `.auth-logo` in the Keycloak
 * organizer theme byte for byte (micro uppercase eyebrow in accent-11, then a
 * 24px/800 wordmark with "Zambia" in accent), so the redirect from this page to
 * the real form is visually seamless.
 */
function Wordmark() {
  return (
    <Flex direction="column" align="start" gap="1">
      <Text as="p" className="ds-label" style={{ color: 'var(--accent-11)' }}>
        Organizer portal
      </Text>
      <Heading size="6" style={{ color: 'var(--gray-12)', letterSpacing: '-0.02em' }}>
        MyTicket <span style={{ color: 'var(--accent-11)' }}>Zambia</span>
      </Heading>
    </Flex>
  );
}

// =============================================================================
// LOADING SCREEN
// =============================================================================

function LoadingScreen({ message }: { message: string }) {
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
      <Flex direction="column" align="center" gap="4">
        <Box
          style={{
            width: 40,
            height: 40,
            borderRadius: '50%',
            border: '3px solid var(--accent-a5)',
            borderTopColor: 'var(--accent-9)',
            animation: 'spin 1s linear infinite',
          }}
        />
        <Text size="2" style={{ color: 'var(--gray-10)' }}>{message}</Text>
      </Flex>
      <style jsx global>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// AUTH CARD
// =============================================================================

function AuthMessageContent() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { data: session, isPending } = useSession();
  const [isRedirecting, setIsRedirecting] = useState(false);
  const hasAutoRedirected = useRef(false);

  const callbackUrl = searchParams.get('callbackUrl') || '/dashboard';
  const authError = searchParams.get('error');
  const justRegistered = searchParams.get('registered') === 'true';

  // Handle auto-redirect to Keycloak
  const handleAutoRedirect = useCallback(async () => {
    if (hasAutoRedirected.current) return;
    hasAutoRedirected.current = true;

    try {
      setIsRedirecting(true);
      await signInWithKeycloak(callbackUrl);
    } catch (err) {
      console.error('Auto-redirect to Keycloak failed:', err);
      hasAutoRedirected.current = false;
      setIsRedirecting(false);
    }
  }, [callbackUrl]);

  // Handle retry login
  const handleRetryLogin = useCallback(async () => {
    try {
      setIsRedirecting(true);
      await signInWithKeycloak(callbackUrl);
    } catch (err) {
      console.error('Sign in failed:', err);
      setIsRedirecting(false);
    }
  }, [callbackUrl]);

  // Handle register
  const handleRegister = useCallback(async () => {
    try {
      setIsRedirecting(true);
      // After registration, come back to login with success message
      await registerWithKeycloak('/login?registered=true');
    } catch (err) {
      console.error('Registration redirect failed:', err);
      setIsRedirecting(false);
    }
  }, []);

  // Redirect if already authenticated
  // Always go to /welcome first - it handles organization status routing
  useEffect(() => {
    if (session?.user) {
      // Use /welcome instead of callbackUrl - welcome page handles proper routing
      // based on organization status (server-side validated)
      router.replace('/welcome');
    }
  }, [session, router]);

  // Auto-redirect to Keycloak if no errors and no success message
  useEffect(() => {
    if (isPending) return;
    if (session?.user) return;
    if (authError) return; // Show error first
    if (justRegistered) return; // Show success message first
    if (isRedirecting) return;
    if (hasAutoRedirected.current) return;

    // No special conditions - redirect to Keycloak
    handleAutoRedirect();
  }, [isPending, session, authError, justRegistered, isRedirecting, handleAutoRedirect]);

  // Auto-redirect after showing success message
  useEffect(() => {
    if (justRegistered && !authError && !isPending && !session?.user) {
      const timer = setTimeout(() => {
        handleAutoRedirect();
      }, 2000); // Show success message for 2 seconds
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [justRegistered, authError, isPending, session, handleAutoRedirect]);

  // Loading states
  if (isPending) {
    return <LoadingScreen message="Checking your session…" />;
  }

  if (session?.user) {
    return <LoadingScreen message="Redirecting…" />;
  }

  if (isRedirecting) {
    return <LoadingScreen message="Taking you to sign in…" />;
  }

  // If no errors and not just registered, this should auto-redirect
  // But if we reach here, show a minimal UI with action buttons
  const showErrorState = !!authError;
  const showSuccessState = justRegistered && !authError;

  return (
    <Box
      style={{
        minHeight: '100vh',
        background: 'var(--color-background)',
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
            background: 'var(--color-panel-solid)',
            borderRadius: 'var(--radius-6)',
            border: '1px solid var(--gray-a5)',
            boxShadow: 'var(--shadow-4)',
          }}
        >
          <Box mb="5">
            <Wordmark />
          </Box>

          <Flex direction="column" gap="5">
            {/* Success Message - Just Registered */}
            {showSuccessState && (
              <Callout.Root color="green" size="2">
                <Callout.Text>
                  Account created. Taking you to sign in…
                </Callout.Text>
              </Callout.Root>
            )}

            {/* Error Message */}
            {showErrorState && (
              <>
                <Callout.Root color="red" size="2">
                  <Callout.Text>
                    {authError === 'OAuthAccountNotLinked'
                      ? 'That email is already linked to another account. Sign in with the original method.'
                      : authError === 'AccessDenied'
                      ? 'Access was denied. Contact support if this keeps happening.'
                      : 'Sign-in did not complete. Try again.'}
                  </Callout.Text>
                </Callout.Root>

                <Button
                  size="3"
                  color="teal"
                  onClick={handleRetryLogin}
                  data-testid="login-retry-button"
                  style={{ width: '100%', height: 48, cursor: 'pointer' }}
                >
                  <Lock style={{ width: 18, height: 18 }} />
                  <span>Try again</span>
                </Button>

                {/* Divider */}
                <Flex align="center" gap="3">
                  <Box style={{ flex: 1, height: 1, background: 'var(--gray-a5)' }} />
                  <Text size="1" style={{ color: 'var(--gray-10)' }}>or</Text>
                  <Box style={{ flex: 1, height: 1, background: 'var(--gray-a5)' }} />
                </Flex>

                <Button
                  size="3"
                  color="teal"
                  variant="outline"
                  onClick={handleRegister}
                  data-testid="login-register-button"
                  style={{ width: '100%', height: 48, cursor: 'pointer' }}
                >
                  <UserPlus style={{ width: 18, height: 18 }} />
                  <span>Create an account</span>
                </Button>
              </>
            )}

            {/* Fallback UI if auto-redirect didn't happen */}
            {!showErrorState && !showSuccessState && (
              <>
                <Box>
                  <Heading size="4" mb="2" style={{ color: 'var(--gray-12)' }}>
                    Sign in to continue
                  </Heading>
                  <Text size="2" style={{ color: 'var(--gray-11)' }}>
                    Your session is not active. Sign in to reach the organizer portal.
                  </Text>
                </Box>

                <Button
                  size="3"
                  color="teal"
                  onClick={handleRetryLogin}
                  data-testid="login-signin-button"
                  style={{ width: '100%', height: 48, cursor: 'pointer' }}
                >
                  <Lock style={{ width: 18, height: 18 }} />
                  <span>Sign in</span>
                </Button>

                <Flex align="center" gap="3">
                  <Box style={{ flex: 1, height: 1, background: 'var(--gray-a5)' }} />
                  <Text size="1" style={{ color: 'var(--gray-10)' }}>New here?</Text>
                  <Box style={{ flex: 1, height: 1, background: 'var(--gray-a5)' }} />
                </Flex>

                <Button
                  size="3"
                  color="teal"
                  variant="outline"
                  onClick={handleRegister}
                  data-testid="login-apply-button"
                  style={{ width: '100%', height: 48, cursor: 'pointer' }}
                >
                  <UserPlus style={{ width: 18, height: 18 }} />
                  <span>Apply to become an organizer</span>
                </Button>
              </>
            )}

            {/* Help Link */}
            <Text size="1" align="center" style={{ color: 'var(--gray-10)' }}>
              Need help?{' '}
              <a
                href="mailto:support@myticket.zm"
                style={{ color: 'var(--accent-11)', textDecoration: 'none', fontWeight: 500 }}
              >
                Contact support
              </a>
            </Text>
          </Flex>
        </Box>
      </Box>
    </Box>
  );
}

// =============================================================================
// EXPORT
// =============================================================================

export default function LoginPage() {
  return (
    <Suspense fallback={<LoadingScreen message="Loading…" />}>
      <AuthMessageContent />
    </Suspense>
  );
}
