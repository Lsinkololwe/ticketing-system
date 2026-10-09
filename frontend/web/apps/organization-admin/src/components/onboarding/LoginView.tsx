'use client';

import { Banner, Button } from '@pml.tickets/shared/components/m3';
import { AuthLayout } from '@pml.tickets/shared/layouts';

export type LoginMode = 'checking' | 'redirecting' | 'error' | 'registered' | 'prompt';

export interface LoginViewProps {
  mode: LoginMode;
  authError?: string | null;
  onSignIn: () => void;
  onRegister: () => void;
}

const ERROR_COPY: Record<string, string> = {
  OAuthAccountNotLinked: 'That email is already linked to another account. Sign in with the original method.',
  AccessDenied: 'Access was denied. Contact support if this keeps happening.',
  FORBIDDEN: 'This account is not an organizer account. Apply to become an organizer or use another account.',
  SIGN_IN_FAILED: 'Sign-in did not complete. Try again.',
  LOGIN_FAILED: 'Sign-in did not complete. Try again.',
  SERVICE_UNAVAILABLE: 'Sign-in is temporarily unavailable. Try again in a moment.',
};

/** Organizer sign-in interstitial. Real authentication happens at Keycloak. */
export function LoginView({ mode, authError, onSignIn, onRegister }: LoginViewProps) {
  const footer = (
    <>
      Need help? <a className="m3-link" href="mailto:support@myticket.zm">Contact support</a>
    </>
  );
  if (mode === 'checking' || mode === 'redirecting') {
    return (
      <AuthLayout product="MyTicketZM" console="Organizer" title="Signing you in" footer={footer}>
        <p role="status" data-testid="login-loading">
          {mode === 'checking' ? 'Checking your session…' : 'Taking you to sign in…'}
        </p>
      </AuthLayout>
    );
  }
  if (mode === 'registered') {
    return (
      <AuthLayout product="MyTicketZM" console="Organizer" title="Account created" footer={footer}>
        <Banner tone="success">Your account was created. Redirecting you to sign in…</Banner>
      </AuthLayout>
    );
  }
  if (mode === 'error') {
    return (
      <AuthLayout product="MyTicketZM" console="Organizer" title="Sign-in problem" footer={footer}>
        <Banner tone="error" urgent>
          {(authError && ERROR_COPY[authError]) || 'Sign-in did not complete. Try again.'}
        </Banner>
        <Button variant="filled" icon="lock" onClick={onSignIn} data-testid="login-retry-button">
          Try again
        </Button>
        <Button variant="outlined" icon="user" onClick={onRegister} data-testid="login-register-button">
          Create an account
        </Button>
      </AuthLayout>
    );
  }
  return (
    <AuthLayout
      product="MyTicketZM"
      console="Organizer"
      title="Sign in to your console"
      description="Your session is not active. Sign in to reach the organizer console."
      footer={footer}
    >
      <Button variant="filled" icon="lock" onClick={onSignIn} data-testid="login-signin-button">
        Sign in
      </Button>
      <p className="m3-muted">New here?</p>
      <Button variant="outlined" icon="user" onClick={onRegister} data-testid="login-apply-button">
        Apply to become an organizer
      </Button>
    </AuthLayout>
  );
}
