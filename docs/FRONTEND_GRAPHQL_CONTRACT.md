# Frontend GraphQL contract

Generated from the subgraph schemas. Do not edit by hand — it is rewritten whenever a `.graphqls` file changes.

## How the frontend consumes this

- One endpoint: the Apollo Router at `/graphql` behind the gateway. Never call a subgraph port directly — the router owns query planning.
- **Types come from codegen, always.** `cd frontend/web && npm run codegen`. A hand-written TypeScript type for a GraphQL shape is a defect.
- Send the JWT as `Authorization: Bearer <token>`. Every subgraph validates it independently against JWKS, so a token the gateway accepted can still be refused downstream — handle 'UNAUTHENTICATED' on any operation.
- Branch on `extensions.errorCode`, never on `message`. Read `extensions.retryable` to decide whether to offer a retry.
- `TOKEN_REVOKED` means sign the user out; it will not resolve by retrying.
- Money-moving mutations take an `idempotencyKey`. Generate it once per user intent and reuse it across retries of that same intent.
- Dashboards poll (D-12); there are no subscriptions on this path. Use a visibility-aware interval and stop polling on a hidden tab.

## Operations by required role (589 total)

### `ADMIN` — 96 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `addPaymentAttemptNote` | `depositId: String!, note: String!` |
| booking | Mutation | `adminUpdateTicket` | `ticketId: ID!, input: AdminTicketUpdateInput!` |
| booking | Mutation | `approveRefundRequest` | `refundRequestId: ID!, reviewComments: String` |
| booking | Mutation | `bulkApproveRefunds` | `refundRequestIds: [ID!]!` |
| booking | Mutation | `bulkCancelTickets` | `ticketIds: [ID!]!, reason: String!` |
| booking | Mutation | `cancelPaymentAttempt` | `depositId: String!, reason: String!` |
| booking | Mutation | `cancelRefundRequest` | `refundRequestId: ID!, reason: String!` |
| booking | Mutation | `createAdminRefundRequest` | `ticketId: ID!, reason: String!, bypassApproval: Boolean` |
| booking | Mutation | `processRefundRequest` | `refundRequestId: ID!` |
| booking | Mutation | `regenerateTicketQrCode` | `ticketId: ID!` |
| booking | Mutation | `rejectRefundRequest` | `refundRequestId: ID!, rejectionReason: String!` |
| booking | Mutation | `setPaymentAttemptReviewStatus` | `depositId: String!, reviewStatus: String!, notes: String` |
| booking | Mutation | `verifyPaymentWithGateway` | `depositId: String!` |
| catalog | Mutation | `acknowledgeEscalation` | `escalationId: ID!, notes: String` |
| catalog | Mutation | `activateEventCategory` | `id: ID!` |
| catalog | Mutation | `addApprovalComment` | `eventId: ID!, comment: String!, isInternal: Boolean = true` |
| catalog | Mutation | `approveEvent` | `eventId: ID!, comments: String` |
| catalog | Mutation | `assignEventReviewer` | `input: AssignReviewerInput!` |
| catalog | Mutation | `createCity` | `input: CreateCityInput!` |
| catalog | Mutation | `createEventCategory` | `input: CreateEventCategoryInput!` |
| catalog | Mutation | `createProvince` | `input: CreateProvinceInput!` |
| catalog | Mutation | `createReferenceData` | `input: CreateReferenceDataInput!` |
| catalog | Mutation | `deactivateEventCategory` | `id: ID!` |
| catalog | Mutation | `deleteCity` | `id: ID!` |
| catalog | Mutation | `deleteEventCategory` | `id: ID!` |
| catalog | Mutation | `deleteProvince` | `id: ID!` |
| catalog | Mutation | `deleteReferenceData` | `id: ID!` |
| catalog | Mutation | `featureEvent` | `eventId: ID!, featured: Boolean!` |
| catalog | Mutation | `rejectEvent` | `eventId: ID!, comments: String!` |
| catalog | Mutation | `requestEventChanges` | `eventId: ID!, comments: String!` |
| catalog | Mutation | `resolveEscalation` | `input: ResolveEscalationInput!` |
| catalog | Mutation | `sendBulkEventPublishReminders` | `eventIds: [ID!]!, triggeredBy: String!` |
| catalog | Mutation | `sendEventPublishReminder` | `eventId: ID!, triggeredBy: String!` |
| catalog | Mutation | `setReferenceDataActive` | `id: ID!, active: Boolean!` |
| catalog | Mutation | `triggerManualEscalation` | `eventId: ID!, reason: String!, escalateTo: String!` |
| catalog | Mutation | `unassignEventReviewer` | `eventId: ID!, reason: String` |
| catalog | Mutation | `updateCity` | `id: ID!, input: UpdateCityInput!` |
| catalog | Mutation | `updateEventCategory` | `id: ID!, input: UpdateEventCategoryInput!` |
| catalog | Mutation | `updatePlatformConfiguration` | `input: UpdatePlatformConfigurationInput!` |
| catalog | Mutation | `updateProvince` | `id: ID!, input: UpdateProvinceInput!` |
| catalog | Mutation | `updateReferenceData` | `id: ID!, input: UpdateReferenceDataInput!` |
| catalog | Query | `activeEscalationsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `activeEscalationsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `activeEventCategoriesOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `approvalEscalation` | `id: ID!` |
| catalog | Query | `approvalStats` | `—` |
| catalog | Query | `approvalTimeline` | `eventId: String!` |
| catalog | Query | `approvalTimelinesByOrganizerCursorPagination` | `organizerId: String!, pagination: CursorPaginationInput` |
| catalog | Query | `approvalTimelinesByOrganizerOffsetPagination` | `organizerId: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `approvalTimelinesCursorPagination` | `filter: ApprovalTimelineFilterInput, pagination: CursorPaginationInput` |
| catalog | Query | `approvalTimelinesOffsetPagination` | `filter: ApprovalTimelineFilterInput, pagination: OffsetPaginationInput` |
| catalog | Query | `approvedNotPublishedEventsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `approvedNotPublishedEventsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `cancelledEventsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `cancelledEventsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `catalogPendingCounts` | `—` |
| catalog | Query | `citiesByCountryOffsetPagination` | `country: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `citiesByProvinceOffsetPagination` | `provinceId: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `citiesOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `completedEventsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `completedEventsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `eventCategoriesOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `eventCount` | `—` |
| catalog | Query | `eventCountByCategory` | `categoryId: String!` |
| catalog | Query | `eventCountByCity` | `city: String!` |
| catalog | Query | `eventCountByStatus` | `status: EventStatus!` |
| catalog | Query | `eventStats` | `—` |
| catalog | Query | `eventsByCategoryOffsetPagination` | `categoryId: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `eventsByCityOffsetPagination` | `city: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `eventsByDateRangeOffsetPagination` | `startDate: DateTime!, endDate: DateTime!, pagination: OffsetPaginationInput` |
| catalog | Query | `eventsByPriceRangeOffsetPagination` | `minPrice: BigDecimal, maxPrice: BigDecimal, pagination: OffsetPaginationInput` |
| catalog | Query | `eventsByStatusCursorPagination` | `status: EventStatus!, pagination: CursorPaginationInput` |
| catalog | Query | `eventsByStatusOffsetPagination` | `status: EventStatus!, pagination: OffsetPaginationInput` |
| catalog | Query | `eventsCursorPagination` | `filter: EventFilterInput, pagination: CursorPaginationInput` |
| catalog | Query | `eventsOffsetPagination` | `filter: EventFilterInput, pagination: OffsetPaginationInput` |
| catalog | Query | `exportEventData` | `eventId: ID!, format: ExportFormat!` |
| catalog | Query | `exportEventsReport` | `filter: EventFilterInput!, format: ExportFormat!` |
| catalog | Query | `featuredEventsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `freeEventsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `myEscalationsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `myEscalationsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `overdueApprovalEventsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `overdueApprovalEventsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `overdueApprovalTimelinesCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `overdueApprovalTimelinesOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `pendingApprovalEventsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `pendingApprovalEventsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `pendingApprovalTimelinesCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `pendingApprovalTimelinesOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `platformConfiguration` | `—` |
| catalog | Query | `provincesByCountryOffsetPagination` | `country: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `provincesOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `referenceDataOffsetPagination` | `type: ReferenceType!, pagination: OffsetPaginationInput` |
| catalog | Query | `searchCitiesOffsetPagination` | `query: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `searchEventCategoriesOffsetPagination` | `query: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `searchProvincesOffsetPagination` | `query: String!, pagination: OffsetPaginationInput` |

### `CUSTOMER` — 4 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `cancelReservation` | `reservationId: ID!` |
| booking | Mutation | `createUserRefundRequest` | `input: CreateRefundRequestInput!` |
| booking | Mutation | `payReservation` | `input: PayReservationInput!` |
| booking | Mutation | `reserveTickets` | `input: ReserveTicketsInput!` |

### `INTERNAL` — 7 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `expireTimedOutPayments` | `—` |
| booking | Mutation | `initiatePaymentAttempt` | `input: InitiatePaymentAttemptInput!` |
| booking | Mutation | `markPaymentFulfilled` | `input: MarkPaymentFulfilledInput!` |
| booking | Mutation | `pollPendingPayments` | `—` |
| booking | Mutation | `processPaymentWebhook` | `input: ProcessPaymentWebhookInput!` |
| booking | Mutation | `retryPaymentAttempt` | `depositId: String!` |
| catalog | Mutation | `completeEvent` | `id: ID!` |

### `ORGANIZER` — 41 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `cancelTicket` | `ticketNumber: String!, reason: String!` |
| booking | Mutation | `refundTicket` | `ticketNumber: String!, reason: String!` |
| booking | Mutation | `reviewConflict` | `id: ID!, note: String!` |
| booking | Mutation | `uploadScans` | `inputs: [ValidateTicketInput!]!` |
| booking | Mutation | `validateTicket` | `input: ValidateTicketInput!` |
| booking | Query | `checkInConflicts` | `eventId: ID!, pagination: OffsetPaginationInput` |
| booking | Query | `checkInSummary` | `eventId: ID!` |
| booking | Query | `payoutEligibility` | `eventId: ID!` |
| booking | Query | `recentCheckIns` | `eventId: ID!, limit: Int = 25` |
| catalog | Mutation | `activateTicketTier` | `tierId: ID!` |
| catalog | Mutation | `cancelEvent` | `id: ID!, input: EventCancellationInput!` |
| catalog | Mutation | `createEvent` | `input: CreateEventInput!` |
| catalog | Mutation | `createTicketTier` | `eventId: ID!, input: CreateTicketTierInput!` |
| catalog | Mutation | `deactivateTicketTier` | `tierId: ID!` |
| catalog | Mutation | `deleteEvent` | `id: ID!` |
| catalog | Mutation | `deleteTicketTier` | `tierId: ID!` |
| catalog | Mutation | `duplicateEvent` | `eventId: ID!, newTitle: String!` |
| catalog | Mutation | `publishEvent` | `id: ID!` |
| catalog | Mutation | `reorderTicketTiers` | `eventId: ID!, tierIds: [ID!]!` |
| catalog | Mutation | `submitEventForApproval` | `eventId: ID!` |
| catalog | Mutation | `unpublishEvent` | `id: ID!` |
| catalog | Mutation | `updateEvent` | `id: ID!, input: UpdateEventInput!` |
| catalog | Mutation | `updateEventAccessibility` | `eventId: ID!, input: EventAccessibilityInput!` |
| catalog | Mutation | `updateEventCapacity` | `eventId: ID!, newCapacity: Int!` |
| catalog | Mutation | `updateTicketTier` | `tierId: ID!, input: UpdateTicketTierInput!` |
| catalog | Query | `allowedStatusTransitions` | `eventId: String!` |
| catalog | Query | `draftEventsCursorPagination` | `organizerId: String!, pagination: CursorPaginationInput` |
| catalog | Query | `draftEventsOffsetPagination` | `organizerId: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `eventCountByOrganizer` | `organizerId: String!` |
| catalog | Query | `eventLifecycle` | `eventId: String!` |
| catalog | Query | `eventStatistics` | `eventId: ID!` |
| catalog | Query | `eventTicketTiers` | `eventId: ID!, includeHidden: Boolean = false` |
| catalog | Query | `eventsByOrganizerOffsetPagination` | `organizerId: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `myDraftEventsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `myEventCount` | `—` |
| catalog | Query | `myEventCountByStatus` | `status: EventStatus!` |
| catalog | Query | `myEventsOffsetPagination` | `filter: OrganizerEventFilterInput, pagination: OffsetPaginationInput` |
| catalog | Query | `publishedEventsOffsetPagination` | `pagination: OffsetPaginationInput` |
| catalog | Query | `searchEventsOffsetPagination` | `query: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `ticketTierStatistics` | `eventId: ID!, tierId: ID!` |
| catalog | Query | `upcomingEventsOffsetPagination` | `pagination: OffsetPaginationInput` |

### `PUBLIC (by omission)` — 441 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `acceptChargeback` | `id: ID!, reason: String` |
| booking | Mutation | `activatePromoCode` | `id: ID!` |
| booking | Mutation | `approvePayoutRequest` | `payoutRequestId: ID!, notes: String` |
| booking | Mutation | `bulkMarkPayoutsForReview` | `payoutRequestIds: [ID!]!, issueType: PayoutIssueType!, notes: String` |
| booking | Mutation | `bulkRetryFailedPayouts` | `payoutRequestIds: [ID!]!` |
| booking | Mutation | `cancelPayoutRequest` | `payoutRequestId: ID!, reason: String!` |
| booking | Mutation | `closeEscrowAccount` | `accountId: ID!, reason: String!` |
| booking | Mutation | `completePayoutRequest` | `payoutRequestId: ID!, bankReference: String!` |
| booking | Mutation | `completeReconciliation` | `runId: ID!, notes: String` |
| booking | Mutation | `createBankAccount` | `input: CreateBankAccountInput!` |
| booking | Mutation | `createChartOfAccountsEntry` | `input: CreateChartOfAccountsInput!` |
| booking | Mutation | `createEscrowAccount` | `input: CreateEscrowAccountInput!` |
| booking | Mutation | `createJournalEntry` | `input: CreateJournalEntryInput!` |
| booking | Mutation | `createPayoutRequest` | `input: CreatePayoutRequestInput!` |
| booking | Mutation | `createPlatformAccount` | `accountType: PlatformAccountType!, name: String!, currency: String!` |
| booking | Mutation | `createPromoCode` | `input: CreatePromoCodeInput!` |
| booking | Mutation | `creditPlatformAccount` | `id: ID!, amount: BigDecimal!, description: String!` |
| booking | Mutation | `deactivateChartOfAccountsEntry` | `id: ID!` |
| booking | Mutation | `deactivatePromoCode` | `id: ID!` |
| booking | Mutation | `debitPlatformAccount` | `id: ID!, amount: BigDecimal!, description: String!` |
| booking | Mutation | `deleteBankAccount` | `id: ID!` |
| booking | Mutation | `deletePromoCode` | `id: ID!` |
| booking | Mutation | `disputeChargeback` | `id: ID!, input: DisputeChargebackInput!` |
| booking | Mutation | `escalatePayoutRequest` | `payoutRequestId: ID!, reason: String!` |
| booking | Mutation | `failReconciliation` | `runId: ID!, reason: String!` |
| booking | Mutation | `forceExpireReservation` | `reservationId: ID!` |
| booking | Mutation | `lockEscrowAccount` | `accountId: ID!, lockUntil: DateTime!, reason: String!` |
| booking | Mutation | `markPayoutEligible` | `accountId: ID!` |
| booking | Mutation | `markPayoutForReview` | `payoutRequestId: ID!, issueType: PayoutIssueType!, notes: String` |
| booking | Mutation | `postJournalEntry` | `id: ID!` |
| booking | Mutation | `processPayoutRequest` | `payoutRequestId: ID!` |
| booking | Mutation | `receiveChargeback` | `input: ReceiveChargebackInput!` |
| booking | Mutation | `recordChargebackOutcome` | `id: ID!, won: Boolean!, notes: String` |
| booking | Mutation | `recordGatewaySettlement` | `input: RecordGatewaySettlementInput!` |
| booking | Mutation | `recoverChargebackFunds` | `id: ID!, input: RecoverChargebackInput!` |
| booking | Mutation | `rejectPayoutRequest` | `payoutRequestId: ID!, rejectionReason: String!` |
| booking | Mutation | `resolvePayoutIssue` | `payoutRequestId: ID!, resolutionType: PayoutResolutionType!, notes: String!, newBankAccoun…` |
| booking | Mutation | `resolveReconciliationItem` | `runId: ID!, input: ResolveReconciliationItemInput!` |
| booking | Mutation | `resumePayoutRequest` | `payoutRequestId: ID!` |
| booking | Mutation | `retryPayoutRequest` | `payoutRequestId: ID!` |
| booking | Mutation | `reverseJournalEntry` | `id: ID!, reason: String!` |
| booking | Mutation | `seedChartOfAccounts` | `—` |
| booking | Mutation | `setDefaultBankAccount` | `id: ID!` |
| booking | Mutation | `startChargebackReview` | `id: ID!, notes: String` |
| booking | Mutation | `startReconciliation` | `input: StartReconciliationInput!` |
| booking | Mutation | `unlockEscrowAccount` | `accountId: ID!, reason: String!` |
| booking | Mutation | `updateBankAccount` | `id: ID!, input: UpdateBankAccountInput!` |
| booking | Mutation | `updateChartOfAccountsEntry` | `id: ID!, input: CreateChartOfAccountsInput!` |
| booking | Mutation | `updateEscrowAccountStatus` | `accountId: ID!, status: EscrowAccountStatus!, reason: String` |
| booking | Mutation | `updatePromoCode` | `id: ID!, input: UpdatePromoCodeInput!` |
| booking | Mutation | `verifyBankAccount` | `id: ID!` |
| booking | Mutation | `verifyEscrowJournalConsistency` | `eventId: ID!` |
| booking | Query | `accountBalance` | `accountCode: String!, asOf: DateTime` |
| booking | Query | `accountSummary` | `accountId: String!` |
| booking | Query | `bankAccount` | `id: ID!` |
| booking | Query | `bankAccountsByOrganizer` | `organizerId: String!` |
| booking | Query | `bookingPendingCounts` | `—` |
| booking | Query | `calculateRefundAmount` | `ticketId: String!` |
| booking | Query | `chargeback` | `id: ID!` |
| booking | Query | `chargebackByChargebackId` | `chargebackId: String!` |
| booking | Query | `chargebackStats` | `organizerId: String, eventId: String` |
| booking | Query | `chargebacksByEvent` | `eventId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `chargebacksByOrganizer` | `organizerId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `chargebacksOffsetPagination` | `filter: ChargebackFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `chargebacksPendingRecovery` | `—` |
| booking | Query | `chartOfAccounts` | `—` |
| booking | Query | `chartOfAccountsByCode` | `accountCode: String!` |
| booking | Query | `chartOfAccountsByType` | `accountType: AccountType!` |
| booking | Query | `chartOfAccountsEntry` | `id: ID!` |
| booking | Query | `chartOfAccountsOffsetPagination` | `pagination: OffsetPaginationInput` |
| booking | Query | `confirmedUnfulfilledPaymentAttempts` | `—` |
| booking | Query | `defaultBankAccount` | `organizerId: String!` |
| booking | Query | `escrowAccount` | `id: ID!` |
| booking | Query | `escrowAccountBalance` | `accountId: String!` |
| booking | Query | `escrowAccountByEvent` | `eventId: String!` |
| booking | Query | `escrowAccountByNumber` | `accountNumber: String!` |
| booking | Query | `escrowAccountsByOrganizerCursorPagination` | `organizerId: String!, pagination: CursorPaginationInput` |
| booking | Query | `escrowAccountsByOrganizerOffsetPagination` | `organizerId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `escrowAccountsCursorPagination` | `filter: EscrowAccountFilterInput, pagination: CursorPaginationInput` |
| booking | Query | `escrowAccountsOffsetPagination` | `filter: EscrowAccountFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `escrowBalance` | `escrowAccountId: String!` |
| booking | Query | `escrowBalanceAsOf` | `escrowAccountId: String!, asOf: DateTime!` |
| booking | Query | `escrowJournalInconsistencies` | `—` |
| booking | Query | `escrowJournalVerification` | `eventId: ID!` |
| booking | Query | `escrowJournalVerificationAll` | `—` |
| booking | Query | `escrowTransaction` | `id: ID!` |
| booking | Query | `escrowTransactionsByAccount` | `escrowAccountId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `escrowTransactionsByTicket` | `ticketId: String!` |
| booking | Query | `escrowTransactionsUnlinked` | `—` |
| booking | Query | `eventLiveDashboard` | `eventId: ID!` |
| booking | Query | `eventPromoCodes` | `eventId: ID!` |
| booking | Query | `eventRefundSummary` | `eventId: String!` |
| booking | Query | `expiredReservationsCursorPagination` | `eventId: ID, since: DateTime!, pagination: CursorPaginationInput` |
| booking | Query | `expiredReservationsOffsetPagination` | `eventId: ID, since: DateTime!, pagination: OffsetPaginationInput` |
| booking | Query | `exportFinancialReport` | `filter: FinancialReportFilterInput!, format: ExportFormat!` |
| booking | Query | `exportSalesReport` | `eventId: ID!, format: ExportFormat!` |
| booking | Query | `failedPayoutRequestsCursorPagination` | `pagination: CursorPaginationInput` |
| booking | Query | `failedPayoutRequestsOffsetPagination` | `pagination: OffsetPaginationInput` |
| booking | Query | `financialReport` | `filter: FinancialReportFilterInput!` |
| booking | Query | `hasSuccessfulPayment` | `reservationId: String!` |
| booking | Query | `isTicketEligibleForRefund` | `ticketId: String!` |
| booking | Query | `journalEntriesByAccountCode` | `accountCode: String!, pagination: OffsetPaginationInput` |
| booking | Query | `journalEntriesByCorrelationId` | `correlationId: String!` |
| booking | Query | `journalEntriesOffsetPagination` | `filter: JournalEntryFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `journalEntry` | `id: ID!` |
| booking | Query | `journalEntryByNumber` | `entryNumber: String!` |
| booking | Query | `latestPaymentAttemptByReservation` | `reservationId: String!` |
| booking | Query | `myActiveReservations` | `userId: ID!` |
| booking | Query | `myCheckInRate` | `—` |
| booking | Query | `myDashboardStats` | `—` |
| booking | Query | `myFinanceOverview` | `—` |
| booking | Query | `myPayoutSources` | `—` |
| booking | Query | `myPayoutWindow` | `—` |
| booking | Query | `myRecentActivity` | `limit: Int` |
| booking | Query | `myRevenueSeries` | `months: Int` |
| booking | Query | `myTicketMix` | `—` |
| booking | Query | `myTransactionsOffsetPagination` | `filter: OrganizerTransactionFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `myUpcomingEvents` | `limit: Int` |
| booking | Query | `organizerPromoCodes` | `organizerId: ID!` |
| booking | Query | `paymentAttempt` | `id: ID!` |
| booking | Query | `paymentAttemptByAttemptNumber` | `attemptNumber: String!` |
| booking | Query | `paymentAttemptByDepositId` | `depositId: String!` |
| booking | Query | `paymentAttemptCountByStatus` | `status: PaymentAttemptStatus!` |
| booking | Query | `paymentAttemptsByBuyer` | `buyerId: String!` |
| booking | Query | `paymentAttemptsByEvent` | `eventId: String!` |
| booking | Query | `paymentAttemptsByReservation` | `reservationId: String!` |
| booking | Query | `paymentAttemptsByStatus` | `status: PaymentAttemptStatus!` |
| booking | Query | `payoutRecoverySummary` | `—` |
| booking | Query | `payoutRequest` | `id: ID!` |
| booking | Query | `payoutRequestByRequestId` | `requestId: String!` |
| booking | Query | `payoutRequestStats` | `organizerId: ID` |
| booking | Query | `payoutRequestsByEventCursorPagination` | `eventId: String!, pagination: CursorPaginationInput` |
| booking | Query | `payoutRequestsByEventOffsetPagination` | `eventId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `payoutRequestsByIssueTypeOffsetPagination` | `issueType: PayoutIssueType!, pagination: OffsetPaginationInput` |
| booking | Query | `payoutRequestsByOrganizerCursorPagination` | `organizerId: String!, pagination: CursorPaginationInput` |
| booking | Query | `payoutRequestsByOrganizerOffsetPagination` | `organizerId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `payoutRequestsCursorPagination` | `filter: PayoutRequestFilterInput!, pagination: CursorPaginationInput` |
| booking | Query | `payoutRequestsForReviewCursorPagination` | `reviewStatus: PayoutReviewStatus, pagination: CursorPaginationInput` |
| booking | Query | `payoutRequestsForReviewOffsetPagination` | `reviewStatus: PayoutReviewStatus, pagination: OffsetPaginationInput` |
| booking | Query | `payoutRequestsOffsetPagination` | `filter: PayoutRequestFilterInput!, pagination: OffsetPaginationInput` |
| booking | Query | `pendingChargebacks` | `—` |
| booking | Query | `pendingJournalEntriesOffsetPagination` | `pagination: OffsetPaginationInput` |
| booking | Query | `pendingPaymentAttempts` | `—` |
| booking | Query | `pendingPayoutRequestsCursorPagination` | `pagination: CursorPaginationInput` |
| booking | Query | `pendingPayoutRequestsOffsetPagination` | `pagination: OffsetPaginationInput` |
| booking | Query | `pendingRefundRequestsCursorPagination` | `pagination: CursorPaginationInput` |
| booking | Query | `pendingRefundRequestsOffsetPagination` | `pagination: OffsetPaginationInput` |
| booking | Query | `platformAccount` | `id: ID!` |
| booking | Query | `platformAccountByType` | `accountType: PlatformAccountType!` |
| booking | Query | `platformAccounts` | `—` |
| booking | Query | `platformSummary` | `—` |
| booking | Query | `promoCode` | `id: ID!` |
| booking | Query | `promoCodeByCode` | `code: String!` |
| booking | Query | `recentlyResolvedPayoutRequestsOffsetPagination` | `pagination: OffsetPaginationInput` |
| booking | Query | `reconciliationRun` | `id: ID!` |
| booking | Query | `reconciliationRunsByType` | `type: ReconciliationType!, pagination: OffsetPaginationInput` |
| booking | Query | `reconciliationRunsOffsetPagination` | `filter: ReconciliationFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `reconciliationRunsRequiringReview` | `—` |
| booking | Query | `reconciliationSummary` | `type: ReconciliationType, startDate: DateTime, endDate: DateTime` |
| booking | Query | `refundRequest` | `id: ID!` |
| booking | Query | `refundRequestByRequestId` | `requestId: String!` |
| booking | Query | `refundRequestsByBuyerCursorPagination` | `buyerId: String!, pagination: CursorPaginationInput` |
| booking | Query | `refundRequestsByBuyerOffsetPagination` | `buyerId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `refundRequestsByEventCursorPagination` | `eventId: String!, pagination: CursorPaginationInput` |
| booking | Query | `refundRequestsByEventOffsetPagination` | `eventId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `refundRequestsByTicket` | `ticketId: String!` |
| booking | Query | `refundRequestsCursorPagination` | `filter: RefundRequestFilterInput!, pagination: CursorPaginationInput` |
| booking | Query | `refundRequestsOffsetPagination` | `filter: RefundRequestFilterInput!, pagination: OffsetPaginationInput` |
| booking | Query | `reservation` | `id: ID!` |
| booking | Query | `reservationsByEventCursorPagination` | `eventId: ID!, pagination: CursorPaginationInput` |
| booking | Query | `reservationsByEventOffsetPagination` | `eventId: ID!, pagination: OffsetPaginationInput` |
| booking | Query | `retryablePayoutRequestsOffsetPagination` | `pagination: OffsetPaginationInput` |
| booking | Query | `searchTicketsCursorPagination` | `filter: TicketFilterInput!, pagination: CursorPaginationInput` |
| booking | Query | `searchTicketsOffsetPagination` | `filter: TicketFilterInput!, pagination: OffsetPaginationInput` |
| booking | Query | `stuckPayoutRequestsCursorPagination` | `pagination: CursorPaginationInput` |
| booking | Query | `stuckPayoutRequestsOffsetPagination` | `pagination: OffsetPaginationInput` |
| booking | Query | `successfulPaymentAttemptByReservation` | `reservationId: String!` |
| booking | Query | `ticket` | `id: ID!` |
| booking | Query | `ticketByNumber` | `ticketNumber: String!` |
| booking | Query | `ticketCountByBuyer` | `buyerId: String!` |
| booking | Query | `ticketCountByEvent` | `eventId: String!` |
| booking | Query | `ticketStats` | `eventId: ID` |
| booking | Query | `ticketsByBuyerCursorPagination` | `buyerId: String!, status: TicketStatus, pagination: CursorPaginationInput` |
| booking | Query | `ticketsByBuyerOffsetPagination` | `buyerId: String!, status: TicketStatus, pagination: OffsetPaginationInput` |
| booking | Query | `ticketsByEventCursorPagination` | `eventId: String!, pagination: CursorPaginationInput` |
| booking | Query | `ticketsByEventOffsetPagination` | `eventId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `ticketsByOrganizerCursorPagination` | `organizerId: String!, filter: TicketFilterInput, pagination: CursorPaginationInput` |
| booking | Query | `ticketsByOrganizerOffsetPagination` | `organizerId: String!, filter: TicketFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `transactionStats` | `eventId: ID, organizerId: ID` |
| booking | Query | `trialBalance` | `asOf: DateTime` |
| booking | Query | `validatePromoCode` | `code: String!, eventId: ID!, amount: BigDecimal` |
| catalog | Query | `activeEventCategoriesCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `availableTicketTiers` | `eventId: ID!` |
| catalog | Query | `citiesByCountryCursorPagination` | `country: String!, pagination: CursorPaginationInput` |
| catalog | Query | `citiesByProvinceCursorPagination` | `provinceId: String!, pagination: CursorPaginationInput` |
| catalog | Query | `citiesCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `citiesWithEvents` | `—` |
| catalog | Query | `city` | `id: ID!` |
| catalog | Query | `discoverEvents` | `filter: EventDiscoveryFilterInput!, pagination: CursorPaginationInput` |
| catalog | Query | `event` | `id: ID!` |
| catalog | Query | `eventCategoriesCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `eventCategory` | `id: ID!` |
| catalog | Query | `eventsByCategoryCursorPagination` | `categoryId: String!, pagination: CursorPaginationInput` |
| catalog | Query | `eventsByCityCursorPagination` | `city: String!, pagination: CursorPaginationInput` |
| catalog | Query | `eventsByDateRangeCursorPagination` | `startDate: DateTime!, endDate: DateTime!, pagination: CursorPaginationInput` |
| catalog | Query | `eventsByOrganizerCursorPagination` | `organizerId: String!, pagination: CursorPaginationInput` |
| catalog | Query | `eventsByPriceRangeCursorPagination` | `minPrice: BigDecimal, maxPrice: BigDecimal, pagination: CursorPaginationInput` |
| catalog | Query | `featuredEventsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `freeEventsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `location` | `id: ID!` |
| catalog | Query | `locationsByCityCursorPagination` | `city: String!, pagination: CursorPaginationInput` |
| catalog | Query | `locationsByCountryCursorPagination` | `country: String!, pagination: CursorPaginationInput` |
| catalog | Query | `locationsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `locationsNearbyCursorPagination` | `input: NearbyLocationInput!, pagination: CursorPaginationInput` |
| catalog | Query | `popularCategories` | `limit: Int = 10` |
| catalog | Query | `province` | `id: ID!` |
| catalog | Query | `provincesByCountryCursorPagination` | `country: String!, pagination: CursorPaginationInput` |
| catalog | Query | `provincesCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `publishedEventsCursorPagination` | `pagination: CursorPaginationInput` |
| catalog | Query | `referenceData` | `type: ReferenceType!, activeOnly: Boolean = true` |
| catalog | Query | `referenceDataByParent` | `type: ReferenceType!, parentCode: String!` |
| catalog | Query | `referenceItem` | `type: ReferenceType!, code: String!` |
| catalog | Query | `referenceTypes` | `—` |
| catalog | Query | `searchCitiesCursorPagination` | `query: String!, pagination: CursorPaginationInput` |
| catalog | Query | `searchEventCategoriesCursorPagination` | `query: String!, pagination: CursorPaginationInput` |
| catalog | Query | `searchEventsCursorPagination` | `query: String!, pagination: CursorPaginationInput` |
| catalog | Query | `searchLocationsCursorPagination` | `query: String!, pagination: CursorPaginationInput` |
| catalog | Query | `searchProvincesCursorPagination` | `query: String!, pagination: CursorPaginationInput` |
| catalog | Query | `ticketTier` | `id: ID!` |
| catalog | Query | `upcomingEventsCursorPagination` | `pagination: CursorPaginationInput` |
| identity | Mutation | `AUTHORIZATION` | `—` |
| identity | Mutation | `AUTHORIZATION` | `—` |
| identity | Mutation | `AUTHORIZATION` | `—` |
| identity | Mutation | `AUTHORIZATION` | `—` |
| identity | Mutation | `COMPLIANCE` | `—` |
| identity | Mutation | `Note` | `—` |
| identity | Mutation | `SECURITY` | `—` |
| identity | Mutation | `acceptInvitation` | `token: String!` |
| identity | Mutation | `acceptOwnershipTransfer` | `token: String!, confirmationCode: String!` |
| identity | Mutation | `activateUser` | `id: ID!` |
| identity | Mutation | `addUserRole` | `userId: ID!, role: UserType!` |
| identity | Mutation | `applyToBeOrganizer` | `input: OrganizationApplicationInput!` |
| identity | Mutation | `approveOrganization` | `id: ID!` |
| identity | Mutation | `approveVerificationDocument` | `documentId: ID!` |
| identity | Mutation | `assignPermissionToRole` | `roleId: ID!, permissionId: ID!` |
| identity | Mutation | `bulkGrantEventAccess` | `eventId: ID!, organizationId: ID!, grants: [EventAccessGrantInput!]!` |
| identity | Mutation | `bulkInviteTeamMembers` | `organizationId: ID!, invitations: [InviteMemberInput!]!` |
| identity | Mutation | `cancelAccountDeletion` | `—` |
| identity | Mutation | `cancelEventReminder` | `reminderId: ID!` |
| identity | Mutation | `cancelOwnershipTransfer` | `organizationId: ID!` |
| identity | Mutation | `changePassword` | `oldPassword: String!, newPassword: String!` |
| identity | Mutation | `createEventOwner` | `eventId: ID!, organizationId: ID!` |
| identity | Mutation | `createPermission` | `name: String!, description: String, category: String` |
| identity | Mutation | `createUser` | `input: CreateUserInput!` |
| identity | Mutation | `deactivateUser` | `id: ID!` |
| identity | Mutation | `declineInvitation` | `token: String!` |
| identity | Mutation | `declineOwnershipTransfer` | `token: String!` |
| identity | Mutation | `deleteNotification` | `notificationId: ID!` |
| identity | Mutation | `deletePermission` | `id: ID!` |
| identity | Mutation | `deleteVerificationDocument` | `documentId: ID!` |
| identity | Mutation | `disableTwoFactor` | `confirmationCode: String!` |
| identity | Mutation | `getOrCreateMyOrganization` | `—` |
| identity | Mutation | `grantEventAccess` | `eventId: ID!, organizationId: ID!, userId: ID!, role: EventRole!, customPermissions: [Stri…` |
| identity | Mutation | `id` | `—` |
| identity | Mutation | `id` | `—` |
| identity | Mutation | `initiateOwnershipTransfer` | `organizationId: ID!, newOwnerId: ID!, reason: String` |
| identity | Mutation | `input` | `—` |
| identity | Mutation | `input` | `—` |
| identity | Mutation | `input` | `—` |
| identity | Mutation | `input` | `—` |
| identity | Mutation | `input` | `—` |
| identity | Mutation | `inviteTeamMember` | `organizationId: ID!, input: InviteMemberInput!` |
| identity | Mutation | `leaveOrganization` | `organizationId: ID!` |
| identity | Mutation | `linkSocialAccount` | `input: SocialAuthInput!` |
| identity | Mutation | `lockUser` | `id: ID!, reason: String!` |
| identity | Mutation | `login` | `email: String!, password: String!` |
| identity | Mutation | `logout` | `—` |
| identity | Mutation | `markAllNotificationsRead` | `—` |
| identity | Mutation | `markNotificationRead` | `notificationId: ID!` |
| identity | Mutation | `organizationId` | `—` |
| identity | Mutation | `organizationId` | `—` |
| identity | Mutation | `organizationId` | `—` |
| identity | Mutation | `organizationId` | `—` |
| identity | Mutation | `reactivateMember` | `memberId: ID!` |
| identity | Mutation | `refreshToken` | `refreshToken: String!` |
| identity | Mutation | `register` | `input: RegisterInput!` |
| identity | Mutation | `registerDevice` | `input: RegisterDeviceInput!` |
| identity | Mutation | `rejectOrganization` | `id: ID!, reason: String!` |
| identity | Mutation | `rejectVerificationDocument` | `documentId: ID!, reason: String!` |
| identity | Mutation | `removeMember` | `memberId: ID!, reason: String` |
| identity | Mutation | `removePermissionFromRole` | `roleId: ID!, permissionId: ID!` |
| identity | Mutation | `removeUserRole` | `userId: ID!, role: UserType!` |
| identity | Mutation | `requestAccountDeletion` | `input: RequestAccountDeletionInput!` |
| identity | Mutation | `requestDocumentUploadUrl` | `input: RequestUploadUrlInput!` |
| identity | Mutation | `requestOrganizationChanges` | `id: ID!, reason: String!` |
| identity | Mutation | `requestPhoneOtp` | `phoneNumber: String!, channel: String` |
| identity | Mutation | `resendInvitation` | `invitationId: ID!` |
| identity | Mutation | `resetPassword` | `email: String!` |
| identity | Mutation | `revokeEventAccess` | `accessId: ID!, reason: String` |
| identity | Mutation | `revokeInvitation` | `invitationId: ID!` |
| identity | Mutation | `sendBulkNotification` | `userIds: [ID!]!, input: SendNotificationInput!` |
| identity | Mutation | `sendEmailVerification` | `—` |
| identity | Mutation | `sendNotification` | `input: SendNotificationInput!` |
| identity | Mutation | `sendPhoneVerification` | `—` |
| identity | Mutation | `setEventReminder` | `input: SetEventReminderInput!` |
| identity | Mutation | `setUserRoles` | `userId: ID!, roles: [UserType!]!` |
| identity | Mutation | `setupTwoFactor` | `input: SetupTwoFactorInput!` |
| identity | Mutation | `socialAuth` | `input: SocialAuthInput!` |
| identity | Mutation | `submitOrganizationForReview` | `id: ID!` |
| identity | Mutation | `suspendMember` | `memberId: ID!, reason: String` |
| identity | Mutation | `suspendOrganization` | `id: ID!, reason: String!` |
| identity | Mutation | `suspendUser` | `id: ID!, reason: String!` |
| identity | Mutation | `syncAllUsersFromKeycloak` | `—` |
| identity | Mutation | `syncEmailVerificationStatus` | `—` |
| identity | Mutation | `syncUserFromKeycloak` | `userId: ID!` |
| identity | Mutation | `transferOrganizationOwnership` | `organizationId: ID!, newOwnerId: ID!` |
| identity | Mutation | `unlinkSocialAccount` | `provider: SocialProvider!` |
| identity | Mutation | `unlockUser` | `id: ID!` |
| identity | Mutation | `unregisterDevice` | `deviceId: ID!` |
| identity | Mutation | `unsuspendOrganization` | `id: ID!` |
| identity | Mutation | `unsuspendUser` | `id: ID!` |
| identity | Mutation | `updateEventAccess` | `accessId: ID!, newRole: EventRole, customPermissions: [String!], expiresAt: DateTime` |
| identity | Mutation | `updateMemberRole` | `memberId: ID!, input: UpdateMemberRoleInput!` |
| identity | Mutation | `updateMyProfile` | `input: UpdateUserInput!` |
| identity | Mutation | `updateNotificationPreferences` | `input: UpdateNotificationPreferencesInput!` |
| identity | Mutation | `updateOrganizationApplication` | `id: ID!, input: OrganizationApplicationInput!` |
| identity | Mutation | `updateOrganizationStatus` | `id: ID!, status: OrganizationStatus!` |
| identity | Mutation | `updatePermission` | `id: ID!, name: String, description: String, category: String` |
| identity | Mutation | `updateProfile` | `input: JSON!` |
| identity | Mutation | `updateUser` | `id: ID!, input: UpdateUserInput!` |
| identity | Mutation | `upgradeToBusinessOrganization` | `organizationId: ID!, businessName: String!` |
| identity | Mutation | `uploadVerificationDocument` | `input: UploadVerificationDocumentInput!` |
| identity | Mutation | `validateToken` | `token: String!` |
| identity | Mutation | `verified` | `—` |
| identity | Mutation | `verifyEmail` | `token: String!` |
| identity | Mutation | `verifyPhone` | `code: String!` |
| identity | Mutation | `verifyPhoneOtp` | `phoneNumber: String!, otp: String!` |
| identity | Mutation | `verifyTwoFactor` | `input: VerifyTwoFactorInput!` |
| identity | Query | `Example` | `—` |
| identity | Query | `accountStatus` | `—` |
| identity | Query | `accountStatus` | `—` |
| identity | Query | `activeOnly` | `—` |
| identity | Query | `allPermissions` | `scope: PermissionScope` |
| identity | Query | `currentUserPermissions` | `—` |
| identity | Query | `eventAccessGrant` | `id: ID!` |
| identity | Query | `eventId` | `—` |
| identity | Query | `eventId` | `—` |
| identity | Query | `eventId` | `—` |
| identity | Query | `hasEventPermission` | `eventId: ID!, permission: String!` |
| identity | Query | `hasOrganizationPermission` | `organizationId: ID!, permission: String!` |
| identity | Query | `hasPendingOwnershipTransfer` | `organizationId: ID!` |
| identity | Query | `identityPendingCounts` | `—` |
| identity | Query | `invitationByToken` | `token: String!` |
| identity | Query | `isSlugAvailable` | `slug: String!` |
| identity | Query | `me` | `—` |
| identity | Query | `myApprovedDocumentCount` | `—` |
| identity | Query | `myDevices` | `—` |
| identity | Query | `myEventAccess` | `eventId: ID!` |
| identity | Query | `myEventAccessGrants` | `—` |
| identity | Query | `myEventReminders` | `eventId: ID` |
| identity | Query | `myEventRole` | `eventId: ID!` |
| identity | Query | `myNotificationPreferences` | `—` |
| identity | Query | `myOrganizationMembership` | `organizationId: ID!` |
| identity | Query | `myOrganizationRole` | `organizationId: ID!` |
| identity | Query | `myOrganizations` | `—` |
| identity | Query | `myOwnedOrganization` | `—` |
| identity | Query | `myPendingInvitations` | `—` |
| identity | Query | `myPendingOwnershipTransfers` | `—` |
| identity | Query | `myVerificationDocumentByType` | `documentType: String!` |
| identity | Query | `myVerificationDocumentCount` | `—` |
| identity | Query | `myVerificationDocuments` | `status: DocumentStatus` |
| identity | Query | `organization` | `id: ID!` |
| identity | Query | `organizationByOwnerId` | `ownerId: ID!` |
| identity | Query | `organizationBySlug` | `slug: String!` |
| identity | Query | `organizationCount` | `status: OrganizationStatus` |
| identity | Query | `organizationId` | `—` |
| identity | Query | `organizationId` | `—` |
| identity | Query | `organizationId` | `—` |
| identity | Query | `organizationId` | `—` |
| identity | Query | `organizationId` | `—` |
| identity | Query | `organizationMember` | `organizationId: ID!, userId: ID!` |
| identity | Query | `organizerStatistics` | `organizerId: ID!` |
| identity | Query | `ownershipTransfer` | `id: ID!` |
| identity | Query | `ownershipTransferByToken` | `token: String!` |
| identity | Query | `ownershipTransfers` | `organizationId: ID!` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pagination` | `—` |
| identity | Query | `pendingOwnershipTransfer` | `organizationId: ID!` |
| identity | Query | `pendingVerificationDocuments` | `—` |
| identity | Query | `permission` | `id: ID!` |
| identity | Query | `permissionByName` | `name: String!` |
| identity | Query | `permissions` | `—` |
| identity | Query | `permissionsByCategory` | `category: String!` |
| identity | Query | `platformStatistics` | `—` |
| identity | Query | `role` | `—` |
| identity | Query | `role` | `—` |
| identity | Query | `role` | `—` |
| identity | Query | `role` | `—` |
| identity | Query | `role` | `—` |
| identity | Query | `rolePermissions` | `roleId: String!` |
| identity | Query | `search` | `—` |
| identity | Query | `search` | `—` |
| identity | Query | `search` | `—` |
| identity | Query | `search` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `status` | `—` |
| identity | Query | `teamStatistics` | `organizationId: ID!` |
| identity | Query | `type` | `—` |
| identity | Query | `type` | `—` |
| identity | Query | `unreadNotificationCount` | `—` |
| identity | Query | `user` | `id: ID!` |
| identity | Query | `userByEmail` | `email: String!` |
| identity | Query | `userByPhone` | `phoneNumber: String!` |
| identity | Query | `userEventAccess` | `userId: ID!, eventId: ID!` |
| identity | Query | `userStats` | `—` |
| identity | Query | `usersCountByRole` | `role: UserType!` |
| identity | Query | `verificationDocument` | `id: ID!` |
| identity | Query | `verificationDocuments` | `organizationId: ID!, status: DocumentStatus` |
| identity | Query | `verified` | `—` |
| identity | Query | `verified` | `—` |

