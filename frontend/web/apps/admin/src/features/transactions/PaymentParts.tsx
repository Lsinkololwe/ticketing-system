'use client';

import { z } from 'zod';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import {
  Banner,
  Button,
  KeyValue,
  Select,
  SideSheet,
  StatusPill,
  TextArea,
  useSnackbar,
} from '@pml.tickets/shared/components/m3';
import {
  attemptAgeMinutes,
  isStuckAttempt,
  usePaymentAttemptActions,
  type TxPaymentAttempt,
} from '@pml.tickets/shared/api/admin/modules/transactions';
import { RiskSignals } from './PaymentRisk';
import { FormDialog } from '@/features/ledger/FormDialog';
import { formatDateTime, humanize, money } from '@/lib/format';
import { REVIEW_STATUS_LABELS, TRANSACTION_RESOLUTION_LABELS, enumValues } from '@/lib/enumLabels';

export const REVIEW_STATUSES = enumValues(REVIEW_STATUS_LABELS);
export const RESOLUTIONS = enumValues(TRANSACTION_RESOLUTION_LABELS);

export const ageText = (m: number) => (m < 60 ? `${m} min` : m < 1440 ? `${Math.round(m / 60)} h` : `${Math.round(m / 1440)} d`);
export const reviewOf = (a: TxPaymentAttempt) => a.reviewStatus || 'NONE';

export function useRun() {
  const snackbar = useSnackbar();
  return async (fn: () => Promise<unknown>, ok: string) => {
    try {
      await fn();
      snackbar.show(ok);
      return true;
    } catch (e) {
      snackbar.show((e as Error).message || 'That did not go through');
      return false;
    }
  };
}

export type PaymentDialogKind = 'note' | 'review' | 'resolve';

const paymentSchema = z.object({
  text: z.string().trim(),
  review: z.string(),
  resolution: z.string(),
});

/** The three payment dialogs share one form: they differ by fields only. Mount only while open. */
export function PaymentDialog({ kind, attempt, onClose }: { kind: PaymentDialogKind | null; attempt: TxPaymentAttempt | null; onClose: () => void }) {
  if (!kind || !attempt) return null;
  return <PaymentDialogBody kind={kind} attempt={attempt} onClose={onClose} />;
}

function PaymentDialogBody({ kind, attempt, onClose }: { kind: PaymentDialogKind; attempt: TxPaymentAttempt; onClose: () => void }) {
  const { addNote, setReviewStatus } = usePaymentAttemptActions();
  const snackbar = useSnackbar();
  const minLen = kind === 'note' ? 3 : 5;
  const needsText = kind !== 'review';
  const schema = paymentSchema.superRefine((v, ctx) => {
    if (needsText && v.text.length < minLen) {
      ctx.addIssue({ code: 'custom', path: ['text'], message: kind === 'note' ? 'Write a short note' : 'Give a short note (at least 5 characters)' });
    }
  });
  const form = useZodForm(schema, { defaultValues: { text: '', review: reviewOf(attempt), resolution: 'MANUAL_APPROVAL' } });
  const { register, formState: { errors } } = form;
  const title = kind === 'note' ? `Add a note to ${attempt.attemptNumber}` : kind === 'review' ? `Review status for ${attempt.attemptNumber}` : `Resolve ${attempt.attemptNumber}`;
  const label = kind === 'note' ? 'Add note' : kind === 'review' ? 'Save' : 'Resolve';
  return (
    <FormDialog
      title={title}
      form={form}
      onClose={onClose}
      submitLabel={label}
      onSubmit={async (v) => {
        if (kind === 'note') await addNote(attempt.depositId, v.text);
        else if (kind === 'review') await setReviewStatus(attempt.depositId, v.review, v.text || undefined);
        else {
          await addNote(attempt.depositId, `Resolved (${humanize(v.resolution)}): ${v.text}`);
          await setReviewStatus(attempt.depositId, v.resolution === 'ESCALATED' ? 'ESCALATED' : 'REVIEWED');
        }
        snackbar.show(kind === 'note' ? 'Note added' : kind === 'review' ? 'Review status updated' : `${attempt.attemptNumber} resolved: ${humanize(v.resolution).toLowerCase()}`);
        onClose();
      }}
    >
      {kind === 'resolve' ? <p className="m3-muted">{attempt.payerPhone} · {money(Number(attempt.amount))} · {humanize(attempt.status)}</p> : null}
      {kind === 'review' ? (
        <Select label="Review status" density="form" {...register('review')}>
          {REVIEW_STATUSES.map((r) => <option key={r} value={r}>{humanize(r)}</option>)}
        </Select>
      ) : null}
      {kind === 'resolve' ? (
        <Select label="Resolution" density="form" {...register('resolution')}>
          {RESOLUTIONS.map((r) => <option key={r} value={r}>{humanize(r)}</option>)}
        </Select>
      ) : null}
      <TextArea label={kind === 'review' ? 'Notes (optional)' : 'Note'} rows={3} {...register('text')} errorText={errors.text?.message} />
      {kind === 'resolve' ? (
        <Banner tone="info">The resolution is recorded on the attempt and its review status is set. The payment itself only moves through its purchase workflow.</Banner>
      ) : null}
    </FormDialog>
  );
}

export function PaymentSheet({
  attempt,
  onClose,
  onDialog,
}: {
  attempt: TxPaymentAttempt | null;
  onClose: () => void;
  onDialog: (kind: PaymentDialogKind) => void;
}) {
  const stuck = attempt ? isStuckAttempt(attempt) : false;
  return (
    <SideSheet
      open={attempt !== null}
      onClose={onClose}
      title={`Payment ${attempt?.attemptNumber ?? ''}`}
      actions={
        attempt ? (
          <>
            <Button variant="tonal" onClick={() => onDialog('note')}>Add note…</Button>
            <Button variant="tonal" onClick={() => onDialog('review')}>Review status…</Button>
            {stuck ? <Button variant="filled" onClick={() => onDialog('resolve')}>Resolve…</Button> : null}
          </>
        ) : undefined
      }
    >
      {attempt ? (
        <div className="m3-stack">
          <div className="m3-row">
            <StatusPill status={attempt.status} />
            <StatusPill>{`Review: ${humanize(reviewOf(attempt))}`}</StatusPill>
            {stuck ? <StatusPill tone="error">{`Stuck ${ageText(attemptAgeMinutes(attempt))}`}</StatusPill> : null}
          </div>
          <KeyValue
            items={[
              { label: 'Deposit', value: <span className="m3-mono">{attempt.depositId}</span> },
              { label: 'Ticket', value: <span className="m3-mono">{attempt.ticketId}</span> },
              { label: 'Buyer', value: <span className="m3-mono">{attempt.buyerId}</span> },
              { label: 'Provider', value: humanize(attempt.provider) },
              { label: 'Phone', value: <span className="m3-mono">{attempt.payerPhone}</span> },
              { label: 'Amount', value: <span className="m3-mono">{money(Number(attempt.amount))}</span> },
              { label: 'Event', value: <span className="m3-mono">{attempt.eventId ?? '—'}</span> },
              { label: 'Created', value: formatDateTime(attempt.createdAt) },
              { label: 'Provider status', value: attempt.providerStatus ?? '—' },
              { label: 'Failure', value: attempt.failureMessage ?? attempt.failureCode ?? '—' },
              { label: 'Reviewed by', value: attempt.reviewedBy ? `${attempt.reviewedBy} · ${formatDateTime(attempt.reviewedAt)}` : '—' },
            ]}
          />
          <section>
            <h3>Fraud and risk signals</h3>
            <RiskSignals attempt={attempt} />
          </section>
          <section>
            <h3>Notes</h3>
            {attempt.notes || attempt.reviewNotes ? (
              <p style={{ whiteSpace: 'pre-wrap' }}>{[attempt.notes, attempt.reviewNotes].filter(Boolean).join('\n')}</p>
            ) : (
              <p className="m3-muted">No notes yet.</p>
            )}
          </section>
        </div>
      ) : null}
    </SideSheet>
  );
}
