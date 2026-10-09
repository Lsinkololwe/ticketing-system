'use client';

import { useRouter } from 'next/navigation';
import { Banner, Button, Dialog, useSnackbar } from '@pml.tickets/shared/components/m3';
import { Form, SelectRHF, TextAreaRHF, useFormUi, useZodForm } from '@pml.tickets/shared/forms';
import { useStepUp } from '@/lib/useStepUp';
import { useCancelEvent, useEventPayouts } from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { useReferenceData } from '@pml.tickets/shared/api/admin/modules/reference-data';
import { canOpenModule } from '@/config/navigation';
import { useStaff } from '@/components/console';
import { formatNumber, humanize } from '@/lib/format';
import { cancelSchema } from './schemas';

export interface CancelTarget {
  id: string;
  title: string;
  soldTickets: number;
}

/**
 * Cancel an event. Blocked while a payout request for the event is still open
 * (pending, approved or processing); otherwise asks for a reason category and
 * the details ticket holders will read.
 */
export function CancelEventDialog({ event, onClose, onDone }: { event: CancelTarget | null; onClose: () => void; onDone?: () => void }) {
  const router = useRouter();
  const staff = useStaff();
  const { openPayout, loading: checking, error: payoutError } = useEventPayouts(event?.id ?? '', !event);
  const { items: reasons } = useReferenceData('CANCELLATION_REASON', { skip: !event });

  if (!event) return null;

  if (openPayout) {
    return (
      <Dialog
        open
        onClose={onClose}
        title="Cannot cancel this event"
        actions={
          <>
            <Button variant="text" onClick={onClose}>
              Close
            </Button>
            {canOpenModule(staff.roles, 'finance') ? (
              <Button variant="filled" onClick={() => router.push('/finance/payouts')}>
                Open payouts
              </Button>
            ) : null}
          </>
        }
      >
        <p>
          Payout request <b>{openPayout.requestId}</b> is still open ({humanize(openPayout.status).toLowerCase()}). Cancel or reject the payout first, then cancel the event.
        </p>
      </Dialog>
    );
  }

  return (
    <Dialog open onClose={onClose} title={`Cancel ${event.title}?`}>
      <CancelForm key={reasons.length} event={event} reasons={reasons.map((r) => r.name)} checking={checking} payoutError={Boolean(payoutError)} onClose={onClose} onDone={onDone} />
    </Dialog>
  );
}

function Actions({ checking, onClose }: { checking: boolean; onClose: () => void }) {
  const { submitting } = useFormUi();
  return (
    <div className="m3-row" style={{ justifyContent: 'flex-end' }}>
      <Button type="button" variant="text" onClick={onClose}>
        Keep event
      </Button>
      <Button type="submit" variant="filled" danger loading={submitting} disabled={checking}>
        Cancel event
      </Button>
    </div>
  );
}

function CancelForm({ event, reasons, checking, payoutError, onClose, onDone }: { event: CancelTarget; reasons: string[]; checking: boolean; payoutError: boolean; onClose: () => void; onDone?: () => void }) {
  const snackbar = useSnackbar();
  const { cancel } = useCancelEvent();
  const { guard } = useStepUp();
  const form = useZodForm(cancelSchema, { defaultValues: { why: reasons[0] ?? '', details: '' } });
  return (
    <Form
      form={form}
      aria-label={`Cancel ${event.title}`}
      guardLeave={false}
      onSubmit={async (v) => {
        const text = v.why ? `${v.why}: ${v.details}` : v.details;
        const res = await guard(() => cancel(event.id, text));
        if (!res.success) throw new Error(res.message ?? 'Could not cancel the event.');
        snackbar.show(`${event.title} cancelled. ${event.soldTickets > 0 ? `Refunds queued for ${formatNumber(event.soldTickets)} tickets` : 'No tickets were sold'}`);
        onDone?.();
        onClose();
      }}
    >
      <p>
        Sales stop now and refunds start for all <b>{formatNumber(event.soldTickets)}</b> issued tickets. This cannot be undone.
      </p>
      {payoutError ? <Banner tone="warning">Could not check for open payouts. Check Finance before cancelling.</Banner> : null}
      {reasons.length > 0 ? <SelectRHF name="why" label="Reason category" options={reasons.map((r) => ({ value: r, label: r }))} /> : null}
      <TextAreaRHF name="details" label="Details for ticket holders" rows={3} />
      <Actions checking={checking} onClose={onClose} />
    </Form>
  );
}
