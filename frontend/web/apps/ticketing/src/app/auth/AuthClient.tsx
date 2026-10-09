'use client';

import { Banner } from '@pml.tickets/shared/components/m3';
import { IdentifyStep } from '@/components/identify/IdentifyStep';
import { SiteShell } from '@/components/shell/SiteShell';
import { AUTH_PAGE_ERRORS } from '@/lib/identity/messages';

/** Sign in or create an account with a one-time code. Browsing events never needs an account. */
export default function AuthClient({ next, error, settingUp }: { next: string; error: string | null; settingUp: boolean }) {
  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-narrow buyer-page">
        <section className="m3-panel m3-stack" aria-labelledby="auth-title">
          <h1 className="m3-page-title" id="auth-title">
            Sign in to Showstop
          </h1>
          <p className="m3-muted">
            Enter your WhatsApp number or email and we will send you a 6-digit code. No password needed. New here? We will create your account.
          </p>
          {settingUp && (
            <div role="status" data-testid="auth-setting-up">
              <Banner tone="warning">Your account is being set up. This can take a moment: please try again shortly.</Banner>
            </div>
          )}
          {error && (
            <div role="alert" data-testid="auth-error">
              <Banner tone="error">{AUTH_PAGE_ERRORS[error] ?? AUTH_PAGE_ERRORS.SIGN_IN_FAILED}</Banner>
            </div>
          )}
          <IdentifyStep returnTo={next} />
          <p className="m3-muted">Pay with MTN, Airtel or Zamtel mobile money. Browsing events never needs an account.</p>
        </section>
      </div>
    </SiteShell>
  );
}
