'use client';

/**
 * Unauthorized (403) Page
 *
 * Shown when an authenticated user reaches an admin route without an allowed
 * platform role (ADMIN / SUPER_ADMIN / FINANCE). The server-side guard in
 * (dashboard)/layout.tsx redirects here via requireRoles().
 */

import { Box, Flex, Heading, Text, Button } from '@radix-ui/themes';
import { Lock } from 'iconoir-react';
import { signOut } from '@/lib/auth/client';

export default function UnauthorizedPage() {
  return (
    <Box
      style={{
        minHeight: '100vh',
        background: '#0A0A0F',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        padding: '24px',
      }}
    >
      <Box style={{ width: '100%', maxWidth: 420, textAlign: 'center' }}>
        <Flex direction="column" align="center" gap="5">
          <Box
            style={{
              width: 64,
              height: 64,
              borderRadius: '16px',
              background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 40px rgba(16, 185, 129, 0.3)',
            }}
          >
            <Lock style={{ width: 30, height: 30, color: 'white' }} />
          </Box>

          <Box>
            <Heading size="6" style={{ color: '#F8FAFC', marginBottom: 8 }}>
              Access Denied
            </Heading>
            <Text size="2" style={{ color: '#8A9BAA' }}>
              Your account doesn&apos;t have permission to access the admin dashboard.
              If you believe this is a mistake, contact a platform administrator.
            </Text>
          </Box>

          <Flex direction="column" gap="3" style={{ width: '100%' }}>
            <Button
              size="3"
              onClick={() => signOut()}
              style={{
                width: '100%',
                height: 48,
                background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
                cursor: 'pointer',
                boxShadow: '0 4px 20px rgba(16, 185, 129, 0.35)',
              }}
            >
              Sign in with a different account
            </Button>

            <Text size="1" style={{ color: '#6B7280' }}>
              Need help?{' '}
              <a href="mailto:support@pml.tickets" style={{ color: '#10B981', textDecoration: 'none' }}>
                Contact Support
              </a>
            </Text>
          </Flex>
        </Flex>
      </Box>
    </Box>
  );
}
