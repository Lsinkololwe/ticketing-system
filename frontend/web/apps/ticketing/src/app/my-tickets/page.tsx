'use client';

import React, { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Flex,
  Text,
  Heading,
  Button,
  Spinner,
  Badge,
  Grid,
  Container,
} from '@radix-ui/themes';
import { Calendar, MapPin, QrCode } from 'iconoir-react';
import { useAuth, useMyTickets, type Ticket } from '@pml.tickets/shared';
import NavbarComponent from '@components/Navbar';
import { EmptyState, Money, Toast } from '@/components/ui';

/** Deterministic 12×12 "QR" from a ticket's code (no external asset / no network). */
function FakeQr({ seed }: { seed: string }) {
  const size = 12;
  let h = 0;
  for (let i = 0; i < seed.length; i += 1) h = (h * 31 + seed.charCodeAt(i)) >>> 0;
  const cells: boolean[] = [];
  let x = h || 1;
  for (let i = 0; i < size * size; i += 1) {
    x ^= x << 13;
    x ^= x >>> 17;
    x ^= x << 5;
    x >>>= 0;
    cells.push((x & 1) === 1);
  }
  return (
    <Box
      aria-label="Ticket QR code"
      style={{
        display: 'grid',
        gridTemplateColumns: `repeat(${size}, 1fr)`,
        width: 96,
        height: 96,
        gap: 1,
        /* Fixed paper white — a QR needs its quiet zone regardless of theme. */
        background: 'var(--qr-paper)',
        padding: 4,
        borderRadius: 'var(--radius-4)',
        flexShrink: 0,
      }}
    >
      {cells.map((on, i) => (
        <span key={i} style={{ background: on ? 'var(--qr-ink)' : 'transparent', borderRadius: 1 }} />
      ))}
    </Box>
  );
}

type BadgeColor = 'green' | 'amber' | 'red' | 'gray';

function statusMeta(status: string): { color: BadgeColor; label: string } {
  switch (status) {
    case 'VALID':
    case 'CONFIRMED':
    case 'PURCHASED':
      // Status green, not money jade — jade stays reserved for amounts.
      return { color: 'green', label: 'Valid' };
    case 'PENDING_PAYMENT':
      return { color: 'amber', label: 'Pending' };
    case 'USED':
      return { color: 'gray', label: 'Used' };
    case 'CANCELLED':
      return { color: 'red', label: 'Cancelled' };
    case 'REFUNDED':
      return { color: 'red', label: 'Refunded' };
    case 'EXPIRED':
      return { color: 'gray', label: 'Expired' };
    default:
      return { color: 'gray', label: status.replace(/_/g, ' ').toLowerCase() };
  }
}

const MyTicketsPage: React.FC = () => {
  const router = useRouter();
  const { user, authenticated, loading: authLoading } = useAuth();
  const { tickets, loading, error } = useMyTickets({ buyerId: user?.id });

  useEffect(() => {
    if (!authLoading && !authenticated) router.push('/auth');
  }, [authLoading, authenticated, router]);

  return (
    <Box style={{ minHeight: '100vh', background: 'var(--gray-2)' }}>
      <NavbarComponent />
      <Container size="3" py="6">
        <Heading size="7" mb="1" className="font-display">My tickets</Heading>
        <Text size="3" color="gray" mb="6" style={{ display: 'block' }}>
          Show a QR at the gate — your tickets work offline.
        </Text>

        {loading || authLoading ? (
          <Flex justify="center" py="9"><Spinner size="3" /></Flex>
        ) : error ? (
          <Toast
            variant="error"
            title="Tickets didn't load"
            description="Check your connection and refresh the page to try again."
          />
        ) : tickets.length === 0 ? (
          <EmptyState
            icon={<QrCode width={24} height={24} />}
            title="No tickets yet"
            description="Book an event and your tickets land here — ready to scan at the gate."
            action={
              <Button onClick={() => router.push('/')} data-testid="tickets-browse">
                Browse events
              </Button>
            }
          />
        ) : (
          <Grid columns={{ initial: '1', md: '2' }} gap="4">
            {tickets.map((ticket: Ticket) => {
              const meta = statusMeta(ticket.status);
              const date = ticket.eventDate ? new Date(ticket.eventDate) : null;
              return (
                <Box
                  key={ticket.id}
                  className="ds-card-bento"
                  style={{ borderRadius: 'var(--card-radius-bento)', overflow: 'hidden' }}
                >
                  {/* Flat accent band — the stub edge of the ticket. */}
                  <Box p="3" style={{ background: 'var(--accent-9)' }}>
                    <Flex justify="between" align="start" gap="2">
                      <Heading
                        size="4"
                        className="font-display"
                        style={{ color: 'var(--accent-contrast)' }}
                      >
                        {ticket.eventTitle}
                      </Heading>
                      <Badge color={meta.color} variant="solid" radius="full" style={{ textTransform: 'capitalize' }}>
                        {meta.label}
                      </Badge>
                    </Flex>
                  </Box>

                  <Flex gap="4" p="4" align="center">
                    <FakeQr seed={ticket.qrCode || ticket.barcode || ticket.ticketNumber} />
                    <Box style={{ minWidth: 0 }}>
                      {date && (
                        <Flex align="center" gap="2" mb="1">
                          <Calendar style={{ width: '0.9rem', height: '0.9rem', color: 'var(--gray-9)' }} />
                          <Text size="2" color="gray" className="ds-amount">
                            {date.toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' })}
                          </Text>
                        </Flex>
                      )}
                      {ticket.eventLocationName && (
                        <Flex align="center" gap="2" mb="2">
                          <MapPin style={{ width: '0.9rem', height: '0.9rem', color: 'var(--gray-9)' }} />
                          <Text size="2" color="gray">{ticket.eventLocationName}</Text>
                        </Flex>
                      )}
                      <Text as="div" size="1" color="gray">Ticket</Text>
                      <Text as="div" size="2" className="ds-amount" weight="medium" style={{ marginBottom: 6 }}>
                        {ticket.ticketNumber}
                      </Text>
                      <Flex align="center" gap="2">
                        {ticket.ticketCategoryName && (
                          <Badge variant="soft" color="gray" radius="full">{ticket.ticketCategoryName}</Badge>
                        )}
                        <Money amount={Number(ticket.price)} decimals tone="money" size="2" />
                      </Flex>
                    </Box>
                  </Flex>
                </Box>
              );
            })}
          </Grid>
        )}
      </Container>
    </Box>
  );
};

export default MyTicketsPage;
