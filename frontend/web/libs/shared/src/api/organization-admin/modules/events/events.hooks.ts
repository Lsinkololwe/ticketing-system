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
import type {
  MyEventsQuery,
  MyEventsQueryVariables,
  MyEventDetailQuery,
  MyEventDetailQueryVariables,
  CreateEventMutation,
  CreateEventMutationVariables,
  PublishEventMutation,
  PublishEventMutationVariables,
  UnpublishEventMutation,
  UnpublishEventMutationVariables,
} from '../../../../types/graphql';

/**
 * The subset of Event this app selects, taken from the generated query type
 * rather than hand-written, so a schema change surfaces here as a compile error.
 */
export type EventDetailVM = NonNullable<MyEventDetailQuery['event']>;

/** The subset of Event fields the organizer events list renders. */
export type MyEventRow = MyEventsQuery['myEvents']['content'][number];

/**
 * Outcome of publish or unpublish.
 *
 * `publishEvent`/`unpublishEvent` return the entity directly (`{id, status}`),
 * not an envelope with a `success` field, so `success` is derived from
 * whether the mutation resolved with a payload rather than read off a field
 * the wire never sends.
 */
interface EventStatusActionResult {
  success: boolean;
  message: string | null;
  errors: string[];
  data: PublishEventMutation['publishEvent'] | null;
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
  const { data, loading, error, refetch } = useQuery<MyEventsQuery, MyEventsQueryVariables>(
    MY_EVENTS,
    {
      variables: {
        pagination: { page: 0, size: options?.size ?? 50, sortBy: null, sortDirection: null },
      },
      fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
      errorPolicy: 'all',
      notifyOnNetworkStatusChange: true,
      skip: options?.skip ?? false,
    }
  );

  // `errorPolicy: 'all'` makes Apollo type `data` as deeply partial, since a
  // partial GraphQL response is possible alongside errors. Absent an error,
  // the response matches the query exactly, so the read site trusts that.
  const page = data?.myEvents as MyEventsQuery['myEvents'] | undefined;

  return {
    events: page?.content ?? [],
    totalElements: page?.totalElements ?? 0,
    loading,
    error,
    refetch,
  };
}

/**
 * Publish an event.
 *
 * `publishEvent` returns the updated `{id, status}` directly — there is no
 * `success` field on the wire. A GraphQL error throws out of `mutate` and is
 * caught here; anything else that resolves is a success, since the server
 * would have returned a GraphQL error rather than a partial entity for a
 * refused publish.
 */
export function usePublishEvent() {
  const [mutate, { loading }] = useMutation<PublishEventMutation, PublishEventMutationVariables>(
    PUBLISH_EVENT
  );
  const publish = async (id: string): Promise<EventStatusActionResult> => {
    try {
      const res = await mutate({ variables: { id } });
      const payload = res.data?.publishEvent;
      if (!payload) {
        return { success: false, message: 'Publish failed', errors: ['Publish failed'], data: null };
      }
      return { success: true, message: null, errors: [], data: payload };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Publish failed';
      return { success: false, message, errors: [message], data: null };
    }
  };
  return { publish, loading };
}

/**
 * Unpublish an event.
 *
 * Same shape as `usePublishEvent`: `unpublishEvent` returns `{id, status}`
 * directly, so success is whether the mutation resolved, not a field that
 * does not exist on the response.
 */
export function useUnpublishEvent() {
  const [mutate, { loading }] = useMutation<UnpublishEventMutation, UnpublishEventMutationVariables>(
    UNPUBLISH_EVENT
  );
  const unpublish = async (id: string): Promise<EventStatusActionResult> => {
    try {
      const res = await mutate({ variables: { id } });
      const payload = res.data?.unpublishEvent;
      if (!payload) {
        return { success: false, message: 'Unpublish failed', errors: ['Unpublish failed'], data: null };
      }
      return { success: true, message: null, errors: [], data: payload };
    } catch (error) {
      const message = error instanceof Error ? error.message : 'Unpublish failed';
      return { success: false, message, errors: [message], data: null };
    }
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
  const { data, loading, error, refetch } = useQuery<MyEventDetailQuery, MyEventDetailQueryVariables>(
    MY_EVENT_DETAIL,
    {
      variables: { id: id ?? '' },
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
 * Resolves to a result object rather than throwing, so the form can surface
 * `errors` in place and keep the user's input intact on a failed create
 * instead of navigating away from a form the caller has no way to restore.
 *
 * `createEvent` returns the created `{id, title, status}` directly, with no
 * `success` field — success is whether the mutation resolved with a payload,
 * not a fabricated field that is always absent from the response.
 */
export function useCreateEvent() {
  const [mutate, { loading }] = useMutation<CreateEventMutation, CreateEventMutationVariables>(
    CREATE_EVENT,
    { refetchQueries: [MY_EVENTS] }
  );

  const createEvent = async (input: CreateEventArgs) => {
    try {
      const result = await mutate({
        variables: {
          input: {
            title: input.title,
            description: input.description,
            categoryId: input.categoryId,
            eventDateTime: input.eventDateTime,
            endDateTime: input.endDateTime,
            totalCapacity: input.totalCapacity,
            ticketTiers: input.ticketTiers.map((tier) => ({
              code: tier.code,
              name: tier.name,
              description: tier.description ?? null,
              price: String(tier.price),
              currency: tier.currency,
              quantity: tier.quantity,
              sortOrder: tier.sortOrder ?? null,
              accessCode: null,
              category: null,
              benefits: null,
              earlyBirdEndsAt: null,
              earlyBirdPrice: null,
              isHidden: null,
              maxPerOrder: null,
              minPerOrder: null,
              salesEndAt: null,
              salesStartAt: null,
            })),
            location: input.location
              ? {
                  name: input.location.name,
                  address: input.location.address,
                  city: input.location.city,
                  province: input.location.province ?? null,
                  country: input.location.country,
                  coordinates: null,
                  description: null,
                  postalCode: null,
                }
              : null,
            isVirtual: input.isVirtual ?? null,
            virtualEventUrl: input.virtualEventUrl ?? null,
            bannerImageUrl: input.bannerImageUrl ?? null,
            // This form collects none of these; the event is created without
            // them and an organizer can add them later through event settings.
            accessibility: null,
            additionalInfo: null,
            cancellationPolicy: null,
            enableWaitlist: null,
            isFreeEvent: null,
            refundPolicy: null,
            termsAndConditions: null,
            waitlistCapacity: null,
          },
        },
      });
      const payload = result.data?.createEvent;
      if (!payload) {
        return {
          success: false,
          message: 'The event could not be created.',
          errors: ['The event could not be created.'],
          event: null,
        };
      }
      return { success: true, message: null, errors: [], event: payload };
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
