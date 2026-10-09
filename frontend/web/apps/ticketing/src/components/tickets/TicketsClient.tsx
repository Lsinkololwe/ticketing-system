'use client';

import { useMemo, useState } from 'react';
import { resolveServerError, useCancelRefundRequest, useMyBookings, useMyTicketTransfers, useNotificationPrefs, useResendTicket, useTicketTransferActions, type BuyerBookingTicket as MyTicketRow } from '@pml.tickets/shared';
import { Button, ConfirmDialog, Dialog, EmptyState, ErrorState, SectionHeader, Skeleton, Tabs, useSnackbar, type TabItem } from '@pml.tickets/shared/components/m3';
import type { GraphQLLikeError } from '@pml.tickets/shared';
import { useMyRefunds } from '@pml.tickets/shared';
import { useReminders } from '@pml.tickets/shared';
import { LinkBtn } from '@/components/LinkBtn';
import { SiteShell } from '@/components/shell/SiteShell';
import { HelpContent } from '@/components/help/HelpContent';
import { BookingCard } from './BookingCard';
import { RefundDialog } from './RefundDialog';
import { RefundCard } from './RefundsList';
import { TicketDialog } from './TicketDialog';
import { TransferDialog } from './TransferDialog';
import { IncomingTransferCard } from './IncomingTransfers';
import { groupBookings, splitBookings } from './group';

const PAGE = 3;
type Tab = 'up' | 'past' | 'refunds';

/** My tickets: Upcoming / Past / Refunds, bookings with their tickets, QR, transfer, refund and reminder actions. */
export function TicketsClient({ accountId, holder }: { accountId: string; holder: string | null }) {
  const snack = useSnackbar();
  const { bookings, loading, error, refetch } = useMyBookings();
  const outgoing = useMyTicketTransfers({ direction: 'OUTGOING', status: 'PENDING' });
  const incoming = useMyTicketTransfers({ direction: 'INCOMING', status: 'PENDING' });
  const transferActions = useTicketTransferActions();
  const resendApi = useResendTicket();
  const cancelRefund = useCancelRefundRequest();
  const [cancelTransfer, setCancelTransfer] = useState<MyTicketRow | null>(null);
  const [cancelRf, setCancelRf] = useState<string | null>(null);
  const refundsQ = useMyRefunds();
  const reminders = useReminders();
  const { prefs } = useNotificationPrefs();
  const [tab, setTab] = useState<Tab>('up');
  const [shown, setShown] = useState(PAGE);
  const [qr, setQr] = useState<MyTicketRow | null>(null);
  const [transfer, setTransfer] = useState<MyTicketRow | null>(null);
  const [refund, setRefund] = useState<MyTicketRow | null>(null);
  const [help, setHelp] = useState(false);

  const tickets = useMemo(() => bookings.flatMap((b) => b.tickets), [bookings]);
  const groups = useMemo(() => groupBookings(bookings), [bookings]);
  const { upcoming, past } = useMemo(() => splitBookings(groups), [groups]);
  const byId = useMemo(() => new Map(tickets.map((t) => [t.id, t])), [tickets]);
  const titleOf = (eventId: string) => tickets.find((t) => t.eventId === eventId)?.eventTitle ?? null;
  const refunds = refundsQ.refunds;

  const list = tab === 'up' ? upcoming : tab === 'past' ? past : refunds;
  const visible = list.slice(0, shown);
  const tabs: TabItem[] = [
    { id: 'up', label: <>Upcoming <span className="buyer-cnt">{upcoming.length}</span></> },
    { id: 'past', label: <>Past <span className="buyer-cnt">{past.length}</span></> },
    { id: 'refunds', label: <>Refunds <span className="buyer-cnt">{refunds.length}</span></> },
  ];

  const toggleReminder = async (ticketIds: string[], eventDate: string | null, on: boolean) => {
    try {
      if (on && eventDate) await Promise.all(ticketIds.map((id) => reminders.setFor(id, eventDate)));
      else await Promise.all(reminders.reminders.filter((r) => ticketIds.includes(r.ticketId)).map((r) => reminders.cancelFor(r.id)));
      snack.show(on ? 'Reminder set' : 'Reminder removed');
    } catch {
      snack.show({ message: 'We could not update your reminder. Try again.', tone: 'error' });
    }
  };

  const resend = async (ticketId: string) => {
    try {
      const r = await resendApi.resend(ticketId);
      snack.show(
        r.status === 'NO_VERIFIED_CONTACT'
          ? { message: 'You have no verified contact to send it to. Add one in your profile.', tone: 'error' }
          : r.status === 'DUPLICATE'
            ? 'That ticket was just sent. Check your messages.'
            : `Ticket sent${r.destination ? ` to ${r.destination}` : ''}`
      );
    } catch (e) {
      snack.show({ message: resolveServerError(e).message, tone: 'error' });
    }
  };
  const confirmCancelTransfer = async () => {
    const t = cancelTransfer;
    if (!t) return;
    const offer = outgoing.transfers.find((x) => x.ticketId === t.id);
    setCancelTransfer(null);
    try {
      if (offer) await transferActions.cancel(offer.id);
      snack.show('Transfer cancelled');
    } catch (e) {
      snack.show({ message: resolveServerError(e).message, tone: 'error' });
    }
  };
  const confirmCancelRefund = async () => {
    const id = cancelRf;
    setCancelRf(null);
    if (!id) return;
    try {
      await cancelRefund.cancel(id, 'Changed my mind');
      snack.show('Refund request cancelled');
    } catch (e) {
      snack.show({ message: resolveServerError(e).message, tone: 'error' });
    }
  };

  let body: React.ReactNode;
  if (loading && !tickets.length && tab !== 'refunds') {
    body = (
      <div aria-busy="true" aria-label="Loading your tickets" className="m3-stack">
        <Skeleton shape="block" width="100%" />
        <Skeleton shape="block" width="100%" />
      </div>
    );
  } else if (error && !tickets.length && tab !== 'refunds') {
    body = <ErrorState error={error as unknown as GraphQLLikeError} onRetry={() => void refetch()} />;
  } else if (!visible.length) {
    body = (
      <EmptyState
        icon="ticket"
        title={tab === 'up' ? 'No upcoming tickets' : tab === 'past' ? 'No past tickets' : 'No refund requests'}
        description={tab === 'up' ? 'When you buy tickets they appear here, grouped by booking.' : tab === 'past' ? 'Tickets for events that have taken place appear here.' : 'Refund requests you make appear here with their progress.'}
        action={tab === 'up' ? <LinkBtn href="/" variant="filled">Find an event</LinkBtn> : undefined}
      />
    );
  } else if (tab === 'refunds') {
    body = <div className="m3-stack">{refunds.slice(0, shown).map((r) => <RefundCard key={r.id} refund={r} eventTitle={titleOf(r.eventId)} onCancel={() => setCancelRf(r.id)} />)}</div>;
  } else {
    body = (
      <div className="m3-stack">
        {(visible as typeof upcoming).map((b) => (
          <BookingCard
            key={b.key}
            booking={b}
            reminderHours={prefs?.reminderHoursBefore ?? null}
            reminderOn={b.tickets.every((t) => reminders.reminders.some((r) => r.ticketId === t.id))}
            onReminder={(on) => void toggleReminder(b.tickets.map((t) => t.id), b.eventDate, on)}
            onShowQr={(id) => setQr(byId.get(id) ?? null)}
            onTransfer={(id) => setTransfer(byId.get(id) ?? null)}
            onRefund={(id) => setRefund(byId.get(id) ?? null)}
            onHelp={() => setHelp(true)}
            onViewRefund={() => { setTab('refunds'); setShown(PAGE); }}
            onCancelTransfer={(id) => setCancelTransfer(byId.get(id) ?? null)}
            onResend={(id) => void resend(id)}
            transferTo={(id) => outgoing.transfers.find((x) => x.ticketId === id)?.toDisplayName ?? outgoing.transfers.find((x) => x.ticketId === id)?.recipientMasked ?? null}
          />
        ))}
      </div>
    );
  }

  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-page">
        <SectionHeader
          level={1}
          eyebrow="Your account"
          title="My tickets"
          actions={<Button variant="link" onClick={() => setHelp(true)}>Need help with your booking?</Button>}
        />
        {incoming.transfers.length ? (
          <section className="m3-stack" aria-label="Tickets offered to you">
            {incoming.transfers.map((t) => (
              <IncomingTransferCard key={t.id} transfer={t} />
            ))}
          </section>
        ) : null}
        <Tabs label="My tickets" variant="segmented" tabs={tabs} value={tab} onChange={(id) => { setTab(id as Tab); setShown(PAGE); }}>
          {() => (
            <>
              {body}
              {list.length > shown ? (
                <div className="buyer-more">
                  <span className="m3-muted">Showing {visible.length} of {list.length}</span>
                  <Button onClick={() => setShown((n) => n + PAGE)}>Load more</Button>
                </div>
              ) : null}
            </>
          )}
        </Tabs>
      </div>
      <TicketDialog ticket={qr} holder={holder} onClose={() => setQr(null)} />
      <TransferDialog
        ticket={transfer}
        onClose={() => setTransfer(null)}
        onDone={() => {
          setTransfer(null);
          snack.show('Transfer sent. It stays pending until they accept.');
        }}
      />
      <ConfirmDialog
        open={cancelTransfer !== null}
        onClose={() => setCancelTransfer(null)}
        onConfirm={() => void confirmCancelTransfer()}
        title="Cancel this transfer?"
        description={cancelTransfer ? `The transfer of ${cancelTransfer.ticketNumber} will be cancelled and the ticket stays with you.` : ''}
        confirmLabel="Cancel transfer"
        cancelLabel="Keep transfer"
        danger
      />
      <ConfirmDialog
        open={cancelRf !== null}
        onClose={() => setCancelRf(null)}
        onConfirm={() => void confirmCancelRefund()}
        title="Cancel this refund request?"
        description="Your ticket will go back to normal and you keep your place at the event."
        confirmLabel="Cancel request"
        cancelLabel="Keep request"
        danger
      />
      <RefundDialog
        ticket={refund}
        buyerId={accountId}
        onClose={() => setRefund(null)}
        onDone={() => {
          setRefund(null);
          setTab('refunds');
          setShown(PAGE);
          void refetch();
          snack.show('Refund requested. We will update you here.');
        }}
      />
      <Dialog open={help} onClose={() => setHelp(false)} title="Need help with your booking?" actions={<Button variant="text" onClick={() => setHelp(false)}>Close</Button>}>
        <HelpContent />
      </Dialog>
    </SiteShell>
  );
}
