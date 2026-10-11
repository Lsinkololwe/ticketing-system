'use client';

/**
 * Hooks for the admin Events module (list, detail, categories, locations).
 * Types are local because the generated schema types predate some selections.
 */
import type { PayoutRequestStatus, AdminEventDetailQuery, EventApprovalTimelineQuery, EscrowByEventQuery, PayoutsByEventQuery, RefundsByEventQuery, CancelEventAsAdminMutation, ProvincesAdminQuery, CitiesAdminQuery } from '../../../../types/graphql';
import { useCallback } from 'react';
import { useMutation, useQuery } from '@apollo/client/react';
import type { DocumentNode } from '@apollo/client';
import {
  ADMIN_EVENTS_TABLE,
  ADMIN_EVENT_DETAIL,
  CITIES_ADMIN,
  ESCROW_BY_EVENT,
  EVENT_APPROVAL_TIMELINE,
  PAYOUTS_BY_EVENT,
  PROVINCES_ADMIN,
  REFUNDS_BY_EVENT,
} from './catalog-admin.queries';
import {
  ADD_EVENT_APPROVAL_COMMENT,
  CANCEL_EVENT,
  CREATE_CITY,
  CREATE_EVENT_CATEGORY,
  CREATE_PROVINCE,
  DELETE_CITY,
  DELETE_EVENT_CATEGORY,
  DELETE_PROVINCE,
  FEATURE_EVENT,
  SET_EVENT_CATEGORY_ACTIVE,
  UPDATE_CITY,
  UPDATE_EVENT_CATEGORY,
  UPDATE_PROVINCE,
} from './catalog-admin.mutations';

export type EventStatusValue =
  | 'DRAFT'
  | 'PENDING_APPROVAL'
  | 'CHANGES_REQUESTED'
  | 'APPROVED'
  | 'REJECTED'
  | 'PUBLISHED'
  | 'CANCELLED'
  | 'COMPLETED';

export interface AdminEventTableRow {
  id: string;
  title: string;
  status: EventStatusValue;
  published: boolean;
  featured: boolean;
  eventDateTime: string;
  endDateTime: string | null;
  organizerId: string;
  organizerName: string;
  organizationId: string | null;
  locationName: string | null;
  cityName: string | null;
  categoryId: string | null;
  totalCapacity: number;
  soldTickets: number;
  currency: string | null;
  minTicketPrice: number | string | null;
  submittedForApprovalAt: string | null;
  approvalDeadline: string | null;
  isOverdue: boolean | null;
  category: { id: string; name: string } | null;
}

export interface AdminEventsTableOptions {
  status?: string | null;
  searchQuery?: string | null;
  categoryId?: string | null;
  cityId?: string | null;
  organizerId?: string | null;
  eventDateAfter?: string | null;
  eventDateBefore?: string | null;
  page?: number;
  size?: number;
}

export interface OffsetPageMeta {
  totalCount: number;
  pageSize: number;
  currentPage: number;
  totalPages: number;
}

interface EventsTableData {
  events: {
    content: AdminEventTableRow[];
    pageNumber: number | null;
    pageSize: number | null;
    totalElements: number | null;
    totalPages: number | null;
  };
}

export function useAdminEventsTable(options: AdminEventsTableOptions = {}) {
  const size = options.size ?? 20;
  const { data, loading, error, refetch } = useQuery<EventsTableData>(ADMIN_EVENTS_TABLE, {
    variables: {
      filter: {
        status: options.status || null,
        searchQuery: options.searchQuery || null,
        categoryId: options.categoryId || null,
        cityId: options.cityId || null,
        organizerId: options.organizerId || null,
        eventDateAfter: options.eventDateAfter || null,
        eventDateBefore: options.eventDateBefore || null,
      },
      pagination: { page: options.page ?? 0, size, sortBy: 'eventDateTime', sortDirection: 'DESC' },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const page = data?.events;
  const pageInfo: OffsetPageMeta = {
    totalCount: page?.totalElements ?? 0,
    pageSize: page?.pageSize ?? size,
    currentPage: page?.pageNumber ?? 0,
    totalPages: page?.totalPages ?? 0,
  };
  return {
    events: page?.content ?? [],
    pageInfo,
    loading,
    error: error as Error | undefined,
    refetch: () => void refetch(),
  };
}

// ---------------------------------------------------------------- detail

export interface AdminEventTier {
  id: string;
  name: string;
  code: string;
  price: number | string;
  currency: string;
  quantity: number;
  soldQuantity: number;
  isActive: boolean;
  isHidden: boolean;
  earlyBirdPrice: number | string | null;
  earlyBirdEndsAt: string | null;
}

export interface AdminEventDetail extends Omit<AdminEventTableRow, 'category' | 'cityName'> {
  description: string;
  publishedAt: string | null;
  /** Resolved from identity through the federated `Organization`; contact fields are members and admins only. */
  organization: { id: string; name: string; businessEmail: string | null; businessPhone: string | null } | null;
  locationAddress: string | null;
  cityName: string | null;
  category: { id: string; name: string } | null;
  location: { city: string | null; province: string | null; country: string | null } | null;
  availableTickets: number;
  maxTicketPrice: number | string | null;
  refundPolicy: string | null;
  cancellationPolicy: string | null;
  bannerImageUrl: string | null;
  thumbnailImageUrl: string | null;
  galleryImages: string[] | null;
  approvedAt: string | null;
  approvedBy: string | null;
  rejectedAt: string | null;
  rejectedBy: string | null;
  rejectionReason: string | null;
  approvalBlockers: Array<'NO_PUBLISHED_TIER' | 'NO_LOCATION' | 'NO_CAPACITY'>;
  createdAt: string | null;
  updatedAt: string | null;
  ticketTiers: AdminEventTier[] | null;
}

export function useAdminEventDetail(id: string) {
  const { data, loading, error, refetch } = useQuery<AdminEventDetailQuery>(ADMIN_EVENT_DETAIL, {
    variables: { id },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { event: data?.event ?? null, loading, error: error as Error | undefined, refetch: () => void refetch() };
}

export interface ApprovalTimelineEntry {
  id: string;
  timestamp: string;
  action: string;
  actorName: string;
  actorRole: string | null;
  description: string;
  comments: string | null;
  isEscalationRelated: boolean;
}

export interface EventApprovalTimelineData {
  eventId: string;
  currentStatus: EventStatusValue;
  assignedReviewerName: string | null;
  submittedAt: string | null;
  slaDeadline: string | null;
  isOverdue: boolean;
  hoursUntilDeadline: number | null;
  submissionCount: number;
  hasActiveEscalation: boolean;
  escalation: { status: string } | null;
  timelineEvents: ApprovalTimelineEntry[];
}

export function useEventApprovalTimeline(eventId: string) {
  const { data, loading, error, refetch } = useQuery<EventApprovalTimelineQuery>(
    EVENT_APPROVAL_TIMELINE,
    { variables: { eventId }, fetchPolicy: 'cache-and-network', errorPolicy: 'all' },
  );
  return { timeline: data?.approvalTimeline ?? null, loading, error: error as Error | undefined, refetch: () => void refetch() };
}

// --------------------------------------------------- finance by event

export interface EventEscrow {
  id: string;
  accountNumber: string;
  eventId: string;
  currentBalance: number | string;
  totalDeposits: number | string;
  totalWithdrawals: number | string;
  totalRefunds: number | string;
  totalCommissions: number | string;
  pendingWithdrawals: number | string | null;
  currency: string;
  status: string;
}

export function useEventEscrow(eventId: string, skip = false) {
  const { data, loading, error } = useQuery<EscrowByEventQuery>(ESCROW_BY_EVENT, {
    variables: { eventId },
    skip,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { escrow: data?.escrowAccountByEvent ?? null, loading, error: error as Error | undefined };
}

export interface EventPayout {
  id: string;
  requestId: string;
  requestedAmount: number | string;
  currency: string;
  status: string;
  payoutMethod: string | null;
  requestedAt: string | null;
}

/** Payout statuses that block cancelling the event. */
export const OPEN_PAYOUT_STATUSES: readonly PayoutRequestStatus[] = ['PENDING', 'APPROVED', 'PROCESSING'];

export function useEventPayouts(eventId: string, skip = false) {
  const { data, loading, error } = useQuery<PayoutsByEventQuery>(PAYOUTS_BY_EVENT, {
    variables: { eventId },
    skip,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const payouts = data?.payoutRequests?.data ?? [];
  return {
    payouts,
    openPayout: payouts.find((p) => (OPEN_PAYOUT_STATUSES as readonly string[]).includes(p.status)) ?? null,
    loading,
    error: error as Error | undefined,
  };
}

export interface EventRefund {
  id: string;
  requestId: string;
  refundAmount: number | string;
  currency: string;
  status: string;
  requestedAt: string | null;
}

export function useEventRefunds(eventId: string, skip = false) {
  const { data, loading, error } = useQuery<RefundsByEventQuery>(REFUNDS_BY_EVENT, { variables: { eventId }, skip, fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  return {
    refunds: data?.refundRequests?.data ?? [],
    total: data?.refundRequests?.pagination?.totalCount ?? 0,
    loading,
    error: error as Error | undefined,
  };
}

// -------------------------------------------------------- mutations

export interface MutationOutcome {
  success: boolean;
  message: string | null;
}
const ok: MutationOutcome = { success: true, message: null };
const fail = (e: unknown, fallback: string): MutationOutcome => ({
  success: false,
  message: e instanceof Error && e.message ? e.message : fallback,
});

const EVENT_REFETCH = ['AdminEventsTable', 'AdminEventDetail'];

export function useFeatureEvent() {
  const [run, { loading }] = useMutation(FEATURE_EVENT, { refetchQueries: EVENT_REFETCH });
  const feature = useCallback(
    async (eventId: string, featured: boolean): Promise<MutationOutcome> => {
      try {
        await run({ variables: { eventId, featured } });
        return ok;
      } catch (e) {
        return fail(e, 'Could not change the featured flag.');
      }
    },
    [run],
  );
  return { feature, loading };
}

export interface CancelEventResult extends MutationOutcome {
  ticketsAffected: number;
}

export function useCancelEvent() {
  const [run, { loading }] = useMutation<CancelEventAsAdminMutation>(CANCEL_EVENT, {
    refetchQueries: EVENT_REFETCH,
  });
  const cancel = useCallback(
    async (eventId: string, reason: string): Promise<CancelEventResult> => {
      try {
        const { data } = await run({
          variables: {
            id: eventId,
            input: { eventId, reason, notifyAttendees: true, triggerRefunds: true, notifyBuyers: true, processRefundsImmediately: true },
          },
        });
        return { ...ok, ticketsAffected: data?.cancelEvent.ticketsAffected ?? 0 };
      } catch (e) {
        return { ...fail(e, 'Could not cancel the event.'), ticketsAffected: 0 };
      }
    },
    [run],
  );
  return { cancel, loading };
}

// ------------------------------------------------ provinces & cities

export interface ProvinceRow {
  id: string;
  name: string;
  code: string;
  country: string;
  cityCount: number | null;
  isActive: boolean;
}
export interface CityRow {
  id: string;
  name: string;
  code: string | null;
  provinceId: string | null;
  province: string | null;
  country: string | null;
  eventCount: number | null;
  isActive: boolean;
}

export function useProvincesAdmin() {
  const { data, loading, error, refetch } = useQuery<ProvincesAdminQuery>(PROVINCES_ADMIN, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { provinces: data?.provinces ?? [], loading, error: error as Error | undefined, refetch: () => void refetch() };
}

export function useCitiesAdmin() {
  const { data, loading, error, refetch } = useQuery<CitiesAdminQuery>(CITIES_ADMIN, {
    variables: { provinceId: null },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { cities: data?.cities ?? [], loading, error: error as Error | undefined, refetch: () => void refetch() };
}

function useRun(doc: DocumentNode, refetch: string[]) {
  const [run, { loading }] = useMutation(doc, { refetchQueries: refetch });
  const exec = useCallback(
    async (variables: Record<string, unknown>, fallback: string): Promise<MutationOutcome> => {
      try {
        await run({ variables });
        return ok;
      } catch (e) {
        return fail(e, fallback);
      }
    },
    [run],
  );
  return { exec, loading };
}

export interface CategoryInput {
  name: string;
  code: string;
  description?: string | null;
}

/**
 * Create / update / delete / (de)activate event categories, as `EVENT_CATEGORY` rows of the reference data.
 * `id` is the row's id, and the code cannot change after creation, so `update` takes the name and description only.
 */
export function useCategoryMutations() {
  const refetch = ['AdminEventCategories'];
  const create = useRun(CREATE_EVENT_CATEGORY, refetch);
  const update = useRun(UPDATE_EVENT_CATEGORY, refetch);
  const del = useRun(DELETE_EVENT_CATEGORY, refetch);
  const toggle = useRun(SET_EVENT_CATEGORY_ACTIVE, refetch);
  return {
    create: (input: CategoryInput) =>
      create.exec({ input: { type: 'EVENT_CATEGORY', ...input } }, 'Could not create the category.'),
    update: (id: string, input: Pick<CategoryInput, 'name' | 'description'>) =>
      update.exec({ id, input }, 'Could not save the category.'),
    remove: (id: string) => del.exec({ id }, 'Could not delete the category.'),
    setActive: (id: string, active: boolean) =>
      toggle.exec({ id, active }, active ? 'Could not activate the category.' : 'Could not deactivate the category.'),
    loading: create.loading || update.loading || del.loading || toggle.loading,
  };
}

export interface ProvinceInput {
  name: string;
  code: string;
  country: string;
}
export function useProvinceMutations() {
  const refetch = ['ProvincesAdmin', 'CitiesAdmin'];
  const create = useRun(CREATE_PROVINCE, refetch);
  const update = useRun(UPDATE_PROVINCE, refetch);
  const del = useRun(DELETE_PROVINCE, refetch);
  return {
    create: (input: ProvinceInput) => create.exec({ input }, 'Could not create the province.'),
    update: (id: string, input: Partial<ProvinceInput> & { isActive?: boolean }) =>
      update.exec({ id, input }, 'Could not save the province.'),
    remove: (id: string) => del.exec({ id }, 'Could not delete the province.'),
    loading: create.loading || update.loading || del.loading,
  };
}

export interface CityInput {
  name: string;
  code: string;
  provinceId: string;
  country: string;
}
export function useCityMutations() {
  const refetch = ['CitiesAdmin', 'ProvincesAdmin'];
  const create = useRun(CREATE_CITY, refetch);
  const update = useRun(UPDATE_CITY, refetch);
  const del = useRun(DELETE_CITY, refetch);
  return {
    create: (input: CityInput) => create.exec({ input }, 'Could not create the city.'),
    update: (id: string, input: Partial<CityInput> & { isActive?: boolean }) =>
      update.exec({ id, input }, 'Could not save the city.'),
    remove: (id: string) => del.exec({ id }, 'Could not delete the city.'),
    loading: create.loading || update.loading || del.loading,
  };
}

/** Reviewer comment on the approval timeline (internal by default). */
export function useAddApprovalComment() {
  const [run, { loading }] = useMutation(ADD_EVENT_APPROVAL_COMMENT, { refetchQueries: ['EventApprovalTimeline'] });
  const add = useCallback(
    async (eventId: string, comment: string): Promise<MutationOutcome> => {
      try {
        await run({ variables: { eventId, comment, isInternal: true } });
        return ok;
      } catch (e) {
        return fail(e, 'Could not post the comment.');
      }
    },
    [run],
  );
  return { add, loading };
}
