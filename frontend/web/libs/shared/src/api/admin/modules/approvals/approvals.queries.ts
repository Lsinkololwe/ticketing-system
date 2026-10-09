import { gql } from '@apollo/client';



/** Organizer applications (any status; the caller filters). */
export const ORGANIZER_APPLICATIONS = gql`
  query OrganizerApplications($status: OrganizationStatus, $search: String, $pagination: OffsetPaginationInput) {
    organizations(status: $status, search: $search, pagination: $pagination) {
      content {
        id
        name
        type
        status
        kybStatus
        description
        businessEmail
        businessPhone
        businessAddress { city province country }
        taxId
        businessRegistrationNumber
        rejectionReason
        submittedAt
        payoutAccountVerified
        owner { id fullName email contacts { valueMasked primary } }
        verificationDocuments { 
  id
  documentType
  fileName
  fileSize
  status
  uploadedAt
  rejectionReason }
        payoutConfig {
          verified
          isConfigured
          bankAccount { bankName maskedAccountNumber verified }
          mobileMoneyAccount { provider maskedPhoneNumber verified }
        }
      }
      pageInfo { currentPage pageSize totalCount hasNext hasPrevious }
    }
  }
`;

/** Organizations with their verification documents, for the documents queue. */
export const ORGANIZATION_DOCUMENTS = gql`
  query OrganizationDocuments($pagination: OffsetPaginationInput) {
    organizations(pagination: $pagination) {
      content {
        id
        name
        verificationDocuments { 
  id
  documentType
  fileName
  fileSize
  status
  uploadedAt
  rejectionReason }
      }
      pageInfo { currentPage pageSize totalCount hasNext hasPrevious }
    }
  }
`;

export const PENDING_APPROVAL_EVENTS = gql`
  query PendingApprovalEvents($pagination: OffsetPaginationInput) {
    events(
      filter: { statuses: [PENDING_APPROVAL] }
      pagination: $pagination
    ) {
      content {
        id
        title
        status
        organizerId
        organizerName
        eventDateTime
        cityName
        locationName
        totalCapacity
        minTicketPrice
        currency
        submittedForApprovalAt
        approvalBlockers
        category { id name }
      }
      pageNumber
      pageSize
      totalElements
      totalPages
      hasNext
      hasPrevious
    }
    pendingApprovalTimelines(pagination: { page: 0, size: 100 }) {
      content {
        eventId
        assignedReviewerId
        assignedReviewerName
        submittedAt
        slaDeadline
        isOverdue
        hoursUntilDeadline
        submissionCount
        hasActiveEscalation
        escalation { id status triggeredAt }
      }
    }
  }
`;



export const APPROVAL_TIMELINE = gql`
  query ApprovalTimeline($eventId: String!) {
    approvalTimeline(eventId: $eventId) { 
  eventId
  eventTitle
  organizerName
  currentStatus
  assignedReviewerId
  assignedReviewerName
  submittedAt
  slaDeadline
  isOverdue
  hoursUntilDeadline
  submissionCount
  totalComments
  hasActiveEscalation
  escalation {
    id
    status
    reason
    triggeredAt
    acknowledgedAt
    escalatedToName
    hoursOverdue
  }
  timelineEvents {
    id
    timestamp
    action
    actorName
    description
    comments
    isEscalationRelated
  } }
  }
`;

export const REVIEWER_CANDIDATES = gql`
  query ReviewerCandidates($role: UserType) {
    users(role: $role, pagination: { page: 0, size: 50 }) {
      content { id fullName }
    }
  }
`;

export const ASSIGN_EVENT_REVIEWER = gql`
  mutation AssignEventReviewer($input: AssignReviewerInput!) {
    assignEventReviewer(input: $input) { 
  eventId
  eventTitle
  organizerName
  currentStatus
  assignedReviewerId
  assignedReviewerName
  submittedAt
  slaDeadline
  isOverdue
  hoursUntilDeadline
  submissionCount
  totalComments
  hasActiveEscalation
  escalation {
    id
    status
    reason
    triggeredAt
    acknowledgedAt
    escalatedToName
    hoursOverdue
  }
  timelineEvents {
    id
    timestamp
    action
    actorName
    description
    comments
    isEscalationRelated
  } }
  }
`;

export const UNASSIGN_EVENT_REVIEWER = gql`
  mutation UnassignEventReviewer($eventId: ID!, $reason: String) {
    unassignEventReviewer(eventId: $eventId, reason: $reason) { 
  eventId
  eventTitle
  organizerName
  currentStatus
  assignedReviewerId
  assignedReviewerName
  submittedAt
  slaDeadline
  isOverdue
  hoursUntilDeadline
  submissionCount
  totalComments
  hasActiveEscalation
  escalation {
    id
    status
    reason
    triggeredAt
    acknowledgedAt
    escalatedToName
    hoursOverdue
  }
  timelineEvents {
    id
    timestamp
    action
    actorName
    description
    comments
    isEscalationRelated
  } }
  }
`;

export const ADD_APPROVAL_COMMENT = gql`
  mutation AddApprovalComment($eventId: ID!, $comment: String!, $isInternal: Boolean) {
    addApprovalComment(eventId: $eventId, comment: $comment, isInternal: $isInternal) { 
  eventId
  eventTitle
  organizerName
  currentStatus
  assignedReviewerId
  assignedReviewerName
  submittedAt
  slaDeadline
  isOverdue
  hoursUntilDeadline
  submissionCount
  totalComments
  hasActiveEscalation
  escalation {
    id
    status
    reason
    triggeredAt
    acknowledgedAt
    escalatedToName
    hoursOverdue
  }
  timelineEvents {
    id
    timestamp
    action
    actorName
    description
    comments
    isEscalationRelated
  } }
  }
`;

export const ACKNOWLEDGE_ESCALATION = gql`
  mutation AcknowledgeEscalation($escalationId: ID!, $notes: String) {
    acknowledgeEscalation(escalationId: $escalationId, notes: $notes) { id status }
  }
`;

export const TRIGGER_MANUAL_ESCALATION = gql`
  mutation TriggerManualEscalation($eventId: ID!, $reason: String!, $escalateTo: String!) {
    triggerManualEscalation(eventId: $eventId, reason: $reason, escalateTo: $escalateTo) { id status }
  }
`;
