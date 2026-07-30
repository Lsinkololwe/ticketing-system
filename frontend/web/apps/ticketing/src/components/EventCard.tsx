'use client';

import React from 'react';
import {
  Card,
  Box,
  Flex,
  Text,
  Badge,
  Button,
  Avatar,
  Progress,
  IconButton,
  Tooltip,
} from '@radix-ui/themes';
import type { Event, EventStatus } from '@pml.tickets/shared';
import { Calendar, MapPin, Clock, Eye, Heart, ShareAndroid } from 'iconoir-react';
import { Money, MobileMoneyStrip } from '@/components/ui';

interface EventCardProps {
  event: Event;
  onViewDetails?: (event: Event) => void;
  onBookTicket?: (event: Event) => void;
  onToggleFavorite?: (event: Event) => void;
  isFavorite?: boolean;
}

type RadixBadgeColor = 'green' | 'iris' | 'orange' | 'red' | 'blue' | 'gray';

const STATUS_META: Record<EventStatus, { color: RadixBadgeColor; label: string }> = {
  PUBLISHED: { color: 'green', label: 'Live' },
  DRAFT: { color: 'gray', label: 'Draft' },
  PENDING_REVIEW: { color: 'orange', label: 'Pending' },
  CHANGES_REQUESTED: { color: 'orange', label: 'Changes requested' },
  APPROVED: { color: 'blue', label: 'Approved' },
  COMPLETED: { color: 'blue', label: 'Completed' },
  CANCELLED: { color: 'red', label: 'Cancelled' },
};

const EventCard: React.FC<EventCardProps> = ({
  event,
  onViewDetails,
  onBookTicket,
  onToggleFavorite,
  isFavorite = false,
}) => {
  const status = STATUS_META[event.status] ?? { color: 'gray' as const, label: event.status };

  const formatDate = (dateString: string) =>
    new Date(dateString).toLocaleDateString('en-GB', {
      weekday: 'short',
      month: 'short',
      day: 'numeric',
    });

  const formatTime = (dateString: string) =>
    new Date(dateString).toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' });

  const salesPercentage =
    event.totalCapacity > 0 ? Math.round((event.soldTickets / event.totalCapacity) * 100) : 0;

  const minPrice = Number(event.minTicketPrice ?? 0);
  const maxPrice = Number(event.maxTicketPrice ?? minPrice);

  const isUpcoming = new Date(event.eventDateTime) > new Date();
  const isToday = new Date(event.eventDateTime).toDateString() === new Date().toDateString();

  const heroImage = event.bannerImageUrl ?? event.galleryImages?.[0] ?? null;
  const locationLabel = [event.locationName, event.cityName].filter(Boolean).join(', ');

  return (
    <Card
      size="2"
      className="ds-lift"
      style={{ height: '100%', borderRadius: 'var(--card-radius-bento)', overflow: 'hidden' }}
    >
      {/* Event image / gradient header */}
      <Box
        className="ds-accent-chip"
        style={{
          position: 'relative',
          height: '12rem',
          borderRadius: 'var(--card-radius-bento) var(--card-radius-bento) 0 0',
          overflow: 'hidden',
        }}
      >
        {heroImage ? (
          <img
            src={heroImage}
            alt={`${event.title} event artwork`}
            style={{ width: '100%', height: '100%', objectFit: 'cover' }}
          />
        ) : (
          <Flex align="center" justify="center" style={{ height: '100%' }}>
            <Calendar style={{ width: '4rem', height: '4rem', opacity: 0.5 }} />
          </Flex>
        )}

        <Box className="artwork-scrim">
          <Box style={{ position: 'absolute', top: '0.75rem', left: '0.75rem' }}>
            <Badge color={status.color} variant="solid" radius="full">
              {status.label}
            </Badge>
          </Box>

          <Flex gap="2" style={{ position: 'absolute', top: '0.75rem', right: '0.75rem' }}>
            <Tooltip content={isFavorite ? 'Remove from favorites' : 'Add to favorites'}>
              <IconButton
                size="1"
                variant="solid"
                color="gray"
                radius="full"
                aria-label={isFavorite ? 'Remove from favorites' : 'Add to favorites'}
                onClick={() => onToggleFavorite?.(event)}
                className="on-artwork"
                data-testid="event-card-favorite"
              >
                <Heart
                  style={{
                    width: '1.1rem',
                    height: '1.1rem',
                    fill: isFavorite ? 'var(--status-danger-9)' : 'transparent',
                    color: isFavorite ? 'var(--status-danger-9)' : 'currentColor',
                  }}
                />
              </IconButton>
            </Tooltip>

            <Tooltip content="Share event">
              <IconButton
                size="1"
                variant="solid"
                color="gray"
                radius="full"
                aria-label="Share event"
                className="on-artwork"
                data-testid="event-card-share"
              >
                <ShareAndroid style={{ width: '1.1rem', height: '1.1rem' }} />
              </IconButton>
            </Tooltip>
          </Flex>

          {/* "Today" is urgency, not danger — copper highlight, not red. */}
          {isToday && (
            <Box style={{ position: 'absolute', bottom: '0.75rem', left: '0.75rem' }}>
              <Badge
                variant="solid"
                radius="full"
                style={{ background: 'var(--color-highlight)', color: 'var(--on-scrim)' }}
              >
                Tonight
              </Badge>
            </Box>
          )}
        </Box>
      </Box>

      {/* Card body */}
      <Box pt="4">
        <Box mb="3">
          <Text as="div" size="4" weight="bold" mb="2" className="font-display">
            {event.title}
          </Text>
          <Flex gap="2" wrap="wrap">
            {event.category?.name && (
              <Badge variant="outline" color="gray" radius="full">
                {event.category.name}
              </Badge>
            )}
            {/* Featured — copper highlight, used sparingly */}
            {event.featured && (
              <Badge
                variant="soft"
                radius="full"
                style={{
                  background: 'var(--color-highlight-surface)',
                  color: 'var(--color-highlight-text)',
                }}
              >
                Featured
              </Badge>
            )}
          </Flex>
        </Box>

        <Text
          size="2"
          color="gray"
          mb="3"
          style={{
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
          }}
        >
          {event.description}
        </Text>

        <Flex direction="column" gap="2" mb="3">
          <Flex align="center" gap="2">
            <Calendar style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)' }} />
            <Text size="2" color="gray">
              {formatDate(event.eventDateTime)}
            </Text>
            <Clock
              style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)', marginLeft: '0.5rem' }}
            />
            <Text size="2" color="gray">
              {formatTime(event.eventDateTime)}
            </Text>
          </Flex>

          {locationLabel && (
            <Flex align="center" gap="2">
              <MapPin style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)' }} />
              <Text
                size="2"
                color="gray"
                style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}
              >
                {locationLabel}
              </Text>
            </Flex>
          )}

          <Flex align="center" gap="2">
            <Avatar
              fallback={event.organizerName?.charAt(0)?.toUpperCase() || 'O'}
              size="1"
              radius="full"
              color="iris"
            />
            <Text size="2" color="gray">
              by {event.organizerName}
            </Text>
          </Flex>
        </Flex>

        {/* Ticket sales progress — jade (commerce) */}
        <Box mb="3">
          <Flex justify="between" align="center" mb="1">
            <Text size="1" color="gray" className="ds-amount">
              {event.soldTickets} / {event.totalCapacity} sold
            </Text>
            <Text size="1" weight="medium" className="ds-amount">
              {salesPercentage}%
            </Text>
          </Flex>
          <Progress value={salesPercentage} color="jade" size="1" />
        </Box>

        {/* Price — jade money role, "From" label */}
        <Flex justify="between" align="end" mb="3">
          <Box>
            <Text as="div" size="1" color="gray">
              From
            </Text>
            <Flex align="baseline" gap="1">
              <Money amount={minPrice} tone="money" size="6" />
              {maxPrice > minPrice && (
                <Money amount={maxPrice} tone="muted" size="2" weight="regular" />
              )}
            </Flex>
          </Box>
        </Flex>

        {/* Always-visible mobile money */}
        <Box mb="3">
          <MobileMoneyStrip label="Mobile money" />
        </Box>

        {/* Actions */}
        <Flex gap="2">
          <Button
            variant="outline"
            color="gray"
            size="2"
            style={{ flex: 1 }}
            onClick={() => onViewDetails?.(event)}
            data-testid="event-card-details"
          >
            <Eye style={{ width: '1rem', height: '1rem' }} />
            Details
          </Button>

          {isUpcoming && event.status === 'PUBLISHED' && (
            <Button
              size="2"
              color="jade"
              style={{ flex: 1 }}
              onClick={() => onBookTicket?.(event)}
              data-testid="event-card-book"
            >
              Book now
            </Button>
          )}
        </Flex>
      </Box>
    </Card>
  );
};

export default EventCard;
