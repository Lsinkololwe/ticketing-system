'use client';

/**
 * React Hooks for the Organizer Events list + lifecycle (Organization Admin App)
 *
 * Types come from codegen. Publish/unpublish are gated server-side by
 * organization status (Organization.canPerform → canPublishEvents); these hooks
 * surface the backend's success/message/errors so the UI can react.
 */

import { useQuery, useMutation } from '@apollo/client/react';
import type { FetchPolicy } from '@apollo/client';
import { MY_EVENTS, MY_EVENT_DETAIL, CREATE_EVENT, PUBLISH_EVENT, UNPUBLISH_EVENT } from './events.queries';
import type { EventStatus, Event as CatalogEvent, TicketTier } from '../../../../types/graphql';

/**
 * The subset of Event this app selects. Derived from the generated type rather
 * than hand-written, so a schema change surfaces here as a compile error.
 */
export type EventDetailVM = Pick<
  CatalogEvent,
  | 'id' | 'title' | 'description' | 'status' | 'eventDateTime' | 'endDateTime'
  | 'locationName' | 'locationAddress' | 'cityName' | 'bannerImageUrl'
  | 'totalCapacity' | 'soldTickets' | 'availableTickets' | 'revenue'
  | 'currency' | 'rejectionReason'
> & {
  ticketTiers: Array<
    Pick<TicketTier, 'id' | 'name' | 'price' | 'currency' | 'quantity' | 'soldQuantity' | 'isActive'>
  > | null;
};

/** The subset of Event fields the organizer events list renders. */
export interface MyEventRow {
  id: string;
  title: string;
  status: EventStatus;
  eventDateTime: string;
  endDateTime: string;
  locationName: string | null;
  cityName: string | null;
  bannerImageUrl: string | null;
  totalCapacity: number;
  soldTickets: number;
  revenue: string;
  currency: string | null;
}

interface MyEventsData {
  myEventsOffsetPagination: {
    content: MyEventRow[];
    totalElements: number;
    totalPages: number;
    hasNext: boolean;
  };
}

/** Backend mutation envelope shared by publish/unpublish. */
interface EventMutationResult {
  success: boolean;
  message: string | null;
  errors: string[];
  data: { id: string; status: EventStatus } | null;
}

/**
 * The organizer's own events. Defaults to an empty array so pages render clean
 * empty states before data (or for a brand-new organizer).
 */
export function useMyEvents(options?: {
  size?: number;
  fetchPolicy?: FetchPolicy;
  skip?: boolean;
}) {
  const { data, loading, error, refetch } = useQuery<MyEventsData>(MY_EVENTS, {
    variables: { pagination: { page: 0, size: options?.size ?? 50 } },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
    skip: options?.skip ?? false,
  });

  return {
    events: data?.myEventsOffsetPagination.content ?? [],
    totalElements: data?.myEventsOffsetPagination.totalElements ?? 0,
    loading,
    error,
    refetch,
  };
}

/** Publish an event; resolves to the backend envelope (success/message/errors). */
export function usePublishEvent() {
  const [mutate, { loading }] = useMutation<{ publishEvent: EventMutationResult }>(PUBLISH_EVENT);
  const publish = async (id: string): Promise<EventMutationResult> => {
    const res = await mutate({ variables: { id } });
    return (
      res.data?.publishEvent ?? {
        success: false,
        message: 'Publish failed',
        errors: ['Publish failed'],
        data: null,
      }
    );
  };
  return { publish, loading };
}

/** Unpublish an event; resolves to the backend envelope (success/message/errors). */
export function useUnpublishEvent() {
  const [mutate, { loading }] = useMutation<{ unpublishEvent: EventMutationResult }>(UNPUBLISH_EVENT);
  const unpublish = async (id: string): Promise<EventMutationResult> => {
    const res = await mutate({ variables: { id } });
    return (
      res.data?.unpublishEvent ?? {
        success: false,
        message: 'Unpublish failed',
        errors: ['Unpublish failed'],
        data: null,
      }
    );
  };
  return { unpublish, loading };
}

/**
 * A single event owned by the signed-in organizer.
 *
 * `event` stays null until resolved, and stays null for an id the organizer
 * does not own — the screen must render "not found" rather than an empty shell
 * that looks like a real but blank event.
 */
export function useMyEventDetail(id: string | null | undefined, options?: {
  fetchPolicy?: FetchPolicy;
}) {
  const { data, loading, error, refetch } = useQuery<{ event: EventDetailVM | null }>(
    MY_EVENT_DETAIL,
    {
      variables: { id },
      fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
      errorPolicy: 'all',
      skip: !id,
    }
  );

  return { event: data?.event ?? null, loading, error, refetch };
}

export interface CreateEventTierInput {
  code: string;
  name: string;
  description?: string | null;
  price: number | string;
  currency: string;
  quantity: number;
  sortOrder?: number;
}

export interface CreateEventArgs {
  title: string;
  description: string;
  categoryId: string;
  /** ISO-8601 instants. The caller composes these from its date + time fields. */
  eventDateTime: string;
  endDateTime: string;
  totalCapacity: number;
  ticketTiers: CreateEventTierInput[];
  location?: {
    name: string;
    address: string;
    city: string;
    province?: string | null;
    country: string;
  } | null;
  isVirtual?: boolean;
  virtualEventUrl?: string | null;
  bannerImageUrl?: string | null;
}

/**
 * Create an event.
 *
 * Returns the mutation envelope rather than throwing, so the form can surface
 * `errors` in place. A failed create must leave the user on the form with their
 * input intact — the previous implementation navigated away regardless.
 */
export function useCreateEvent() {
  const [mutate, { loading }] = useMutation<{
    createEvent: {
      success: boolean;
      message: string | null;
      errors: string[];
      data: { id: string; title: string; status: EventStatus } | null;
    };
  }>(CREATE_EVENT, { refetchQueries: [MY_EVENTS] });

  const createEvent = async (input: CreateEventArgs) => {
    try {
      const result = await mutate({ variables: { input } });
      const payload = result.data?.createEvent;
      return {
        success: payload?.success ?? false,
        message: payload?.message ?? null,
        errors: payload?.errors ?? [],
        event: payload?.data ?? null,
      };
    } catch (error) {
      return {
        success: false,
        message: 'Could not reach the server. The event was not created.',
        errors: [error instanceof Error ? error.message : String(error)],
        event: null,
      };
    }
  };

  return { createEvent, loading };
}
