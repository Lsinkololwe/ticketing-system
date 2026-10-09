'use client';

import { useEffect, useState } from 'react';
import { Banner, Button, CircularProgress, StatusPill } from '@pml.tickets/shared/components/m3';
import { money } from '@/lib/format';
import { useMobileOperators } from '@/hooks/useMobileOperators';

export interface ApprovalStepProps {
  total: string | number;
  provider: string;
  /** Display form of the number the prompt was sent to. */
  number: string;
  holdEnded: boolean;
  resendBusy: boolean;
  onResend: () => void;
  onCancel: () => void;
}

const RESEND_AFTER_SECONDS = 30;
const SLOW_AFTER_SECONDS = 90;

/** Step 4: waiting for the buyer to approve the prompt; the page polls the reservation for the result. */
export function ApprovalStep({ total, provider, number, holdEnded, resendBusy, onResend, onCancel }: ApprovalStepProps) {
  const { labelOf } = useMobileOperators();
  const [waited, setWaited] = useState(0);
  useEffect(() => {
    const t = window.setInterval(() => setWaited((w) => w + 1), 1000);
    return () => window.clearInterval(t);
  }, []);
  const canResend = waited >= RESEND_AFTER_SECONDS;
  return (
    <section className="m3-panel buyer-pend m3-stack" aria-labelledby="pend-title">
      <CircularProgress label="Waiting for approval" />
      <h2 className="m3-card__title" id="pend-title">
        Approve the payment on your phone
      </h2>
      <p className="buyer-lead">
        We sent a prompt for <b>{money(total)}</b> to <b>{labelOf(provider)}</b> on <b>{number}</b>. Check your phone and enter your mobile money PIN to approve.
      </p>
      <p>
        <StatusPill status="PENDING" />
      </p>
      {waited >= SLOW_AFTER_SECONDS ? (
        <Banner tone="info" title={`Still waiting for ${labelOf(provider)}.`}>
          Confirmations can take a few minutes. Do not pay again. We will update this page and send you an SMS as soon as the payment is confirmed.
        </Banner>
      ) : null}
      {holdEnded ? (
        <Banner tone="warning">
          Your hold has ended, but because a payment is in progress we are still waiting for it. If it arrives too late we refund you automatically.
        </Banner>
      ) : null}
      <div className="m3-row">
        <Button disabled={!canResend} loading={resendBusy} onClick={onResend}>
          {canResend ? 'Resend prompt' : `Resend prompt in ${RESEND_AFTER_SECONDS - waited}s`}
        </Button>
        <Button onClick={onCancel}>Cancel reservation</Button>
      </div>
    </section>
  );
}
