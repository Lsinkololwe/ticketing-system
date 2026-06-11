'use client';

/**
 * Application Flow Layout
 *
 * Layout for the KYB (Know Your Business) application process.
 * Features:
 * - Clean, focused design for form completion
 * - Progress indicator header
 * - Minimal distractions
 * - Authentication required but no organization required
 *
 * Security:
 * - Requires authentication (via middleware)
 * - No organization context yet (user is applying)
 */

import { ReactNode } from 'react';
import { Box, Flex, Text, Button, Avatar } from '@radix-ui/themes';
import { Calendar, LogOut, HelpCircle } from 'iconoir-react';
import Link from 'next/link';
import { useSession, signOutComplete } from '@/lib/auth/client';
import { ProtectedRoute } from '@/components/auth/ProtectedRoute';

// =============================================================================
// TYPES
// =============================================================================

interface ApplicationLayoutProps {
  children: ReactNode;
}

// =============================================================================
// HEADER COMPONENT
// =============================================================================

function ApplicationHeader() {
  const { data: session } = useSession();

  const handleLogout = async () => {
    try {
      await signOutComplete();
    } catch (error) {
      console.error('Logout failed:', error);
    }
  };

  return (
    <Box
      asChild
      px={{ initial: '4', sm: '6', lg: '8' }}
      style={{
        height: '72px',
        background: 'linear-gradient(180deg, rgba(15, 23, 42, 0.98) 0%, rgba(15, 23, 42, 0.95) 100%)',
        backdropFilter: 'blur(12px)',
        borderBottom: '1px solid rgba(148, 163, 184, 0.1)',
        position: 'sticky',
        top: 0,
        zIndex: 50,
      }}
    >
      <header>
        <Flex align="center" justify="between" style={{ height: '100%', maxWidth: '1200px', margin: '0 auto' }}>
          {/* Logo */}
          <Link href="/" style={{ textDecoration: 'none' }}>
            <Flex align="center" gap="3">
              <Box
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: '10px',
                  background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 0 20px rgba(16, 185, 129, 0.3)',
                }}
              >
                <Calendar style={{ width: 20, height: 20, color: 'white' }} />
              </Box>
              <Box>
                <Text size="3" weight="bold" style={{ color: '#F8FAFC' }}>
                  MyTicket Zambia
                </Text>
                <Text size="1" style={{ color: '#94A3B8', display: 'block' }}>
                  Organizer Application
                </Text>
              </Box>
            </Flex>
          </Link>

          {/* Right Side */}
          <Flex align="center" gap="4">
            {/* Help Link */}
            <Button
              variant="ghost"
              size="2"
              style={{ color: '#94A3B8' }}
              onClick={() => window.open('mailto:support@myticket.zm', '_blank')}
            >
              <HelpCircle style={{ width: 18, height: 18, marginRight: 6 }} />
              <span className="hidden-mobile">Help</span>
            </Button>

            {/* User Info */}
            <Flex align="center" gap="3">
              <Avatar
                size="2"
                fallback={session?.user?.name?.charAt(0) || 'U'}
                radius="full"
                style={{
                  background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                }}
              />
              <Box className="hidden-mobile">
                <Text size="2" weight="medium" style={{ color: '#F8FAFC', display: 'block' }}>
                  {session?.user?.name || 'User'}
                </Text>
                <Text size="1" style={{ color: '#94A3B8' }}>
                  {session?.user?.email || ''}
                </Text>
              </Box>
            </Flex>

            {/* Logout */}
            <Button
              variant="ghost"
              size="2"
              style={{ color: '#94A3B8' }}
              onClick={handleLogout}
            >
              <LogOut style={{ width: 18, height: 18 }} />
            </Button>
          </Flex>
        </Flex>
      </header>
    </Box>
  );
}

// =============================================================================
// FOOTER COMPONENT
// =============================================================================

function ApplicationFooter() {
  return (
    <Box
      asChild
      py="4"
      px={{ initial: '4', sm: '6' }}
      style={{
        borderTop: '1px solid rgba(148, 163, 184, 0.1)',
        background: 'rgba(15, 23, 42, 0.5)',
      }}
    >
      <footer>
        <Flex
          justify="between"
          align="center"
          style={{ maxWidth: '1200px', margin: '0 auto' }}
          direction={{ initial: 'column', sm: 'row' }}
          gap="3"
        >
          <Text size="1" style={{ color: '#94A3B8' }}>
            &copy; {new Date().getFullYear()} MyTicket Zambia. All rights reserved.
          </Text>
          <Flex gap="4">
            <a
              href="mailto:support@myticket.zm"
              style={{ color: '#94A3B8', fontSize: '12px', textDecoration: 'none' }}
            >
              Contact Support
            </a>
            <a
              href="/privacy"
              style={{ color: '#94A3B8', fontSize: '12px', textDecoration: 'none' }}
            >
              Privacy Policy
            </a>
            <a
              href="/terms"
              style={{ color: '#94A3B8', fontSize: '12px', textDecoration: 'none' }}
            >
              Terms of Service
            </a>
          </Flex>
        </Flex>
      </footer>
    </Box>
  );
}

// =============================================================================
// LAYOUT CONTENT
// =============================================================================

function ApplicationLayoutContent({ children }: ApplicationLayoutProps) {
  return (
    <Box
      style={{
        minHeight: '100vh',
        backgroundColor: '#0F172A',
        display: 'flex',
        flexDirection: 'column',
      }}
    >
      <ApplicationHeader />

      {/* Main Content */}
      <Box
        asChild
        px={{ initial: '4', sm: '6', lg: '8' }}
        py={{ initial: '6', sm: '8' }}
        style={{
          flex: 1,
          maxWidth: '900px',
          width: '100%',
          margin: '0 auto',
        }}
      >
        <main>{children}</main>
      </Box>

      <ApplicationFooter />

      {/* Global Styles for Application Flow */}
      <style jsx global>{`
        /* Hide on mobile */
        @media (max-width: 640px) {
          .hidden-mobile {
            display: none !important;
          }
        }

        /* Application form styles */
        .application-card {
          background: rgba(30, 41, 59, 0.5);
          border: 1px solid rgba(148, 163, 184, 0.1);
          border-radius: 16px;
          padding: 32px;
          backdrop-filter: blur(10px);
        }

        .application-card:hover {
          border-color: rgba(16, 185, 129, 0.2);
        }

        /* Form input styles */
        .application-input {
          background: rgba(15, 23, 42, 0.6) !important;
          border: 1px solid rgba(148, 163, 184, 0.2) !important;
          color: #F8FAFC !important;
        }

        .application-input:focus {
          border-color: #10B981 !important;
          box-shadow: 0 0 0 3px rgba(16, 185, 129, 0.1) !important;
        }

        .application-input::placeholder {
          color: #94A3B8 !important;
        }

        /* Form label styles */
        .application-label {
          color: #CBD5E1;
          font-weight: 500;
          margin-bottom: 8px;
          display: block;
        }

        /* Form helper text */
        .application-helper {
          color: #94A3B8;
          font-size: 12px;
          margin-top: 4px;
        }

        /* Form error text */
        .application-error {
          color: #F87171;
          font-size: 12px;
          margin-top: 4px;
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// EXPORT
// =============================================================================

export default function ApplicationLayout({ children }: ApplicationLayoutProps) {
  return (
    <ProtectedRoute>
      <ApplicationLayoutContent>{children}</ApplicationLayoutContent>
    </ProtectedRoute>
  );
}
