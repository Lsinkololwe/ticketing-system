/**
 * Staff sign-in. Server-rendered: an existing session goes straight on; otherwise one button starts the
 * authorization-code flow at /api/auth/start. Username, password and MFA are entered at Keycloak.
 */
import { redirect } from 'next/navigation';
import { Banner, LinkButton } from '@pml.tickets/shared/components/m3';
import { AuthLayout } from '@pml.tickets/shared/layouts';
import { safeReturnTo } from '@pml.tickets/shared/auth/bff';
import { bff } from '@/lib/bff';

interface LoginSearch {
  next?: string | string[];
  error?: string | string[];
}

const first = (v: string | string[] | undefined) => (Array.isArray(v) ? v[0] : v);

/** Error codes the BFF puts in ?error= (plus anything a postLogin hook returns). */
export function loginMessage(code: string | undefined): string | null {
  if (!code) return null;
  if (code === 'FORBIDDEN') return 'This account does not hold a platform staff role.';
  if (code === 'SERVICE_UNAVAILABLE') return 'Sign-in is temporarily unavailable. Please try again in a moment.';
  return 'That sign-in did not work. Please try again.';
}

export default async function LoginPage({ searchParams }: { searchParams: Promise<LoginSearch> }) {
  const sp = await searchParams;
  const next = safeReturnTo(first(sp.next) ?? '/dashboard');
  if (await bff.getSession()) redirect(next);
  const message = loginMessage(first(sp.error));
  return (
    <AuthLayout
      product="MyTicketZM"
      console="Platform admin"
      title="MyTicketZM platform admin"
      description="You will be taken to the single sign-on page to enter your username, password and MFA code."
      footer="Staff only. Customers and organizers sign in with a phone code in their own apps."
    >
      {message ? (
        <Banner tone="error" title="Sign-in failed" urgent>
          {message}
        </Banner>
      ) : null}
      <LinkButton variant="filled" data-testid="admin-login-sso-button" href={`/api/auth/start?next=${encodeURIComponent(next)}`}>
        Sign in with SSO
      </LinkButton>
      <div className="m3-row">
        <LinkButton variant="text" size="sm" href="mailto:support@pml.tickets">
          Contact support
        </LinkButton>
      </div>
    </AuthLayout>
  );
}
