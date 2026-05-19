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
import { Event, EventStatus } from '@/types/event';
import {
  Calendar,
  MapPin,
  User,
  Clock,
  Eye,
  Heart,
  ShareAndroid,
} from 'iconoir-react';

interface EventCardProps {
  event: Event;
  onViewDetails?: (event: Event) => void;
  onBookTicket?: (event: Event) => void;
  onToggleFavorite?: (event: Event) => void;
  isFavorite?: boolean;
}

type RadixBadgeColor = 'green' | 'iris' | 'orange' | 'red' | 'blue' | 'gray';

const EventCard: React.FC<EventCardProps> = ({
  event,
  onViewDetails,
  onBookTicket,
  onToggleFavorite,
  isFavorite = false,
}) => {
  const getStatusColor = (status: EventStatus): RadixBadgeColor => {
    switch (status) {
      case EventStatus.PUBLISHED:
        return 'green';
      case EventStatus.DRAFT:
        return 'iris';
      case EventStatus.PENDING_APPROVAL:
        return 'orange';
      case EventStatus.CANCELLED:
        return 'red';
      case EventStatus.COMPLETED:
        return 'blue';
      default:
        return 'iris';
    }
  };

  const getStatusLabel = (status: EventStatus) => {
    switch (status) {
      case EventStatus.PUBLISHED:
        return 'Live';
      case EventStatus.DRAFT:
        return 'Draft';
      case EventStatus.PENDING_APPROVAL:
        return 'Pending';
      case EventStatus.CANCELLED:
        return 'Cancelled';
      case EventStatus.COMPLETED:
        return 'Completed';
      default:
        return status;
    }
  };

  const formatDate = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleDateString('en-US', {
      weekday: 'short',
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  };

  const formatTime = (dateString: string) => {
    const date = new Date(dateString);
    return date.toLocaleTimeString('en-US', {
      hour: '2-digit',
      minute: '2-digit',
    });
  };

  const getSalesPercentage = () => {
    if (event.totalCapacity === 0) return 0;
    return Math.round((event.soldTickets / event.totalCapacity) * 100);
  };

  const getLowestPrice = () => {
    if (!event.ticketCategories || event.ticketCategories.length === 0) return 0;
    return Math.min(...event.ticketCategories.map((cat) => cat.price));
  };

  const getHighestPrice = () => {
    if (!event.ticketCategories || event.ticketCategories.length === 0) return 0;
    return Math.max(...event.ticketCategories.map((cat) => cat.price));
  };

  const formatPrice = (price: number) => {
    return new Intl.NumberFormat('en-US', {
      style: 'currency',
      currency: 'USD',
    }).format(price);
  };

  const isEventUpcoming = () => {
    return new Date(event.eventDateTime) > new Date();
  };

  const isEventToday = () => {
    const today = new Date();
    const eventDate = new Date(event.eventDateTime);
    return today.toDateString() === eventDate.toDateString();
  };

  return (
    <Card
      size="2"
      style={{
        height: '100%',
        transition: 'transform 0.3s, box-shadow 0.3s',
      }}
      className="hover:shadow-xl hover:-translate-y-1"
    >
      {/* Event Header/Image */}
      <Box
        style={{
          position: 'relative',
          height: '12rem',
          borderRadius: 'var(--radius-3) var(--radius-3) 0 0',
          overflow: 'hidden',
          background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
        }}
      >
        {event.images && event.images.length > 0 ? (
          <img
            src={event.images[0]}
            alt={event.title}
            style={{
              width: '100%',
              height: '100%',
              objectFit: 'cover',
            }}
          />
        ) : (
          <Flex align="center" justify="center" style={{ height: '100%' }}>
            <Calendar
              style={{
                width: '4rem',
                height: '4rem',
                color: 'white',
                opacity: 0.5,
              }}
            />
          </Flex>
        )}

        {/* Overlay with status and actions */}
        <Box
          style={{
            position: 'absolute',
            inset: 0,
            background: 'rgba(0, 0, 0, 0.2)',
          }}
        >
          <Box style={{ position: 'absolute', top: '1rem', left: '1rem' }}>
            <Badge color={getStatusColor(event.status)} variant="solid">
              {getStatusLabel(event.status)}
            </Badge>
          </Box>

          <Flex
            gap="2"
            style={{ position: 'absolute', top: '1rem', right: '1rem' }}
          >
            <Tooltip content="Add to favorites">
              <IconButton
                size="1"
                variant="ghost"
                onClick={() => onToggleFavorite?.(event)}
                style={{ color: 'white' }}
              >
                <Heart
                  style={{
                    width: '1.25rem',
                    height: '1.25rem',
                    fill: isFavorite ? 'var(--red-9)' : 'transparent',
                    color: isFavorite ? 'var(--red-9)' : 'currentColor',
                  }}
                />
              </IconButton>
            </Tooltip>

            <Tooltip content="Share event">
              <IconButton size="1" variant="ghost" style={{ color: 'white' }}>
                <ShareAndroid style={{ width: '1.25rem', height: '1.25rem' }} />
              </IconButton>
            </Tooltip>
          </Flex>

          {isEventToday() && (
            <Box style={{ position: 'absolute', bottom: '1rem', left: '1rem' }}>
              <Badge color="red" variant="solid">
                Today
              </Badge>
            </Box>
          )}
        </Box>
      </Box>

      {/* Card Body */}
      <Box p="4">
        {/* Event Title and Category */}
        <Box mb="3">
          <Text size="4" weight="bold" style={{ display: 'block' }} mb="2">
            {event.title}
          </Text>
          <Flex gap="2" wrap="wrap">
            <Badge variant="outline">{event.category}</Badge>
            {event.tags && event.tags.length > 0 && (
              <Badge color="green" variant="outline">
                {event.tags[0]}
              </Badge>
            )}
          </Flex>
        </Box>

        {/* Event Description */}
        <Text
          size="2"
          color="gray"
          style={{
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            overflow: 'hidden',
          }}
          mb="3"
        >
          {event.description}
        </Text>

        {/* Event Details */}
        <Flex direction="column" gap="2" mb="3">
          {/* Date and Time */}
          <Flex align="center" gap="2">
            <Calendar style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)' }} />
            <Text size="2" color="gray">
              {formatDate(event.eventDateTime)}
            </Text>
            <Clock style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)', marginLeft: '0.5rem' }} />
            <Text size="2" color="gray">
              {formatTime(event.eventDateTime)}
            </Text>
          </Flex>

          {/* Location */}
          <Flex align="center" gap="2">
            <MapPin style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)' }} />
            <Text size="2" color="gray" style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
              {event.location.name}, {event.location.city}
            </Text>
          </Flex>

          {/* Organizer */}
          <Flex align="center" gap="2">
            <Avatar
              src={`https://ui-avatars.com/api/?name=${event.organizerName}&background=8B5CF6&color=fff`}
              fallback={event.organizerName?.charAt(0) || 'O'}
              size="1"
              radius="full"
            />
            <Text size="2" color="gray">
              by {event.organizerName}
            </Text>
          </Flex>
        </Flex>

        {/* Ticket Sales Progress */}
        <Box mb="3">
          <Flex justify="between" align="center" mb="1">
            <Flex align="center" gap="2">
              <User style={{ width: '1rem', height: '1rem', color: 'var(--gray-9)' }} />
              <Text size="2" color="gray">
                {event.soldTickets} / {event.totalCapacity} sold
              </Text>
            </Flex>
            <Text size="2" weight="medium">
              {getSalesPercentage()}%
            </Text>
          </Flex>
          <Progress value={getSalesPercentage()} color="green" size="1" />
        </Box>

        {/* Price Range */}
        <Box mb="3">
          <Text size="1" color="gray" mb="1">
            From
          </Text>
          <Text size="4" weight="bold" color="green">
            {formatPrice(getLowestPrice())}
            {getLowestPrice() !== getHighestPrice() && (
              <Text size="2" color="gray" ml="1">
                - {formatPrice(getHighestPrice())}
              </Text>
            )}
          </Text>
        </Box>

        {/* Action Buttons */}
        <Flex gap="2">
          <Button
            variant="outline"
            size="2"
            style={{ flex: 1 }}
            onClick={() => onViewDetails?.(event)}
          >
            <Eye style={{ width: '1rem', height: '1rem', marginRight: '0.25rem' }} />
            View Details
          </Button>

          {isEventUpcoming() && event.status === EventStatus.PUBLISHED && (
            <Button
              size="2"
              color="green"
              style={{ flex: 1 }}
              onClick={() => onBookTicket?.(event)}
            >
              Book Now
            </Button>
          )}
        </Flex>
      </Box>
    </Card>
  );
};

export default EventCard;
