'use client';

/**
 * Admin Login Page
 *
 * Professional enterprise login for the PML Ticketing Admin Portal.
 * Features:
 * - Split-screen layout with branding and login
 * - Dark mode OLED aesthetic
 * - Trust signals and security indicators
 * - Responsive design
 *
 * Authentication via Better Auth with Keycloak OIDC.
 * Sessions stored in Redis for scalability.
 */

import { useEffect, useCallback, Suspense, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { Box, Flex, Text, Button, Heading, Spinner, Callout } from '@radix-ui/themes';
import { ShieldCheck, Lock, Server, GraphUp } from 'iconoir-react';
import { useSession, signInWithKeycloak } from '@/lib/auth/client';

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
        backgroundColor: '#020617',
      }}
    >
      <Flex direction="column" align="center" gap="4">
        <Box
          style={{
            width: 48,
            height: 48,
            borderRadius: '50%',
            border: '3px solid rgba(139, 92, 246, 0.2)',
            borderTopColor: '#8B5CF6',
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
// FEATURE CARD
// =============================================================================

function FeatureCard({ icon, title, description }: {
  icon: React.ReactNode;
  title: string;
  description: string;
}) {
  return (
    <Flex gap="3" align="start">
      <Box
        style={{
          padding: '10px',
          borderRadius: '12px',
          backgroundColor: 'rgba(139, 92, 246, 0.1)',
          border: '1px solid rgba(139, 92, 246, 0.2)',
          flexShrink: 0,
        }}
      >
        {icon}
      </Box>
      <Box>
        <Text
          size="2"
          weight="medium"
          style={{ color: '#F8FAFC', display: 'block', marginBottom: 4 }}
        >
          {title}
        </Text>
        <Text size="1" style={{ color: '#64748B' }}>
          {description}
        </Text>
      </Box>
    </Flex>
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
        backgroundColor: '#020617',
        display: 'flex',
      }}
    >
      {/* Left Panel - Branding */}
      <Box
        style={{
          flex: 1,
          display: 'none',
          padding: '48px',
          background: 'linear-gradient(135deg, #0F172A 0%, #1E1B4B 50%, #0F172A 100%)',
          position: 'relative',
          overflow: 'hidden',
        }}
        className="lg-flex"
      >
        {/* Background Pattern */}
        <Box
          style={{
            position: 'absolute',
            inset: 0,
            backgroundImage: `
              radial-gradient(circle at 20% 80%, rgba(139, 92, 246, 0.15) 0%, transparent 50%),
              radial-gradient(circle at 80% 20%, rgba(59, 130, 246, 0.1) 0%, transparent 50%),
              radial-gradient(circle at 50% 50%, rgba(139, 92, 246, 0.05) 0%, transparent 70%)
            `,
          }}
        />

        {/* Grid Pattern Overlay */}
        <Box
          style={{
            position: 'absolute',
            inset: 0,
            backgroundImage: `
              linear-gradient(rgba(139, 92, 246, 0.03) 1px, transparent 1px),
              linear-gradient(90deg, rgba(139, 92, 246, 0.03) 1px, transparent 1px)
            `,
            backgroundSize: '50px 50px',
          }}
        />

        <Flex
          direction="column"
          justify="between"
          style={{ position: 'relative', zIndex: 1, height: '100%' }}
        >
          {/* Logo & Title */}
          <Box>
            <Flex align="center" gap="3" mb="6">
              <Box
                style={{
                  width: 48,
                  height: 48,
                  borderRadius: '12px',
                  background: 'linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 0 30px rgba(139, 92, 246, 0.4)',
                }}
              >
                <Text size="5" weight="bold" style={{ color: 'white' }}>P</Text>
              </Box>
              <Box>
                <Text size="4" weight="bold" style={{ color: '#F8FAFC' }}>
                  PML Tickets - Admin Portal
                </Text>
              </Box>
            </Flex>

            <Box mb="6">
              <Heading
                size="8"
                style={{
                  color: '#F8FAFC',
                  lineHeight: 1.2,
                  letterSpacing: '-0.02em',
                }}
              >
                Event Management
                <br />
                <span style={{ color: '#8B5CF6' }}>Made Simple</span>
              </Heading>
              <Text
                size="3"
                style={{ color: '#94A3B8', marginTop: 16, display: 'block', maxWidth: 400 }}
              >
                Powerful tools to manage events, organizers, tickets, and payments
                across your entire platform.
              </Text>
            </Box>
          </Box>

          {/* Features */}
          <Flex direction="column" gap="5">
            <FeatureCard
              icon={<GraphUp style={{ width: 20, height: 20, color: '#8B5CF6' }} />}
              title="Real-time Analytics"
              description="Monitor ticket sales, revenue, and attendee data in real-time"
            />
            <FeatureCard
              icon={<ShieldCheck style={{ width: 20, height: 20, color: '#8B5CF6' }} />}
              title="Organizer Verification"
              description="Approve and manage organizer applications with document verification"
            />
            <FeatureCard
              icon={<Server style={{ width: 20, height: 20, color: '#8B5CF6' }} />}
              title="Payment Management"
              description="Process payouts, track commissions, and manage escrow accounts"
            />
          </Flex>

          {/* Footer */}
          <Text size="1" style={{ color: '#475569' }}>
            &copy; {new Date().getFullYear()} PML Tickets. Admin Portal.
          </Text>
        </Flex>
      </Box>

      {/* Right Panel - Login Form */}
      <Flex
        direction="column"
        align="center"
        justify="center"
        style={{
          flex: 1,
          padding: '48px 24px',
          minHeight: '100vh',
        }}
      >
        <Box style={{ width: '100%', maxWidth: 400 }}>
          {/* Mobile Logo */}
          <Flex
            direction="column"
            align="center"
            gap="4"
            mb="8"
            className="lg-hidden"
          >
            <Box
              style={{
                width: 64,
                height: 64,
                borderRadius: '16px',
                background: 'linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 0 40px rgba(139, 92, 246, 0.3)',
              }}
            >
              <Text size="7" weight="bold" style={{ color: 'white' }}>P</Text>
            </Box>
            <Box style={{ textAlign: 'center' }}>
              <Heading size="6" style={{ color: '#F8FAFC' }}>
                PML Admin Portal
              </Heading>
              <Text size="2" style={{ color: '#64748B' }}>
                Event Ticketing Management
              </Text>
            </Box>
          </Flex>

          {/* Login Card */}
          <Box
            p="6"
            style={{
              backgroundColor: 'rgba(15, 23, 42, 0.8)',
              borderRadius: '16px',
              border: '1px solid rgba(148, 163, 184, 0.1)',
              backdropFilter: 'blur(10px)',
            }}
          >
            <Flex direction="column" gap="5">
              <Box style={{ textAlign: 'center' }}>
                <Heading size="5" mb="2" style={{ color: '#F8FAFC' }}>
                  Welcome Back
                </Heading>
                <Text size="2" style={{ color: '#64748B' }}>
                  Sign in to access your admin dashboard
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

              {/* SSO Login Button */}
              <Button
                size="3"
                onClick={handleLogin}
                disabled={isSigningIn}
                style={{
                  width: '100%',
                  height: 48,
                  background: 'linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%)',
                  cursor: isSigningIn ? 'not-allowed' : 'pointer',
                  transition: 'all 200ms ease',
                  boxShadow: '0 4px 14px rgba(139, 92, 246, 0.3)',
                }}
              >
                {isSigningIn ? (
                  <Flex align="center" gap="2">
                    <Spinner size="1" />
                    <span>Redirecting to SSO...</span>
                  </Flex>
                ) : (
                  <Flex align="center" gap="2">
                    <Lock style={{ width: 18, height: 18 }} />
                    <span>Sign In with SSO</span>
                  </Flex>
                )}
              </Button>

              {/* Help Link */}
              <Text size="1" align="center" style={{ color: '#64748B' }}>
                Having trouble signing in?{' '}
                <a
                  href="mailto:support@pml.tickets"
                  style={{
                    color: '#8B5CF6',
                    textDecoration: 'none',
                    cursor: 'pointer',
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
            style={{ color: '#475569', display: 'block' }}
          >
            &copy; {new Date().getFullYear()} PML Tickets. All rights reserved.
          </Text>
        </Box>
      </Flex>

      {/* CSS for responsive classes */}
      <style jsx global>{`
        .lg-flex {
          display: none !important;
        }
        .lg-hidden {
          display: flex !important;
        }
        @media (min-width: 1024px) {
          .lg-flex {
            display: flex !important;
          }
          .lg-hidden {
            display: none !important;
          }
        }
      `}</style>
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
