'use client';

import React, { useEffect, useMemo, useRef, useState } from 'react';
import { useRouter, useParams } from 'next/navigation';
import {
  Box,
  Flex,
  Text,
  Heading,
  Button,
  TextField,
  Spinner,
  Badge,
  Grid,
  Container,
  Card,
  Separator,
  IconButton,
} from '@radix-ui/themes';
import {
  Calendar,
  MapPin,
  Check,
  SmartphoneDevice,
  NavArrowLeft,
  Minus,
  Plus,
  Clock,
} from 'iconoir-react';
import {
  useEvent,
  useAuth,
  useReserveTickets,
  useCompleteReservation,
  type TicketTier,
} from '@pml.tickets/shared';
import { ZambianMobileProvider } from '@/types/payment';
import {
  MOBILE_PROVIDERS,
  MOBILE_PROVIDER_LIST,
  validatePhoneNumber,
  detectProvider,
  toE164,
} from '@/hooks/usePayment';
import { Money, Toast } from '@/components/ui';
import { formatKwacha } from '@/lib/currency';

type CheckoutStage = 'select' | 'pay' | 'prompt' | 'done';

/** MM:SS from a future ISO timestamp. */
function useCountdown(expiresAt: string | null): number {
  const [remaining, setRemaining] = useState(0);
  useEffect(() => {
    if (!expiresAt) return;
    const tick = () => {
      const secs = Math.max(0, Math.floor((new Date(expiresAt).getTime() - Date.now()) / 1000));
      setRemaining(secs);
    };
    tick();
    const t = setInterval(tick, 1000);
    return () => clearInterval(t);
  }, [expiresAt]);
  return remaining;
}

function formatMMSS(totalSeconds: number): string {
  const m = Math.floor(totalSeconds / 60);
  const s = totalSeconds % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

const TicketBookingPage: React.FC = () => {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { user, authenticated, loading: authLoading } = useAuth();

  const { event, loading: eventLoading, error: eventError } = useEvent(id);
  const { reserveTickets, loading: reserving, error: reserveError } = useReserveTickets();
  const {
    completeReservation,
    loading: completing,
    error: completeError,
  } = useCompleteReservation();

  const [stage, setStage] = useState<CheckoutStage>('select');
  const [quantities, setQuantities] = useState<Record<string, number>>({});
  const [reservationId, setReservationId] = useState<string | null>(null);
  const [expiresAt, setExpiresAt] = useState<string | null>(null);
  const [provider, setProvider] = useState<ZambianMobileProvider>(ZambianMobileProvider.MTN);
  const [phone, setPhone] = useState('');
  const [ticketCount, setTicketCount] = useState(0);

  const remaining = useCountdown(expiresAt);
  const holdExpired = stage !== 'select' && stage !== 'done' && expiresAt !== null && remaining === 0;

  // Prefill phone from the signed-in profile.
  const seededPhone = useRef(false);
  useEffect(() => {
    if (!seededPhone.current && user?.phoneNumber) {
      setPhone(user.phoneNumber.replace('+260', '0'));
      seededPhone.current = true;
    }
  }, [user?.phoneNumber]);

  // Auth gate (defence-in-depth; the purchase mutation is also server-authorised).
  useEffect(() => {
    if (!authLoading && !authenticated) router.push('/auth');
  }, [authLoading, authenticated, router]);

  const tiers = useMemo(
    () => (event?.ticketTiers ?? []).filter((t: TicketTier) => t.isActive && !t.isHidden),
    [event?.ticketTiers]
  );

  const selections = useMemo(
    () =>
      Object.entries(quantities)
        .filter(([, qty]) => qty > 0)
        .map(([ticketTierId, quantity]) => ({ ticketTierId, quantity })),
    [quantities]
  );

  const subtotal = useMemo(() => {
    return tiers.reduce((sum: number, tier: TicketTier) => {
      const qty = quantities[tier.id] ?? 0;
      return sum + Number(tier.price) * qty;
    }, 0);
  }, [tiers, quantities]);

  const totalTickets = selections.reduce((n, s) => n + s.quantity, 0);

  const setQty = (tier: TicketTier, next: number) => {
    const max = Math.min(tier.availableQuantity, tier.maxPerOrder ?? tier.availableQuantity);
    const clamped = Math.max(0, Math.min(next, max));
    setQuantities((prev) => ({ ...prev, [tier.id]: clamped }));
  };

  const phoneValid = validatePhoneNumber(phone, provider);

  // Auto-select provider from the typed prefix.
  useEffect(() => {
    const detected = detectProvider(phone);
    if (detected) setProvider(detected);
  }, [phone]);

  const handleReserve = async () => {
    if (!event || selections.length === 0) return;
    const res = await reserveTickets({ eventId: event.id, selections, promoCode: null });
    const reservation = res.data?.reserveTickets;
    if (reservation) {
      setReservationId(reservation.id);
      setExpiresAt(reservation.expiresAt);
      setStage('pay');
    }
  };

  const handlePay = async () => {
    if (!reservationId || !phoneValid) return;
    setStage('prompt');
    try {
      const res = await completeReservation({
        reservationId,
        paymentMethod: 'MOBILE_MONEY',
        phoneNumber: toE164(phone),
        promoCode: null,
      });
      const tickets = res.data?.completeReservation ?? [];
      if (tickets.length > 0) {
        setTicketCount(tickets.length);
        setStage('done');
      } else {
        setStage('pay');
      }
    } catch {
      setStage('pay');
    }
  };

  // ---- Loading / error / not-found ----------------------------------------

  if (eventLoading || authLoading) {
    return (
      <Flex style={{ minHeight: '100vh', background: 'var(--gray-2)' }} align="center" justify="center">
        <Flex direction="column" align="center" gap="3">
          <Spinner size="3" />
          <Text size="3" color="gray">Loading checkout…</Text>
        </Flex>
      </Flex>
    );
  }

  if (eventError || !event) {
    return (
      <Flex style={{ minHeight: '100vh', background: 'var(--gray-2)' }} align="center" justify="center" p="4">
        <Toast
          variant="error"
          title="Event unavailable"
          description="This event can't be booked right now. Head back to browse what else is on."
        />
      </Flex>
    );
  }

  const eventDate = new Date(event.eventDateTime);

  return (
    <Box style={{ minHeight: '100vh', background: 'var(--gray-2)', paddingBottom: 96 }}>
      <Container size="2" py="6">
        {/* Back link */}
        <Button
          variant="ghost"
          color="gray"
          size="2"
          mb="4"
          onClick={() => router.push(`/events/${event.id}`)}
          data-testid="book-back"
        >
          <NavArrowLeft style={{ width: '1rem', height: '1rem' }} />
          Back to event
        </Button>

        {/* Event summary header */}
        <Card size="3" mb="5" style={{ borderRadius: 'var(--card-radius-bento)' }}>
          <Flex gap="4" align="center">
            {/* Small accent chip — the one sanctioned gradient. */}
            <Flex
              align="center"
              justify="center"
              className="ds-accent-chip"
              style={{
                width: 56,
                height: 56,
                borderRadius: 'var(--card-radius-bento)',
                flexShrink: 0,
              }}
            >
              <Calendar style={{ width: '1.5rem', height: '1.5rem' }} />
            </Flex>
            <Box style={{ minWidth: 0 }}>
              <Heading size="5" className="font-display" style={{ marginBottom: 4 }}>
                {event.title}
              </Heading>
              <Flex align="center" gap="3" wrap="wrap">
                <Flex align="center" gap="1">
                  <Calendar style={{ width: '0.9rem', height: '0.9rem', color: 'var(--gray-9)' }} />
                  <Text size="2" color="gray" className="ds-amount">
                    {eventDate.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}
                  </Text>
                </Flex>
                {(event.locationName || event.cityName) && (
                  <Flex align="center" gap="1">
                    <MapPin style={{ width: '0.9rem', height: '0.9rem', color: 'var(--gray-9)' }} />
                    <Text size="2" color="gray">
                      {[event.locationName, event.cityName].filter(Boolean).join(', ')}
                    </Text>
                  </Flex>
                )}
              </Flex>
            </Box>
          </Flex>
        </Card>

        {/* Hold timer (once reserved) */}
        {(stage === 'pay' || stage === 'prompt') && expiresAt && (
          <Flex
            align="center"
            justify="between"
            mb="4"
            px="4"
            py="3"
            style={{
              borderRadius: 'var(--radius-5)',
              background: holdExpired ? 'var(--status-danger-a3)' : 'var(--status-warning-a3)',
              border: `1px solid ${holdExpired ? 'var(--red-a6)' : 'var(--amber-a6)'}`,
            }}
          >
            <Flex align="center" gap="2">
              <Clock
                style={{
                  width: '1rem',
                  height: '1rem',
                  color: holdExpired ? 'var(--status-danger-11)' : 'var(--status-warning-11)',
                }}
              />
              <Text
                size="2"
                weight="medium"
                style={{ color: holdExpired ? 'var(--status-danger-11)' : 'var(--status-warning-11)' }}
              >
                {holdExpired ? 'Your hold expired' : 'Tickets held for you'}
              </Text>
            </Flex>
            {!holdExpired && (
              <Text
                size="5"
                weight="bold"
                className="ds-amount"
                style={{ color: 'var(--status-warning-11)' }}
              >
                {formatMMSS(remaining)}
              </Text>
            )}
          </Flex>
        )}

        {holdExpired && (
          <Box mb="4">
            <Toast
              variant="warning"
              title="Your hold ended"
              description="The 10-minute hold ran out before payment went through. Pick your tickets again to get a fresh hold."
            />
            <Box mt="2">
              <Button
                variant="soft"
                size="2"
                onClick={() => {
                  setStage('select');
                  setReservationId(null);
                  setExpiresAt(null);
                }}
                data-testid="hold-restart"
              >
                Start over
              </Button>
            </Box>
          </Box>
        )}

        {/* ---- STAGE: SELECT ---- */}
        {stage === 'select' && (
          <>
            <Heading size="5" mb="3" className="font-display">Choose your tickets</Heading>
            <Flex direction="column" gap="3" mb="5">
              {tiers.length === 0 && (
                <Text size="2" color="gray">No tickets are on sale for this event yet.</Text>
              )}
              {tiers.map((tier: TicketTier) => {
                const qty = quantities[tier.id] ?? 0;
                const soldOut = tier.availableQuantity <= 0;
                const lowStock = tier.availableQuantity > 0 && tier.availableQuantity < 20;
                const maxPer = Math.min(tier.availableQuantity, tier.maxPerOrder ?? tier.availableQuantity);
                return (
                  <Card
                    key={tier.id}
                    style={{
                      borderRadius: 'var(--card-radius-bento)',
                      border: qty > 0 ? '2px solid var(--accent-8)' : 'var(--hairline)',
                      background: qty > 0 ? 'var(--accent-a2)' : undefined,
                      opacity: soldOut ? 0.6 : 1,
                    }}
                  >
                    <Flex justify="between" align="center" gap="3">
                      <Box style={{ minWidth: 0 }}>
                        <Flex align="center" gap="2" mb="1" wrap="wrap">
                          <Text size="3" weight="bold">{tier.name}</Text>
                          {tier.earlyBirdPrice != null && (
                            <Badge
                              radius="full"
                              style={{ background: 'var(--color-highlight-surface)', color: 'var(--color-highlight-text)' }}
                            >
                              Early bird
                            </Badge>
                          )}
                          {lowStock && (
                            <Badge
                              radius="full"
                              style={{
                                background: 'var(--status-warning-a3)',
                                color: 'var(--status-warning-11)',
                              }}
                            >
                              Only {tier.availableQuantity} left
                            </Badge>
                          )}
                          {soldOut && (
                            <Badge radius="full" color="gray">Sold out</Badge>
                          )}
                        </Flex>
                        {tier.description && (
                          <Text as="p" size="2" color="gray" style={{ marginBottom: 6 }}>
                            {tier.description}
                          </Text>
                        )}
                        <Money amount={Number(tier.price)} tone="money" size="4" />
                      </Box>

                      {/* Stepper */}
                      <Flex align="center" gap="2" style={{ flexShrink: 0 }}>
                        <IconButton
                          variant="outline"
                          color="gray"
                          radius="full"
                          size="2"
                          disabled={qty <= 0}
                          aria-label={`Remove one ${tier.name}`}
                          onClick={() => setQty(tier, qty - 1)}
                          data-testid={`tier-minus-${tier.code}`}
                        >
                          <Minus style={{ width: '1rem', height: '1rem' }} />
                        </IconButton>
                        <Text size="4" weight="bold" className="ds-amount" style={{ minWidth: 24, textAlign: 'center' }}>
                          {qty}
                        </Text>
                        <IconButton
                          variant="solid"
                          radius="full"
                          size="2"
                          disabled={soldOut || qty >= maxPer}
                          aria-label={`Add one ${tier.name}`}
                          onClick={() => setQty(tier, qty + 1)}
                          data-testid={`tier-plus-${tier.code}`}
                        >
                          <Plus style={{ width: '1rem', height: '1rem' }} />
                        </IconButton>
                      </Flex>
                    </Flex>
                  </Card>
                );
              })}
            </Flex>

            {reserveError && (
              <Box mb="4">
                <Toast
                  variant="error"
                  title="Those tickets couldn't be held"
                  description="They may have just sold out. Adjust your selection and try again."
                />
              </Box>
            )}
          </>
        )}

        {/* ---- STAGE: PAY ---- */}
        {stage === 'pay' && (
          <>
            {/* Order summary */}
            <Card size="3" mb="5" style={{ borderRadius: 'var(--card-radius-bento)' }}>
              <Heading size="3" mb="3" className="font-display">Order summary</Heading>
              <Flex direction="column" gap="2">
                {tiers
                  .filter((t: TicketTier) => (quantities[t.id] ?? 0) > 0)
                  .map((t: TicketTier) => (
                    <Flex key={t.id} justify="between" align="center">
                      <Text size="2">{quantities[t.id]} × {t.name}</Text>
                      <Money amount={Number(t.price) * (quantities[t.id] ?? 0)} weight="regular" size="2" />
                    </Flex>
                  ))}
                <Separator size="4" my="1" />
                <Flex justify="between" align="center">
                  <Text size="3" weight="bold">Total</Text>
                  <Money amount={subtotal} decimals tone="money" size="5" />
                </Flex>
              </Flex>
              <Text size="1" color="gray" mt="2" style={{ display: 'block' }}>
                No booking fees — the price you see is the price you pay.
              </Text>
            </Card>

            {/* Mobile money — always-visible providers */}
            <Box mb="3">
              <Text className="ds-label" size="1" style={{ display: 'block', marginBottom: 8 }}>
                Pay with mobile money
              </Text>
              {/* All three providers are always visible — never behind a
                  "more options" disclosure (spec §8). */}
              <Grid columns="3" gap="2" mb="4" role="radiogroup" aria-label="Mobile money provider">
                {MOBILE_PROVIDER_LIST.map((p) => {
                  const info = MOBILE_PROVIDERS[p];
                  const selected = provider === p;
                  return (
                    <button
                      key={p}
                      type="button"
                      role="radio"
                      aria-checked={selected}
                      onClick={() => setProvider(p)}
                      data-testid={`momo-${info.shortName.toLowerCase()}`}
                      style={{
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        gap: 6,
                        padding: '10px 8px',
                        borderRadius: 'var(--radius-4)',
                        cursor: 'pointer',
                        background: selected ? 'var(--accent-a3)' : 'var(--color-surface)',
                        border: selected ? '2px solid var(--accent-8)' : 'var(--hairline)',
                        color: selected ? 'var(--accent-11)' : 'var(--gray-11)',
                        fontWeight: 'var(--weight-semibold)',
                        fontSize: 'var(--text-2-size)',
                        transition: 'background var(--transition-fast) var(--ease-standard)',
                      }}
                    >
                      <span
                        className="ds-momo-dot"
                        style={{ background: info.colorVar }}
                        aria-hidden="true"
                      />
                      {info.shortName}
                    </button>
                  );
                })}
              </Grid>

              <Text as="label" size="1" className="ds-label" mb="2" style={{ display: 'block' }}>
                Mobile money number
              </Text>
              <TextField.Root
                placeholder="0961234567"
                value={phone}
                onChange={(e) => setPhone(e.target.value)}
                size="3"
                type="tel"
                inputMode="numeric"
                aria-invalid={Boolean(phone) && !phoneValid}
                data-testid="momo-phone"
                style={{ fontFamily: 'var(--font-mono)' }}
              />
              {phone && !phoneValid && (
                <Text
                  size="1"
                  mt="1"
                  role="alert"
                  style={{ color: 'var(--status-danger-11)', display: 'block' }}
                >
                  That isn&apos;t a {MOBILE_PROVIDERS[provider].name} number. Check the
                  digits and try again.
                </Text>
              )}
              {phoneValid && (
                <Text
                  size="1"
                  mt="1"
                  style={{ color: 'var(--color-money-text)', display: 'block' }}
                >
                  {MOBILE_PROVIDERS[provider].name} number recognised
                </Text>
              )}
            </Box>

            {completeError && (
              <Box mb="3">
                <Toast
                  variant="error"
                  title="Payment didn't start"
                  description="We couldn't reach your mobile money provider. Check the number and send the prompt again."
                />
              </Box>
            )}
          </>
        )}

        {/* ---- STAGE: PROMPT (awaiting STK push) ---- */}
        {stage === 'prompt' && (
          <Flex
            direction="column"
            align="center"
            gap="3"
            py="7"
            px="4"
            style={{
              borderRadius: 'var(--card-radius-bento)',
              background: 'var(--color-money-surface)',
              border: '1px solid var(--jade-a6)',
              textAlign: 'center',
            }}
          >
            {/* momo-pulse — the "we're waiting on your handset" beat. */}
            <Flex
              align="center"
              justify="center"
              className="momo-waiting"
              style={{
                width: 56,
                height: 56,
                borderRadius: '50%',
                background: 'var(--color-money)',
                color: 'var(--gray-1)',
              }}
            >
              <SmartphoneDevice style={{ width: '1.6rem', height: '1.6rem' }} />
            </Flex>
            <Heading size="5" className="font-display">Check your phone</Heading>
            <Text size="3" color="gray">
              Enter your {MOBILE_PROVIDERS[provider].name} PIN to approve{' '}
              <Text as="span" className="ds-amount" weight="bold">{formatKwacha(subtotal, { decimals: true })}</Text>.
            </Text>
            <Flex align="center" gap="2" mt="2">
              <Spinner size="2" />
              <Text size="2" color="gray">Waiting for your approval…</Text>
            </Flex>
          </Flex>
        )}

        {/* ---- STAGE: DONE ---- */}
        {stage === 'done' && (
          <Flex direction="column" align="center" gap="4" py="6" style={{ textAlign: 'center' }}>
            <Flex
              align="center"
              justify="center"
              style={{
                width: 64,
                height: 64,
                borderRadius: '50%',
                background: 'var(--color-money)',
                color: 'var(--gray-1)',
              }}
            >
              <Check style={{ width: '2rem', height: '2rem' }} />
            </Flex>
            <Heading size="6" className="font-display">You&apos;re going</Heading>
            <Text size="3" color="gray" style={{ maxWidth: 380 }}>
              {ticketCount} {ticketCount === 1 ? 'ticket' : 'tickets'} for {event.title} confirmed and
              paid. We&apos;ve sent them to your phone — they work offline at the gate.
            </Text>
            <Flex gap="3" mt="2">
              <Button size="3" variant="soft" onClick={() => router.push('/')} data-testid="done-browse">
                Browse more events
              </Button>
              <Button size="3" onClick={() => router.push('/my-tickets')} data-testid="done-tickets">
                View my tickets
              </Button>
            </Flex>
          </Flex>
        )}
      </Container>

      {/* Sticky checkout bar */}
      {(stage === 'select' || stage === 'pay') && (
        <Box
          style={{
            position: 'fixed',
            bottom: 0,
            left: 0,
            right: 0,
            background: 'var(--color-panel-solid)',
            borderTop: '1px solid var(--gray-a5)',
            boxShadow: 'var(--shadow-4)',
            zIndex: 40,
          }}
          py="3"
          px="4"
        >
          <Container size="2">
            <Flex align="center" justify="between" gap="4">
              <Box>
                <Text size="1" color="gray" style={{ display: 'block' }}>
                  {totalTickets} {totalTickets === 1 ? 'ticket' : 'tickets'}
                </Text>
                <Money amount={subtotal} decimals tone="money" size="6" />
              </Box>
              {stage === 'select' ? (
                <Button
                  size="4"
                  color="jade"
                  disabled={selections.length === 0 || reserving}
                  onClick={handleReserve}
                  data-testid="checkout-reserve"
                >
                  {reserving ? <Spinner size="2" /> : 'Reserve & pay'}
                </Button>
              ) : (
                <Button
                  size="4"
                  color="jade"
                  disabled={!phoneValid || completing || holdExpired}
                  onClick={handlePay}
                  data-testid="checkout-pay"
                >
                  {completing ? <Spinner size="2" /> : 'Send prompt'}
                </Button>
              )}
            </Flex>
          </Container>
        </Box>
      )}
    </Box>
  );
};

export default TicketBookingPage;
