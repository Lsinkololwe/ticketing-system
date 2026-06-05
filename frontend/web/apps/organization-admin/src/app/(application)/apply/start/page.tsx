'use client';

/**
 * Apply Start Page - Redirect to Business Info
 *
 * @deprecated This page now just redirects to /apply/business-info
 * which handles profile creation directly.
 *
 * Kept for backward compatibility with any existing links.
 */

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Spinner, Text } from '@radix-ui/themes';

export default function ApplyStartPage() {
  const router = useRouter();

  useEffect(() => {
    // Redirect to business-info which now handles profile creation
    router.replace('/apply/business-info');
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
