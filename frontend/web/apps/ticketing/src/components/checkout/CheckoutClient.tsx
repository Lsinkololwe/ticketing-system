'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { useRouter } from 'next/navigation';
import { resolveError, useBookingForReservation, useIdempotencyKey, usePayReservation, usePlatformRules, useReservation, useReserveTickets, type GraphQLLikeError } from '@pml.tickets/shared';
import { ConfirmDialog, EmptyState, ErrorState, Stepper, useSnackbar } from '@pml.tickets/shared/components/m3';
import { useBuyerAuth } from '@/lib/auth/session-context';
import { useEventPage } from '@pml.tickets/shared';
import { ticketCount, useCancelReservation, type ReservationRow } from '@pml.tickets/shared';
import { saveCartIntent } from '@/lib/identity/client';
import { writeHold } from '@/lib/hold';
import { useNow } from '@/lib/useNow';
import { fmtZmPhone, mmss, money } from '@/lib/format';
import { toE164 } from '@/hooks/usePayment';
import { IdentifyStep } from '@/components/identify/IdentifyStep';
import { LinkBtn } from '@/components/LinkBtn';
import { SiteShell } from '@/components/shell/SiteShell';
import { ApprovalStep } from './ApprovalStep';
import { ConfirmationStep } from './ConfirmationStep';
import { ContactStep } from './ContactStep';
import { EndState } from './EndState';
import { PayStep } from './PayStep';
import { ReserveStep } from './ReserveStep';
import { SummaryAside } from './SummaryAside';
import { CHECKOUT_STEPS, type ProviderCode } from './steps';

export interface CheckoutClientProps {
  eventId: string;
  /** Tickets parked before sign-in, applied once the buyer is identified. */
  initialQuantities: Record<string, number>;
  /** Reservation to resume, from `?r=`. */
  reservationId: string | null;
}

type Step = 1 | 2 | 3 | 4 | 5;

/** The five-step checkout: Reserve, Contact (sign-in), Pay, Approval (mobile money prompt), Confirmation. */
export function CheckoutClient({ eventId, initialQuantities, reservationId }: CheckoutClientProps) {
  const router = useRouter();
  const snack = useSnackbar();
  const auth = useBuyerAuth();
  const { event } = useEventPage(eventId);
  const { reserveTickets } = useReserveTickets();
  const { payReservation, loading: paying } = usePayReservation();
  const { cancel, loading: cancelling } = useCancelReservation();
  const [resId, setResId] = useState<string | null>(reservationId);
  const [step, setStep] = useState<Step | null>(null);
  const [provider, setProvider] = useState<ProviderCode | null>(null);
  const [number, setNumber] = useState<string | null>(null);
  const [declined, setDeclined] = useState<string | null>(null);
  const [reserveError, setReserveError] = useState<GraphQLLikeError | null>(null);
  const [releaseOpen, setReleaseOpen] = useState(false);
  const [released, setReleased] = useState(false);
  // Keyed by the event (no reservation exists yet), persisted so a reload mid-reservation reuses it.
  const [idemKey, regenerateIdem] = useIdempotencyKey(`reserve:${eventId}`);
  // Keyed by the reservation once it exists, persisted so a reload mid-payment reuses it.
  const [payIdemKey, regeneratePayIdem] = useIdempotencyKey(resId ? `pay:${resId}` : null);
  const started = useRef(false);

  const effective = step ?? 1;
  const { reservation: raw, error: loadError } = useReservation(resId, effective === 4 ? 2000 : 0);
  const reservation = raw as unknown as ReservationRow | null;
  const now = useNow(1000, Boolean(reservation));
  const { rules } = usePlatformRules();
  // The booking this reservation became: the confirmed one with its tickets, or the late payment that was refunded.
  const lateWindow = ((rules?.reservationGraceMinutes ?? 0) + 10) * 60_000;
  const lateWatch = reservation?.status === 'EXPIRED' && Boolean(reservation.paymentIntentId) && now - Date.parse(reservation.expiresAt) < lateWindow;
  const bookingQ = useBookingForReservation(resId, reservation?.status === 'CONFIRMED' || lateWatch);
  const booking = bookingQ.booking;

  // A signed-in buyer who arrives with parked tickets gets them reserved straight away.
  const hasParked = Object.keys(initialQuantities).length > 0;
  useEffect(() => {
    if (!auth.authenticated || resId || !hasParked || started.current) return;
    started.current = true;
    void (async () => {
      try {
        const res = await reserveTickets({
          eventId,
          selections: Object.entries(initialQuantities).map(([ticketTierId, quantity]) => ({ ticketTierId, quantity })),
          promoCode: null,
          idempotencyKey: idemKey,
          contactName: null,
          contactEmail: null,
          contactPhone: null,
        });
        const r = res.data?.reserveTickets;
        if (r) {
          writeHold({ reservationId: r.id, eventId, expiresAt: r.expiresAt });
          setResId(r.id);
          router.replace(`/events/${eventId}/book?r=${encodeURIComponent(r.id)}`);
        }
      } catch (e) {
        setReserveError(e as GraphQLLikeError);
      }
    })();
  }, [auth.authenticated, resId, hasParked, eventId, initialQuantities, reserveTickets, router, idemKey]);

  // Where a resumed reservation belongs: a payment already started means we are waiting for approval.
  useEffect(() => {
    if (!reservation || step !== null) return;
    if (reservation.status === 'CONFIRMED') setStep(5);
    else if (reservation.status === 'HELD') setStep(reservation.paymentIntentId ? 4 : 1);
  }, [reservation, step]);

  // The poll decides the outcome of an approval step.
  useEffect(() => {
    if (!reservation) return;
    if (reservation.status === 'CONFIRMED' && step !== 5) setStep(5);
    if (reservation.status !== 'HELD') writeHold(null);
  }, [reservation, step]);

  // Tickets arrive a moment after confirmation: refetch until all are visible. A payment that lands after the
  // hold lapsed is refunded by the platform; keep looking for that outcome while the expired page is open.
  const wanted = reservation ? ticketCount(reservation) : 0;
  const mine = useMemo(() => booking?.tickets ?? [], [booking]);
  const refundedLate = booking?.status === 'PAID_AFTER_EXPIRY_AUTO_REFUNDED';
  const waiting = (step === 5 && mine.length < wanted) || (lateWatch && !refundedLate);
  useEffect(() => {
    if (!waiting) return;
    const t = window.setInterval(() => void bookingQ.refetch(), 4000);
    return () => window.clearInterval(t);
  }, [waiting, bookingQ]);

  const pay = useCallback(
    async (local: string, prov: ProviderCode) => {
      if (!resId) return;
      setDeclined(null);
      try {
        await payReservation({ reservationId: resId, phoneNumber: toE164(local), idempotencyKey: payIdemKey });
        setProvider(prov);
        setNumber(fmtZmPhone(local.replace(/^0/, '')));
        setStep(4);
      } catch (e) {
        setDeclined(resolveError(e as GraphQLLikeError).message);
      }
    },
    [resId, payReservation, payIdemKey]
  );

  const resend = async () => {
    if (!resId || !number) return;
    try {
      // A deliberate new prompt, not a retry of one the guard already answered: a fresh key so
      // the provider is actually called again rather than replaying the first prompt's response.
      const fresh = regeneratePayIdem();
      await payReservation({
        reservationId: resId,
        phoneNumber: toE164(number.replace(/\D/g, '').replace(/^260/, '0')),
        idempotencyKey: fresh,
      });
      snack.show(`Payment prompt sent again to ${number}`);
    } catch (e) {
      snack.show({ message: resolveError(e as GraphQLLikeError).message, tone: 'error' });
    }
  };

  const release = async () => {
    if (!resId) return;
    try {
      await cancel(resId);
      writeHold(null);
      setReleased(true);
      setReleaseOpen(false);
      snack.show('Reservation released');
    } catch (e) {
      setReleaseOpen(false);
      snack.show({ message: resolveError(e as GraphQLLikeError).message, tone: 'error' });
    }
  };

  const again = <LinkBtn href={`/events/${eventId}`} variant="accent">Choose tickets again</LinkBtn>;
  const home = <LinkBtn href="/" variant="outlined">Back to events</LinkBtn>;
  const title = event?.title ?? 'your event';

  const frame = (children: React.ReactNode) => <SiteShell>{children}</SiteShell>;

  // ---- no reservation yet -------------------------------------------------
  if (!resId) {
    if (reserveError) {
      return frame(
        <div className="m3-site-wrap buyer-narrow">
          <ErrorState error={reserveError} onRetry={() => { started.current = false; setReserveError(null); regenerateIdem(); }} />
          <div className="m3-row buyer-center">{again}</div>
        </div>
      );
    }
    if (auth.authenticated && hasParked) {
      return frame(
        <div className="m3-site-wrap buyer-narrow" aria-busy="true" role="status">
          <p className="buyer-lead">Reserving your tickets…</p>
        </div>
      );
    }
    if (!auth.authenticated && hasParked) {
      return frame(
        <div className="m3-site-wrap">
          <h1 className="m3-page-title">Checkout</h1>
          <Stepper variant="underline" label="Checkout progress" steps={CHECKOUT_STEPS} current={1} />
          <div className="buyer-narrow">
            <section className="m3-panel m3-stack" aria-labelledby="who-title">
              <h2 className="m3-card__title" id="who-title">
                Where should we send your tickets?
              </h2>
              <p className="m3-muted">
                Verify your WhatsApp number or email. No password, and your tickets are saved while you do.
              </p>
              <IdentifyStep
                returnTo={`/events/${eventId}/book`}
                onBeforeRedirect={async () => {
                  await saveCartIntent(eventId, initialQuantities);
                }}
              />
            </section>
          </div>
        </div>
      );
    }
    return frame(
      <div className="m3-site-wrap">
        <EmptyState icon="ticket" title="Choose your tickets first" description="Pick the tickets you want on the event page, then come back to reserve them." action={again} />
      </div>
    );
  }

  // ---- reservation loading / failed to load ------------------------------
  if (!reservation) {
    return frame(
      <div className="m3-site-wrap buyer-narrow" aria-busy={!loadError} role="status">
        {loadError ? <ErrorState error={loadError as unknown as GraphQLLikeError} onRetry={() => router.refresh()} /> : <p className="buyer-lead">Loading your reservation…</p>}
      </div>
    );
  }

  // ---- terminal states -----------------------------------------------------
  const heldMs = Date.parse(reservation.expiresAt) - now;
  const expiredLocal = reservation.status === 'HELD' && heldMs <= 0 && !reservation.paymentIntentId && effective !== 4;
  if (released || reservation.status === 'RELEASED') {
    return frame(
      <EndState icon="check-circle" title="Reservation released" actions={<>{again}{home}</>}>
        Your tickets for <b>{title}</b> were released and you have not been charged. If you approve a payment prompt that is already on your phone, the money is refunded automatically.
      </EndState>
    );
  }
  if (refundedLate) {
    const hold = rules?.reservationHoldMinutes;
    return frame(
      <EndState icon="check-circle" tone="warn" title="Payment received after your hold ended" actions={<>{again}{home}</>}>
        Your payment of <b>{money(booking?.totalAmount ?? reservation.totalAmount)}</b> arrived after the {hold ? `${hold}-minute ` : ''}hold expired, so the tickets were no longer reserved. <b>We have automatically refunded the full amount</b> to the mobile money number that paid{number ? ` (${number})` : ''}. There is nothing more you need to do.
      </EndState>
    );
  }
  if (reservation.status === 'EXPIRED' || expiredLocal) {
    return frame(
      <EndState icon="clock" tone="warn" title="Your hold has expired" actions={<>{again}{home}</>}>
        The hold on your tickets for <b>{title}</b> has ended and they were released for other buyers. You have not been charged.
      </EndState>
    );
  }
  if (reservation.status === 'FAILED') {
    return frame(
      <EndState icon="error" tone="bad" title="We could not complete this booking" actions={<>{again}{home}</>}>
        {reservation.failureReason ?? 'The payment did not go through.'} If any money was taken it is refunded automatically.
      </EndState>
    );
  }

  const heldHere = reservation.status === 'HELD';
  const showHold = heldHere && effective < 4;
  const payingNow = effective === 4;

  const body =
    effective === 1 ? (
      <ReserveStep reservation={reservation} eventTitle={title} now={now} onContinue={() => setStep(2)} onRelease={() => setReleaseOpen(true)} />
    ) : effective === 2 ? (
      <ContactStep name={auth.user ? `${auth.user.givenName} ${auth.user.familyName}`.trim() : null} onBack={() => setStep(1)} onContinue={() => setStep(3)} />
    ) : effective === 3 ? (
      <PayStep total={reservation.totalAmount} declined={declined} busy={paying} onBack={() => setStep(2)} onPay={(l, p) => void pay(l, p)} />
    ) : effective === 4 ? (
      <ApprovalStep
        total={reservation.totalAmount}
        provider={provider ?? 'MTN'}
        number={number ?? 'your phone'}
        holdEnded={heldMs <= 0}
        resendBusy={paying}
        onResend={() => void resend()}
        onCancel={() => setReleaseOpen(true)}
      />
    ) : null;

  return frame(
    <div className="m3-site-wrap">
      <div className="buyer-chead">
        <h1 className="m3-page-title">Checkout</h1>
        {heldHere ? (
          <span className="buyer-hold">{payingNow && heldMs <= 0 ? 'Payment in progress' : <>Tickets held for <b>{mmss(heldMs)}</b></>}</span>
        ) : null}
      </div>
      <Stepper variant="underline" label="Checkout progress" steps={CHECKOUT_STEPS} current={effective - 1} />
      {effective === 5 ? (
        <ConfirmationStep
          reservationId={reservation.id}
          bookingNumber={booking?.bookingNumber ?? null}
          total={reservation.totalAmount}
          provider={provider}
          phone={number}
          event={event}
          tickets={mine}
          holder={auth.user ? `${auth.user.givenName} ${auth.user.familyName}`.trim() : null}
        />
      ) : (
        <div className="buyer-two">
          <div>{body}</div>
          <SummaryAside reservation={reservation} event={event} now={now} showHold={showHold} />
        </div>
      )}
      <ConfirmDialog
        open={releaseOpen}
        onClose={() => setReleaseOpen(false)}
        onConfirm={() => void release()}
        title="Release this reservation?"
        confirmLabel="Release reservation"
        cancelLabel="Go back"
        danger
        loading={cancelling}
        description={
          payingNow
            ? 'We will stop waiting for your payment and release the tickets. If you still approve the prompt on your phone, the money is refunded automatically.'
            : 'Your tickets will go back on sale straight away and you will have to reserve again. You have not been charged.'
        }
      />
    </div>
  );
}
