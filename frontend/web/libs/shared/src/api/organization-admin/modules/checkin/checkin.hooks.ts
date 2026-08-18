'use client';

/**
 * React hooks for gate check-in.
 *
 * Types come from codegen — never hand-defined.
 *
 * @see specs/ticketing/003-validation-and-checkin/spec.md
 */

import { useCallback, useMemo, useRef } from 'react';
import { useQuery, useMutation } from '@apollo/client/react';
import type { FetchPolicy } from '@apollo/client';
import {
  EVENT_TICKET_HOLDERS,
  VALIDATE_TICKET,
  CHECK_IN_SUMMARY,
  RECENT_CHECK_INS,
  CHECK_IN_CONFLICTS,
  REVIEW_CONFLICT,
} from './checkin.queries';
import type {
  Ticket,
  CheckIn,
  CheckInConflict,
  CheckInSummary,
  CheckInOutcome,
  ValidationMethod,
} from '../../../../types/graphql';

/** The subset of Ticket the gate screen selects. */
export type TicketHolderVM = Pick<
  Ticket,
  | 'id'
  | 'ticketNumber'
  | 'buyerName'
  | 'buyerEmail'
  | 'ticketCategoryName'
  | 'status'
  | 'purchaseDate'
  | 'validatedAt'
>;

export type CheckInVM = Pick<
  CheckIn,
  'id' | 'ticketId' | 'ticketNumber' | 'method' | 'scannedBy' | 'deviceId' | 'recordedAt' | 'reason'
>;

export type CheckInConflictVM = Pick<
  CheckInConflict,
  | 'id'
  | 'ticketId'
  | 'presentedCode'
  | 'type'
  | 'method'
  | 'scannedBy'
  | 'deviceId'
  | 'scannedAt'
  | 'detectedAt'
  | 'originalCheckInAt'
  | 'status'
  | 'reviewNote'
  | 'reviewedAt'
>;

export interface ValidateTicketResult {
  /** ADMITTED, ALREADY_ADMITTED, WRONG_EVENT, INVALID_STATE, NOT_FOUND, ALREADY_RECORDED. */
  outcome: CheckInOutcome | 'UNREACHABLE';
  /** Whether this person goes in. */
  admitted: boolean;
  message: string;
  checkIn: Pick<CheckIn, 'id' | 'ticketNumber' | 'method' | 'recordedAt'> | null;
  conflict: Pick<CheckInConflict, 'id' | 'type' | 'originalCheckInAt'> | null;
  ticket: TicketHolderVM | null;
}

/**
 * Ticket holders for one event.
 *
 * `size` defaults high because a gate list is scanned by eye and searched
 * locally; paging through it mid-queue is worse than one larger fetch.
 */
export function useEventTicketHolders(
  eventId: string | null | undefined,
  options?: { size?: number; fetchPolicy?: FetchPolicy }
) {
  const { data, loading, error, refetch } = useQuery<{
    ticketsByEventOffsetPagination: {
      content: TicketHolderVM[];
      totalElements: number;
      hasNext: boolean;
    };
  }>(EVENT_TICKET_HOLDERS, {
    variables: { eventId, pagination: { page: 0, size: options?.size ?? 200 } },
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
    skip: !eventId,
  });

  return {
    holders: data?.ticketsByEventOffsetPagination.content ?? [],
    total: data?.ticketsByEventOffsetPagination.totalElements ?? 0,
    hasMore: data?.ticketsByEventOffsetPagination.hasNext ?? false,
    loading,
    error,
    refetch,
  };
}

/**
 * Validate a ticket at the gate.
 *
 * The server is the only authority on whether a ticket is admissible. This hook
 * deliberately performs no local check — not "is it in the list", not "was it
 * already scanned". A gate that decides locally will admit a refunded ticket
 * whenever the roster it loaded is a few minutes stale, and at a gate it always
 * is.
 *
 * `eventId` is required because a gate admits to ONE event. A ticket for
 * tomorrow's show is a valid ticket and must still be refused tonight.
 *
 * A network failure resolves to `outcome: 'UNREACHABLE'` rather than throwing,
 * so the steward is told the ticket was NOT checked in instead of being left to
 * guess from a blank screen. It deliberately does not read as a refusal: the
 * ticket may be perfectly good and the person should not be turned away on the
 * strength of a dropped connection.
 */
export function useValidateTicket(eventId: string | null | undefined) {
  const [mutate, { loading }] = useMutation<{
    validateTicket: {
      outcome: CheckInOutcome;
      admitted: boolean;
      message: string;
      checkIn: Pick<CheckIn, 'id' | 'ticketNumber' | 'method' | 'recordedAt'> | null;
      conflict: Pick<CheckInConflict, 'id' | 'type' | 'originalCheckInAt'> | null;
      ticket: TicketHolderVM | null;
    };
  }>(VALIDATE_TICKET);

  const validateTicket = useCallback(
    async (
      code: string,
      options?: { method?: ValidationMethod; reason?: string; deviceId?: string }
    ): Promise<ValidateTicketResult> => {
      if (!eventId) {
        return {
          outcome: 'UNREACHABLE',
          admitted: false,
          message: 'No event selected.',
          checkIn: null,
          conflict: null,
          ticket: null,
        };
      }

      try {
        const result = await mutate({
          variables: {
            input: {
              eventId,
              code,
              method: options?.method ?? 'QR_ONLINE',
              reason: options?.reason ?? null,
              deviceId: options?.deviceId ?? null,
            },
          },
        });

        const payload = result.data?.validateTicket;
        if (!payload) {
          return {
            outcome: 'UNREACHABLE',
            admitted: false,
            message: 'The server did not answer. The ticket was not checked in.',
            checkIn: null,
            conflict: null,
            ticket: null,
          };
        }

        return {
          outcome: payload.outcome,
          admitted: payload.admitted,
          message: payload.message,
          checkIn: payload.checkIn,
          conflict: payload.conflict,
          ticket: payload.ticket,
        };
      } catch (error) {
        return {
          outcome: 'UNREACHABLE',
          admitted: false,
          message: 'Could not reach the server. The ticket was not checked in.',
          checkIn: null,
          conflict: null,
          ticket: null,
        };
      }
    },
    [eventId, mutate]
  );

  return { validateTicket, loading };
}

/**
 * Attendance for one event's gate.
 *
 * Polls while the gate is open. The interval is the spec's 15 seconds, which is
 * a compromise: the summary is a multi-collection aggregation on the hottest
 * write path of the evening, and every staff device watching pays for it.
 */
export function useCheckInSummary(
  eventId: string | null | undefined,
  options?: { pollIntervalMs?: number }
) {
  const { data, loading, error, refetch } = useQuery<{ checkInSummary: CheckInSummary }>(
    CHECK_IN_SUMMARY,
    {
      variables: { eventId },
      fetchPolicy: 'cache-and-network',
      errorPolicy: 'all',
      notifyOnNetworkStatusChange: true,
      pollInterval: options?.pollIntervalMs ?? 15_000,
      skip: !eventId,
    }
  );

  const summary = data?.checkInSummary ?? null;

  return useMemo(
    () => ({
      summary,
      /**
       * Share of issued tickets admitted. Null rather than 0 when nothing has
       * been issued — a rate against an empty denominator is undefined, and
       * showing 0% would assert a finding the data does not support.
       */
      admittedRate:
        summary && summary.issued > 0 ? summary.admitted / summary.issued : null,
      /**
       * Share of admissions that bypassed the QR. The spec alerts above 10%,
       * which usually means the scanning is broken rather than that stewards
       * are careless.
       */
      manualRate:
        summary && summary.admitted > 0
          ? summary.manualAdmissions / summary.admitted
          : null,
      loading,
      error,
      refetch,
    }),
    [summary, loading, error, refetch]
  );
}

/** Most recent admissions, newest first. The server caps this at 100. */
export function useRecentCheckIns(
  eventId: string | null | undefined,
  options?: { limit?: number; pollIntervalMs?: number }
) {
  const { data, loading, error, refetch } = useQuery<{ recentCheckIns: CheckInVM[] }>(
    RECENT_CHECK_INS,
    {
      variables: { eventId, limit: options?.limit ?? 25 },
      fetchPolicy: 'cache-and-network',
      errorPolicy: 'all',
      notifyOnNetworkStatusChange: true,
      pollInterval: options?.pollIntervalMs ?? 15_000,
      skip: !eventId,
    }
  );

  return {
    checkIns: data?.recentCheckIns ?? [],
    loading,
    error,
    refetch,
  };
}

/**
 * Scans that were refused.
 *
 * Worth a screen rather than a log line: for a duplicate admitted offline by a
 * second device, this is the ONLY record that a second person walked in.
 */
export function useCheckInConflicts(
  eventId: string | null | undefined,
  options?: { page?: number; size?: number }
) {
  const { data, loading, error, refetch } = useQuery<{
    checkInConflicts: {
      content: CheckInConflictVM[];
      totalElements: number;
      page: number;
      size: number;
    };
  }>(CHECK_IN_CONFLICTS, {
    variables: {
      eventId,
      pagination: { page: options?.page ?? 0, size: options?.size ?? 20 },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
    notifyOnNetworkStatusChange: true,
    skip: !eventId,
  });

  return {
    conflicts: data?.checkInConflicts.content ?? [],
    total: data?.checkInConflicts.totalElements ?? 0,
    loading,
    error,
    refetch,
  };
}

/** Annotate a conflict. Refetches rather than patching the cache by hand. */
export function useReviewConflict() {
  const [mutate, { loading }] = useMutation<{
    reviewConflict: Pick<CheckInConflict, 'id' | 'status' | 'reviewNote' | 'reviewedAt'>;
  }>(REVIEW_CONFLICT, { refetchQueries: [CHECK_IN_CONFLICTS, CHECK_IN_SUMMARY] });

  const reviewConflict = useCallback(
    async (id: string, note: string) => {
      try {
        const result = await mutate({ variables: { id, note } });
        return { success: true, conflict: result.data?.reviewConflict ?? null, error: null };
      } catch (error) {
        return {
          success: false,
          conflict: null,
          error: error instanceof Error ? error.message : String(error),
        };
      }
    },
    [mutate]
  );

  return { reviewConflict, loading };
}

/**
 * A stable device id for this browser, for the gate reconciliation report.
 *
 * Per tab rather than persisted: it exists to tell two staff devices apart in
 * the conflict report, and inventing a durable identifier for that would be
 * storing more about the user than the job needs.
 */
export function useGateDeviceId(): string {
  const ref = useRef<string | null>(null);
  if (ref.current === null) {
    ref.current =
      typeof crypto !== 'undefined' && 'randomUUID' in crypto
        ? `web-${crypto.randomUUID().slice(0, 8)}`
        : `web-${Math.floor(Math.random() * 1e8).toString(16)}`;
  }
  return ref.current;
}
