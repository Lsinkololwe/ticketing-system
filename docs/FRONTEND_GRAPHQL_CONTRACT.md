# Frontend GraphQL contract

Generated from the subgraph schemas by `FrontendContractLintTest`, which also fails the build when this file and the schemas disagree. To rewrite it after a schema change:

```
mvn -q -pl shared-library test -Dtest=FrontendContractLintTest -Dcontract.write=true
```

## How the frontend consumes this

- One endpoint: the Apollo Router at `/graphql` behind the gateway. Never call a subgraph port directly — the router owns query planning.
- **Types come from codegen, always.** `cd frontend/web && npm run codegen`. A hand-written TypeScript type for a GraphQL shape is a defect.
- Send the JWT as `Authorization: Bearer <token>`. Every subgraph validates it independently against JWKS, so a token the gateway accepted can still be refused downstream — handle `UNAUTHENTICATED` on any operation.
- Branch on `extensions.errorCode`, never on `message`. Read `extensions.retryable` to decide whether to offer a retry.
- `TOKEN_REVOKED` means sign the user out; it will not resolve by retrying.
- Money-moving mutations take an `idempotencyKey`. Generate it once per user intent and reuse it across retries of that same intent.
- Dashboards poll (D-12); there are no subscriptions on this path. Use a visibility-aware interval and stop polling on a hidden tab.
- The role below is resolved from the field's `@auth(requires: …)` directive when it has one, and from the resolver's `@PreAuthorize` when it does not. Catalog uses the directive, booking and identity use `@PreAuthorize`; neither source alone describes the platform.
- **`AUTHENTICATED (endpoint floor only)` means neither check was found on the field.** It is not public: all three subgraphs carry `.pathMatchers("/graphql/**").authenticated()`, so a token is always required whatever a `# PUBLIC` comment in the schema says. It does mean *any* signed-in caller reaches it — a self-service `CUSTOMER` token included — which is where `event(id)` sat while it was returning other organizations' unpublished events (F-007).

## Operations by required role (515 total)

### `@isEscrowOwner or ADMIN or FINANCE` — 3 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `accountSummary` | `accountId: String!` |
| booking | Query | `escrowAccountBalance` | `accountId: String!` |
| booking | Query | `escrowAccountByNumber` | `accountNumber: String!` |

### `@isEventOrganizer or ADMIN` — 1 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `eventPromoCodes` | `eventId: ID!` |

### `@isEventOrganizer or ADMIN or FINANCE` — 10 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `escrowAccountByEvent` | `eventId: String!` |
| booking | Query | `eventLiveDashboard` | `eventId: ID!` |
| booking | Query | `eventRefundSummary` | `eventId: String!` |
| booking | Query | `exportSalesReport` | `eventId: ID!, format: ExportFormat!` |
| booking | Query | `payoutRequestsByEvent` | `eventId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `refundRequestsByEvent` | `eventId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `reservationsByEvent` | `eventId: ID!, pagination: OffsetPaginationInput` |
| booking | Query | `ticketCountByEvent` | `eventId: String!` |
| booking | Query | `ticketStats` | `eventId: ID` |
| booking | Query | `ticketsByEvent` | `eventId: String!, pagination: OffsetPaginationInput` |

### `@isPublic` — 2 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| catalog | Query | `referenceData` | `type: ReferenceType!, activeOnly: Boolean = true` |
| catalog | Query | `referenceDataByParent` | `type: ReferenceType!, parentCode: String!` |

### `@isRefundRequestOwner or ADMIN or FINANCE` — 1 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `refundRequest` | `id: ID!` |

### `@isRefundRequestOwnerByRequestId or ADMIN or FINANCE` — 1 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `refundRequestByRequestId` | `requestId: String!` |

### `@isTicketOwner or ADMIN or FINANCE` — 3 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `calculateRefundAmount` | `ticketId: String!` |
| booking | Query | `isTicketEligibleForRefund` | `ticketId: String!` |
| booking | Query | `refundRequestsByTicket` | `ticketId: String!` |

### `@isTicketOwner or ADMIN or FINANCE or ORGANIZER` — 1 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `ticket` | `id: ID!` |

### `@isTicketOwnerByNumber or ADMIN or FINANCE or ORGANIZER or SCANNER` — 1 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `ticketByNumber` | `ticketNumber: String!` |

### `@rolesOrFinancialView` — 3 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `escrowAccountsByOrganizer` | `organizerId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `payoutRequestStats` | `organizerId: ID` |
| booking | Query | `payoutRequestsByOrganizer` | `organizerId: String!, pagination: OffsetPaginationInput` |

### `@rolesOrTeamMember` — 4 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `bankAccountsByOrganizer` | `organizerId: String!` |
| booking | Query | `defaultBankAccount` | `organizerId: String!` |
| booking | Query | `organizerPromoCodes` | `organizerId: ID!` |
| booking | Query | `ticketsByOrganizer` | `organizerId: String!, filter: TicketFilterInput, pagination: OffsetPaginationInput` |

### `ADMIN` — 143 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `acceptChargeback` | `id: ID!, reason: String` |
| booking | Mutation | `addPaymentAttemptNote` | `depositId: String!, note: String!` |
| booking | Mutation | `adminUpdateTicket` | `ticketId: ID!, input: AdminTicketUpdateInput!` |
| booking | Mutation | `approveRefundRequest` | `refundRequestId: ID!, reviewComments: String` |
| booking | Mutation | `bulkApproveRefunds` | `refundRequestIds: [ID!]!` |
| booking | Mutation | `bulkCancelTickets` | `ticketIds: [ID!]!, reason: String!` |
| booking | Mutation | `closeEscrowAccount` | `accountId: ID!, reason: String!` |
| booking | Mutation | `completeReconciliation` | `runId: ID!, notes: String` |
| booking | Mutation | `createChartOfAccountsEntry` | `input: CreateChartOfAccountsInput!` |
| booking | Mutation | `createJournalEntry` | `input: CreateJournalEntryInput!` |
| booking | Mutation | `deactivateChartOfAccountsEntry` | `id: ID!` |
| booking | Mutation | `disputeChargeback` | `id: ID!, input: DisputeChargebackInput!` |
| booking | Mutation | `failReconciliation` | `runId: ID!, reason: String!` |
| booking | Mutation | `forceExpireReservation` | `reservationId: ID!` |
| booking | Mutation | `lockEscrowAccount` | `accountId: ID!, lockUntil: DateTime!, reason: String!` |
| booking | Mutation | `markPayoutEligible` | `accountId: ID!` |
| booking | Mutation | `postJournalEntry` | `id: ID!` |
| booking | Mutation | `processRefundRequest` | `refundRequestId: ID!` |
| booking | Mutation | `receiveChargeback` | `input: ReceiveChargebackInput!` |
| booking | Mutation | `recordChargebackOutcome` | `id: ID!, won: Boolean!, notes: String` |
| booking | Mutation | `recordGatewaySettlement` | `input: RecordGatewaySettlementInput!` |
| booking | Mutation | `regenerateTicketQrCode` | `ticketId: ID!` |
| booking | Mutation | `rejectRefundRequest` | `refundRequestId: ID!, rejectionReason: String!` |
| booking | Mutation | `resolveReconciliationItem` | `runId: ID!, input: ResolveReconciliationItemInput!` |
| booking | Mutation | `reverseJournalEntry` | `id: ID!, reason: String!` |
| booking | Mutation | `seedChartOfAccounts` | — |
| booking | Mutation | `setPaymentAttemptReviewStatus` | `depositId: String!, reviewStatus: String!, notes: String` |
| booking | Mutation | `startChargebackReview` | `id: ID!, notes: String` |
| booking | Mutation | `startReconciliation` | `input: StartReconciliationInput!` |
| booking | Mutation | `unlockEscrowAccount` | `accountId: ID!, reason: String!` |
| booking | Mutation | `updateChartOfAccountsEntry` | `id: ID!, input: CreateChartOfAccountsInput!` |
| booking | Mutation | `updateEscrowAccountStatus` | `accountId: ID!, status: EscrowAccountStatus!, reason: String` |
| booking | Mutation | `verifyEscrowJournalConsistency` | `eventId: ID!` |
| booking | Query | `chargeback` | `id: ID!` |
| booking | Query | `chargebackByChargebackId` | `chargebackId: String!` |
| booking | Query | `chargebackStats` | `organizerId: String, eventId: String` |
| booking | Query | `chargebacks` | `filter: ChargebackFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `chargebacksByEvent` | `eventId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `chargebacksByOrganizer` | `organizerId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `chargebacksPendingRecovery` | — |
| booking | Query | `chartOfAccounts` | — |
| booking | Query | `chartOfAccountsByCode` | `accountCode: String!` |
| booking | Query | `chartOfAccountsByType` | `accountType: AccountType!` |
| booking | Query | `chartOfAccountsEntry` | `id: ID!` |
| booking | Query | `confirmedUnfulfilledPaymentAttempts` | — |
| booking | Query | `escrowBalance` | `escrowAccountId: String!` |
| booking | Query | `escrowBalanceAsOf` | `escrowAccountId: String!, asOf: DateTime!` |
| booking | Query | `escrowJournalInconsistencies` | — |
| booking | Query | `escrowJournalVerification` | `eventId: ID!` |
| booking | Query | `escrowJournalVerificationAll` | — |
| booking | Query | `escrowTransaction` | `id: ID!` |
| booking | Query | `escrowTransactionsByTicket` | `ticketId: String!` |
| booking | Query | `escrowTransactionsUnlinked` | — |
| booking | Query | `hasSuccessfulPayment` | `reservationId: String!` |
| booking | Query | `journalEntries` | `filter: JournalEntryFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `journalEntriesByAccountCode` | `accountCode: String!, pagination: OffsetPaginationInput` |
| booking | Query | `journalEntriesByCorrelationId` | `correlationId: String!` |
| booking | Query | `journalEntry` | `id: ID!` |
| booking | Query | `journalEntryByNumber` | `entryNumber: String!` |
| booking | Query | `latestPaymentAttemptByReservation` | `reservationId: String!` |
| booking | Query | `paymentAttempt` | `id: ID!` |
| booking | Query | `paymentAttemptByAttemptNumber` | `attemptNumber: String!` |
| booking | Query | `paymentAttemptByDepositId` | `depositId: String!` |
| booking | Query | `paymentAttemptCountByStatus` | `status: PaymentAttemptStatus!` |
| booking | Query | `paymentAttempts` | `intentId: ID!` |
| booking | Query | `paymentAttemptsByBuyer` | `buyerId: String!` |
| booking | Query | `paymentAttemptsByEvent` | `eventId: String!` |
| booking | Query | `paymentAttemptsByReservation` | `reservationId: String!` |
| booking | Query | `paymentAttemptsByStatus` | `status: PaymentAttemptStatus!` |
| booking | Query | `pendingChargebacks` | — |
| booking | Query | `pendingJournalEntries` | `pagination: OffsetPaginationInput` |
| booking | Query | `pendingPaymentAttempts` | — |
| booking | Query | `platformAccount` | `id: ID!` |
| booking | Query | `platformAccountByType` | `accountType: PlatformAccountType!` |
| booking | Query | `platformAccounts` | — |
| booking | Query | `reconciliationRun` | `id: ID!` |
| booking | Query | `reconciliationRuns` | `filter: ReconciliationFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `reconciliationRunsByType` | `type: ReconciliationType!, pagination: OffsetPaginationInput` |
| booking | Query | `reconciliationRunsRequiringReview` | — |
| booking | Query | `reconciliationSummary` | `type: ReconciliationType, startDate: DateTime, endDate: DateTime` |
| booking | Query | `successfulPaymentAttemptByReservation` | `reservationId: String!` |
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
| catalog | Mutation | `deleteStockImage` | `id: ID!` |
| catalog | Mutation | `featureEvent` | `eventId: ID!, featured: Boolean!` |
| catalog | Mutation | `flagMedia` | `id: ID!, reason: String!` |
| catalog | Mutation | `overrideEventBanner` | `eventId: ID!, mediaId: ID, reason: String!` |
| catalog | Mutation | `rejectEvent` | `eventId: ID!, comments: String!` |
| catalog | Mutation | `removeMedia` | `id: ID!, reason: String!` |
| catalog | Mutation | `requestEventChanges` | `eventId: ID!, comments: String!` |
| catalog | Mutation | `resolveEscalation` | `input: ResolveEscalationInput!` |
| catalog | Mutation | `restoreMedia` | `id: ID!` |
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
| catalog | Mutation | `updateStockImage` | `id: ID!, input: UpdateStockImageInput!` |
| catalog | Mutation | `uploadStockImage` | `input: UploadStockImageInput!` |
| catalog | Query | `activeEscalations` | `pagination: OffsetPaginationInput` |
| catalog | Query | `approvalEscalation` | `id: ID!` |
| catalog | Query | `approvalStats` | — |
| catalog | Query | `approvalTimeline` | `eventId: String!` |
| catalog | Query | `approvalTimelines` | `filter: ApprovalTimelineFilterInput, pagination: OffsetPaginationInput` |
| catalog | Query | `approvalTimelinesByOrganizer` | `organizerId: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `approvedNotPublishedEvents` | `pagination: OffsetPaginationInput` |
| catalog | Query | `cancelledEvents` | `pagination: OffsetPaginationInput` |
| catalog | Query | `catalogPendingCounts` | — |
| catalog | Query | `completedEvents` | `pagination: OffsetPaginationInput` |
| catalog | Query | `eventCount` | — |
| catalog | Query | `eventCountByCategory` | `categoryId: String!` |
| catalog | Query | `eventCountByCity` | `city: String!` |
| catalog | Query | `eventCountByStatus` | `status: EventStatus!` |
| catalog | Query | `eventStats` | — |
| catalog | Query | `events` | `filter: EventFilterInput, pagination: OffsetPaginationInput` |
| catalog | Query | `eventsByStatus` | `status: EventStatus!, pagination: OffsetPaginationInput` |
| catalog | Query | `exportEventData` | `eventId: ID!, format: ExportFormat!` |
| catalog | Query | `exportEventsReport` | `filter: EventFilterInput!, format: ExportFormat!` |
| catalog | Query | `mediaAssets` | `filter: MediaModerationFilterInput, pagination: OffsetPaginationInput` |
| catalog | Query | `myEscalations` | `pagination: OffsetPaginationInput` |
| catalog | Query | `overdueApprovalEvents` | `pagination: OffsetPaginationInput` |
| catalog | Query | `overdueApprovalTimelines` | `pagination: OffsetPaginationInput` |
| catalog | Query | `pendingApprovalEvents` | `pagination: OffsetPaginationInput` |
| catalog | Query | `pendingApprovalTimelines` | `pagination: OffsetPaginationInput` |
| catalog | Query | `platformConfiguration` | — |
| catalog | Query | `referenceDataAll` | `type: ReferenceType!, pagination: OffsetPaginationInput` |

### `ADMIN or FINANCE` — 31 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `approvePayoutRequest` | `payoutRequestId: ID!, notes: String` |
| booking | Mutation | `bulkMarkPayoutsForReview` | `payoutRequestIds: [ID!]!, issueType: PayoutIssueType!, notes: String` |
| booking | Mutation | `bulkRetryFailedPayouts` | `payoutRequestIds: [ID!]!` |
| booking | Mutation | `completePayoutRequest` | `payoutRequestId: ID!, bankReference: String!` |
| booking | Mutation | `escalatePayoutRequest` | `payoutRequestId: ID!, reason: String!` |
| booking | Mutation | `markPayoutForReview` | `payoutRequestId: ID!, issueType: PayoutIssueType!, notes: String` |
| booking | Mutation | `processPayoutRequest` | `payoutRequestId: ID!` |
| booking | Mutation | `rejectPayoutRequest` | `payoutRequestId: ID!, rejectionReason: String!` |
| booking | Mutation | `resolvePayoutIssue` | `payoutRequestId: ID!, resolutionType: PayoutResolutionType!, notes: String!` |
| booking | Mutation | `resumePayoutRequest` | `payoutRequestId: ID!` |
| booking | Mutation | `retryPayoutRequest` | `payoutRequestId: ID!` |
| booking | Mutation | `verifyBankAccount` | `id: ID!` |
| booking | Query | `escrowAccount` | `id: ID!` |
| booking | Query | `escrowAccounts` | `filter: EscrowAccountFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `expiredReservations` | `eventId: ID, since: DateTime!, pagination: OffsetPaginationInput` |
| booking | Query | `exportFinancialReport` | `filter: FinancialReportFilterInput!, format: ExportFormat!` |
| booking | Query | `failedPayoutRequests` | `pagination: OffsetPaginationInput` |
| booking | Query | `financialReport` | `filter: FinancialReportFilterInput!` |
| booking | Query | `payoutRecoverySummary` | — |
| booking | Query | `payoutRequests` | `filter: PayoutRequestFilterInput!, pagination: OffsetPaginationInput` |
| booking | Query | `payoutRequestsByIssueType` | `issueType: PayoutIssueType!, pagination: OffsetPaginationInput` |
| booking | Query | `payoutRequestsForReview` | `reviewStatus: PayoutReviewStatus, pagination: OffsetPaginationInput` |
| booking | Query | `pendingPayoutRequests` | `pagination: OffsetPaginationInput` |
| booking | Query | `pendingRefundRequests` | `pagination: OffsetPaginationInput` |
| booking | Query | `platformSummary` | — |
| booking | Query | `recentlyResolvedPayoutRequests` | `pagination: OffsetPaginationInput` |
| booking | Query | `refundRequests` | `filter: RefundRequestFilterInput!, pagination: OffsetPaginationInput` |
| booking | Query | `retryablePayoutRequests` | `pagination: OffsetPaginationInput` |
| booking | Query | `searchTickets` | `filter: TicketFilterInput!, pagination: OffsetPaginationInput` |
| booking | Query | `stuckPayoutRequests` | `pagination: OffsetPaginationInput` |
| booking | Query | `transactionStats` | `eventId: ID, organizerId: ID` |

### `ADMIN or FINANCE or ORGANIZER` — 7 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `cancelPayoutRequest` | `payoutRequestId: ID!, reason: String!` |
| booking | Mutation | `deleteBankAccount` | `id: ID!` |
| booking | Mutation | `updateBankAccount` | `id: ID!, input: UpdateBankAccountInput!` |
| booking | Query | `bankAccount` | `id: ID!` |
| booking | Query | `escrowTransactions` | `escrowAccountId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `payoutRequest` | `id: ID!` |
| booking | Query | `payoutRequestByRequestId` | `requestId: String!` |

### `ADMIN or FINANCE or SUPER_ADMIN` — 8 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `bookingPendingCounts` | — |
| booking | Query | `trialBalance` | `asOf: DateTime` |
| identity | Mutation | `reinstateBankAccount` | `organizationId: ID!` |
| identity | Mutation | `rejectBankAccount` | `organizationId: ID!, reason: String!` |
| identity | Mutation | `rejectPayoutAccount` | `organizationId: ID!, reason: String!` |
| identity | Mutation | `suspendBankAccount` | `organizationId: ID!, reason: String!` |
| identity | Mutation | `verifyPayoutAccount` | `organizationId: ID!, verified: Boolean!` |
| identity | Query | `bankAccounts` | `filter: PayoutAccountFilterInput, pagination: OffsetPaginationInput` |

### `ADMIN or FINANCE or self` — 5 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Query | `myActiveReservations` | `userId: ID!` |
| booking | Query | `refundRequestsByBuyer` | `buyerId: String!, pagination: OffsetPaginationInput` |
| booking | Query | `ticketCountByBuyer` | `buyerId: String!` |
| booking | Query | `ticketsByBuyerCursorPagination` | `buyerId: String!, status: TicketStatus, pagination: CursorPaginationInput` |
| booking | Query | `ticketsByBuyerOffsetPagination` | `buyerId: String!, status: TicketStatus, pagination: OffsetPaginationInput` |

### `ADMIN or INTERNAL_SERVICE or SCOPE_internal-write` — 1 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `createEscrowAccount` | `input: CreateEscrowAccountInput!` |

### `ADMIN or ORGANIZER` — 10 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `activatePromoCode` | `id: ID!` |
| booking | Mutation | `createPromoCode` | `input: CreatePromoCodeInput!` |
| booking | Mutation | `deactivatePromoCode` | `id: ID!` |
| booking | Mutation | `deletePromoCode` | `id: ID!` |
| booking | Mutation | `updatePromoCode` | `id: ID!, input: UpdatePromoCodeInput!` |
| booking | Query | `promoCode` | `id: ID!` |
| booking | Query | `promoCodeByCode` | `code: String!` |
| identity | Mutation | `setBankAccount` | `organizationId: ID!, input: SetBankAccountInput!` |
| identity | Mutation | `setMobileMoneyAccount` | `organizationId: ID!, input: SetMobileMoneyAccountInput!` |
| identity | Mutation | `updatePayoutConfig` | `organizationId: ID!, input: UpdatePayoutConfigInput!` |

### `ADMIN or SUPER_ADMIN` — 46 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| identity | Mutation | `acknowledgeAlert` | `id: ID!` |
| identity | Mutation | `activateUser` | `id: ID!` |
| identity | Mutation | `addUserRole` | `userId: ID!, role: UserType!` |
| identity | Mutation | `approveOrganization` | `id: ID!, commissionRate: Float` |
| identity | Mutation | `approveVerificationDocument` | `documentId: ID!` |
| identity | Mutation | `broadcastNotification` | `input: BroadcastInput!` |
| identity | Mutation | `cancelAnnouncement` | `id: ID!` |
| identity | Mutation | `createUser` | `input: CreateUserInput!` |
| identity | Mutation | `deactivateUser` | `id: ID!` |
| identity | Mutation | `deleteUser` | `id: ID!` |
| identity | Mutation | `lockUser` | `id: ID!, reason: String!` |
| identity | Mutation | `rejectOrganization` | `id: ID!, reason: String!` |
| identity | Mutation | `rejectVerificationDocument` | `documentId: ID!, reason: String!` |
| identity | Mutation | `removeUserRole` | `userId: ID!, role: UserType!` |
| identity | Mutation | `requestOrganizationChanges` | `id: ID!, reason: String!` |
| identity | Mutation | `setOrganizationCommissionRate` | `organizationId: ID!, rate: Float!, reason: String` |
| identity | Mutation | `setUserRoles` | `userId: ID!, roles: [UserType!]!` |
| identity | Mutation | `suspendOrganization` | `id: ID!, reason: String!` |
| identity | Mutation | `suspendUser` | `id: ID!, reason: String!` |
| identity | Mutation | `syncUserFromKeycloak` | `userId: ID!` |
| identity | Mutation | `unlockUser` | `id: ID!` |
| identity | Mutation | `unsuspendOrganization` | `id: ID!` |
| identity | Mutation | `unsuspendUser` | `id: ID!` |
| identity | Mutation | `updateOrganizationStatus` | `id: ID!, status: OrganizationStatus!` |
| identity | Mutation | `updateUser` | `id: ID!, input: UpdateUserInput!` |
| identity | Query | `auditLogs` | `filter: AuditLogFilterInput, pagination: OffsetPaginationInput` |
| identity | Query | `identityPendingCounts` | — |
| identity | Query | `organizationApplications` | `status: OrganizationStatus, pagination: OffsetPaginationInput` |
| identity | Query | `organizationCount` | `status: OrganizationStatus` |
| identity | Query | `organizations` | `search: String, status: OrganizationStatus, verified: Boolean, kybStatus: KybStatus, pagination: OffsetPaginationInput` |
| identity | Query | `pendingVerificationDocuments` | — |
| identity | Query | `permission` | `code: String!` |
| identity | Query | `permissions` | — |
| identity | Query | `rolePermissions` | `role: String!` |
| identity | Query | `serviceHealth` | — |
| identity | Query | `staffAccounts` | `search: String, role: UserType, pagination: OffsetPaginationInput` |
| identity | Query | `systemAlerts` | `status: AlertStatus, severity: AlertSeverity` |
| identity | Query | `systemAnnouncements` | — |
| identity | Query | `user` | `id: ID!` |
| identity | Query | `userByEmail` | `email: String!` |
| identity | Query | `userByPhone` | `phoneNumber: String!` |
| identity | Query | `userGrowthSeries` | `from: DateTime!, to: DateTime!, bucket: GrowthBucket = DAY, role: UserType` |
| identity | Query | `userStats` | — |
| identity | Query | `users` | `search: String, role: UserType, accountStatus: AccountStatus, pagination: OffsetPaginationInput` |
| identity | Query | `usersByRole` | `role: UserType!, activeOnly: Boolean = true, pagination: OffsetPaginationInput` |
| identity | Query | `verificationDocuments` | `organizationId: ID!, status: DocumentStatus` |

### `AUTHENTICATED` — 112 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `confirmBankVerification` | `id: ID!, amount: BigDecimal!` |
| booking | Mutation | `createBankAccount` | `input: CreateBankAccountInput!` |
| booking | Mutation | `createPayoutRequest` | `input: CreatePayoutRequestInput!` |
| booking | Mutation | `resendTicket` | `ticketId: ID!` |
| booking | Mutation | `setDefaultBankAccount` | `id: ID!` |
| booking | Mutation | `startBankVerification` | `id: ID!` |
| booking | Query | `booking` | `id: ID!` |
| booking | Query | `bookingByNumber` | `bookingNumber: String!` |
| booking | Query | `bookingsByBuyer` | `buyerId: String!, filter: BookingFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `myEscrowAccounts` | `organizationId: ID, pagination: OffsetPaginationInput` |
| booking | Query | `myPayoutRequests` | `organizationId: ID, status: PayoutRequestStatus, pagination: OffsetPaginationInput` |
| booking | Query | `myRefundRequests` | `pagination: OffsetPaginationInput` |
| booking | Query | `reservation` | `id: ID!` |
| booking | Query | `ticketTransferChain` | `ticketId: ID!` |
| booking | Query | `validatePromoCode` | `code: String!, eventId: ID!, amount: BigDecimal` |
| identity | Mutation | `acceptInvitation` | `token: String!` |
| identity | Mutation | `acceptOwnershipTransfer` | `token: String!, confirmationCode: String!` |
| identity | Mutation | `applyToBeOrganizer` | `input: OrganizationApplicationInput!` |
| identity | Mutation | `bulkGrantEventAccess` | `eventId: ID!, organizationId: ID!, grants: [BulkEventAccessGrantInput!]!` |
| identity | Mutation | `bulkInviteTeamMembers` | `organizationId: ID!, invitations: [InviteMemberInput!]!` |
| identity | Mutation | `cancelAccountDeletion` | — |
| identity | Mutation | `cancelContactChange` | `changeId: ID!` |
| identity | Mutation | `cancelEventReminder` | `reminderId: ID!` |
| identity | Mutation | `cancelOrganizationDeletion` | `organizationId: ID!` |
| identity | Mutation | `cancelOwnershipTransfer` | `organizationId: ID!` |
| identity | Mutation | `confirmContactAdd` | `input: ConfirmContactAddInput!` |
| identity | Mutation | `confirmContactChange` | `input: ConfirmContactChangeInput!` |
| identity | Mutation | `confirmContactRemoval` | `input: ConfirmContactRemovalInput!` |
| identity | Mutation | `declineInvitation` | `token: String!` |
| identity | Mutation | `declineOwnershipTransfer` | `token: String!` |
| identity | Mutation | `deleteNotification` | `notificationId: ID!` |
| identity | Mutation | `deleteVerificationDocument` | `documentId: ID!` |
| identity | Mutation | `getOrCreateMyOrganization` | — |
| identity | Mutation | `grantEventAccess` | `eventId: ID!, organizationId: ID!, userId: ID!, role: EventRole!, customPermissions: [String!], reason: String, expiresAt: DateTime` |
| identity | Mutation | `initiateOwnershipTransfer` | `organizationId: ID!, newOwnerId: ID!, reason: String` |
| identity | Mutation | `inviteTeamMember` | `organizationId: ID!, input: InviteMemberInput!` |
| identity | Mutation | `leaveOrganization` | `organizationId: ID!` |
| identity | Mutation | `logout` | — |
| identity | Mutation | `markAllNotificationsRead` | — |
| identity | Mutation | `markNotificationRead` | `notificationId: ID!` |
| identity | Mutation | `reactivateMember` | `memberId: ID!` |
| identity | Mutation | `registerDevice` | `input: RegisterDeviceInput!` |
| identity | Mutation | `removeMember` | `memberId: ID!, reason: String` |
| identity | Mutation | `requestAccountDeletion` | `reason: String` |
| identity | Mutation | `requestContactAdd` | `input: RequestContactAddInput!` |
| identity | Mutation | `requestContactChange` | `input: RequestContactChangeInput!` |
| identity | Mutation | `requestContactRemoval` | `contactId: ID!` |
| identity | Mutation | `requestDocumentUploadUrl` | `input: RequestUploadUrlInput!` |
| identity | Mutation | `requestOrganizationDeletion` | `organizationId: ID!, reason: String` |
| identity | Mutation | `requestOwnershipTransferCode` | `token: String!` |
| identity | Mutation | `requestPrimaryContact` | `contactId: ID!` |
| identity | Mutation | `resendContactCode` | `input: ResendContactCodeInput!` |
| identity | Mutation | `resendInvitation` | `invitationId: ID!` |
| identity | Mutation | `revokeEventAccess` | `accessId: ID!, reason: String` |
| identity | Mutation | `revokeInvitation` | `invitationId: ID!` |
| identity | Mutation | `revokeSession` | `sessionId: ID!` |
| identity | Mutation | `setEventReminder` | `input: SetEventReminderInput!` |
| identity | Mutation | `setPrimaryContact` | `input: SetPrimaryContactInput!` |
| identity | Mutation | `submitOrganizationForReview` | `id: ID!` |
| identity | Mutation | `suspendMember` | `memberId: ID!, reason: String` |
| identity | Mutation | `unregisterDevice` | `deviceId: ID!` |
| identity | Mutation | `updateEventAccess` | `accessId: ID!, newRole: EventRole, customPermissions: [String!], expiresAt: DateTime` |
| identity | Mutation | `updateMemberRole` | `memberId: ID!, input: UpdateMemberRoleInput!` |
| identity | Mutation | `updateMyProfile` | `input: UpdateUserInput!` |
| identity | Mutation | `updateNotificationPreferences` | `input: UpdateNotificationPreferencesInput!` |
| identity | Mutation | `updateOrganization` | `id: ID!, input: UpdateOrganizationInput!` |
| identity | Mutation | `updateOrganizationApplication` | `id: ID!, input: OrganizationApplicationInput!` |
| identity | Mutation | `updateOrganizationSettings` | `id: ID!, input: UpdateOrganizationSettingsInput!` |
| identity | Mutation | `upgradeToBusinessOrganization` | `organizationId: ID!, businessName: String!` |
| identity | Mutation | `uploadVerificationDocument` | `input: UploadVerificationDocumentInput!` |
| identity | Query | `currentUserPermissions` | — |
| identity | Query | `eventAccessGrant` | `id: ID!` |
| identity | Query | `eventAccessGrants` | `eventId: ID!, status: AccessGrantStatus, pagination: OffsetPaginationInput` |
| identity | Query | `hasPendingOwnershipTransfer` | `organizationId: ID!` |
| identity | Query | `isSlugAvailable` | `slug: String!` |
| identity | Query | `me` | — |
| identity | Query | `myAnnouncements` | — |
| identity | Query | `myApprovedDocumentCount` | — |
| identity | Query | `myContacts` | — |
| identity | Query | `myDevices` | — |
| identity | Query | `myEffectivePermissions` | `organizationId: ID, eventId: ID` |
| identity | Query | `myEventAccess` | `eventId: ID!` |
| identity | Query | `myEventAccessGrants` | — |
| identity | Query | `myEventReminders` | `eventId: ID` |
| identity | Query | `myNotificationPreferences` | — |
| identity | Query | `myNotifications` | `type: NotificationType, status: NotificationStatus, pagination: CursorPaginationInput` |
| identity | Query | `myOrganization` | — |
| identity | Query | `myOrganizationMembership` | `organizationId: ID!` |
| identity | Query | `myOrganizations` | — |
| identity | Query | `myOwnedOrganization` | — |
| identity | Query | `myPendingInvitations` | — |
| identity | Query | `myPendingOwnershipTransfers` | — |
| identity | Query | `myPermissions` | — |
| identity | Query | `mySessions` | — |
| identity | Query | `myVerificationDocumentByType` | `documentType: String!` |
| identity | Query | `myVerificationDocumentCount` | — |
| identity | Query | `myVerificationDocuments` | `status: DocumentStatus` |
| identity | Query | `organization` | `id: ID!` |
| identity | Query | `organizationByOwnerId` | `ownerId: ID!` |
| identity | Query | `organizationBySlug` | `slug: String!` |
| identity | Query | `organizationEventAccessGrants` | `organizationId: ID!, status: AccessGrantStatus, pagination: OffsetPaginationInput` |
| identity | Query | `organizationMember` | `organizationId: ID!, userId: ID!` |
| identity | Query | `organizationMembers` | `organizationId: ID!, role: OrganizationRole, status: MemberStatus, pagination: OffsetPaginationInput` |
| identity | Query | `ownershipTransfer` | `id: ID!` |
| identity | Query | `ownershipTransferByToken` | `token: String!` |
| identity | Query | `ownershipTransfers` | `organizationId: ID!` |
| identity | Query | `pendingInvitations` | `organizationId: ID!, pagination: OffsetPaginationInput` |
| identity | Query | `pendingOwnershipTransfer` | `organizationId: ID!` |
| identity | Query | `platformRules` | — |
| identity | Query | `unreadNotificationCount` | — |
| identity | Query | `userEventAccess` | `userId: ID!, eventId: ID!` |
| identity | Query | `verificationDocument` | `id: ID!` |

### `CUSTOMER` — 14 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `acceptTicketTransfer` | `transferId: ID!` |
| booking | Mutation | `cancelRefundRequest` | `refundRequestId: ID!, reason: String!` |
| booking | Mutation | `cancelReservation` | `reservationId: ID!` |
| booking | Mutation | `cancelTicketTransfer` | `transferId: ID!` |
| booking | Mutation | `createUserRefundRequest` | `input: CreateRefundRequestInput!` |
| booking | Mutation | `declineTicketTransfer` | `transferId: ID!` |
| booking | Mutation | `initiateTicketTransfer` | `input: InitiateTicketTransferInput!` |
| booking | Mutation | `payReservation` | `input: PayReservationInput!` |
| booking | Mutation | `reserveTickets` | `input: ReserveTicketsInput!` |
| booking | Query | `myBookings` | `filter: BookingFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `myTicketTransfers` | `direction: TransferDirection, status: TicketTransferStatus, pagination: OffsetPaginationInput` |
| booking | Query | `transferRecipient` | `channel: TransferChannel!, value: String!` |
| catalog | Mutation | `unlockTierWithAccessCode` | `eventId: ID!, accessCode: String!` |
| catalog | Query | `recommendedEvents` | `basedOnEventIds: [ID!], first: Int = 10` |

### `FINANCE` — 18 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `confirmRecoveryAction` | `proposalId: ID!, reason: String!` |
| booking | Mutation | `createAdminRefundRequest` | `ticketId: ID!, reason: String!, bypassApproval: Boolean, amount: BigDecimal` |
| booking | Mutation | `holdPayoutRequest` | `payoutRequestId: ID!, reason: String!` |
| booking | Mutation | `proposeRecoveryAction` | `input: ProposeRecoveryActionInput!` |
| booking | Mutation | `releasePayoutHold` | `payoutRequestId: ID!, note: String` |
| booking | Mutation | `resumePaymentAttempt` | `depositId: String!` |
| booking | Mutation | `retryPaymentAttempts` | `depositIds: [String!]!` |
| booking | Mutation | `transferBetweenPlatformAccounts` | `input: PlatformTransferInput!` |
| booking | Mutation | `updateChargebackRecovery` | `id: ID!, input: UpdateChargebackRecoveryInput!` |
| booking | Mutation | `withdrawRecoveryProposal` | `proposalId: ID!` |
| booking | Query | `commissionRecords` | `filter: CommissionFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `dualControlQueue` | — |
| booking | Query | `gatewaySettlements` | `filter: GatewaySettlementFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `myRecoveryProposals` | — |
| booking | Query | `paymentAttemptSearch` | `filter: PaymentAttemptFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `paymentRiskSummary` | `windowHours: Int = 24` |
| booking | Query | `pendingRecoveryProposals` | — |
| booking | Query | `stuckTransactions` | `minutes: Int = 30, pagination: OffsetPaginationInput` |

### `INTERNAL` — 1 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| catalog | Mutation | `completeEvent` | `id: ID!` |

### `ORGANIZER` — 61 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `cancelTicket` | `ticketNumber: String!, reason: String!` |
| booking | Mutation | `messageTicketHolders` | `eventId: ID!, input: MessageTicketHoldersInput!` |
| booking | Mutation | `refundTicket` | `ticketNumber: String!, reason: String!, amount: BigDecimal` |
| booking | Mutation | `reviewConflict` | `id: ID!, note: String!` |
| booking | Mutation | `uploadScans` | `inputs: [ValidateTicketInput!]!` |
| booking | Mutation | `validateTicket` | `input: ValidateTicketInput!` |
| booking | Query | `bookingsByOrganizer` | `organizationId: ID, filter: BookingFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `checkInConflicts` | `eventId: ID!, pagination: OffsetPaginationInput` |
| booking | Query | `checkInSummary` | `eventId: ID!` |
| booking | Query | `myCheckInRate` | — |
| booking | Query | `myDashboardStats` | — |
| booking | Query | `myFinanceOverview` | — |
| booking | Query | `myPayoutSources` | — |
| booking | Query | `myPayoutWindow` | — |
| booking | Query | `myRecentActivity` | `limit: Int` |
| booking | Query | `myRevenueSeries` | `months: Int` |
| booking | Query | `myTicketMix` | — |
| booking | Query | `myTransactions` | `filter: OrganizerTransactionFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `myUpcomingEvents` | `limit: Int` |
| booking | Query | `payoutEligibility` | `eventId: ID!` |
| booking | Query | `purchasesByDayAndHour` | `from: DateTime, to: DateTime, eventId: ID, organizationId: ID` |
| booking | Query | `recentCheckIns` | `eventId: ID!, limit: Int = 25` |
| booking | Query | `refundRequestsByOrganizer` | `organizationId: ID, filter: RefundRequestFilterInput, pagination: OffsetPaginationInput` |
| booking | Query | `salesOverTime` | `eventId: ID!, from: DateTime, to: DateTime, bucket: SalesBucket = DAY` |
| booking | Query | `ticketHolderAudience` | `eventId: ID!, segment: HolderSegment = ALL, ticketTierId: String` |
| booking | Query | `ticketHolderMessages` | `eventId: ID!, pagination: OffsetPaginationInput` |
| catalog | Mutation | `activateTicketTier` | `tierId: ID!` |
| catalog | Mutation | `cancelEvent` | `id: ID!, input: EventCancellationInput!` |
| catalog | Mutation | `cancelScheduledPublish` | `eventId: ID!` |
| catalog | Mutation | `createEvent` | `input: CreateEventInput!` |
| catalog | Mutation | `createTicketTier` | `eventId: ID!, input: CreateTicketTierInput!` |
| catalog | Mutation | `deactivateTicketTier` | `tierId: ID!` |
| catalog | Mutation | `deleteEvent` | `id: ID!` |
| catalog | Mutation | `deleteMedia` | `id: ID!` |
| catalog | Mutation | `deleteTicketTier` | `tierId: ID!` |
| catalog | Mutation | `duplicateEvent` | `eventId: ID!, newTitle: String!` |
| catalog | Mutation | `publishEvent` | `id: ID!` |
| catalog | Mutation | `reorderTicketTiers` | `eventId: ID!, tierIds: [ID!]!` |
| catalog | Mutation | `rescheduleEvent` | `input: RescheduleEventInput!` |
| catalog | Mutation | `submitEventForApproval` | `eventId: ID!` |
| catalog | Mutation | `unpublishEvent` | `id: ID!` |
| catalog | Mutation | `updateEvent` | `id: ID!, input: UpdateEventInput!` |
| catalog | Mutation | `updateEventAccessibility` | `eventId: ID!, input: EventAccessibilityInput!` |
| catalog | Mutation | `updateEventCapacity` | `eventId: ID!, newCapacity: Int!` |
| catalog | Mutation | `updateMedia` | `id: ID!, input: UpdateMediaInput!` |
| catalog | Mutation | `updateTicketTier` | `tierId: ID!, input: UpdateTicketTierInput!` |
| catalog | Mutation | `uploadMedia` | `input: UploadMediaInput!` |
| catalog | Query | `allowedStatusTransitions` | `eventId: String!` |
| catalog | Query | `draftEvents` | `organizerId: String!, pagination: OffsetPaginationInput` |
| catalog | Query | `eventCountByOrganizer` | `organizerId: String!` |
| catalog | Query | `eventLifecycle` | `eventId: String!` |
| catalog | Query | `eventStatistics` | `eventId: ID!` |
| catalog | Query | `eventTicketTiers` | `eventId: ID!, includeHidden: Boolean = false` |
| catalog | Query | `myDraftEvents` | `pagination: OffsetPaginationInput` |
| catalog | Query | `myEventCount` | — |
| catalog | Query | `myEventCountByStatus` | `status: EventStatus!` |
| catalog | Query | `myEvents` | `filter: OrganizerEventFilterInput, pagination: OffsetPaginationInput` |
| catalog | Query | `myEventsConnection` | `filter: OrganizerEventFilterInput, pagination: CursorPaginationInput` |
| catalog | Query | `myMedia` | `filter: MediaFilterInput, pagination: CursorPaginationInput` |
| catalog | Query | `stockImages` | `filter: StockImageFilterInput, pagination: CursorPaginationInput` |
| catalog | Query | `ticketTierStatistics` | `eventId: ID!, tierId: ID!` |

### `PUBLIC` — 26 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| catalog | Query | `availableTicketTiers` | `eventId: ID!` |
| catalog | Query | `categories` | — |
| catalog | Query | `cities` | `provinceId: String` |
| catalog | Query | `citiesWithEvents` | — |
| catalog | Query | `city` | `id: ID!` |
| catalog | Query | `discoverEvents` | `filter: EventDiscoveryFilterInput!, pagination: CursorPaginationInput, sort: EventDiscoverySort = SOONEST` |
| catalog | Query | `event` | `id: ID!` |
| catalog | Query | `eventCategory` | `id: ID!` |
| catalog | Query | `eventsByCategory` | `categoryId: String!, pagination: CursorPaginationInput` |
| catalog | Query | `eventsByCity` | `city: String!, pagination: CursorPaginationInput` |
| catalog | Query | `location` | `id: ID!` |
| catalog | Query | `locations` | `pagination: CursorPaginationInput` |
| catalog | Query | `locationsByCity` | `city: String!, pagination: CursorPaginationInput` |
| catalog | Query | `locationsByCountry` | `country: String!, pagination: CursorPaginationInput` |
| catalog | Query | `locationsNearby` | `input: NearbyLocationInput!, pagination: CursorPaginationInput` |
| catalog | Query | `popularCategories` | `limit: Int = 10` |
| catalog | Query | `province` | `id: ID!` |
| catalog | Query | `provinces` | — |
| catalog | Query | `referenceItem` | `type: ReferenceType!, code: String!` |
| catalog | Query | `referenceTypes` | — |
| catalog | Query | `searchEvents` | `query: String!, pagination: CursorPaginationInput` |
| catalog | Query | `searchLocations` | `query: String!, pagination: CursorPaginationInput` |
| catalog | Query | `ticketTier` | `id: ID!` |
| catalog | Query | `trendingEvents` | `first: Int = 10` |
| identity | Query | `invitationByToken` | `token: String!` |
| identity | Query | `publicPlatformRules` | — |

### `SUPER_ADMIN` — 2 operation(s)

| Subgraph | Kind | Operation | Arguments |
|---|---|---|---|
| booking | Mutation | `forceCompletePaymentAttempts` | `depositIds: [String!]!, reason: String!` |
| identity | Mutation | `syncAllUsersFromKeycloak` | — |
