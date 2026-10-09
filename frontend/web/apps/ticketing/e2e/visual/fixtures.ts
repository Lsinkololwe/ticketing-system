import type { Page, Route } from '@playwright/test';

/** Fixture data lives here only: the app itself carries no sample data. */
const day = 86_400_000;
const iso = (d: number) => new Date(Date.now() + d * day).toISOString();

const card = (id: string, title: string, o: Record<string, unknown> = {}) => ({
  __typename: 'Event', id, title, description: `${title} description`, status: 'PUBLISHED', featured: false, eventDateTime: iso(20), endDateTime: iso(21), cityName: 'Lusaka',
  locationName: 'Fixture Grounds', bannerImageUrl: null, galleryImages: null, organizerName: 'Fixture Organizer', soldTickets: 120, totalCapacity: 1000,
  availableTickets: 880, minTicketPrice: '120', maxTicketPrice: '600', currency: 'ZMW', soldOut: false, category: { __typename: 'EventCategory', id: 'c1', name: 'Music' }, ...o,
});
export const EVENTS = [
  card('e1', 'Fixture Sunset Sessions', { featured: true }),
  card('e2', 'Fixture Comedy Night', { category: { __typename: 'EventCategory', id: 'c2', name: 'Comedy' }, eventDateTime: iso(5), availableTickets: 50, soldTickets: 950 }),
  card('e3', 'Fixture Derby', { category: { __typename: 'EventCategory', id: 'c3', name: 'Sport' }, soldOut: true, availableTickets: 0, soldTickets: 1000 }),
];
const tier = (id: string, name: string, price: number, o: Record<string, unknown> = {}) => ({
  __typename: 'TicketTier', id, name, code: name.toUpperCase(), description: null, price: String(price), originalPrice: null, earlyBirdPrice: null, earlyBirdEndsAt: null, salesStartAt: null,
  salesEndAt: null, currency: 'ZMW', quantity: 100, soldQuantity: 10, availableQuantity: 90, minPerOrder: 1, maxPerOrder: 8, benefits: ['Standard entry'],
  isActive: true, isHidden: false, sortOrder: 0, ...o,
});
export const EVENT_DETAIL = {
  ...EVENTS[0], locationAddress: '1 Fixture Road', refundPolicy: 'PARTIAL_REFUND', cancellationPolicy: null, termsAndConditions: null, isVirtual: false, isFreeEvent: false,
  accessibility: { __typename: 'EventAccessibility', wheelchairAccessible: true, wheelchairSeatsAvailable: 6, signLanguageInterpreter: false, hearingLoopAvailable: true, accessibleParking: true, accessibleRestrooms: true, assistanceDogsAllowed: true, additionalNotes: 'Step-free north door.' },
  ticketTiers: [
    tier('t1', 'General', 150),
    tier('t2', 'Early bird', 120, { earlyBirdPrice: '100', earlyBirdEndsAt: iso(2), sortOrder: 1, availableQuantity: 8, benefits: ['Limited release'] }),
    tier('t3', 'VIP', 600, { sortOrder: 2, benefits: ['Fast-track entry', 'VIP lounge'] }),
    tier('t4', 'VVIP', 1300, { sortOrder: 3, availableQuantity: 0, soldQuantity: 100 }),
  ],
};

const page = (nodes: unknown[]) => ({ __typename: 'EventConnection', edges: nodes.map((node) => ({ __typename: 'EventEdge', node, cursor: 'c' })), pageInfo: { __typename: 'PageInfo', totalElements: nodes.length, hasNext: false, endCursor: null } });

/** Answers the buyer app's GraphQL calls from the fixtures above. */
export async function mockCatalog(p: Page, opts: { fail?: boolean } = {}) {
  await p.route('**/api/graphql', async (route: Route) => {
    const body = route.request().postDataJSON() as { operationName?: string; variables?: Record<string, unknown> };
    const op = body.operationName ?? '';
    if (opts.fail && op !== 'GetActiveEventCategories') {
      return route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ errors: [{ message: 'boom', extensions: { code: 'INTERNAL' } }] }) });
    }
    const data: Record<string, unknown> =
      op === 'DiscoverEvents'
        ? { discoverEvents: page(EVENTS) }
        : op === 'BuyerTrendingEvents'
          ? { trendingEvents: EVENTS }
          : op === 'GetActiveEventCategories'
            ? { categories: [{ __typename: 'EventCategory', id: 'c1', name: 'Music', code: 'MUSIC', eventCount: 1 }, { __typename: 'EventCategory', id: 'c2', name: 'Comedy', code: 'COMEDY', eventCount: 1 }] }
            : op === 'GetCitiesWithEvents'
              ? { citiesWithEvents: [{ __typename: 'City', id: 'l', name: 'Lusaka', province: 'Lusaka' }, { __typename: 'City', id: 'n', name: 'Ndola', province: 'Copperbelt' }] }
              : op === 'EventPage'
                ? { event: EVENT_DETAIL }
                : {};
    await route.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ data }) });
  });
}
