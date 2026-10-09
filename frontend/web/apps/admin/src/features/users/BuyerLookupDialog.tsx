'use client';

import { useEffect, useMemo, useState } from 'react';
import { z } from 'zod';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { useRouter } from 'next/navigation';
import { Button, Dialog, EmptyState, SegmentedButton, StatusPill, TextField } from '@pml.tickets/shared/components/m3';
import { useBuyerLookup, type AdminUserRecord } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { MaskedValue, RolePills, errorMessage } from './common';

/** Find a buyer account by email or phone; contact details stay masked until revealed. */
export function BuyerLookupDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const router = useRouter();
  const { lookup, loading } = useBuyerLookup();
  const [kind, setKind] = useState<'email' | 'phone'>('email');
  const [result, setResult] = useState<AdminUserRecord | null | undefined>(undefined);
  const [error, setError] = useState<string | null>(null);
  const schema = useMemo(
    () => z.object({ value: z.string().trim().min(1, kind === 'email' ? 'Enter an email address' : 'Enter a phone number') }),
    [kind]
  );
  const form = useZodForm(schema, { defaultValues: { value: '' } });
  const { register, handleSubmit, reset: resetForm, formState } = form;
  useEffect(() => resetForm({ value: '' }), [kind, resetForm]);

  const search = handleSubmit(async ({ value }) => {
    setError(null);
    try {
      setResult(await lookup(kind, value));
    } catch (e) {
      setResult(undefined);
      setError(errorMessage(e));
    }
  });
  const reset = () => {
    resetForm({ value: '' });
    setResult(undefined);
    setError(null);
    onClose();
  };

  return (
    <Dialog
      open={open}
      onClose={reset}
      title="Buyer account lookup"
      actions={
        <>
          <Button variant="text" onClick={reset}>
            Close
          </Button>
          <Button variant="filled" loading={loading} onClick={search}>
            Find account
          </Button>
        </>
      }
    >
      <div className="m3-stack">
        <p className="m3-muted">Look up the account behind a purchase by the buyer’s email or phone number.</p>
        <SegmentedButton
          label="Look up by"
          value={kind}
          onChange={(k) => {
            setKind(k);
            setResult(undefined);
            setError(null);
          }}
          options={[
            { value: 'email', label: 'Email' },
            { value: 'phone', label: 'Phone' },
          ]}
        />
        <TextField
          label={kind === 'email' ? 'Email address' : 'Phone number'}
          type={kind === 'email' ? 'email' : 'tel'}
          errorText={formState.errors.value?.message ?? error ?? undefined}
          {...register('value')}
          onKeyDown={(e) => {
            if (e.key === 'Enter') void search();
          }}
        />
        {result === null ? <EmptyState icon="search" title="No account found" description="Check the spelling, or try the other contact." /> : null}
        {result ? (
          <div className="m3-stack" data-testid="lookup-result">
            <div className="m3-row">
              <strong>{result.fullName}</strong>
              <StatusPill status={result.accountStatus} />
              <RolePills roles={result.roles} />
            </div>
            <div>
              Email: <MaskedValue kind="email" value={result.email} label="email" />
            </div>
            <div>
              Phone: <MaskedValue kind="phone" value={result.phoneNumber} label="phone" />
            </div>
            <div>
              <Button
                variant="tonal"
                onClick={() => {
                  reset();
                  router.push(`/user/${result.id}`);
                }}
              >
                Open full profile
              </Button>
            </div>
          </div>
        ) : null}
      </div>
    </Dialog>
  );
}
