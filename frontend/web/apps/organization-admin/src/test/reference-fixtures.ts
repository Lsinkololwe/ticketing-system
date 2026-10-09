/**
 * Reference lists for unit tests. Production code carries no list of its own; these are the platform's rows as a
 * test would have the backend answer them, shaped like `referenceData` (code, name, description, metadata).
 */
export interface FixtureRow {
  code: string;
  name: string;
  description?: string | null;
  parentCode?: string | null;
  metadata?: Record<string, unknown>;
}

export const REFERENCE_FIXTURES: Record<string, FixtureRow[]> = {
  ORGANIZER_TYPE: [
    { code: 'INDIVIDUAL', name: 'Individual', description: 'Promoting events in your own name' },
    { code: 'BUSINESS', name: 'Business', description: 'A registered company or partnership' },
    { code: 'NON_PROFIT', name: 'Non-profit', description: 'A registered NGO or trust' },
    { code: 'RELIGIOUS', name: 'Religious', description: 'A church or faith organisation' },
  ],
  BUSINESS_TYPE: [
    { code: 'SOLE_PROPRIETORSHIP', name: 'Sole proprietorship', metadata: { requiredDocuments: ['NATIONAL_ID', 'TAX_CERTIFICATE'] } },
    { code: 'PARTNERSHIP', name: 'Partnership', metadata: { requiredDocuments: ['NATIONAL_ID', 'TAX_CERTIFICATE', 'PARTNERSHIP_AGREEMENT'] } },
    { code: 'LIMITED_COMPANY', name: 'Limited company', metadata: { requiredDocuments: ['NATIONAL_ID', 'TAX_CERTIFICATE', 'CERTIFICATE_OF_INCORPORATION'] } },
    { code: 'GOVERNMENT', name: 'Government', metadata: { requiredDocuments: ['NATIONAL_ID', 'AUTHORISATION_LETTER'] } },
  ],
  KYB_DOCUMENT_TYPE: [
    { code: 'NATIONAL_ID', name: 'National ID', description: 'Government-issued photo ID' },
    { code: 'TAX_CERTIFICATE', name: 'Tax certificate', description: 'ZRA tax clearance certificate' },
    { code: 'CERTIFICATE_OF_INCORPORATION', name: 'Certificate of incorporation', description: 'PACRA registration certificate' },
    { code: 'PARTNERSHIP_AGREEMENT', name: 'Partnership agreement', description: 'Signed partnership deed' },
    { code: 'AUTHORISATION_LETTER', name: 'Authorisation letter', description: 'Letter on official letterhead' },
  ],
  PROVINCE: [
    { code: 'LUS', name: 'Lusaka', parentCode: 'ZM' },
    { code: 'CB', name: 'Copperbelt', parentCode: 'ZM' },
    { code: 'NW', name: 'North-Western', parentCode: 'ZM' },
  ],
  COUNTRY: [
    { code: 'ZM', name: 'Zambia', metadata: { dialCode: '+260', iso3: 'ZMB' } },
    { code: 'ZW', name: 'Zimbabwe', metadata: { dialCode: '+263', iso3: 'ZWE' } },
  ],
  CURRENCY: [
    { code: 'ZMW', name: 'Zambian Kwacha', metadata: { symbol: 'K', decimals: 2 } },
    { code: 'USD', name: 'US Dollar', metadata: { symbol: '$', decimals: 2 } },
  ],
  MOBILE_MONEY_OPERATOR: [
    { code: 'MTN', name: 'MTN Mobile Money', metadata: { providerCode: 'MTN_MOMO_ZMB', msisdnPrefixes: ['26096', '26076'] } },
    { code: 'AIRTEL', name: 'Airtel Money', metadata: { providerCode: 'AIRTEL_OAPI_ZMB', msisdnPrefixes: ['26097', '26077'] } },
    { code: 'ZAMTEL', name: 'Zamtel Kwacha', metadata: { providerCode: 'ZAMTEL_ZMB', msisdnPrefixes: ['26095', '26055'] } },
  ],
  TICKET_TIER_CATEGORY: [
    { code: 'GENERAL', name: 'General admission' },
    { code: 'VIP', name: 'VIP' },
    { code: 'EARLY_BIRD', name: 'Early bird' },
  ],
  ORGANIZATION_ROLE: [
    { code: 'OWNER', name: 'Owner', description: 'Full control, billing, payouts, ownership.', metadata: { invitable: false } },
    { code: 'ADMIN', name: 'Admin', description: 'Runs the organization day to day.', metadata: { invitable: true } },
    { code: 'MANAGER', name: 'Manager', description: 'Creates and publishes events.', metadata: { invitable: true } },
    { code: 'MARKETER', name: 'Marketer', description: 'Analytics and promo codes.', metadata: { invitable: true } },
    { code: 'CONTRIBUTOR', name: 'Contributor', description: 'Views attendees and checks guests in.', metadata: { invitable: true } },
  ],
  EVENT_ROLE: [
    { code: 'EVENT_OWNER', name: 'Event owner', description: 'Full control of one event.', metadata: { invitable: false } },
    { code: 'EVENT_ADMIN', name: 'Event admin', metadata: { invitable: true } },
    { code: 'EDITOR', name: 'Editor', metadata: { invitable: true } },
    { code: 'CHECK_IN', name: 'Check-in', metadata: { invitable: true } },
    { code: 'VIEWER', name: 'Viewer', metadata: { invitable: true } },
  ],
  NOTIFICATION_CHANNEL: [
    { code: 'WHATSAPP', name: 'WhatsApp', metadata: { preferenceKey: 'whatsappEnabled' } },
    { code: 'SMS', name: 'SMS', metadata: { preferenceKey: 'smsEnabled' } },
    { code: 'PUSH', name: 'Push notifications', metadata: { preferenceKey: 'pushEnabled' } },
    { code: 'EMAIL', name: 'Email', metadata: { preferenceKey: 'emailEnabled' } },
    { code: 'IN_APP', name: 'In-app', metadata: { preferenceKey: 'inAppEnabled' } },
  ],
  NOTIFICATION_CATEGORY: [
    { code: 'TICKET', name: 'Ticket notifications', description: 'Bookings and tickets. Always on.', metadata: { preferenceKey: 'ticketNotifications', locked: true } },
    { code: 'EVENT_REMINDER', name: 'Event reminders', metadata: { preferenceKey: 'eventReminders', locked: false } },
    { code: 'EVENT_UPDATE', name: 'Event updates', metadata: { preferenceKey: 'eventUpdates', locked: false } },
    { code: 'PAYMENT', name: 'Payment notifications', metadata: { preferenceKey: 'paymentNotifications', locked: true } },
    { code: 'TEAM', name: 'Team notifications', metadata: { preferenceKey: 'teamNotifications', locked: false } },
    { code: 'MARKETING', name: 'Marketing emails', metadata: { preferenceKey: 'marketingEmails', locked: false } },
    { code: 'SYSTEM', name: 'System announcements', metadata: { preferenceKey: 'systemAnnouncements', locked: false } },
  ],
  TIMEZONE: [
    { code: 'Africa/Lusaka', name: 'Lusaka (CAT, UTC+2)' },
    { code: 'Africa/Johannesburg', name: 'Johannesburg (SAST, UTC+2)' },
  ],
};

type Meta = Record<string, unknown>;

/** The shape of `useReferenceOptions` / `useCountryOptions`, answered from `lists` instead of Apollo. */
export function fakeReferenceModule(lists: Record<string, FixtureRow[]> = REFERENCE_FIXTURES) {
  const build = (type: string, settings?: { skip?: boolean; parentCode?: string | null }) => {
    const rows = settings?.skip ? [] : (lists[type] ?? []).filter((r) => !settings?.parentCode || r.parentCode === settings.parentCode);
    const items = rows.map((r, i) => ({
      id: `${type}-${r.code}`, code: r.code, name: r.name, description: r.description ?? null, parentCode: r.parentCode ?? null, displayOrder: i, metadata: (r.metadata ?? {}) as Meta,
    }));
    const options = items.map((r) => ({ value: r.code, label: r.name, description: r.description, parentCode: r.parentCode, metadata: r.metadata }));
    const byCode = new Map(options.map((o) => [o.value, o]));
    return {
      items, options, byCode, loading: false, error: undefined, empty: options.length === 0, ready: true,
      labelOf: (code: string | null | undefined) => (code ? byCode.get(code)?.label ?? code : ''),
    };
  };
  return {
    useReferenceOptions: build,
    useCountryOptions: () => {
      const list = build('COUNTRY');
      return {
        countries: list.options.map((o) => ({ code: o.value, name: o.label, dial: String((o.metadata as { dialCode?: string }).dialCode ?? '').replace(/^\+/, '') })),
        loading: false, error: undefined, empty: list.empty,
      };
    },
  };
}
