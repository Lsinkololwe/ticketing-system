'use client';

/**
 * Unauthorized (403) Page
 *
 * Shown when an authenticated user reaches an admin route without an allowed
 * platform role (ADMIN / SUPER_ADMIN / FINANCE). The server-side guard in
 * (dashboard)/layout.tsx redirects here via requireRoles().
 *
 * Branding: this is a plain admin surface — light-primary, teal accent, slate
 * gray, everything through tokens. The previous version painted a hardcoded
 * near-black canvas with an emerald gradient, which broke light mode outright
 * and used the MONEY colour as a brand colour. Both are DS violations.
 */

import { Box, Flex, Heading, Text } from '@radix-ui/themes';
import { Lock } from 'iconoir-react';
import { signOut } from '@/lib/auth/client';
import { Button } from '@/components/ui';

export default function UnauthorizedPage() {
  return (
    <Box
      style={{
        minHeight: '100vh',
        background: 'var(--color-background)',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: 'var(--space-5)',
      }}
    >
      <Box style={{ width: '100%', maxWidth: 420, textAlign: 'center' }}>
        <Flex direction="column" align="center" gap="5">
          {/* Small accent chip — the one sanctioned gradient in dashboard UI. */}
          <Flex
            align="center"
            justify="center"
            className="ds-accent-chip"
            style={{
              width: 64,
              height: 64,
              borderRadius: 'var(--radius-5)',
            }}
          >
            <Lock style={{ width: 28, height: 28 }} />
          </Flex>

          <Box>
            <Heading size="6" mb="2" style={{ color: 'var(--gray-12)' }}>
              You do not have access
            </Heading>
            <Text size="2" style={{ color: 'var(--gray-11)' }}>
              This account is not assigned an admin role. Sign in with an account
              that has one, or ask a platform administrator to grant access.
            </Text>
          </Box>

          <Flex direction="column" gap="3" style={{ width: '100%' }}>
            <Button
              size="3"
              data-testid="unauthorized-switch-account-button"
              onClick={() => signOut()}
              style={{ width: '100%', height: 48 }}
            >
              Sign in with a different account
            </Button>

            <Text size="1" style={{ color: 'var(--gray-11)' }}>
              Need help?{' '}
              <a
                href="mailto:support@pml.tickets"
                style={{ color: 'var(--accent-11)', textDecoration: 'none' }}
              >
                Contact support
              </a>
            </Text>
          </Flex>
        </Flex>
      </Box>
    </Box>
  );
}
