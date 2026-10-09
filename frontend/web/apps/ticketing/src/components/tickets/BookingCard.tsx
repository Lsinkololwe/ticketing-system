'use client';

import { Button, StatusPill, Switch } from '@pml.tickets/shared/components/m3';
import type { BookingGroup } from './group';
import { clock, fullDate, money, shortDate } from '@/lib/format';

export interface BookingCardProps {
  booking: BookingGroup;
  reminderOn: boolean;
  /** Lead time from the buyer's notification preferences; null while unknown. */
  reminderHours?: number | null;
  onReminder: (on: boolean) => void;
  onShowQr: (ticketId: string) => void;
  onTransfer: (ticketId: string) => void;
  onRefund: (ticketId: string) => void;
  onHelp: () => void;
  onViewRefund: () => void;
  onCancelTransfer: (ticketId: string) => void;
  onResend: (ticketId: string) => void;
  /** Who a pending transfer of this ticket is addressed to (masked), if known. */
  transferTo?: (ticketId: string) => string | null;
}

/** One purchase: event header, a row per ticket with its status and actions, reminder switch and help link. */
export function BookingCard({ booking: b, reminderOn, reminderHours, onReminder, onShowQr, onTransfer, onRefund, onHelp, onViewRefund, onCancelTransfer, onResend, transferTo }: BookingCardProps) {
  return (
    <article className="m3-panel buyer-bk" aria-label={`Booking for ${b.eventTitle}`}>
      <header className="buyer-bk__head">
        <div>
          <span className="m3-muted m3-mono">{b.bookingNumber}</span>
          <span className="m3-muted"> · Bought {b.purchasedAt ? shortDate(b.purchasedAt) : ''}</span>
          <h3 className="m3-card__title">{b.eventTitle}</h3>
          <div className="m3-muted">
            {b.eventDate ? `${fullDate(b.eventDate)} · ${clock(b.eventDate)}` : ''}
            {b.venue ? ` · ${b.venue}` : ''}
          </div>
        </div>
        <div className="buyer-bk__total">
          <b className="m3-num">{money(b.total)}</b>
          <span className="m3-muted">
            {b.tickets.length} ticket{b.tickets.length === 1 ? '' : 's'}
          </span>
        </div>
      </header>
      {b.autoRefunded ? (
        <p className="buyer-note" role="status">
          Your payment arrived after the hold on these tickets ended, so we refunded the full amount automatically. Nothing more is needed.
        </p>
      ) : null}
      <div className="buyer-bk__rows">
        {b.tickets.map((t) => (
          <div className="buyer-trow" key={t.id}>
            <div className="buyer-trow__main">
              <b>{t.ticketCategoryName ?? 'Ticket'}</b>
              <span className="m3-mono m3-muted">{t.ticketNumber}</span>
            </div>
            <StatusPill tone={t.status === 'ISSUED' && !t.transferPending ? 'info' : undefined} status={t.transferPending && t.status === 'ISSUED' ? 'TRANSFER_PENDING' : t.status} />
            <div className="buyer-trow__acts">
              {t.status === 'ISSUED' ? (
                <>
                  <Button size="sm" onClick={() => onShowQr(t.id)}>
                    Show QR
                  </Button>
                  <Button size="sm" onClick={() => onResend(t.id)}>
                    Resend ticket
                  </Button>
                  {b.upcoming && t.transferPending ? (
                    <Button size="sm" onClick={() => onCancelTransfer(t.id)}>
                      Cancel transfer
                    </Button>
                  ) : null}
                  {b.upcoming && !t.transferPending ? (
                    <>
                      <Button size="sm" onClick={() => onTransfer(t.id)}>
                        Transfer
                      </Button>
                      <Button size="sm" onClick={() => onRefund(t.id)}>
                        Request refund
                      </Button>
                    </>
                  ) : null}
                </>
              ) : null}
              {t.status === 'REFUND_PENDING' ? (
                <Button size="sm" onClick={onViewRefund}>
                  View refund
                </Button>
              ) : null}
            </div>
            {t.transferPending && t.status === 'ISSUED' ? (
              <p className="buyer-note" role="status">
                Transfer{transferTo?.(t.id) ? <> to <b>{transferTo(t.id)}</b></> : null} is pending until they accept it. Until then the ticket is still yours.
              </p>
            ) : null}
          </div>
        ))}
      </div>
      <footer className="buyer-bk__foot">
        {b.upcoming ? (
          <Switch label={reminderHours ? `Remind me ${reminderHours} hour${reminderHours === 1 ? '' : 's'} before` : 'Remind me before the event'} checked={reminderOn} onChange={(e) => onReminder(e.target.checked)} />
        ) : (
          <span className="m3-muted">This event has taken place</span>
        )}
        <Button variant="link" onClick={onHelp}>
          Need help with your booking?
        </Button>
      </footer>
    </article>
  );
}
