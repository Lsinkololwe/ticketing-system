'use client';

import { describeRule, hoursText, sortedRules, usePlatformRules } from '@pml.tickets/shared';
import { ErrorState, Skeleton } from '@pml.tickets/shared/components/m3';
import type { GraphQLLikeError } from '@pml.tickets/shared';

const SUPPORT_EMAIL = process.env.NEXT_PUBLIC_SUPPORT_EMAIL ?? '';
const SUPPORT_PHONE = process.env.NEXT_PUBLIC_SUPPORT_PHONE ?? '';

/** "Need help with your booking?" topics. `booking` is shown when the buyer opens help from a booking. */
export function HelpContent({ booking }: { booking?: string }) {
  return (
    <div className="m3-stack">
      <p>
        Have your booking details ready{booking ? <> (<b>{booking}</b>)</> : null} and contact us. We reply within one working day.
      </p>
      <ul className="buyer-plain">
        <li>
          <b>Refunds</b>
          <br />
          <span className="m3-muted">Use Request refund on a ticket in My tickets to see your refund quote.</span>
        </li>
        <li>
          <b>Lost access to a ticket</b>
          <br />
          <span className="m3-muted">Tickets live in My tickets under the contact you signed in with. Sign in again to see them.</span>
        </li>
        <li>
          <b>Payment taken but no tickets</b>
          <br />
          <span className="m3-muted">Wait a few minutes for the mobile money confirmation. If you were charged after your hold expired, we refund you automatically.</span>
        </li>
      </ul>
      {SUPPORT_EMAIL || SUPPORT_PHONE ? (
        <p className="m3-muted">
          {[SUPPORT_EMAIL, SUPPORT_PHONE].filter(Boolean).join(' · ')}
        </p>
      ) : null}
    </div>
  );
}

/** The refund policies the platform offers, with their rules, read from the platform rules. */
export function RefundPoliciesContent() {
  const { rules, loading, error, refetch } = usePlatformRules();
  return (
    <div className="m3-stack">
      <p className="m3-muted">
        Each event picks one policy.
        {rules ? ` Refund requests close ${hoursText(rules.refundCutoffHours)} before the event and there are no refunds after it.` : ' Refund requests close before the event and there are no refunds after it.'} A person reviews every request, and the refund quote on your ticket shows exactly what applies.
      </p>
      {loading && !rules ? (
        <div aria-busy="true" aria-label="Loading refund policies">
          <Skeleton width="60%" />
          <Skeleton width="80%" />
        </div>
      ) : rules ? (
        <dl className="buyer-pol">
          {rules.refundPolicies.map((p) => (
            <div key={p.code}>
              <dt>{p.label}</dt>
              <dd>{p.summary}</dd>
              {p.rules.length ? (
                <dd>
                  <ul className="buyer-plain">
                    {sortedRules(p).map((r) => (
                      <li key={r.daysBefore}>{describeRule(r)}</li>
                    ))}
                  </ul>
                </dd>
              ) : null}
            </div>
          ))}
        </dl>
      ) : error ? (
        <ErrorState error={error as unknown as GraphQLLikeError} onRetry={() => void refetch()} />
      ) : (
        <p className="m3-muted">The refund policies are not available right now.</p>
      )}
    </div>
  );
}
