import { SiteShell } from '@/components/shell/SiteShell';
import { LinkBtn } from '@/components/LinkBtn';
import { HelpContent, RefundPoliciesContent } from './HelpContent';

/** Help centre: booking help topics and the refund policies explained. Public; no sign-in needed. */
export function HelpPage() {
  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-narrow buyer-page m3-stack">
        <h1 className="m3-page-title">Need help with your booking?</h1>
        <section className="m3-panel" aria-labelledby="help-title">
          <h2 className="m3-card__title" id="help-title">
            Booking help
          </h2>
          <HelpContent />
        </section>
        <section className="m3-panel" id="refund-policies" aria-labelledby="pol-title">
          <h2 className="m3-card__title" id="pol-title">
            Refund policies
          </h2>
          <RefundPoliciesContent />
        </section>
        <div className="m3-row">
          <LinkBtn href="/my-tickets" variant="filled">
            Go to My tickets
          </LinkBtn>
          <LinkBtn href="/">Back to events</LinkBtn>
        </div>
      </div>
    </SiteShell>
  );
}
