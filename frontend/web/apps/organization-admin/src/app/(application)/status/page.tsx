'use client';

/**
 * Status Page - Redirect to /apply/status
 *
 * @deprecated This page redirects to /apply/status
 * Kept for backward compatibility with any existing links.
 */

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Spinner, Text } from '@radix-ui/themes';

export default function StatusPage() {
  const router = useRouter();

  useEffect(() => {
    // Redirect to the new status page location
    router.replace('/apply/status');
  }, [router]);

  return (
    <Box style={{ textAlign: 'center', padding: '60px 0' }}>
      <Spinner size="3" />
      <Text size="2" style={{ color: '#94A3B8', display: 'block', marginTop: 16 }}>
        Redirecting...
      </Text>
    </Box>
  );
}
