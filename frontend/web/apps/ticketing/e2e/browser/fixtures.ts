/**
 * Buyer-app fixtures for the browser harness. Test data lives here and in the specs only; the app
 * ships none. Shapes are typed with the generated GraphQL types (DeepPartial: the fake upstream's
 * `conform` fills what a query selects and nulls the rest, reporting it in `upstream.missing`).
 */
import type {
  DiscoverEventsQuery,
  EventPageQuery,
  BuyerPlatformRulesQuery,
  MeQuery,
  BuyerMyBookingsQuery,
  GetActiveEventCategoriesQuery,
  GetCitiesWithEventsQuery,
} from '../../../../libs/shared/src/types/graphql';
import type { GqlHandler } from '../../../../e2e-harness/browser/playwright';

export type DeepPartial<T> = T extends object ? { [K in keyof T]?: DeepPartial<T[K]> } : T;

const day = 86_400_000;
export const iso = (d: number) => new Date(Date.now() + d * day).toISOString();

type Card = NonNullable<DiscoverEventsQuery['discoverEvents']>['edges'][number]['node'];
export const card = (id: string, title: string, o: DeepPartial<Card> = {}): DeepPartial<Card> => ({
  __typename: 'Event', id, title, description: `${title} description`, status: 'PUBLISHED', featured: false, eventDateTime: iso(20), endDateTime: iso(21), cityName: 'Lusaka',
  locationName: 'Fixture Grounds', bannerImageUrl: null, galleryImages: null, organizerName: 'Fixture Organizer', soldTickets: 120, totalCapacity: 1000,
  availableTickets: 880, minTicketPrice: '120', maxTicketPrice: '600', currency: 'ZMW', soldOut: false, category: { __typename: 'EventCategory', id: 'c1', name: 'Music' }, ...o,
});
export const EVENTS = [
  card('e1', 'Fixture Sunset Sessions', { featured: true }),
  card('e2', 'Fixture Comedy Night', { category: { id: 'c2', name: 'Comedy' }, eventDateTime: iso(5), availableTickets: 50, soldTickets: 950 }),
  card('e3', 'Fixture Derby', { category: { id: 'c3', name: 'Sport' }, soldOut: true, availableTickets: 0, soldTickets: 1000 }),
];

type Tier = NonNullable<NonNullable<EventPageQuery['event']>['ticketTiers']>[number];
const tier = (id: string, name: string, price: number, o: DeepPartial<Tier> = {}): DeepPartial<Tier> => ({
  __typename: 'TicketTier', id, name, code: name.toUpperCase(), description: null, price: String(price), originalPrice: null, earlyBirdPrice: null, earlyBirdEndsAt: null, salesStartAt: null,
  salesEndAt: null, currency: 'ZMW', quantity: 100, soldQuantity: 10, availableQuantity: 90, minPerOrder: 1, maxPerOrder: 8, benefits: ['Standard entry'],
  isActive: true, isHidden: false, sortOrder: 0, ...o,
});
export const EVENT_DETAIL: DeepPartial<NonNullable<EventPageQuery['event']>> = {
  ...(EVENTS[0] as object), locationAddress: '1 Fixture Road', refundPolicy: 'MODERATE', cancellationPolicy: null, termsAndConditions: null, isVirtual: false, isFreeEvent: false,
  accessibility: { __typename: 'EventAccessibility', wheelchairAccessible: true, wheelchairSeatsAvailable: 6, signLanguageInterpreter: false, hearingLoopAvailable: true, accessibleParking: true, accessibleRestrooms: true, assistanceDogsAllowed: true, additionalNotes: 'Step-free north door.' },
  ageRestriction: 'Ages 16 and over', doorsOpenAt: iso(20), gettingThere: 'Take the Great East Road to the north gate.', parkingInfo: 'Paid parking at the east lot.', bagPolicy: 'Small bags only.',
  faqs: [{ question: 'Is there an age limit?', answer: 'Ages 16 and over.' }],
  runningOrder: [{ time: '17:00', title: 'Doors' }, { time: '19:00', title: 'Headline set' }],
  organization: { __typename: 'Organization', id: 'org1', verified: true, publishedEventCount: 4 },
  ticketTiers: [
    tier('t1', 'General', 150),
    tier('t2', 'Early bird', 120, { earlyBirdPrice: '100', earlyBirdEndsAt: iso(2), sortOrder: 1, availableQuantity: 8, benefits: ['Limited release'] }),
    tier('t3', 'VIP', 600, { sortOrder: 2, benefits: ['Fast-track entry', 'VIP lounge'] }),
    tier('t4', 'VVIP', 1300, { sortOrder: 3, availableQuantity: 0, soldQuantity: 100 }),
  ],
};

export const RULES: DeepPartial<BuyerPlatformRulesQuery['publicPlatformRules']> = {
  __typename: 'PublicPlatformRules', version: 4, updatedAt: iso(-3), currency: 'ZMW', reservationHoldMinutes: 10, reservationGraceMinutes: 5, maxTicketsPerBooking: 8, refundCutoffHours: 48, rescheduleLimit: 2,
  refundPolicies: [
    { __typename: 'RulesRefundPolicy', code: 'FLEXIBLE', label: 'Flexible', summary: 'Full refund until 24 hours before.', rules: [{ __typename: 'RulesRefundTier', daysBefore: 1, percent: 100 }] },
    { __typename: 'RulesRefundPolicy', code: 'MODERATE', label: 'Moderate', summary: 'Full refund until 7 days before, half until 2 days.', rules: [{ daysBefore: 7, percent: 100 }, { daysBefore: 2, percent: 50 }] },
    { __typename: 'RulesRefundPolicy', code: 'STRICT', label: 'Strict', summary: 'Half back until 7 days before.', rules: [{ daysBefore: 7, percent: 50 }] },
    { __typename: 'RulesRefundPolicy', code: 'NO_REFUNDS', label: 'No refunds', summary: 'All sales are final.', rules: [] },
  ],
};

export const ME: DeepPartial<NonNullable<MeQuery['me']>> = { __typename: 'User', id: 'u1', firstName: 'Mwila', lastName: 'Banda', displayName: 'Mwila B.', fullName: 'Mwila Banda', deletionRequestedAt: null, deletionScheduledFor: null };

const conn = (nodes: unknown[]) => ({
  __typename: 'EventConnection',
  edges: nodes.map((node) => ({ __typename: 'EventEdge', node, cursor: 'c' })),
  pageInfo: { __typename: 'PageInfo', totalElements: nodes.length, hasNext: false, endCursor: null },
});

/** The public catalogue + rules: what every page needs, signed out or in. */
export const catalog = (o: { events?: unknown[]; failDiscover?: boolean } = {}): Record<string, GqlHandler> => {
  const events = o.events ?? EVENTS;
  return {
    DiscoverEvents: { discoverEvents: conn(events) },
    BuyerTrendingEvents: { trendingEvents: events },
    BuyerRecommendedEvents: { recommendedEvents: [] },
    GetActiveEventCategories: {
      categories: [
        { __typename: 'EventCategory', id: 'c1', name: 'Music', code: 'MUSIC', eventCount: 1 },
        { __typename: 'EventCategory', id: 'c2', name: 'Comedy', code: 'COMEDY', eventCount: 1 },
      ] satisfies DeepPartial<GetActiveEventCategoriesQuery['categories']>,
    },
    GetCitiesWithEvents: { citiesWithEvents: [{ __typename: 'City', id: 'l', name: 'Lusaka', province: 'Lusaka' }, { __typename: 'City', id: 'n', name: 'Ndola', province: 'Copperbelt' }] satisfies DeepPartial<GetCitiesWithEventsQuery['citiesWithEvents']> },
    EventPage: { event: EVENT_DETAIL },
    BuyerPlatformRules: { publicPlatformRules: RULES },
  };
};

/* ---------------------------------------------------------------- signed-in fixtures */

type Booking = BuyerMyBookingsQuery['myBookings']['data'][number];
type Tkt = Booking['tickets'][number];
export const ticket = (id: string, n: number, o: DeepPartial<Tkt> = {}): DeepPartial<Tkt> => ({
  __typename: 'Ticket', id, ticketNumber: `TKT-${1000 + n}`, eventId: 'e1', eventTitle: 'Fixture Sunset Sessions', eventDate: iso(20), eventLocationName: 'Fixture Grounds',
  ticketCategoryName: 'General', price: '150', currency: 'ZMW', status: 'ISSUED', qrCode: `HARNESS-QR-${id}`, barcode: `HARNESS-BC-${id}`, purchaseDate: iso(-2), validUntil: iso(21),
  bookingNumber: 'BK-2026-0001', transferPending: false, transferCount: 0, ...o,
});
export const booking = (id: string, o: DeepPartial<Booking> = {}): DeepPartial<Booking> => ({
  __typename: 'Booking', id, bookingNumber: 'BK-2026-0001', reservationId: `r-${id}`, eventId: 'e1', eventTitle: 'Fixture Sunset Sessions', eventDate: iso(20), status: 'CONFIRMED', ticketCount: 2,
  totalAmount: '300', currency: 'ZMW', refundedAmount: '0', lateRefundStatus: null, contactName: 'Mwila Banda', contactEmail: 'mwila@example.test', contactPhone: '+260971234567',
  createdAt: iso(-2), confirmedAt: iso(-2), items: [{ ticketTierId: 't1', tierName: 'General', quantity: 2 }], tickets: [ticket(`${id}-1`, 1), ticket(`${id}-2`, 2)], ...o,
});
export const BOOKINGS = [
  booking('b1'),
  booking('b2', { bookingNumber: 'BK-2026-0002', eventId: 'e2', eventTitle: 'Fixture Comedy Night', eventDate: iso(-10), totalAmount: '150', ticketCount: 1, tickets: [ticket('b2-1', 3, { eventId: 'e2', eventTitle: 'Fixture Comedy Night', eventDate: iso(-10), status: 'VALIDATED', bookingNumber: 'BK-2026-0002' })] }),
];
export const bookings = (rows: unknown[]) => ({ myBookings: { __typename: 'BookingPage', data: rows, pagination: { __typename: 'Pagination', totalElements: rows.length, hasNext: false } } });

export const NOTIFICATIONS = [
  { __typename: 'Notification', id: 'n1', type: 'TICKET_ISSUED', title: 'Your tickets are ready', body: 'Fixture Sunset Sessions: 2 tickets.', actionUrl: '/my-tickets', status: 'UNREAD', readAt: null, createdAt: iso(-1) },
  { __typename: 'Notification', id: 'n2', type: 'EVENT_REMINDER', title: 'Fixture Comedy Night is tomorrow', body: 'Doors open at 19:00.', actionUrl: null, status: 'READ', readAt: iso(-1), createdAt: iso(-3) },
];
export const notifications = (rows: unknown[], unread = rows.filter((r) => (r as { readAt: unknown }).readAt === null).length) => ({
  MyNotifications: { myNotifications: { __typename: 'NotificationConnection', edges: rows.map((node) => ({ __typename: 'NotificationEdge', node })), pageInfo: { __typename: 'PageInfo', hasNext: false, endCursor: null }, totalCount: rows.length } },
  UnreadNotificationCount: { unreadNotificationCount: unread },
});

export const PREFS = { __typename: 'NotificationPreferences', emailEnabled: true, smsEnabled: false, whatsappEnabled: true, pushEnabled: false, inAppEnabled: true, ticketNotifications: true, eventReminders: true, eventUpdates: true, paymentNotifications: true, teamNotifications: false, marketingEmails: false, systemAnnouncements: true, reminderHoursBefore: 24, quietHoursStart: null, quietHoursEnd: null, timezone: 'Africa/Lusaka' };

export const CONTACTS = {
  myContacts: {
    __typename: 'MyContacts',
    contacts: [
      { __typename: 'Contact', id: 'contact-whatsapp-1', type: 'WHATSAPP', valueMasked: '+260 97 *** 4567', verifiedAt: iso(-30), primary: true, createdAt: iso(-30) },
      { __typename: 'Contact', id: 'contact-email-0001', type: 'EMAIL', valueMasked: 'm***@example.test', verifiedAt: iso(-20), primary: false, createdAt: iso(-20) },
    ],
    pendingChange: null,
  },
};

/** What every signed-in page asks for besides its own data. */
export const signedInBase = (o: { unread?: number; bookings?: unknown[] } = {}): Record<string, GqlHandler> => ({
  ...catalog(),
  Me: { me: ME },
  ...notifications(NOTIFICATIONS.slice(0, 0), o.unread ?? 0),
  BuyerMyBookings: bookings(o.bookings ?? BOOKINGS),
  BuyerMyTicketTransfers: { myTicketTransfers: { __typename: 'TicketTransferPage', data: [], pagination: { __typename: 'Pagination', totalElements: 0, hasNext: false } } },
  MyRefundRequests: { myRefundRequests: { __typename: 'RefundRequestPage', data: [], pagination: { __typename: 'Pagination', totalElements: 0, hasNext: false } } },
  MyEventReminders: { myEventReminders: [] },
  BuyerMyNotificationPreferences: { myNotificationPreferences: PREFS },
  MyContacts: CONTACTS,
});
