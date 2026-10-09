'use client';

import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import Link from 'next/link';
import { useRouter } from 'next/navigation';
import { resolveError, useReserveTickets, type GraphQLLikeError } from '@pml.tickets/shared';
import { z } from 'zod';
import { Button, EmptyState, ErrorState, Skeleton } from '@pml.tickets/shared/components/m3';
import { useBuyerAuth } from '@/lib/auth/session-context';
import { useDiscoverEvents } from '@pml.tickets/shared';
import { allSold, tierState, useEventPage, usePlatformRules, usePromoValidation, useUnlockTier, visibleTiers, type EventTierRow } from '@pml.tickets/shared';
import { policyView } from './policy';
import { saveCartIntent } from '@/lib/identity/client';
import { writeHold } from '@/lib/hold';
import { rememberViewed } from '@/lib/recent';
import { useNow } from '@/lib/useNow';
import { money } from '@/lib/format';
import { Form, TextFieldRHF, useZodForm } from '@pml.tickets/shared/forms';
import { SiteShell } from '@/components/shell/SiteShell';
import { EventCardView, isSellingFast } from './EventCardView';
import { EventHero } from './EventHero';
import { EventTabs } from './EventTabs';
import { OrderPanel, buildLines } from './OrderPanel';
import { TierList } from './TierList';
import { SectionHeader, EventGrid } from '@pml.tickets/shared/components/m3';

const promoSchema = z.object({ code: z.string().trim().min(1, 'Enter a promo code.') });
const accessSchema = z.object({ code: z.string().trim().min(1, 'Enter your access code.') });

function Related({ eventId, categoryId, category, city }: { eventId: string; categoryId: string | null; category: string | null; city: string | null }) {
  const { events } = useDiscoverEvents({ categoryId: categoryId ?? undefined }, 8, !categoryId);
  // No `hasAvailableTickets` filter exists (ET-CAT-003 R4): drop sold-out events here instead.
  const list = events.filter((e) => e.id !== eventId && !e.soldOut).slice(0, 4);
  if (!list.length) return null;
  return (
    <section className="m3-site-section" aria-labelledby="related-title">
      <SectionHeader title="You might also like" description={category && city ? `More events in ${category.toLowerCase()} and ${city}.` : undefined} />
      <EventGrid label="Similar events">
        {list.map((e) => (
          <EventCardView key={e.id} event={e} />
        ))}
      </EventGrid>
    </section>
  );
}

/** The event page: hero and facts, tier selection with codes, information tabs and the sticky order summary. */
export function EventClient({ id }: { id: string }) {
  const router = useRouter();
  const auth = useBuyerAuth();
  const { event, loading, error, refetch } = useEventPage(id);
  const { reserveTickets } = useReserveTickets();
  const promoApi = usePromoValidation();
  const unlockApi = useUnlockTier();
  const { rules } = usePlatformRules();
  const now = useNow(1000, Boolean(event));
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const promoForm = useZodForm(promoSchema, { defaultValues: { code: '' } });
  const accessForm = useZodForm(accessSchema, { defaultValues: { code: '' } });
  const [promo, setPromo] = useState<{ code: string; discount: number } | null>(null);
  const [promoMsg, setPromoMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [accessMsg, setAccessMsg] = useState<{ ok: boolean; text: string } | null>(null);
  const [unlocked, setUnlocked] = useState<EventTierRow[]>([]);
  const [busy, setBusy] = useState(false);
  const [reserveError, setReserveError] = useState<string | null>(null);
  const idem = useRef<string>('');

  useEffect(() => {
    if (event) rememberViewed(event);
  }, [event]);

  const tiers = useMemo(() => (event ? visibleTiers(event, unlocked) : []), [event, unlocked]);
  const unlockedIds = useMemo(() => new Set(unlocked.map((t) => t.id)), [unlocked]);
  const maxTotal = rules?.maxTicketsPerBooking;
  const lines = useMemo(() => buildLines(tiers, quantities, now), [tiers, quantities, now]);
  const subtotal = lines.reduce((a, l) => a + l.amount, 0);
  const count = lines.reduce((a, l) => a + l.qty, 0);
  const discount = promo ? Math.min(promo.discount, subtotal) : 0;

  const setQty = useCallback((t: EventTierRow, next: number) => {
    idem.current = '';
    setPromo(null);
    setPromoMsg(null);
    setQuantities((q) => ({ ...q, [t.id]: next }));
  }, []);

  const applyPromo = async ({ code: raw }: { code: string }) => {
    const code = raw.toUpperCase();
    if (!count || !event) return setPromoMsg({ ok: false, text: 'Choose your tickets first, then apply the promo code.' });
    const r = await promoApi.validate(code, event.id, subtotal);
    if (r.valid) {
      setPromo({ code, discount: r.discountAmount });
      setPromoMsg({ ok: true, text: `${code} applied: ${money(r.discountAmount)} off.` });
    } else {
      setPromo(null);
      setPromoMsg({ ok: false, text: r.errorMessage || "That promo code isn't valid for this event." });
    }
  };

  const unlockTier = async ({ code: raw }: { code: string }) => {
    if (!event) return;
    if (!auth.authenticated) return setAccessMsg({ ok: false, text: 'Sign in to unlock a ticket tier with an access code.' });
    const r = await unlockApi.unlock(event.id, raw.trim());
    if (r.ok) {
      setUnlocked((u) => (u.some((t) => t.id === r.tier.id) ? u : [...u, r.tier]));
      accessForm.reset({ code: '' });
      setAccessMsg({ ok: true, text: `Code accepted: the ${r.tier.name} tier is now unlocked.` });
    } else if (r.code === 'INVALID') setAccessMsg({ ok: false, text: "That access code isn't valid for this event." });
    else if (r.code === 'LOCKED_OUT') setAccessMsg({ ok: false, text: 'Too many wrong codes. Wait a few minutes before trying again.' });
    else setAccessMsg({ ok: false, text: 'We could not check that code. Try again.' });
  };

  const reserve = async () => {
    if (!event || !count || busy) return;
    setReserveError(null);
    setBusy(true);
    try {
      if (!auth.authenticated) {
        // Park the selection server-side; the checkout page asks who they are, then comes back with it.
        await saveCartIntent(event.id, quantities);
        router.push(`/events/${event.id}/book`);
        return;
      }
      if (!idem.current) idem.current = crypto.randomUUID();
      const res = await reserveTickets({
        eventId: event.id,
        selections: Object.entries(quantities).filter(([, q]) => q > 0).map(([ticketTierId, quantity]) => ({ ticketTierId, quantity })),
        promoCode: promo?.code ?? null,
        idempotencyKey: idem.current,
        contactName: null,
        contactEmail: null,
        contactPhone: null,
      });
      const r = res.data?.reserveTickets;
      if (r) {
        writeHold({ reservationId: r.id, eventId: event.id, expiresAt: r.expiresAt });
        router.push(`/events/${event.id}/book?r=${encodeURIComponent(r.id)}`);
      }
    } catch (e) {
      setReserveError(resolveError(e as GraphQLLikeError).message);
    } finally {
      setBusy(false);
    }
  };

  if (loading && !event) {
    return (
      <SiteShell>
        <div className="m3-site-wrap" aria-busy="true" aria-label="Loading event">
          <Skeleton shape="block" width="100%" />
        </div>
      </SiteShell>
    );
  }
  if (error && !event) {
    return (
      <SiteShell>
        <div className="m3-site-wrap">
          <ErrorState error={error as unknown as GraphQLLikeError} onRetry={() => void refetch()} />
        </div>
      </SiteShell>
    );
  }
  if (!event) {
    return (
      <SiteShell>
        <div className="m3-site-wrap">
          <EmptyState icon="ticket" title="We could not find that event" description="It may have ended or been removed." action={<Link className="m3-btn m3-state" data-variant="filled" href="/">Back to events</Link>} />
        </div>
      </SiteShell>
    );
  }

  const prices = tiers.filter((t) => ['ON', 'SOON'].includes(tierState(t, now))).map((t) => lines.find((l) => l.tier.id === t.id)?.unit ?? Number(t.price));
  const sold = event.soldOut || allSold(tiers, now);

  return (
    <SiteShell>
      <div className="m3-site-wrap">
        <Link className="buyer-back" href="/">
          ← All events
        </Link>
        <EventHero event={event} prices={prices} soldOut={sold} fast={isSellingFast(event)} now={now} policyName={policyView(rules, event.refundPolicy).name} />
        <div className="buyer-two">
          <div className="m3-stack">
            <section className="m3-panel" id="tiers" aria-labelledby="tiers-title">
              <div className="buyer-ph">
                <h2 className="m3-card__title" id="tiers-title">Choose your tickets</h2>
                <span className="m3-muted">Prices in Zambian Kwacha{maxTotal ? ` · up to ${maxTotal} tickets per booking` : ''}</span>
              </div>
              <TierList tiers={tiers} quantities={quantities} onChange={setQty} maxTotal={maxTotal} unlockedIds={unlockedIds} now={now} />
              <div className="buyer-codes">
                <Form form={accessForm} guardLeave={false} onSubmit={unlockTier}>
                  <div className="buyer-inl">
                    <TextFieldRHF name="code" label="Access code" autoComplete="off" />
                    <Button type="submit" loading={unlockApi.loading}>Unlock</Button>
                  </div>
                </Form>
                {accessMsg ? <p role="status" className={accessMsg.ok ? 'buyer-ok' : 'buyer-err'}>{accessMsg.text}</p> : null}
                <Form form={promoForm} guardLeave={false} onSubmit={applyPromo}>
                  <div className="buyer-inl">
                    <TextFieldRHF name="code" label="Promo code" autoComplete="off" />
                    <Button type="submit" loading={promoApi.loading}>{promo ? 'Update' : 'Apply'}</Button>
                  </div>
                </Form>
                <p role="status" className={promoMsg?.ok ? 'buyer-ok' : 'buyer-err'}>
                  {promoMsg?.text}
                </p>
                {promo ? (
                  <Button
                    variant="text"
                    onClick={() => {
                      setPromo(null);
                      promoForm.reset({ code: '' });
                      setPromoMsg(null);
                    }}
                  >
                    Remove promo code
                  </Button>
                ) : null}
              </div>
            </section>
            <section className="m3-panel">
              <EventTabs event={event} tiers={tiers} now={now} rules={rules} />
            </section>
          </div>
          <OrderPanel
            event={event}
            lines={lines}
            discount={discount}
            promoCode={promo?.code ?? null}
            busy={busy}
            signedIn={auth.authenticated}
            onReserve={() => void reserve()}
            error={reserveError}
            holdMinutes={rules?.reservationHoldMinutes ?? null}
          />
        </div>
        <Related eventId={event.id} categoryId={event.category?.id ?? null} category={event.category?.name ?? null} city={event.cityName ?? null} />
        <div className="buyer-mbar" data-on={count > 0 ? 'true' : undefined} id="mbar">
          <div>
            <b className="m3-num">{money(subtotal - discount)}</b>
            <div className="m3-muted">
              {count} ticket{count === 1 ? '' : 's'}
              {discount ? ' · promo applied' : ''}
            </div>
          </div>
          <Button variant="accent" loading={busy} onClick={() => void reserve()}>
            Reserve tickets
          </Button>
        </div>
      </div>
    </SiteShell>
  );
}
