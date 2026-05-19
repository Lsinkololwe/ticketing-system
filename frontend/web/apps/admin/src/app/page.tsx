'use client';

/**
 * Root Page
 *
 * Redirects to dashboard if authenticated, otherwise to login.
 * Uses Better Auth for session management.
 */

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Flex, Text, Spinner } from '@radix-ui/themes';
import { useSession } from '@/lib/auth/client';

export default function Home() {
  const { data: session, isPending } = useSession();
  const router = useRouter();

  useEffect(() => {
    if (!isPending) {
      if (session?.user) {
        router.push('/dashboard');
      } else {
        router.push('/login');
      }
    }
  }, [session, isPending, router]);

  // Show loading state
  return (
    <Flex
      align="center"
      justify="center"
      style={{ minHeight: '100vh', backgroundColor: 'var(--gray-1)' }}
    >
      <Flex direction="column" align="center" gap="3">
        <Spinner size="3" />
        <Text size="2" color="gray">Loading...</Text>
      </Flex>
    </Flex>
  );
}
