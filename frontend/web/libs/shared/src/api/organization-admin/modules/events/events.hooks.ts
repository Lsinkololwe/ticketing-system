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
import { MY_EVENTS, PUBLISH_EVENT, UNPUBLISH_EVENT } from './events.queries';
import type { EventStatus } from '../../../../types/graphql';

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
