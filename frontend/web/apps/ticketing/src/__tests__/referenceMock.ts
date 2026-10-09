import { vi } from 'vitest';

/**
 * Test double for the shared reference hooks. The fixtures are the test's own: production code carries no list.
 * Use: `vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/__tests__/referenceMock')).referenceModule())`.
 */
type Row = { code: string; name: string; description?: string | null; parentCode?: string | null; metadata?: Record<string, unknown> };

export const REFERENCE_FIXTURES: Record<string, Row[]> = {
  MOBILE_MONEY_OPERATOR: [
    { code: 'MTN', name: 'MTN Mobile Money', metadata: { msisdnPrefixes: ['26096', '26076'] } },
    { code: 'AIRTEL', name: 'Airtel Money', metadata: { msisdnPrefixes: ['26097', '26077'] } },
    { code: 'ZAMTEL', name: 'Zamtel Kwacha', metadata: { msisdnPrefixes: ['26095', '26055'] } },
  ],
  REFUND_REASON: [
    { code: 'CANNOT_ATTEND', name: 'Cannot attend' },
    { code: 'ACCIDENTAL_PURCHASE', name: 'Accidental purchase' },
    { code: 'OTHER', name: 'Other' },
  ],
  COUNTRY: [
    { code: 'ZM', name: 'Zambia', metadata: { dialCode: '+260' } },
    { code: 'ZW', name: 'Zimbabwe', metadata: { dialCode: '+263' } },
  ],
  NOTIFICATION_CHANNEL: [
    { code: 'WHATSAPP', name: 'WhatsApp', description: 'Tickets and updates by WhatsApp', metadata: { preferenceKey: 'whatsappEnabled' } },
    { code: 'SMS', name: 'SMS', description: 'Text messages to your phone', metadata: { preferenceKey: 'smsEnabled' } },
    { code: 'PUSH', name: 'Push notifications', description: 'Notifications on this device', metadata: { preferenceKey: 'pushEnabled' } },
    { code: 'EMAIL', name: 'Email', description: 'Messages to your email address', metadata: { preferenceKey: 'emailEnabled' } },
    { code: 'IN_APP', name: 'In-app', description: 'The notifications list in the app', metadata: { preferenceKey: 'inAppEnabled' } },
  ],
  NOTIFICATION_CATEGORY: [
    { code: 'TICKET', name: 'Ticket notifications', description: 'Bookings, tickets and transfers. Always on.', metadata: { preferenceKey: 'ticketNotifications', locked: true } },
    { code: 'EVENT_REMINDER', name: 'Event reminders', description: 'A reminder before events', metadata: { preferenceKey: 'eventReminders', locked: false } },
    { code: 'EVENT_UPDATE', name: 'Event updates', description: 'Changes to time or venue', metadata: { preferenceKey: 'eventUpdates', locked: false } },
    { code: 'PAYMENT', name: 'Payment notifications', description: 'Payments and refunds. Always on.', metadata: { preferenceKey: 'paymentNotifications', locked: true } },
    { code: 'TEAM', name: 'Team notifications', description: 'Invitations', metadata: { preferenceKey: 'teamNotifications', locked: false } },
    { code: 'MARKETING', name: 'Marketing emails', description: 'News and offers', metadata: { preferenceKey: 'marketingEmails', locked: false } },
    { code: 'SYSTEM', name: 'System announcements', description: 'Maintenance notices', metadata: { preferenceKey: 'systemAnnouncements', locked: false } },
    { code: 'PLATFORM_ONLY', name: 'Not on this form', metadata: { preferenceKey: 'somethingThisFormDoesNotEdit', locked: false } },
  ],
};

export async function referenceModule(fixtures: Record<string, Row[]> = REFERENCE_FIXTURES) {
  const actual = await vi.importActual<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>(
    '@pml.tickets/shared/api/graphql/shared/reference',
  );
  const useReferenceOptions = (type: string, settings?: { skip?: boolean }) => {
    const rows = settings?.skip ? [] : (fixtures[type] ?? []);
    const options = rows.map((r) => ({ value: r.code, label: r.name, description: r.description ?? null, parentCode: r.parentCode ?? null, metadata: r.metadata ?? {} }));
    const byCode = new Map(options.map((o) => [o.value, o]));
    return {
      items: rows, options, byCode, loading: false, error: undefined, empty: options.length === 0, ready: true,
      labelOf: (code: string | null | undefined) => (code ? (byCode.get(code)?.label ?? code) : ''),
    };
  };
  const useCountryOptions = () => {
    const list = useReferenceOptions('COUNTRY');
    return {
      countries: list.options.map((o) => ({ code: o.value, name: o.label, dial: String((o.metadata as { dialCode: string }).dialCode).replace(/^\+/, '') })),
      loading: false, error: undefined, empty: list.empty,
    };
  };
  return { ...actual, useReferenceOptions, useCountryOptions };
}
