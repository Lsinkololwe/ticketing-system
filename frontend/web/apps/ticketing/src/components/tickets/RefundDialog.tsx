'use client';

import { useEffect, useState } from 'react';
import { useMemo } from 'react';
import { z } from 'zod';
import { referenceCode, useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { Banner, Button, Dialog, ErrorState, Skeleton } from '@pml.tickets/shared/components/m3';
import { resolveError, useIdempotencyKey, type GraphQLLikeError, type MyTicketRow } from '@pml.tickets/shared';
import { hoursText, policyFor, useCreateRefund, usePlatformRules, useRefundQuote } from '@pml.tickets/shared';
import { money } from '@/lib/format';
import { Form, FormActions, SelectRHF, useZodForm } from '@pml.tickets/shared/forms';

const refundSchema = (codes: readonly string[]) =>
  z.object({ reason: referenceCode(codes, 'Choose a reason from the list.', 'Choose a reason for your request.') });

/** Refund request: shows the quote the backend calculates (policy, percentage, amount), a reason, then confirms. */
export function RefundDialog({ ticket, buyerId, onClose, onDone }: { ticket: MyTicketRow | null; buyerId: string; onClose: () => void; onDone: () => void }) {
  const { quote, loading, error, load } = useRefundQuote();
  const { create } = useCreateRefund(buyerId);
  const { rules } = usePlatformRules();
  const policy = policyFor(rules, quote?.policyApplied ?? null);
  const reasons = useReferenceOptions('REFUND_REASON');
  const schema = useMemo(() => refundSchema(reasons.options.map((o) => o.value)), [reasons.options]);
  const form = useZodForm(schema, { defaultValues: { reason: '' } });
  const [submitError, setSubmitError] = useState<string | null>(null);
  // A key per ticket, persisted so a reload mid-submission reuses it: a retry of this same
  // request should replay, but a refund request raised for a different ticket afterwards must
  // never be mistaken for one.
  const [refundIdem] = useIdempotencyKey(ticket ? `refund:${ticket.id}` : null);

  useEffect(() => {
    if (ticket) {
      form.reset({ reason: '' });
      setSubmitError(null);
      void load(ticket.id);
    }
    // load identity changes every render; the ticket id is the real trigger
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [ticket?.id]);

  const submit = async ({ reason }: { reason: string }) => {
    if (!ticket) return;
    setSubmitError(null);
    try {
      // The request carries the reason in words, as the platform names it, for the reviewer to read.
      await create(ticket.id, reasons.labelOf(reason), refundIdem);
      onDone();
    } catch (e) {
      setSubmitError(resolveError(e as GraphQLLikeError).message);
    }
  };

  const eligible = quote?.isEligible === true;
  return (
    <Dialog
      open={ticket !== null}
      onClose={onClose}
      title="Request a refund"
      actions={eligible ? undefined : <Button variant="text" onClick={onClose}>Close</Button>}
    >
      {ticket ? (
        <div className="m3-stack">
          <p className="m3-muted">
            {ticket.eventTitle} · {ticket.ticketCategoryName} ticket <span className="m3-mono">{ticket.ticketNumber}</span>
          </p>
          {loading && !quote ? (
            <div aria-busy="true" aria-label="Calculating your refund">
              <Skeleton width="60%" />
              <Skeleton width="80%" />
            </div>
          ) : error ? (
            <ErrorState error={error as unknown as GraphQLLikeError} onRetry={() => void load(ticket.id)} />
          ) : quote ? (
            <div className="buyer-quote">
              <Banner tone={eligible ? 'success' : 'error'} title={eligible ? `Eligible for a ${Math.round(quote.refundPercentage)}% refund` : 'Not eligible for a refund'}>
                {eligible ? null : quote.ineligibleReason}
              </Banner>
              <dl className="m3-kv-grid">
                <div>
                  <dt>Original amount</dt>
                  <dd className="m3-num">{money(quote.originalAmount)}</dd>
                </div>
                <div>
                  <dt>Days before the event</dt>
                  <dd>{quote.daysBeforeEvent}</dd>
                </div>
                <div>
                  <dt>Policy applied</dt>
                  <dd>
                    {policy?.label ?? quote.policyApplied}
                    {policy ? <small className="m3-muted"> {policy.summary}</small> : null}
                  </dd>
                </div>
                <div>
                  <dt>Refund percentage</dt>
                  <dd>{Math.round(quote.refundPercentage)}%</dd>
                </div>
                <div>
                  <dt>Refund amount</dt>
                  <dd className="m3-num"><b>{money(quote.refundAmount)}</b></dd>
                </div>
              </dl>
            </div>
          ) : null}
          {eligible ? (
            <>
              <Form form={form} guardLeave={false} onSubmit={submit}>
                {reasons.empty && !reasons.loading ? <Banner tone="warning" title="Reasons are not available right now.">Close this and try again in a moment.</Banner> : null}
                <SelectRHF name="reason" label="Reason" placeholder={reasons.loading ? 'Loading reasons' : 'Choose a reason'} disabled={reasons.loading || reasons.empty} options={reasons.options.map((o) => ({ value: o.value, label: o.label }))} />
                {submitError ? <p role="alert" className="buyer-err">{submitError}</p> : null}
                <p className="m3-muted">A person reviews every refund request. If approved, the money is returned to the mobile money number you paid with.</p>
                <FormActions submitLabel={`Request refund of ${money(quote?.refundAmount)}`} submittingLabel="Requesting…" cancelLabel="Cancel" onCancel={onClose} />
              </Form>
            </>
          ) : quote ? (
            <p className="m3-muted">{rules ? `Refund requests close ${hoursText(rules.refundCutoffHours)} before the event. ` : ''}Need something else? Use &ldquo;Need help with your booking?&rdquo; from My tickets.</p>
          ) : null}
        </div>
      ) : null}
    </Dialog>
  );
}
