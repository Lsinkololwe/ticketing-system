'use client';

/**
 * Event editor operations (catalog-service). Defined here because the shared
 * organization-admin events module only covers list, detail, create and
 * publish. Shapes follow backend/catalog-service schema.graphqls.
 */
import type { CreateEventInput, EditorCreateEventMutation, EditorCreateEventMutationVariables, EditorEventQuery, EditorEventQueryVariables, EditorReferenceDataQuery, EditorReferenceDataQueryVariables, EditorSubmitForApprovalMutation, EditorSubmitForApprovalMutationVariables } from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';

export type EditorEventData = NonNullable<EditorEventQuery['event']>;
export type EditorTier = NonNullable<EditorEventData['ticketTiers']>[number];

export const EDITOR_EVENT = gql`
  query EditorEvent($id: ID!) {
    event(id: $id) {
      id title description status categoryId eventDateTime endDateTime bannerImageUrl bannerAltText
      isVirtual isFreeEvent virtualEventUrl totalCapacity soldTickets
      refundPolicy cancellationPolicy termsAndConditions rejectionReason
      tagline ageRestriction doorsOpenAt galleryImages gettingThere parkingInfo bagPolicy
      publishAt publishScheduled publishedAt submittedForApprovalAt approvalDeadline approvedAt rejectedAt
      faqs { question answer }
      runningOrder { time title }
      checkoutSettings { maxTicketsPerOrder collectHolderNames extraQuestion }
      location { name address city province country }
      accessibility {
        wheelchairAccessible wheelchairSeatsAvailable signLanguageInterpreter hearingLoopAvailable
        accessibleParking accessibleRestrooms assistanceDogsAllowed additionalNotes
      }
      ticketTiers { id code name description price currency quantity soldQuantity minPerOrder maxPerOrder benefits salesStartAt salesEndAt earlyBirdPrice earlyBirdEndsAt sortOrder isActive isHidden accessCode category }
    }
  }
`;

export const EDITOR_REFERENCE_DATA = gql`
  query EditorReferenceData {
    categories { id name code isActive }
    provinces { id name code }
    cities { id name province provinceId }
  }
`;

export const EDITOR_CREATE_EVENT = gql`
  mutation EditorCreateEvent($input: CreateEventInput!) {
    createEvent(input: $input) { id status }
  }
`;
export const EDITOR_UPDATE_EVENT = gql`
  mutation EditorUpdateEvent($id: ID!, $input: UpdateEventInput!) {
    updateEvent(id: $id, input: $input) { id status }
  }
`;
export const EDITOR_UPDATE_ACCESSIBILITY = gql`
  mutation EditorUpdateAccessibility($eventId: ID!, $input: EventAccessibilityInput!) {
    updateEventAccessibility(eventId: $eventId, input: $input) { id }
  }
`;
export const EDITOR_SUBMIT_FOR_APPROVAL = gql`
  mutation EditorSubmitForApproval($eventId: ID!) {
    submitEventForApproval(eventId: $eventId) { id status }
  }
`;
export const EDITOR_CANCEL_SCHEDULED_PUBLISH = gql`
  mutation EditorCancelScheduledPublish($eventId: ID!) {
    cancelScheduledPublish(eventId: $eventId) { id status publishAt publishScheduled }
  }
`;
export const EDITOR_CREATE_TIER = gql`
  mutation EditorCreateTier($eventId: ID!, $input: CreateTicketTierInput!) {
    createTicketTier(eventId: $eventId, input: $input) { id code name description price currency quantity soldQuantity minPerOrder maxPerOrder benefits salesStartAt salesEndAt earlyBirdPrice earlyBirdEndsAt sortOrder isActive isHidden accessCode category }
  }
`;
export const EDITOR_UPDATE_TIER = gql`
  mutation EditorUpdateTier($tierId: ID!, $input: UpdateTicketTierInput!) {
    updateTicketTier(tierId: $tierId, input: $input) { id code name description price currency quantity soldQuantity minPerOrder maxPerOrder benefits salesStartAt salesEndAt earlyBirdPrice earlyBirdEndsAt sortOrder isActive isHidden accessCode category }
  }
`;
export const EDITOR_DELETE_TIER = gql`
  mutation EditorDeleteTier($tierId: ID!) {
    deleteTicketTier(tierId: $tierId)
  }
`;
export const EDITOR_REORDER_TIERS = gql`
  mutation EditorReorderTiers($eventId: ID!, $tierIds: [ID!]!) {
    reorderTicketTiers(eventId: $eventId, tierIds: $tierIds) { id sortOrder }
  }
`;

export interface EditorReferenceData {
  categories: Array<{ id: string; name: string; code: string; isActive: boolean }>;
  provinces: Array<{ id: string; name: string; code: string }>;
  cities: Array<{ id: string; name: string; province: string | null; provinceId: string | null }>;
}

export function useEditorEvent(id: string | null | undefined) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<EditorEventQuery, EditorEventQueryVariables>(EDITOR_EVENT, {
    variables: { id: id ?? '' },
    skip: !id,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { event: data?.event ?? null, loading, error, refetch };
}

export function useEditorReferenceData() {
  const { data: raw, dataState, loading, error } = useQuery<EditorReferenceDataQuery, EditorReferenceDataQueryVariables>(EDITOR_REFERENCE_DATA, {
    fetchPolicy: 'cache-first',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return {
    categories: (data?.categories ?? []).filter((c) => c.isActive),
    provinces: data?.provinces ?? [],
    cities: data?.cities ?? [],
    loading,
    error,
  };
}

export function useEditorMutations() {
  const [create] = useMutation<EditorCreateEventMutation, EditorCreateEventMutationVariables>(EDITOR_CREATE_EVENT);
  const [update] = useMutation(EDITOR_UPDATE_EVENT);
  const [accessibility] = useMutation(EDITOR_UPDATE_ACCESSIBILITY);
  const [submit] = useMutation<EditorSubmitForApprovalMutation, EditorSubmitForApprovalMutationVariables>(EDITOR_SUBMIT_FOR_APPROVAL);
  const [cancelSchedule] = useMutation(EDITOR_CANCEL_SCHEDULED_PUBLISH);
  const [createTier] = useMutation(EDITOR_CREATE_TIER);
  const [updateTier] = useMutation(EDITOR_UPDATE_TIER);
  const [deleteTier] = useMutation(EDITOR_DELETE_TIER);
  const [reorder] = useMutation(EDITOR_REORDER_TIERS);
  return {
    createEvent: (input: object) => create({ variables: { input: input as CreateEventInput } }),
    updateEvent: (id: string, input: unknown) => update({ variables: { id, input } }),
    updateAccessibility: (eventId: string, input: unknown) => accessibility({ variables: { eventId, input } }),
    submitForApproval: (eventId: string) => submit({ variables: { eventId } }),
    cancelScheduledPublish: (eventId: string) => cancelSchedule({ variables: { eventId } }),
    createTier: (eventId: string, input: unknown) => createTier({ variables: { eventId, input } }),
    updateTier: (tierId: string, input: unknown) => updateTier({ variables: { tierId, input } }),
    deleteTier: (tierId: string) => deleteTier({ variables: { tierId } }),
    reorderTiers: (eventId: string, tierIds: string[]) => reorder({ variables: { eventId, tierIds } }),
  };
}
