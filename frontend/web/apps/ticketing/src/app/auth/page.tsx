import { redirect } from 'next/navigation';
import { safeReturnTo } from '@pml.tickets/shared/auth/bff';
import { getPublicSession } from '@/lib/server/public-session';
import AuthClient from './AuthClient';

/**
 * Sign-in / sign-up entry. Server component so an already signed-in buyer is redirected
 * before anything renders; there is no client-side token or SSO probe.
 */
export default async function AuthPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string; error?: string; setup?: string }>;
}) {
  const sp = await searchParams;
  const next = safeReturnTo(sp.next);
  const session = await getPublicSession();
  if (session.authenticated) redirect(next);
  return <AuthClient next={next} error={sp.error && sp.error !== 'SETUP' ? sp.error : null} settingUp={sp.setup === '1' || sp.error === 'SETUP'} />;
}
