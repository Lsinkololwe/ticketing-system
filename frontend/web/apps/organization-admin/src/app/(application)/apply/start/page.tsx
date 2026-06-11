'use client';

/**
 * Apply Start Page - Redirect to Welcome or Business Info
 *
 * @deprecated This page now redirects to /welcome for new users
 * or /apply/business-info for users with an existing organization.
 *
 * Kept for backward compatibility with any existing links.
 */

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Spinner, Text } from '@radix-ui/themes';
import {
  useMyOrganization,
  getRouteForStatus,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';

export default function ApplyStartPage() {
  const router = useRouter();
  const { hasOrganization, status, loading } = useMyOrganization();

  useEffect(() => {
    if (loading) return;

    if (hasOrganization) {
      // Has organization - redirect based on status
      const route = getRouteForStatus(status);
      router.replace(route);
    } else {
      // No organization - redirect to welcome page
      router.replace('/welcome');
    }
  }, [loading, hasOrganization, status, router]);

  return (
    <Box style={{ textAlign: 'center', padding: '60px 0' }}>
      <Spinner size="3" />
      <Text size="2" style={{ color: '#94A3B8', display: 'block', marginTop: 16 }}>
        Redirecting...
      </Text>
    </Box>
  );
}
