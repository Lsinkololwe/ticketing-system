'use client';

import React, { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Spinner } from '@radix-ui/themes';
import { useKeycloak } from '@pml.tickets/shared';

export default function AuthCallbackPage() {
  const router = useRouter();
  const { authenticated, initialized, loading } = useKeycloak();

  useEffect(() => {
    if (initialized && !loading) {
      if (authenticated) {
        router.replace('/dashboard');
      } else {
        router.replace('/auth');
      }
    }
  }, [authenticated, initialized, loading, router]);

  return (
    <Box
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: 'var(--gray-2)',
      }}
    >
      <Flex direction="column" align="center" gap="4">
        <Spinner size="3" />
        <Text size="3" color="gray">
          Completing sign in...
        </Text>
      </Flex>
    </Box>
  );
}
