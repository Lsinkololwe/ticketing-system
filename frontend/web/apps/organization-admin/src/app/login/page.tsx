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
 */

import { useEffect, useCallback, Suspense, useState, useRef } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Box, Flex, Text, Button, Heading, Callout } from '@radix-ui/themes';
import { Calendar, Lock, UserPlus } from 'iconoir-react';
import { useSession, signInWithKeycloak, registerWithKeycloak } from '@/lib/auth/client';

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
        background: '#0A0A0F',
      }}
    >
      <Flex direction="column" align="center" gap="4">
        <Box
          style={{
            width: 48,
            height: 48,
            borderRadius: '50%',
            border: '3px solid rgba(16, 185, 129, 0.2)',
            borderTopColor: '#10B981',
            animation: 'spin 1s linear infinite',
          }}
        />
        <Text style={{ color: '#94A3B8' }}>{message}</Text>
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
// ERROR/MESSAGE DISPLAY
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
    return <LoadingScreen message="Checking authentication..." />;
  }

  if (session?.user) {
    return <LoadingScreen message="Redirecting..." />;
  }

  if (isRedirecting) {
    return <LoadingScreen message="Redirecting to sign in..." />;
  }

  // If no errors and not just registered, this should auto-redirect
  // But if we reach here, show a minimal UI with action buttons
  const showErrorState = !!authError;
  const showSuccessState = justRegistered && !authError;

  return (
    <Box
      style={{
        minHeight: '100vh',
        background: '#0A0A0F',
        display: 'flex',
        flexDirection: 'column',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px',
      }}
    >
      {/* Background Effects */}
      <Box
        style={{
          position: 'absolute',
          inset: 0,
          background: `
            radial-gradient(ellipse 60% 40% at 50% 30%, rgba(16, 185, 129, 0.12), transparent 50%),
            radial-gradient(ellipse 40% 30% at 70% 70%, rgba(20, 184, 166, 0.08), transparent 50%)
          `,
          pointerEvents: 'none',
        }}
      />

      <Box style={{ width: '100%', maxWidth: 400, position: 'relative', zIndex: 10 }}>
        {/* Logo & Branding */}
        <Flex direction="column" align="center" gap="4" mb="8">
          <Box
            style={{
              width: 72,
              height: 72,
              borderRadius: '20px',
              background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 50px rgba(16, 185, 129, 0.4)',
            }}
          >
            <Calendar style={{ width: 36, height: 36, color: 'white' }} />
          </Box>
          <Box style={{ textAlign: 'center' }}>
            <Heading size="6" style={{ color: '#F8FAFC', marginBottom: 8 }}>
              MyTicket Zambia
            </Heading>
            <Text size="2" style={{ color: '#8A9BAA' }}>
              Organization Portal
            </Text>
          </Box>
        </Flex>

        {/* Card */}
        <Box
          p="6"
          style={{
            background: 'rgba(255, 255, 255, 0.03)',
            borderRadius: '20px',
            border: '1px solid rgba(255, 255, 255, 0.08)',
            backdropFilter: 'blur(20px)',
          }}
        >
          <Flex direction="column" gap="5">
            {/* Success Message - Just Registered */}
            {showSuccessState && (
              <Callout.Root color="green" size="2">
                <Callout.Text>
                  Account created successfully! Redirecting to sign in...
                </Callout.Text>
              </Callout.Root>
            )}

            {/* Error Message */}
            {showErrorState && (
              <>
                <Callout.Root color="red" size="2">
                  <Callout.Text>
                    {authError === 'OAuthAccountNotLinked'
                      ? 'This email is already associated with another account.'
                      : authError === 'AccessDenied'
                      ? 'Access was denied. Please contact support if this continues.'
                      : 'Authentication failed. Please try again.'}
                  </Callout.Text>
                </Callout.Root>

                <Button
                  size="3"
                  onClick={handleRetryLogin}
                  style={{
                    width: '100%',
                    height: 52,
                    background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
                    cursor: 'pointer',
                    boxShadow: '0 4px 20px rgba(16, 185, 129, 0.35)',
                    borderRadius: 14,
                  }}
                >
                  <Flex align="center" gap="2">
                    <Lock style={{ width: 18, height: 18 }} />
                    <span>Try Again</span>
                  </Flex>
                </Button>

                {/* Divider */}
                <Flex align="center" gap="3">
                  <Box style={{ flex: 1, height: 1, background: 'rgba(255, 255, 255, 0.08)' }} />
                  <Text size="1" style={{ color: '#6B7280' }}>or</Text>
                  <Box style={{ flex: 1, height: 1, background: 'rgba(255, 255, 255, 0.08)' }} />
                </Flex>

                <Button
                  size="3"
                  variant="outline"
                  onClick={handleRegister}
                  style={{
                    width: '100%',
                    height: 52,
                    borderColor: 'rgba(16, 185, 129, 0.3)',
                    color: '#10B981',
                    cursor: 'pointer',
                    borderRadius: 14,
                  }}
                >
                  <Flex align="center" gap="2">
                    <UserPlus style={{ width: 18, height: 18 }} />
                    <span>Create New Account</span>
                  </Flex>
                </Button>
              </>
            )}

            {/* Fallback UI if auto-redirect didn't happen */}
            {!showErrorState && !showSuccessState && (
              <>
                <Box style={{ textAlign: 'center' }}>
                  <Heading size="4" mb="2" style={{ color: '#F8FAFC' }}>
                    Sign In Required
                  </Heading>
                  <Text size="2" style={{ color: '#8A9BAA' }}>
                    Please sign in to access the organization portal
                  </Text>
                </Box>

                <Button
                  size="3"
                  onClick={handleRetryLogin}
                  style={{
                    width: '100%',
                    height: 52,
                    background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
                    cursor: 'pointer',
                    boxShadow: '0 4px 20px rgba(16, 185, 129, 0.35)',
                    borderRadius: 14,
                  }}
                >
                  <Flex align="center" gap="2">
                    <Lock style={{ width: 18, height: 18 }} />
                    <span>Sign In</span>
                  </Flex>
                </Button>

                <Flex align="center" gap="3">
                  <Box style={{ flex: 1, height: 1, background: 'rgba(255, 255, 255, 0.08)' }} />
                  <Text size="1" style={{ color: '#6B7280' }}>New here?</Text>
                  <Box style={{ flex: 1, height: 1, background: 'rgba(255, 255, 255, 0.08)' }} />
                </Flex>

                <Button
                  size="3"
                  variant="outline"
                  onClick={handleRegister}
                  style={{
                    width: '100%',
                    height: 52,
                    borderColor: 'rgba(16, 185, 129, 0.3)',
                    color: '#10B981',
                    cursor: 'pointer',
                    borderRadius: 14,
                  }}
                >
                  <Flex align="center" gap="2">
                    <UserPlus style={{ width: 18, height: 18 }} />
                    <span>Apply to Become an Organizer</span>
                  </Flex>
                </Button>
              </>
            )}

            {/* Help Link */}
            <Text size="1" align="center" style={{ color: '#6B7280' }}>
              Need help?{' '}
              <a
                href="mailto:support@myticket.zm"
                style={{
                  color: '#10B981',
                  textDecoration: 'none',
                }}
              >
                Contact Support
              </a>
            </Text>
          </Flex>
        </Box>

        {/* Footer */}
        <Text
          size="1"
          align="center"
          mt="6"
          style={{ color: '#6B7280', display: 'block' }}
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
      <AuthMessageContent />
    </Suspense>
  );
}
