'use client';

/**
 * Organizer sign-in. No credentials are handled here: the BFF (`/api/auth/start`) runs the
 * Keycloak authorization-code + PKCE flow and sets an opaque httpOnly session cookie.
 * Error codes arrive as `?error=` (SIGN_IN_FAILED, FORBIDDEN, LOGIN_FAILED, SERVICE_UNAVAILABLE, ...).
 */

import { Suspense, useCallback, useEffect, useRef, useState } from 'react';
import { useRouter, useSearchParams } from 'next/navigation';
import { LoginView, type LoginMode } from '@/components/onboarding/LoginView';
import { signInUrl, useSession } from '@/lib/session';

function safeNext(raw: string | null): string {
  return raw && raw.startsWith('/') && !raw.startsWith('//') && !raw.startsWith('/\\') && !raw.startsWith('/api/') ? raw : '/dashboard';
}

function AuthMessageContent() {
  const router = useRouter();
  const params = useSearchParams();
  const { data: session, isPending } = useSession();
  const [redirecting, setRedirecting] = useState(false);
  const auto = useRef(false);

  const next = safeNext(params.get('next'));
  const authError = params.get('error');
  const justRegistered = params.get('registered') === 'true';

  const go = useCallback(() => {
    setRedirecting(true);
    window.location.assign(signInUrl(next));
  }, [next]);

  // Already signed in: /welcome resolves the real destination from organization status.
  useEffect(() => {
    if (session?.user) router.replace('/welcome');
  }, [session, router]);

  useEffect(() => {
    if (isPending || session?.user || authError || justRegistered || redirecting || auto.current) return;
    auto.current = true;
    go();
  }, [isPending, session, authError, justRegistered, redirecting, go]);

  useEffect(() => {
    if (!justRegistered || authError || isPending || session?.user) return undefined;
    const t = setTimeout(go, 2000);
    return () => clearTimeout(t);
  }, [justRegistered, authError, isPending, session, go]);

  const mode: LoginMode = isPending
    ? 'checking'
    : session?.user || redirecting
      ? 'redirecting'
      : authError
        ? 'error'
        : justRegistered
          ? 'registered'
          : 'prompt';

  // Account creation is offered on the Keycloak sign-in page; both buttons start the same flow.
  return <LoginView mode={mode} authError={authError} onSignIn={go} onRegister={go} />;
}

export default function LoginPage() {
  return (
    <Suspense fallback={<LoginView mode="checking" onSignIn={() => undefined} onRegister={() => undefined} />}>
      <AuthMessageContent />
    </Suspense>
  );
}
