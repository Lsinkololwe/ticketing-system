/**
 * Fixture data for the organizer browser specs. Test-only: app code never imports this file.
 * Data is shaped to each query by the harness (`conform`); fields a fixture omits are nulled and reported.
 */
import type { FakeUpstream, GqlHandler } from '../../../../e2e-harness/browser/playwright';

export type OrgRole = 'OWNER' | 'ADMIN' | 'MANAGER' | 'MARKETER' | 'CONTRIBUTOR';

export const ORG = {
  id: 'org-1',
  name: 'Zambezi Live Entertainment',
  slug: 'zambezi-live',
  status: 'ACTIVE',
  ownerId: 'acct-owner',
  commissionRate: 5,
  deletionRequestedAt: null as string | null,
  deletionScheduledFor: null as string | null,
  settings: { id: 'set-1', managersCanViewFinancials: false, adminsCanRequestPayouts: false },
};

/** Session options for a person with the given membership role. Platform role is ORGANIZER for everyone. */
export const as = (role: OrgRole) => ({ roles: ['ORGANIZER'], accountId: role === 'OWNER' ? 'acct-owner' : `acct-${role.toLowerCase()}`, displayName: `${role[0]}${role.slice(1).toLowerCase()} User` });

export interface BaseOpts {
  role?: OrgRole;
  org?: Partial<typeof ORG>;
  /** Backs `MyOrganizationStatus` (server layout guard). */
  status?: string | null;
}

/** The queries every console page asks through the shell. */
export function baseFixtures(opts: BaseOpts = {}): Record<string, GqlHandler> {
  const org = { ...ORG, ...opts.org };
  const role = opts.role ?? 'OWNER';
  return {
    // An owner owns the organization; every other staff role only has a membership (myOrganization).
    MyOrganizationStatus: {
      myOwnedOrganization: role === 'OWNER' && opts.status !== null ? { id: org.id, name: org.name, status: opts.status ?? org.status } : null,
      myOrganization: opts.status === null ? null : { id: org.id, name: org.name, status: opts.status ?? org.status },
    },
    OrganizerContext: { myOrganization: { ...org, status: opts.status ?? org.status } },
    OrganizerMembership: { myOrganizationMembership: { id: 'mem-1', userId: 'u-1', role, status: 'ACTIVE', customPermissions: [], deniedPermissions: [] } },
    ...notificationsFixture(0, 0),
    ...platformFixtures(),
    MyEvents: { myEvents: { content: [], totalElements: 0, totalPages: 1, hasNext: false } },
  };
}

export const useFixtures = (up: FakeUpstream, ...sets: Array<Record<string, GqlHandler>>) => {
  for (const s of sets) up.gql(s);
};

const months = ['2025-10-01', '2025-11-01', '2025-12-01', '2026-01-01', '2026-02-01', '2026-03-01', '2026-04-01', '2026-05-01', '2026-06-01', '2026-07-01', '2026-08-01', '2026-09-01'];
const rev = [9000, 15000, 17000, 21000, 8000, 9000, 1200, 29000, 56000, 68000, 118000, 168937];

export const dashboardFixtures = (): Record<string, GqlHandler> => ({
  MyOrganization: { myOwnedOrganization: ownedOrg() },
  MyDashboardStats: {
    myDashboardStats: { totalRevenue: 168937, revenueChange: 43, revenueCurrency: 'ZMW', totalTicketsSold: 4236, ticketsSoldChange: 12, activeEvents: 5, eventsChange: 0, eventsEndingThisWeek: 0, totalAttendees: 907, attendeesChange: 3, pendingPayouts: 110540, availableBalance: 148544 },
  },
  MyRevenueSeries: { myRevenueSeries: months.map((m, i) => ({ periodStart: m, revenue: rev[i], ticketsSold: Math.round(rev[i] / 40), currency: 'ZMW' })) },
  MyTicketMix: {
    myTicketMix: {
      totalSold: 4236,
      totalRevenue: 168937,
      currency: 'ZMW',
      rows: [['General', 3304], ['Early bird', 390], ['VIP', 292], ['Group', 85], ['Corporate', 80], ['Student', 55], ['Other', 30]].map(([name, count]) => ({ name, count, revenue: Number(count) * 40 })),
    },
  },
  MyCheckInRate: { myCheckInRate: { eventId: 'ie', eventTitle: 'Independence Eve Concert', eventDateTime: '2026-10-01T18:00:00Z', issued: 3329, scanned: 2932, ratePercent: 88 } },
  MyPayoutWindow: { myPayoutWindow: { availableNow: 148544, pendingRelease: 167307.35, currency: 'ZMW', windowOpenedAt: '2026-09-20T00:00:00Z', nextReleaseAt: '2026-10-12T00:00:00Z', windowDaysTotal: 14, daysElapsed: 8, daysRemaining: 6 } },
  MyUpcomingEvents: {
    myUpcomingEvents: [
      ['ev1', 'Lusaka Sunset Sessions', '2026-11-14T17:00:00Z', 204, 430],
      ['ev2', 'Livingstone Jazz Nights', '2026-11-21T17:00:00Z', 103, 280],
      ['fp', 'Zambezi Family Fun Day', '2026-11-22T09:00:00Z', 274, 1150],
      ['lv', 'Kitwe Amapiano Nights', '2026-11-27T19:00:00Z', 174, 710],
    ].map(([id, title, eventDateTime, ticketsSold, totalCapacity]) => ({ id, title, eventDateTime, ticketsSold, totalCapacity, status: 'PUBLISHED', revenue: Number(ticketsSold) * 40, currency: 'ZMW' })),
  },
  MyRecentActivity: {
    myRecentActivity: [
      ['PAYOUT_REQUESTED', 'PO-2051 requested for Heroes Day Festival', '2026-10-02T08:40:00Z'],
      ['EVENT_CREATED', 'Kasama Sunrise Run submitted for approval', '2026-10-02T08:15:00Z'],
      ['TICKET_SALE', '24 tickets sold for Kitwe Amapiano Nights today', '2026-10-01T21:00:00Z'],
    ].map(([type, message, timestamp], i) => ({ id: `a${i}`, type, message, timestamp, eventId: null, eventTitle: null, amount: null, currency: 'ZMW' })),
  },
});

export const notificationsFixture = (n = 2, unread = 2): Record<string, GqlHandler> => ({
  OrganizerNotifications: {
    myNotifications: {
      totalCount: n,
      edges: Array.from({ length: n }, (_, i) => ({
        cursor: `c${i}`,
        node: { id: `n${i}`, type: 'APPROVAL', title: 'Approval', body: i === 0 ? 'Kafue River Festival was approved. You can publish it now.' : 'Livingstone Heritage Gala needs changes before approval.', actionUrl: '/events', status: i < unread ? 'UNREAD' : 'READ', readAt: null, createdAt: '2026-09-29T11:00:00Z' },
      })),
      pageInfo: { hasNextPage: false, endCursor: null },
    },
    unreadNotificationCount: unread,
  },
});

type Ev = [id: string, title: string, status: string, date: string, city: string, sold: number, cap: number];
export const EVENTS: Ev[] = [
  ['ev1', 'Lusaka Sunset Sessions', 'PUBLISHED', '2026-11-14T17:00:00Z', 'Lusaka', 204, 430],
  ['ev2', 'Livingstone Jazz Nights', 'PUBLISHED', '2026-11-21T17:00:00Z', 'Livingstone', 103, 280],
  ['ie', 'Independence Eve Concert', 'COMPLETED', '2026-10-01T18:00:00Z', 'Lusaka', 3329, 3500],
  ['kuo', 'Mongu Cultural Showcase', 'APPROVED', '2026-12-05T14:00:00Z', 'Mongu', 0, 600],
  ['kgn', 'Kabwe Gospel Night', 'DRAFT', '2026-12-12T18:00:00Z', 'Kabwe', 0, 300],
  ['nfm', 'Ndola Food & Music Fair', 'PENDING_APPROVAL', '2026-12-19T10:00:00Z', 'Ndola', 0, 900],
  ['lhg', 'Livingstone Heritage Gala', 'CHANGES_REQUESTED', '2026-12-20T19:00:00Z', 'Livingstone', 0, 250],
  ['coo', 'Chipata Open Mic', 'REJECTED', '2026-12-21T19:00:00Z', 'Chipata', 0, 120],
  ['cx', 'Solwezi Comedy Night', 'CANCELLED', '2026-09-20T19:00:00Z', 'Solwezi', 0, 400],
];

export const eventRow = ([id, title, status, eventDateTime, cityName, soldTickets, totalCapacity]: Ev) => ({
  id, title, status, eventDateTime, endDateTime: null, locationName: `${cityName} Arena`, cityName, bannerImageUrl: null, totalCapacity, soldTickets,
  revenue: String(soldTickets * 40), currency: 'ZMW', category: { id: 'cat-1', name: 'Music' }, ticketTiers: [{ id: `${id}-t1`, isActive: true }],
});

export const eventsListFixtures = (list: Ev[] = EVENTS): Record<string, GqlHandler> => ({
  OrgEventsConnection: (vars) => {
    const f = (vars.filter ?? {}) as { status?: string | null; statuses?: string[] | null; searchQuery?: string | null };
    const rows = list.filter((e) => (!f.status || e[2] === f.status) && (!f.statuses?.length || f.statuses.includes(e[2])) && (!f.searchQuery || e[1].toLowerCase().includes(f.searchQuery.toLowerCase())));
    return { myEventsConnection: { edges: rows.map((e, i) => ({ cursor: `c${i}`, node: eventRow(e) })), pageInfo: { hasNextPage: false, endCursor: null } } };
  },
  OrgEventCounts: () => {
    const n = (s: string) => list.filter((e) => e[2] === s).length;
    return { total: list.length, draft: n('DRAFT'), pending: n('PENDING_APPROVAL'), changes: n('CHANGES_REQUESTED'), approved: n('APPROVED'), rejected: n('REJECTED'), published: n('PUBLISHED'), cancelled: n('CANCELLED'), completed: n('COMPLETED') };
  },
});

const tier = (eventId: string, i: number, name: string, price: number, quantity: number, soldQuantity: number) => ({
  id: `${eventId}-t${i}`, eventId, code: name.toUpperCase().replace(/\W/g, ''), name, description: null, price: String(price), currency: 'ZMW', quantity, soldQuantity, availableQuantity: quantity - soldQuantity,
  minPerOrder: 1, maxPerOrder: 10, benefits: [], salesStartAt: '2026-09-01T00:00:00Z', salesEndAt: '2026-11-14T00:00:00Z', earlyBirdPrice: null, earlyBirdEndsAt: null, sortOrder: i, isActive: true, isHidden: false, accessCode: null,
});

export const eventDetailFixtures = (id = 'ev1', status = 'PUBLISHED'): Record<string, GqlHandler> => {
  const e = EVENTS.find((x) => x[0] === id) ?? EVENTS[0];
  return {
    OrgEventDetail: {
      event: {
        ...eventRow([e[0], e[1], status, e[3], e[4], e[5], e[6]]),
        description: 'Golden hour sets on the river lawn.', locationAddress: 'Plot 12, Great East Road', availableTickets: e[6] - e[5], refundPolicy: 'FLEXIBLE', rejectionReason: status === 'REJECTED' ? 'We could not verify the venue.' : null,
        publishedAt: '2026-09-20T10:00:00Z', submittedForApprovalAt: '2026-09-18T10:00:00Z', approvedAt: '2026-09-19T10:00:00Z', rejectedAt: null, createdAt: '2026-09-10T10:00:00Z',
        ticketTiers: [tier(id, 1, 'Early bird', 120, 40, 39), tier(id, 2, 'General', 180, 300, 150), tier(id, 3, 'VIP', 450, 20, 6)],
      },
    },
    OrgEventStatistics: { eventStatistics: { totalTicketsAvailable: e[6], totalTicketsSold: e[5], totalTicketsRefunded: 2, totalGrossRevenue: String(e[5] * 40), totalCommissionEarned: String(e[5] * 2), overallSalesPercentage: Math.round((e[5] / e[6]) * 100), bestSellingTier: 'General' } },
    OrgEventPromos: { eventPromoCodes: [{ id: 'p1', code: 'EARLY10', eventId: id, discountType: 'PERCENTAGE', discountValue: '10', maxUses: 100, currentUses: 12, validFrom: '2026-09-01T00:00:00Z', validUntil: '2026-11-01T00:00:00Z', minPurchaseAmount: null, maxDiscountAmount: null, applicableTiers: [], isActive: true }] },
    OrgEventPayouts: { payoutRequestsByEvent: { data: [] } },
    OrganizerSalesSeries: { salesOverTime: [] },
    OrganizerHeatmap: { purchasesByDayAndHour: [] },
    OrganizerHolderAudience: { ticketHolderAudience: e[5] },
    OrganizerHolderMessages: { ticketHolderMessages: { data: [], pagination: { totalElements: 0 } } },
  };
};

const ref = (items: Array<[string, string]>, parentCode: string | null = null) => items.map(([code, name], i) => ({ id: `r-${code}`, code, name, description: null, parentCode, displayOrder: i, __typename: 'ReferenceData' }));
export const REFERENCE: Record<string, Array<ReturnType<typeof ref>[number]>> = {
  EVENT_CATEGORY: ref([['MUSIC', 'Music'], ['COMEDY', 'Comedy'], ['FAITH', 'Faith'], ['SPORT', 'Sport'], ['FOOD', 'Food and drink']]),
  PROVINCE: ref([['LUSAKA', 'Lusaka'], ['COPPERBELT', 'Copperbelt'], ['SOUTHERN', 'Southern']]),
  CITY: ref([['LUSAKA', 'Lusaka'], ['KITWE', 'Kitwe'], ['LIVINGSTONE', 'Livingstone'], ['NDOLA', 'Ndola']]),
  BANK: ref([['ZANACO', 'Zanaco'], ['STANBIC', 'Stanbic Bank'], ['FNB', 'First National Bank'], ['ABSA', 'Absa Bank']]),
  CANCELLATION_REASON: ref([['VENUE_UNAVAILABLE', 'Venue unavailable'], ['LOW_SALES', 'Low sales'], ['ARTIST_UNAVAILABLE', 'Artist unavailable'], ['OTHER', 'Other']]),
};
export const platformFixtures = (): Record<string, GqlHandler> => ({
  OrganizerPlatformRules: {
    platformRules: {
      version: 3, updatedAt: '2026-09-01T09:00:00Z', updatedBy: 'platform-admin', currency: 'ZMW', commissionDefault: 5, commissionRate: 5, minimumPayout: 500,
      reservationHoldMinutes: 10, reservationGraceMinutes: 2, escrowHoldDays: 7, refundCutoffHours: 48, maxTicketsPerBooking: 10, rescheduleLimit: 2,
      refundPolicies: [
        { code: 'FLEXIBLE', label: 'Flexible', summary: 'Full refund up to 7 days before.', rules: [{ daysBefore: 7, percent: 100 }] },
        { code: 'STRICT', label: 'Strict', summary: 'No refunds.', rules: [] },
      ],
      approval: { slaHours: 48, warnHours: 36, autoEscalation: true, escalationDelayHours: 24, requireCommentsOnRejection: true, requireCommentsOnChangesRequested: true, allowSelfApproval: false },
    },
  },
  OrganizerReferenceList: (vars) => ({ referenceData: REFERENCE[String(vars.type)] ?? [] }),
});

const mkTicket = (n: string, bk: number, i: number, status: string, price = 150) => ({
  id: `tk-${bk}-${i}`, ticketNumber: `TK-${n}-${i}`, eventId: 'ev1', eventTitle: 'Lusaka Sunset Sessions', buyerName: 'Esther Zulu', buyerEmail: 'esther@example.com', buyerPhone: '+260964503047',
  ticketCategoryName: 'General', price: String(price), currency: 'ZMW', status, purchaseDate: '2026-10-02T09:00:00Z', validatedAt: null, cancelledAt: null, cancellationReason: null, refundedAt: null, refundReason: null,
  paymentReference: 'PAY-1', netAmount: String(price * 0.95), commissionAmount: String(price * 0.05),
  paymentInfo: { paymentMethod: 'MTN_MOMO', status: 'COMPLETED', providerReference: 'MTN-9921', paymentDate: '2026-10-02T09:01:00Z' }, refundInfo: null,
});
const mkBooking = (num: number, contactName: string, phone: string, status: string, tickets: number, total: number, event = ['ev1', 'Lusaka Sunset Sessions']) => ({
  id: `bk-${num}`, bookingNumber: `BK-2026-0000${num}`, eventId: event[0], eventTitle: event[1], contactName, contactEmail: `${contactName.split(' ')[0].toLowerCase()}@example.com`, contactPhone: phone, status, ticketCount: tickets,
  subtotal: String(total), discountAmount: '0', totalAmount: String(total), currency: 'ZMW', promoCode: null, refundedAmount: '0', refundableAmount: String(total), createdAt: '2026-10-02T09:00:00Z', confirmedAt: status === 'CONFIRMED' ? '2026-10-02T09:01:00Z' : null,
  payment: { provider: 'MTN_MOMO', status: status === 'CONFIRMED' ? 'COMPLETED' : 'PENDING', reference: 'PAY-1', amount: String(total), currency: 'ZMW', payerPhone: phone, paidAt: null },
  tickets: Array.from({ length: tickets }, (_, i) => mkTicket(String(num), num, i + 1, status === 'CONFIRMED' ? 'ISSUED' : 'PENDING', total / tickets)),
});
export const BOOKINGS = [
  mkBooking(3462, 'Esther Zulu', '+260964503047', 'PENDING', 2, 300),
  mkBooking(3461, 'Chipo Banda', '+260973402112', 'FAILED', 2, 300),
  mkBooking(3460, 'Kennedy Mwiinga', '+260967750303', 'EXPIRED', 1, 150),
  mkBooking(3458, 'Mutale Lungu', '+260964200689', 'CONFIRMED', 1, 200, ['fp', 'Zambezi Family Fun Day']),
];
const refund = (i: number, status: string) => ({ id: `rr-${i}`, requestId: `RF-00${i}`, ticketNumber: `TK-3458-1`, eventId: 'ev1', refundAmount: '150', currency: 'ZMW', status, requestType: 'BUYER_REQUEST', reason: 'Cannot attend', requestedAt: '2026-10-01T11:30:00Z', policyApplied: 'FLEXIBLE' });
export const bookingsFixtures = (list = BOOKINGS, refunds = [refund(1, 'PENDING'), refund(2, 'PENDING'), refund(3, 'APPROVED')]): Record<string, GqlHandler> => ({
  ...eventsListFixtures(),
  OrganizerBookings: { bookingsByOrganizer: { data: list, pagination: { totalElements: list.length, totalPages: 1, hasNext: false } } },
  OrganizerBooking: (vars) => ({
    booking: {
      ...(list.find((b) => b.id === vars.id) ?? list[3]),
      items: [{ ticketTierId: 't2', tierName: 'General', quantity: 2, unitPrice: '150', subtotal: '300' }],
      refundRequests: [],
    },
  }),
  OrganizerRefundInbox: { refundRequestsByOrganizer: { data: refunds, pagination: { totalElements: refunds.length, totalPages: 1, hasNext: false } } },
  OrganizerCalculateRefund: { calculateRefundAmount: { ticketId: 'tk-3458-1', ticketNumber: 'TK-3458-1', eventDate: '2026-11-14T17:00:00Z', originalAmount: '200', daysBeforeEvent: 40, refundPercentage: 100, refundAmount: '200', commissionRefund: '10', platformRetains: '0', policyApplied: 'FLEXIBLE', isEligible: true, ineligibleReason: null } },
  OrganizerResendTicket: { resendTicket: { ticketId: 'tk-3458-1', ticketNumber: 'TK-3458-1', status: 'SENT', channel: 'SMS', destination: '+260964200689' } },
  OrganizerRefundTicket: { refundTicket: { id: 'tk-3458-1', ticketNumber: 'TK-3458-1', status: 'REFUNDED', refundedAt: '2026-10-05T10:00:00Z' } },
  OrganizerTickets: { ticketsByOrganizer: { data: list.flatMap((b) => b.tickets), pagination: { totalElements: 4, totalPages: 1, hasNext: false } } },
  EventRefundRequests: { refundRequestsByEvent: { data: refunds, pagination: { totalElements: refunds.length, totalPages: 1, hasNext: false } } },
});

/* ------------------------------------------------------------------ finance */
const page_ = (n: number) => ({ totalElements: n, totalPages: 1, currentPage: 0, hasNext: false, hasPrevious: false });
const payout = (i: number, status: string, amount: number, event: string) => ({
  id: `po-${i}`, requestId: `PO-205${i}`, organizerId: 'acct-owner', eventId: 'ev1', eventTitle: event, requestedAmount: String(amount), settledAmount: status === 'COMPLETED' ? String(amount) : null, currency: 'ZMW', status, payoutMethod: 'BANK_TRANSFER',
  requestedAt: '2026-10-02T08:40:00Z', approvedAt: null, processedAt: null, rejectionReason: null, bankName: 'Zanaco', accountNumber: '0123456789', bankAccountName: 'Zambezi Live Ltd', notes: null,
});
export const BANKS = [
  { id: 'ba-1', organizerId: 'acct-owner', accountHolderName: 'Zambezi Live Ltd', bankName: 'Zanaco', bankCode: 'ZANACO', branchName: 'Cairo Road', branchCode: '001', accountNumber: '0123456789', accountType: 'CURRENT', currency: 'ZMW', swiftCode: null, isDefault: true, isVerified: true, status: 'VERIFIED', createdAt: '2026-08-01T10:00:00Z' },
  { id: 'ba-2', organizerId: 'acct-owner', accountHolderName: 'Zambezi Live Ltd', bankName: 'Stanbic Bank', bankCode: 'STANBIC', branchName: 'Kitwe', branchCode: '002', accountNumber: '9876543210', accountType: 'SAVINGS', currency: 'ZMW', swiftCode: null, isDefault: false, isVerified: false, status: 'PENDING_VERIFICATION', createdAt: '2026-09-01T10:00:00Z' },
];
export const financeFixtures = (o: { payouts?: unknown[]; banks?: unknown[]; escrow?: unknown[] } = {}): Record<string, GqlHandler> => {
  const payouts = o.payouts ?? [payout(1, 'PENDING', 12000, 'Heroes Day Festival'), payout(2, 'APPROVED', 8000, 'Lusaka Sunset Sessions'), payout(3, 'COMPLETED', 25000, 'Easter Praise Night')];
  const escrow = o.escrow ?? [{ id: 'esc-1', accountNumber: 'ESC-0001', eventId: 'ev1', eventTitle: 'Lusaka Sunset Sessions', currentBalance: '8160', totalDeposits: '8160', totalWithdrawals: '0', totalRefunds: '0', totalCommissions: '408', pendingWithdrawals: null, currency: 'ZMW', status: 'ACTIVE', lockUntil: null, payoutEligibleAt: '2026-11-21T00:00:00Z' }];
  return {
    ...eventsListFixtures(),
    OrganizerEscrowAccounts: { myEscrowAccounts: { data: escrow, pagination: page_(escrow.length) } },
    PayoutsByOrganizer: { payoutRequestsByOrganizer: { data: payouts, pagination: page_(payouts.length) } },
    BankAccountsByOrganizer: { bankAccountsByOrganizer: o.banks ?? BANKS },
    OrganizerPayoutWallet: { myOrganization: { id: ORG.id, payoutConfig: { mobileMoneyAccount: { provider: 'MTN', maskedPhoneNumber: '+260 97 *** 4567', accountHolderName: 'Zambezi Live', verified: true, status: 'VERIFIED', rejectionReason: null, suspended: false, suspendedReason: null, testDepositSentAt: null, verificationAttemptsLeft: 3 } } } },
    OrganizerPayoutEligibility: { payoutEligibility: { eligible: true, reasons: [], availableAmount: '8160', currency: 'ZMW', opensAt: null, minimumAmount: '500' } },
    OrganizerEscrowTransactions: { escrowTransactions: { data: [], pagination: page_(0) } },
    MyPayoutSources: { myPayoutSources: [{ escrowAccountId: 'esc-1', eventId: 'ev1', eventTitle: 'Lusaka Sunset Sessions', availableAmount: '8160', currency: 'ZMW', eligibleSince: '2026-10-01T00:00:00Z' }] },
    MyEvents: { myEvents: { content: EVENTS.map(eventRow), totalElements: EVENTS.length, totalPages: 1, hasNext: false } },
    MyTransactions: {
      myTransactions: {
        content: [
          { id: 'tx-1', type: 'SALE', description: 'Ticket sale', amount: '300', currency: 'ZMW', status: 'COMPLETED', timestamp: '2026-10-02T09:00:00Z', eventId: 'ev1', eventTitle: 'Lusaka Sunset Sessions', reference: 'BK-2026-00003462' },
          { id: 'tx-2', type: 'PAYOUT', description: 'Payout PO-2053', amount: '-25000', currency: 'ZMW', status: 'COMPLETED', timestamp: '2026-10-01T09:00:00Z', eventId: 'ie', eventTitle: 'Independence Eve Concert', reference: 'PO-2053' },
        ],
        totalElements: 2, totalPages: 1, page: 0, size: 20, hasNext: false, hasPrevious: false,
      },
    },
  };
};

/* --------------------------------------------------------------------- team */
const member = (id: string, name: string, role: string, status = 'ACTIVE') => ({ id: `m-${id}`, userId: `u-${id}`, role, status, joinedAt: '2026-06-01T10:00:00Z', lastActiveAt: '2026-10-04T10:00:00Z', customPermissions: [], deniedPermissions: [], user: { id: `u-${id}`, fullName: name, username: id } });
export const teamFixtures = (o: { members?: unknown[]; invites?: unknown[]; transfer?: unknown | null } = {}): Record<string, GqlHandler> => ({
  ...eventsListFixtures(),
  OrganizerRoster: { myOrganization: { id: ORG.id, ownerId: 'u-mutinta', members: o.members ?? [member('mutinta', 'Mutinta Banda', 'OWNER'), member('chanda', 'Chanda Mwape', 'ADMIN'), member('thandi', 'Thandi Phiri', 'MANAGER'), member('joe', 'Joe Tembo', 'MARKETER', 'SUSPENDED')] } },
  OrganizerInvitations: { pendingInvitations: { content: o.invites ?? [{ id: 'inv-1', email: 'new@example.com', phoneNumber: null, inviteeName: 'New Person', proposedRole: 'MANAGER', eventAccessGrants: [], message: null, expiresAt: '2026-10-20T00:00:00Z', status: 'PENDING', createdAt: '2026-10-01T00:00:00Z' }] } },
  OrganizerEventAccessGrants: { eventAccessGrants: { content: [] } },
  OrganizerOrgAccessGrants: { organizationEventAccessGrants: { content: [{ id: 'g-1', userId: 'u-joe', user: { id: 'u-joe', fullName: 'Joe Tembo' }, eventId: 'ev1', eventRole: 'CHECK_IN', reason: null, status: 'ACTIVE', expiresAt: null }] } },
  OrganizerIncomingTransfers: { myPendingOwnershipTransfers: o.transfer ? [o.transfer] : [] },
  OrganizerPendingTransfer: { pendingOwnershipTransfer: null },
});

/* ----------------------------------------------------------------- settings */
export const settingsFixtures = (): Record<string, GqlHandler> => ({
  SettingsOrganization: {
    myOrganization: {
      id: ORG.id, name: 'Zambezi Live Entertainment', slug: 'zambezi-live', tagline: 'Live music across Zambia', description: 'We run live events.', logoUrl: null, bannerUrl: null, website: 'https://zambezilive.example',
      socialLinks: { facebook: null, instagram: '@zambezilive', twitter: null, linkedin: null, youtube: null, tiktok: null }, businessType: 'COMPANY', taxId: '1002003004', businessRegistrationNumber: '120200012345', yearEstablished: 2018,
      businessPhone: '+260211123456', businessEmail: 'hello@zambezilive.example', businessAddress: { addressLine1: 'Plot 5 Great East Road', addressLine2: null, city: 'Lusaka', province: 'Lusaka', country: 'ZM', postalCode: '10101' }, status: 'ACTIVE', commissionRate: 5,
      deletionRequestedAt: null, deletionScheduledFor: null,
      settings: { id: 'set-1', requireEventApproval: true, allowMembersToInvite: true, inviteRequiresApproval: false, managersCanViewFinancials: false, adminsCanRequestPayouts: false, notifyOwnerOnMemberJoin: true, notifyOwnerOnEventCreated: true, notifyOwnerOnPayoutRequest: true },
    },
  },
  SettingsMe: { me: { id: 'u-1', firstName: 'Mutinta', lastName: 'Banda', fullName: 'Mutinta Banda', email: 'mutinta@zambezilive.example', phoneNumber: '+260977123456' } },
  SettingsNotificationPrefs: { myNotificationPreferences: { id: 'np-1', emailEnabled: true, smsEnabled: false, whatsappEnabled: true, pushEnabled: false, inAppEnabled: true, ticketNotifications: true, eventReminders: true, eventUpdates: true, paymentNotifications: true, teamNotifications: true, marketingEmails: false, systemAnnouncements: true, reminderHoursBefore: 24, quietHoursStart: '22:00', quietHoursEnd: '07:00', timezone: 'Africa/Lusaka' } },
});

/* -------------------------------------------------------------------- media */
export const PIXEL = 'https://cdn.example.test/media/logo.gif';
export const mediaFixtures = (n = 3): Record<string, GqlHandler> => ({
  ...eventsListFixtures(),
  OrganizerMedia: { myMedia: { edges: Array.from({ length: n }, (_, i) => ({ cursor: `m${i}`, node: { id: `med-${i}`, url: PIXEL, fileName: `poster-${i}.jpg`, contentType: 'image/jpeg', sizeBytes: 240000, title: `Poster ${i + 1}`, altText: null, eventId: 'ev1', status: 'ACTIVE', flaggedReason: null, removedReason: null, createdAt: '2026-09-20T10:00:00Z' } })), pageInfo: { hasNextPage: false, endCursor: null } } },
});

/* -------------------------------------------------------------------- check-in */
export const checkinFixtures = (): Record<string, GqlHandler> => ({
  ...eventsListFixtures(),
  MyEventDetail: { event: { ...eventRow(EVENTS[0]), description: '', locationAddress: 'Plot 12', availableTickets: 226, rejectionReason: null, ticketTiers: [{ id: 't1', name: 'General', price: '150', currency: 'ZMW', quantity: 300, soldQuantity: 150, isActive: true }] } },
  CheckInSummary: { checkInSummary: { eventId: 'ev1', issued: 204, admitted: 120, conflicts: 2, openConflicts: 1, manualAdmissions: 3, lastCheckInAt: '2026-11-14T18:30:00Z' } },
  OrganizerCheckInConflicts: { checkInConflicts: { content: [{ id: 'cf-1', presentedCode: 'TK-3458-1', type: 'DUPLICATE', status: 'OPEN', detectedAt: '2026-11-14T18:31:00Z', reviewNote: null }], totalElements: 1 } },
  OrganizerRecentCheckIns: { recentCheckIns: [{ id: 'ci-1', ticketNumber: 'TK-3458-1', method: 'SCAN', reason: null, scannedAt: '2026-11-14T18:30:00Z', recordedAt: '2026-11-14T18:30:01Z' }] },
  EventTicketHolders: { ticketsByEvent: { data: [], pagination: page_(0) } },
});

/* ------------------------------------------------------------------- editor */
export const editorRef = (): Record<string, GqlHandler> => ({
  EditorReferenceData: {
    categories: [{ id: 'c1', name: 'Music', code: 'MUSIC', isActive: true }, { id: 'c2', name: 'Comedy', code: 'COMEDY', isActive: true }],
    provinces: [{ id: 'p1', name: 'Lusaka', code: 'LUSAKA' }],
    cities: [{ id: 'ci1', name: 'Lusaka', province: 'Lusaka', provinceId: 'p1' }],
  },
});
export const editorEvent = (id = 'kgn', status = 'DRAFT'): Record<string, GqlHandler> => ({
  ...editorRef(),
  EditorEvent: {
    event: {
      id, title: 'Kabwe Gospel Night', description: 'An evening of gospel.', status, categoryId: 'c1', eventDateTime: '2026-12-12T18:00:00Z', endDateTime: '2026-12-12T22:00:00Z', bannerImageUrl: null, bannerAltText: null, isVirtual: false, isFreeEvent: false, virtualEventUrl: null,
      totalCapacity: 300, soldTickets: 0, refundPolicy: 'FLEXIBLE', cancellationPolicy: null, termsAndConditions: null, rejectionReason: status === 'REJECTED' || status === 'CHANGES_REQUESTED' ? 'Please add a clearer venue address.' : null, tagline: 'Praise together', ageRestriction: null, doorsOpenAt: null, galleryImages: [],
      gettingThere: null, parkingInfo: null, bagPolicy: null, publishAt: null, publishScheduled: false, publishedAt: null, submittedForApprovalAt: null, approvalDeadline: null, approvedAt: null, rejectedAt: null, faqs: [], runningOrder: [],
      checkoutSettings: { maxTicketsPerOrder: 10, collectHolderNames: false, extraQuestion: null }, location: { name: 'Kabwe Showgrounds', address: 'Plot 1', city: 'Kabwe', province: 'Central', country: 'ZM' },
      accessibility: { wheelchairAccessible: true, wheelchairSeatsAvailable: 10, signLanguageInterpreter: false, hearingLoopAvailable: false, accessibleParking: true, accessibleRestrooms: true, assistanceDogsAllowed: true, additionalNotes: null },
      ticketTiers: [{ id: 'kt1', code: 'GEN', name: 'General', description: null, price: '100', currency: 'ZMW', quantity: 300, soldQuantity: 0, minPerOrder: 1, maxPerOrder: 10, benefits: [], salesStartAt: null, salesEndAt: null, earlyBirdPrice: null, earlyBirdEndsAt: null, sortOrder: 0, isActive: true, isHidden: false, accessCode: null, category: 'GENERAL' }],
    },
  },
});

export const ownedOrg = (o: Record<string, unknown> = {}) => ({
  __typename: 'Organization', // the query selects through a fragment on Organization
  id: ORG.id, ownerId: 'u-mutinta', name: 'Zambezi Live Entertainment', slug: 'zambezi-live', description: null, tagline: null, logoUrl: null, bannerUrl: null, website: null,
  socialLinks: { facebook: null, instagram: null, twitter: null, linkedin: null, youtube: null, tiktok: null }, type: 'COMPANY', status: 'ACTIVE', kybStatus: 'VERIFIED', businessEmail: 'hello@zambezilive.example', businessPhone: '+260211123456',
  businessAddress: { addressLine1: 'Plot 5', addressLine2: null, city: 'Lusaka', province: 'Lusaka', country: 'ZM', countryCode: 'ZM', postalCode: '10101', formattedAddress: 'Plot 5, Lusaka' },
  businessType: 'COMPANY', businessRegistrationNumber: '120200012345', taxId: '1002003004', verified: true, documentsVerified: true, payoutAccountVerified: true, verifiedAt: '2026-08-01T00:00:00Z',
  submittedAt: '2026-07-20T00:00:00Z', approvedAt: '2026-08-01T00:00:00Z', rejectionReason: null, reviewedAt: '2026-08-01T00:00:00Z',
  canCreateDraftEvents: true, canPublishEvents: true, canReceivePayouts: true, canBeEdited: true, canSubmitForReview: false, isApproved: true, isInApprovalWorkflow: false, createdAt: '2026-07-01T00:00:00Z', updatedAt: '2026-08-01T00:00:00Z',
  ...o,
});
