'use client';

import { useRouter } from 'next/navigation';
import { WelcomeView } from '@/components/onboarding/WelcomeView';
import { useSession } from '@/lib/session';
import { ROUTES } from '@/lib/onboarding/state';

export default function WelcomePage() {
  const router = useRouter();
  const { data: session } = useSession();
  const firstName = session?.user?.name?.split(' ')[0] || 'there';
  return <WelcomeView firstName={firstName} onStart={() => router.push(ROUTES.businessInfo)} />;
}
