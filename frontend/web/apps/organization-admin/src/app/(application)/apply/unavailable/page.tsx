'use client';

import { useCallback, useState, useTransition } from 'react';
import { useRouter } from 'next/navigation';
import { UnavailableView } from '@/components/onboarding/UnavailableView';

export default function ApplicationUnavailablePage() {
  const router = useRouter();
  const [pending, startTransition] = useTransition();
  const [attempts, setAttempts] = useState(0);
  const retry = useCallback(() => {
    setAttempts((n) => n + 1);
    // Re-runs the server layout, which re-resolves the onboarding state.
    startTransition(() => router.refresh());
  }, [router]);
  return <UnavailableView attempts={attempts} retrying={pending} onRetry={retry} />;
}
