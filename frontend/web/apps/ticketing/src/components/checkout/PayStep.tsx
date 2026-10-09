'use client';

import { useEffect, useMemo } from 'react';
import { Banner, Button } from '@pml.tickets/shared/components/m3';
import { detectOperator, prefixHint, useMobileOperators } from '@/hooks/useMobileOperators';
import { Form, RadioGroupRHF, TextFieldRHF, useZodForm } from '@pml.tickets/shared/forms';
import { money } from '@/lib/format';
import { makePayFormSchema, toLocalNumber } from './payForm';
import type { ProviderCode } from './steps';

export interface PayStepProps {
  total: string | number;
  declined: string | null;
  busy: boolean;
  onBack: () => void;
  /** Called with the validated local number (e.g. 0961234567) and chosen provider. */
  onPay: (localNumber: string, provider: ProviderCode) => void;
}

/** Step 3: choose MTN, Airtel or Zamtel, type the mobile money number, pay. */
export function PayStep({ total, declined, busy, onBack, onPay }: PayStepProps) {
  const { operators, loading, empty } = useMobileOperators();
  const schema = useMemo(() => makePayFormSchema(operators), [operators]);
  const form = useZodForm(schema, { defaultValues: { provider: '', number: '' } });
  const number = form.watch('number');
  const provider = form.watch('provider');
  const unavailable = !loading && empty;

  // Start on the platform's first operator once the list arrives.
  useEffect(() => {
    if (!provider && operators.length > 0) form.setValue('provider', operators[0].code);
  }, [provider, operators, form]);

  // The number's prefix tells us the provider.
  useEffect(() => {
    const d = detectOperator(operators, toLocalNumber(number));
    if (number && d) form.setValue('provider', d);
  }, [number, operators, form]);

  return (
    <Form form={form} guardLeave={false} aria-label="Pay with mobile money" onSubmit={(v) => onPay(toLocalNumber(v.number), v.provider)}>
      <div className="m3-panel m3-stack">
        <h2 className="m3-card__title" id="pay-title">
          Pay with mobile money
        </h2>
        {declined ? (
          <Banner tone="error" urgent title="Payment declined.">
            {declined}
          </Banner>
        ) : null}
        {unavailable ? (
          <Banner tone="warning" title="Mobile money providers are not available right now.">
            We could not load the list of providers. Try again in a moment.
          </Banner>
        ) : null}
        <div className="buyer-prov" aria-busy={loading}>
        <RadioGroupRHF name="provider" legend="Mobile money provider" options={operators.map((o) => ({ value: o.code, label: o.label, hint: prefixHint(o) }))} />
        </div>
        <TextFieldRHF name="number" label="Mobile money number" prefix="+260" inputMode="tel" autoComplete="tel-national" />
        <p className="m3-muted">We will send a payment prompt to this phone. You approve it with your mobile money PIN. Nothing is charged until you approve.</p>
        <div className="m3-row">
          <Button onClick={onBack}>Back</Button>
          <Button type="submit" variant="accent" loading={busy} disabled={loading || unavailable}>
            Pay {money(total)}
          </Button>
        </div>
      </div>
    </Form>
  );
}
