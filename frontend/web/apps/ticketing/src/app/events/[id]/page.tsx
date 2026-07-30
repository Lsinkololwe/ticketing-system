'use client';

import React from 'react';
import { useRouter, useParams } from 'next/navigation';
import {
  Box,
  Flex,
  Text,
  Heading,
  Button,
  Callout,
  Spinner,
  Badge,
  Grid,
  Container,
  Card,
  Progress,
} from '@radix-ui/themes';
import {
  Calendar,
  Clock,
  MapPin,
  Group,
  NavArrowLeft,
  WarningTriangle,
  Check,
} from 'iconoir-react';
import { useEvent, type TicketTier } from '@pml.tickets/shared';
import NavbarComponent from '@components/Navbar';
import { Money, MobileMoneyStrip } from '@/components/ui';

const EventDetailPage: React.FC = () => {
  const router = useRouter();
  const { id } = useParams<{ id: string }>();
  const { event, loading, error } = useEvent(id);

  if (loading) {
    return (
      <Box style={{ minHeight: '100vh', background: 'var(--gray-2)' }}>
        <NavbarComponent />
        <Flex align="center" justify="center" py="9">
          <Spinner size="3" />
        </Flex>
      </Box>
    );
  }

  if (error || !event) {
    return (
      <Box style={{ minHeight: '100vh', background: 'var(--gray-2)' }}>
        <NavbarComponent />
        <Container size="2" py="8">
          <Callout.Root color="red">
            <Callout.Icon><WarningTriangle /></Callout.Icon>
            <Callout.Text>This event could not be found. It may have been removed.</Callout.Text>
          </Callout.Root>
          <Button mt="4" variant="soft" onClick={() => router.push('/')} data-testid="detail-home">
            Back to all events
          </Button>
        </Container>
      </Box>
    );
  }

  const eventDate = new Date(event.eventDateTime);
  const heroImage = event.bannerImageUrl ?? event.galleryImages?.[0] ?? null;
  const tiers = (event.ticketTiers ?? []).filter((t: TicketTier) => t.isActive && !t.isHidden);
  const salesPct =
    event.totalCapacity > 0 ? Math.round((event.soldTickets / event.totalCapacity) * 100) : 0;
  const isUpcoming = eventDate > new Date();
  const canBook = isUpcoming && event.status === 'PUBLISHED';
  const goBook = () => router.push(`/events/${event.id}/book`);

  return (
    <Box style={{ minHeight: '100vh', background: 'var(--gray-2)', paddingBottom: 96 }}>
      <NavbarComponent />

      <Container size="3" py="5">
        <Button
          variant="ghost"
          color="gray"
          size="2"
          mb="4"
          onClick={() => router.push('/')}
          data-testid="detail-back"
        >
          <NavArrowLeft style={{ width: '1rem', height: '1rem' }} />
          All events
        </Button>

        {/* Hero banner */}
        <Box
          className="ds-accent-chip"
          style={{
            position: 'relative',
            height: 260,
            borderRadius: 'var(--card-radius-bento)',
            overflow: 'hidden',
            marginBottom: 24,
          }}
        >
          {heroImage && (
            <img
              src={heroImage}
              alt={`${event.title} artwork`}
              style={{ width: '100%', height: '100%', objectFit: 'cover' }}
            />
          )}
          <Box className="artwork-scrim" />
          <Box style={{ position: 'absolute', top: 16, left: 16 }}>
            {event.category?.name && (
              /* Solid chip — blur is reserved for the top nav, never content. */
              <Badge
                radius="full"
                style={{ background: 'var(--scrim-control)', color: 'var(--on-scrim)' }}
              >
                {event.category.name}
              </Badge>
            )}
          </Box>
          <Box style={{ position: 'absolute', bottom: 16, left: 20, right: 20 }}>
            <Heading
              size="8"
              className="font-display"
              style={{ color: 'var(--on-scrim)', textWrap: 'balance' }}
            >
              {event.title}
            </Heading>
          </Box>
        </Box>

        <Grid columns={{ initial: '1', md: '3' }} gap="6">
          {/* Left: details */}
          <Box style={{ gridColumn: 'span 2' }}>
            {/* Meta row */}
            <Flex gap="5" wrap="wrap" mb="5">
              <Flex align="center" gap="2">
                <Calendar style={{ width: '1.1rem', height: '1.1rem', color: 'var(--gray-9)' }} />
                <Text size="2" className="ds-amount">
                  {eventDate.toLocaleDateString('en-GB', { weekday: 'short', day: 'numeric', month: 'short', year: 'numeric' })}
                </Text>
              </Flex>
              <Flex align="center" gap="2">
                <Clock style={{ width: '1.1rem', height: '1.1rem', color: 'var(--gray-9)' }} />
                <Text size="2" className="ds-amount">
                  {eventDate.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })}
                </Text>
              </Flex>
              {(event.locationName || event.cityName) && (
                <Flex align="center" gap="2">
                  <MapPin style={{ width: '1.1rem', height: '1.1rem', color: 'var(--gray-9)' }} />
                  <Text size="2" color="gray">
                    {[event.locationName, event.cityName].filter(Boolean).join(', ')}
                  </Text>
                </Flex>
              )}
              <Flex align="center" gap="2">
                <Group style={{ width: '1.1rem', height: '1.1rem', color: 'var(--gray-9)' }} />
                <Text size="2" color="gray">by {event.organizerName}</Text>
              </Flex>
            </Flex>

            {/* Mobile-money callout — jade is the commerce role. */}
            <Flex align="center" gap="2" mb="5" wrap="wrap">
              <Check style={{ width: '1rem', height: '1rem', color: 'var(--color-money-text)' }} />
              <Text size="2" weight="medium" style={{ color: 'var(--color-money-text)' }}>
                Mobile money accepted
              </Text>
              <MobileMoneyStrip label="" />
            </Flex>

            {/* About */}
            <Heading size="4" mb="2" className="font-display">About this event</Heading>
            <Text as="p" size="3" color="gray" style={{ whiteSpace: 'pre-line', marginBottom: 24 }}>
              {event.description}
            </Text>

            {/* Sales progress */}
            <Card size="2" mb="4" style={{ borderRadius: 14 }}>
              <Flex justify="between" align="center" mb="2">
                <Text size="2" color="gray" className="ds-amount">
                  {event.soldTickets} / {event.totalCapacity} sold
                </Text>
                <Text size="2" weight="medium" className="ds-amount">{salesPct}%</Text>
              </Flex>
              <Progress value={salesPct} color="jade" size="2" />
            </Card>
          </Box>

          {/* Right: ticket tiers */}
          <Box>
            <Card
              size="3"
              style={{
                borderRadius: 'var(--card-radius-bento)',
                position: 'sticky',
                top: 'var(--space-5)',
              }}
            >
              <Heading size="4" mb="3" className="font-display">Tickets</Heading>
              <Flex direction="column" gap="3" mb="4">
                {tiers.length === 0 && (
                  <Text size="2" color="gray">Tickets aren&apos;t on sale yet — check back soon.</Text>
                )}
                {tiers.map((tier: TicketTier) => {
                  const lowStock = tier.availableQuantity > 0 && tier.availableQuantity < 20;
                  return (
                    <Box
                      key={tier.id}
                      style={{
                        padding: 12,
                        borderRadius: 'var(--radius-4)',
                        border: 'var(--hairline)',
                      }}
                    >
                      <Flex justify="between" align="center" mb="1">
                        <Text size="2" weight="bold">{tier.name}</Text>
                        <Money amount={Number(tier.price)} tone="money" size="3" />
                      </Flex>
                      {tier.description && (
                        <Text as="p" size="1" color="gray">{tier.description}</Text>
                      )}
                      {lowStock && (
                        <Badge
                          mt="1"
                          radius="full"
                          style={{
                            background: 'var(--status-warning-a3)',
                            color: 'var(--status-warning-11)',
                          }}
                        >
                          Only {tier.availableQuantity} left
                        </Badge>
                      )}
                    </Box>
                  );
                })}
              </Flex>
              <Button
                size="4"
                color="jade"
                style={{ width: '100%' }}
                disabled={!canBook}
                onClick={goBook}
                data-testid="detail-book"
              >
                {canBook ? 'Get tickets' : 'Not on sale'}
              </Button>
            </Card>
          </Box>
        </Grid>
      </Container>
    </Box>
  );
};

export default EventDetailPage;
