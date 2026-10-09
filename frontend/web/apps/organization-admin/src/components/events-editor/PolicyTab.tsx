'use client';

import { RadioGroupRHF, SwitchRHF, TextAreaRHF, TextFieldRHF } from '@pml.tickets/shared';
import { Banner, Card, CardHeader } from '@pml.tickets/shared/components/m3';
import { FaqCard } from './ListCards';
import type { RefundPolicyOption } from './model';
import { useEditorValues } from './useEditorValues';

export interface PolicyTabProps {
  /** Platform refund policies; empty while they cannot be read. */
  refundPolicies: RefundPolicyOption[];
  refundCutoffHours?: number | null;
  holdMinutes?: number | null;
  graceMinutes?: number | null;
  maxPerBooking?: number | null;
}

export function PolicyTab({ refundPolicies, refundCutoffHours, holdMinutes, graceMinutes, maxPerBooking }: PolicyTabProps) {
  const values = useEditorValues();
  const chosen = refundPolicies.find((p) => p.value === values.refundPolicy);
  return (
    <div className="m3-stack">
      <Card>
        <CardHeader title="Refund policy" subtitle={`Shown to buyers before they pay.${refundCutoffHours != null ? ` Refunds close ${refundCutoffHours} hours before the event.` : ''}`} />
        {refundPolicies.length === 0 ? <Banner tone="warning">Not available yet: the platform refund policies could not be loaded.</Banner> : null}
        <RadioGroupRHF
          name="refundPolicy"
          legend="Refund policy"
          options={refundPolicies.map((p) => ({ value: p.value, label: p.label, hint: p.summary }))}
        />
        {chosen ? (
          <Banner tone="info">
            <b>{chosen.label}:</b> {chosen.summary}
          </Banner>
        ) : null}
      </Card>
      <Card>
        <CardHeader title="Cancellation policy and terms" subtitle="Shown at payment and on the event page." />
        <div className="m3-stack">
          <TextAreaRHF name="cancellationPolicy" label="Cancellation policy" rows={4} />
          <TextAreaRHF name="termsAndConditions" label="Terms and conditions" rows={5} />
        </div>
      </Card>
      <Card>
        <CardHeader
          title="Basket rules"
          subtitle={holdMinutes != null ? `Tickets are held for ${holdMinutes} minutes while a buyer pays${graceMinutes != null ? ` (plus ${graceMinutes} minutes grace)` : ''}. This is set by the platform.` : 'Set by the platform.'}
        />
        <TextFieldRHF
          name="checkout.maxTicketsPerOrder"
          label="Max tickets per booking"
          inputMode="numeric"
          helperText={maxPerBooking != null ? `Platform limit: ${maxPerBooking} per booking. Leave empty to use it.` : 'Leave empty to use the platform limit.'}
        />
        <SwitchRHF name="checkout.collectHolderNames" label="Ask for a name on every ticket" hint="Buyers can enter a holder name per ticket." />
        <TextFieldRHF name="checkout.extraQuestion" label="Extra question for buyers (optional)" maxLength={80} helperText="For example: Any access requirements?" />
      </Card>
      <FaqCard />
    </div>
  );
}
