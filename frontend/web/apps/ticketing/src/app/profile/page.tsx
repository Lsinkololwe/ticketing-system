import type { Metadata } from 'next';
import { bff } from '@/lib/bff';
import { ProfileClient } from '@/components/profile/ProfileClient';

export const metadata: Metadata = { title: 'Profile and settings | Showstop Tickets', robots: { index: false } };

/** Server-side guard: no valid server session means redirect to /auth before anything renders. */
export default async function ProfilePage() {
  await bff.requireSession({ returnTo: '/profile' });
  return <ProfileClient />;
}
