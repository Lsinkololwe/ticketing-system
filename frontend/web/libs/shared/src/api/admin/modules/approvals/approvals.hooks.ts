'use client';

import type { OrganizerApplicationsQuery, OrganizationDocumentsQuery, PendingApprovalEventsQuery, ApprovalTimelineQuery, ReviewerCandidatesQuery } from '../../../../types/graphql';
import { useMutation, useQuery } from '@apollo/client/react';
import {
  ACKNOWLEDGE_ESCALATION,
  ADD_APPROVAL_COMMENT,
  APPROVAL_TIMELINE,
  ASSIGN_EVENT_REVIEWER,
  ORGANIZATION_DOCUMENTS,
  ORGANIZER_APPLICATIONS,
  PENDING_APPROVAL_EVENTS,
  REVIEWER_CANDIDATES,
  TRIGGER_MANUAL_ESCALATION,
  UNASSIGN_EVENT_REVIEWER,
} from './approvals.queries';

export interface ApprovalDocument {
  id: string;
  documentType: string;
  fileName: string | null;
  fileSize: number | null;
  status: string;
  uploadedAt: string;
  rejectionReason: string | null;
}

export interface PayoutAccountSummary {
  verified: boolean;
  isConfigured: boolean;
  bankAccount: { bankName: string | null; maskedAccountNumber: string | null; verified: boolean } | null;
  mobileMoneyAccount: { provider: string | null; maskedPhoneNumber: string | null; verified: boolean } | null;
}

export interface OrganizerApplication {
  id: string;
  name: string;
  type: string | null;
  status: string;
  kybStatus: string | null;
  description: string | null;
  businessEmail: string | null;
  businessPhone: string | null;
  businessAddress: { city: string | null; province: string | null; country: string | null } | null;
  taxId: string | null;
  businessRegistrationNumber: string | null;
  rejectionReason: string | null;
  submittedAt: string | null;
  payoutAccountVerified: boolean;
  owner: { id: string; fullName: string | null; email?: string | null; contacts?: { valueMasked: string; primary: boolean }[] | null } | null;
  verificationDocuments: ApprovalDocument[] | null;
  payoutConfig: PayoutAccountSummary | null;
}


export function useOrganizerApplications(opts: { status?: string | null; search?: string | null; page?: number; size?: number } = {}) {
  const { data, loading, error, refetch } = useQuery<OrganizerApplicationsQuery>(ORGANIZER_APPLICATIONS, {
    variables: {
      status: opts.status || null,
      search: opts.search || null,
      pagination: { page: opts.page ?? 0, size: opts.size ?? 100, sortBy: 'submittedAt', sortDirection: 'ASC' },
    },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    applications: data?.organizations?.content ?? [],
    pageInfo: data?.organizations?.pageInfo,
    loading,
    error,
    refetch,
  };
}

export interface OrganizationDocumentsRow {
  id: string;
  name: string;
  verificationDocuments: ApprovalDocument[] | null;
}

export function useApprovalOrganizationDocuments(page = 0, size = 100) {
  const { data, loading, error, refetch } = useQuery<OrganizationDocumentsQuery>(ORGANIZATION_DOCUMENTS, {
    variables: { pagination: { page, size } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { organizations: data?.organizations?.content ?? [], loading, error, refetch };
}

export interface PendingEventRow {
  id: string;
  title: string;
  status: string;
  organizerId: string | null;
  organizerName: string | null;
  eventDateTime: string | null;
  cityName: string | null;
  locationName: string | null;
  totalCapacity: number | null;
  minTicketPrice: number | string | null;
  currency: string | null;
  submittedForApprovalAt: string | null;
  approvalBlockers: string[];
  category: { id: string; name: string } | null;
}

export interface PendingTimelineRow {
  eventId: string;
  assignedReviewerId: string | null;
  assignedReviewerName: string | null;
  submittedAt: string | null;
  slaDeadline: string | null;
  isOverdue: boolean;
  hoursUntilDeadline: number | null;
  submissionCount: number;
  hasActiveEscalation: boolean;
  escalation: { id: string; status: string; triggeredAt: string } | null;
}

export function usePendingApprovalEvents(page = 0, size = 100) {
  const { data, loading, error, refetch } = useQuery<PendingApprovalEventsQuery>(PENDING_APPROVAL_EVENTS, {
    variables: { pagination: { page, size, sortBy: 'submittedForApprovalAt', sortDirection: 'ASC' } },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return {
    events: data?.events?.content ?? [],
    timelines: data?.pendingApprovalTimelines?.content ?? [],
    loading,
    error,
    refetch,
  };
}

export interface TimelineEntry {
  id: string;
  timestamp: string;
  action: string;
  actorName: string;
  description: string;
  comments: string | null;
  isEscalationRelated: boolean;
}

export interface ApprovalTimelineDetail {
  eventId: string;
  eventTitle: string;
  organizerName: string;
  currentStatus: string;
  assignedReviewerId: string | null;
  assignedReviewerName: string | null;
  submittedAt: string | null;
  slaDeadline: string | null;
  isOverdue: boolean;
  hoursUntilDeadline: number | null;
  submissionCount: number;
  totalComments: number;
  hasActiveEscalation: boolean;
  escalation: {
    id: string;
    status: string;
    reason: string;
    triggeredAt: string;
    acknowledgedAt: string | null;
    escalatedToName: string;
    hoursOverdue: number;
  } | null;
  timelineEvents: TimelineEntry[];
}

export function useApprovalTimeline(eventId: string | null) {
  const { data, loading, error, refetch } = useQuery<ApprovalTimelineQuery>(APPROVAL_TIMELINE, {
    variables: { eventId: eventId ?? '' },
    skip: !eventId,
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  return { timeline: data?.approvalTimeline ?? null, loading, error, refetch };
}

export function useReviewerCandidates(role: string | null = 'ADMIN') {
  const { data, loading } = useQuery<ReviewerCandidatesQuery>(REVIEWER_CANDIDATES, {
    variables: { role },
    fetchPolicy: 'cache-first',
    errorPolicy: 'all',
  });
  return { reviewers: data?.users?.content ?? [], loading };
}

const REFRESH = [{ query: PENDING_APPROVAL_EVENTS, variables: { pagination: { page: 0, size: 100, sortBy: 'submittedForApprovalAt', sortDirection: 'ASC' } } }];

/** Reviewer claim/assign/comment/escalation mutations for the event approvals queue. */
export function useApprovalWorkflow() {
  const opts = { errorPolicy: 'none' as const, refetchQueries: REFRESH, awaitRefetchQueries: true };
  const [assignM, assignS] = useMutation(ASSIGN_EVENT_REVIEWER, opts);
  const [unassignM, unassignS] = useMutation(UNASSIGN_EVENT_REVIEWER, opts);
  const [commentM, commentS] = useMutation(ADD_APPROVAL_COMMENT, { errorPolicy: 'none' });
  const [ackM, ackS] = useMutation(ACKNOWLEDGE_ESCALATION, opts);
  const [escM, escS] = useMutation(TRIGGER_MANUAL_ESCALATION, opts);
  return {
    assign: (eventId: string, reviewerId: string, reviewerName: string) =>
      assignM({ variables: { input: { eventId, reviewerId, reviewerName } } }),
    unassign: (eventId: string, reason?: string) => unassignM({ variables: { eventId, reason: reason ?? null } }),
    comment: (eventId: string, comment: string) => commentM({ variables: { eventId, comment, isInternal: true } }),
    acknowledge: (escalationId: string, notes?: string) => ackM({ variables: { escalationId, notes: notes ?? null } }),
    escalate: (eventId: string, reason: string, escalateTo: string) =>
      escM({ variables: { eventId, reason, escalateTo } }),
    busy: assignS.loading || unassignS.loading || commentS.loading || ackS.loading || escS.loading,
  };
}
