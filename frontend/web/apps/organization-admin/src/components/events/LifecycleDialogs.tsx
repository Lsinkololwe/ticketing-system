'use client';

import { useRouter } from 'next/navigation';
import { useCallback, useState, type ReactNode } from 'react';
import {
  Banner,
  Button,
  ConfirmDialog,
  Dialog,
  useSnackbar,
} from '@pml.tickets/shared/components/m3';
import { getUserFriendlyErrorMessage } from '@pml.tickets/shared';
import { useReferenceList, usePlatformRules } from '@/lib/api/platform';
import { useEventLifecycle, useEventPayoutsLazy, type EventPayout } from '@/lib/api/events';
import { formatMoney } from '@/lib/format/figure';
import { LinkBtn } from '@/components/console/LinkBtn';
import { CancelEventDialog, DuplicateEventDialog, RescheduleDialog } from './LifecycleForms';
import { blockersOf, BLOCKER_TEXT, whyCannotPublish, type RowAction } from './eventLogic';

/** Minimal shape the lifecycle flows need; both the list row and the detail satisfy it. */
export interface LifecycleEvent {
  id: string;
  title: string;
  status: string;
  eventDateTime: string;
  endDateTime?: string | null;
  soldTickets: number;
  locationName: string | null;
  totalCapacity: number;
  ticketTiers: Array<{ id: string; isActive: boolean }> | null;
}

type Flow =
  | { kind: 'submit' | 'publish' | 'unpublish' | 'delete' | 'duplicate'; event: LifecycleEvent }
  | { kind: 'reschedule' | 'cancel'; event: LifecycleEvent }
  | { kind: 'blocked'; event: LifecycleEvent; reasons: string[] }
  | { kind: 'payout'; event: LifecycleEvent; payouts: EventPayout[] }
  | { kind: 'sold'; event: LifecycleEvent }
  | null;

/**
 * Runs every lifecycle action behind its designed dialog. Returns `run` for the
 * triggering UI and `dialogs` to mount once. `onDone` fires after a mutation
 * so the host can navigate or refetch.
 */
export function useLifecycle(onDone?: (what: string, id?: string) => void) {
  const api = useEventLifecycle();
  const lookupPayouts = useEventPayoutsLazy();
  const reasons = useReferenceList('CANCELLATION_REASON').items.map((r) => r.name);
  const rescheduleLimit = usePlatformRules().rules?.rescheduleLimit ?? null;
  const snack = useSnackbar();
  const router = useRouter();
  const [flow, setFlow] = useState<Flow>(null);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const close = useCallback(() => {
    setFlow(null);
    setError(null);
  }, []);

  const run = useCallback(
    async (action: RowAction['id'], event: LifecycleEvent) => {
      if (action === 'edit') return router.push(`/events/${event.id}/edit`);
      if (action === 'publish') {
        const reasons = whyCannotPublish(event);
        return setFlow(reasons.length ? { kind: 'blocked', event, reasons } : { kind: 'publish', event });
      }
      if (action === 'unpublish') return setFlow(event.soldTickets > 0 ? { kind: 'sold', event } : { kind: 'unpublish', event });
      if (action === 'cancel') {
        const payouts = await lookupPayouts(event.id).catch(() => []);
        return setFlow(payouts.length ? { kind: 'payout', event, payouts } : { kind: 'cancel', event });
      }
      setFlow({ kind: action, event } as Flow);
    },
    [router, lookupPayouts]
  );

  /** Like exec but lets the error reach the surrounding <Form>, which maps it onto the dialog. */
  const submit = async (fn: () => Promise<unknown>, done: string, key: string, id?: string) => {
    await fn();
    snack.show(done);
    close();
    onDone?.(key, id);
  };

  const exec = async (fn: () => Promise<unknown>, done: string, key: string, id?: string) => {
    setBusy(true);
    setError(null);
    try {
      await fn();
      snack.show(done);
      close();
      onDone?.(key, id);
    } catch (e) {
      setError(getUserFriendlyErrorMessage(e as never) || 'That did not work. Try again.');
    } finally {
      setBusy(false);
    }
  };

  const f = flow;
  const err = error ? <Banner tone="error" urgent>{error}</Banner> : null;
  const cancelBtn = (
    <Button variant="text" onClick={close}>
      Cancel
    </Button>
  );
  let dialogs: ReactNode = null;

  if (f?.kind === 'submit') {
    const bl = blockersOf(f.event);
    dialogs = (
      <ConfirmDialog
        open
        onClose={close}
        loading={busy}
        title={`${f.event.status === 'CHANGES_REQUESTED' ? 'Resubmit' : 'Submit'} “${f.event.title}” for approval?`}
        confirmLabel={bl.length ? 'Submit anyway' : 'Submit for approval'}
        onConfirm={() => exec(() => api.submit(f.event.id), 'Submitted for approval', 'submit', f.event.id)}
        description={
          <>
            {err}
            {bl.length ? (
              <>
                These items will stop an admin approving it:
                <ul>{bl.map((b) => <li key={b}>{BLOCKER_TEXT[b]}</li>)}</ul>
              </>
            ) : (
              'A platform reviewer checks the event. You will be told if changes are needed.'
            )}
          </>
        }
      />
    );
  } else if (f?.kind === 'publish') {
    dialogs = (
      <ConfirmDialog
        open
        onClose={close}
        loading={busy}
        title={`Publish “${f.event.title}”?`}
        confirmLabel="Publish"
        onConfirm={() => exec(() => api.publish(f.event.id), 'Event is live', 'publish', f.event.id)}
        description={<>{err}It becomes visible to buyers and tickets go on sale straight away. Escrow collects the payments.</>}
      />
    );
  } else if (f?.kind === 'blocked') {
    dialogs = (
      <Dialog open onClose={close} title="Cannot publish yet" actions={<Button variant="filled" onClick={close}>Close</Button>}>
        <p>“{f.event.title}” cannot go live:</p>
        <ul>{f.reasons.map((r) => <li key={r}>{r}</li>)}</ul>
      </Dialog>
    );
  } else if (f?.kind === 'sold') {
    dialogs = (
      <Dialog
        open
        onClose={close}
        title="Cannot unpublish"
        actions={
          <>
            {cancelBtn}
            <Button variant="filled" onClick={() => setFlow({ kind: 'reschedule', event: f.event })}>Reschedule…</Button>
          </>
        }
      >
        <p>
          <b>{f.event.title}</b> has {f.event.soldTickets} tickets sold. Live events with sales cannot be unpublished. You can reschedule it or cancel it with refunds instead.
        </p>
      </Dialog>
    );
  } else if (f?.kind === 'unpublish') {
    dialogs = (
      <ConfirmDialog
        open
        onClose={close}
        loading={busy}
        danger
        title={`Unpublish “${f.event.title}”?`}
        confirmLabel="Unpublish"
        onConfirm={() => exec(() => api.unpublish(f.event.id), 'Event unpublished', 'unpublish', f.event.id)}
        description={<>{err}It disappears from MyTicketZM. No tickets have been sold.</>}
      />
    );
  } else if (f?.kind === 'reschedule') {
    dialogs = (
      <RescheduleDialog
        title={f.event.title}
        sold={f.event.soldTickets}
        rescheduleLimit={rescheduleLimit}
        currentStart={f.event.eventDateTime}
        onClose={close}
        onSubmit={(v) => submit(() => api.reschedule(f.event.id, new Date(v.start).toISOString(), v.reason), 'Rescheduled', 'reschedule', f.event.id)}
      />
    );
  } else if (f?.kind === 'payout') {
    const p = f.payouts[0];
    dialogs = (
      <Dialog
        open
        onClose={close}
        title="Cannot cancel while a payout is open"
        actions={
          <>
            {cancelBtn}
            <LinkBtn href="/finance" variant="filled">Go to payouts</LinkBtn>
          </>
        }
      >
        <p>
          Payout request <b>{p.requestId}</b> ({formatMoney(p.requestedAmount, p.currency, { decimals: 2 })}) is {p.status.toLowerCase()} for this event. Cancel that payout request first, then cancel the event.
        </p>
      </Dialog>
    );
  } else if (f?.kind === 'cancel') {
    dialogs = (
      <CancelEventDialog
        title={f.event.title}
        sold={f.event.soldTickets}
        reasons={reasons}
        onClose={close}
        onSubmit={(v) =>
          submit(() => api.cancel(f.event.id, v.reason + (v.note ? `: ${v.note}` : '')), 'Event cancelled. Refunds are processing.', 'cancel', f.event.id)
        }
      />
    );
  } else if (f?.kind === 'duplicate') {
    dialogs = (
      <DuplicateEventDialog
        title={f.event.title}
        onClose={close}
        onSubmit={async (v) => {
          const res = await api.duplicate(f.event.id, v.title);
          snack.show('Duplicated as a draft. Set the new date next.');
          close();
          const id = res.data?.duplicateEvent.id;
          onDone?.('duplicate', id);
          if (id) router.push(`/events/${id}/edit`);
        }}
      />
    );
  } else if (f?.kind === 'delete') {
    dialogs = (
      <ConfirmDialog
        open
        onClose={close}
        loading={busy}
        danger
        title={`Delete “${f.event.title || 'this draft'}”?`}
        confirmLabel="Delete draft"
        onConfirm={() => exec(() => api.remove(f.event.id), 'Draft deleted', 'delete', f.event.id)}
        description={<>{err}Only drafts can be deleted. The event and its tiers are removed.</>}
      />
    );
  }

  return { run, dialogs, bulkPublish: api.publish };
}
