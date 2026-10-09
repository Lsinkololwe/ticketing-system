import type { IconName } from '@pml.tickets/shared/components/m3';

export type NoteCategory = 'Tickets' | 'Payment' | 'Reminder' | 'Event update' | 'Marketing' | 'System';

/** Buyer-facing category for a backend NotificationType. */
export function categoryOf(type: string): NoteCategory {
  if (/^(TICKET_)/.test(type)) return 'Tickets';
  if (/^(PAYMENT_|REFUND_)/.test(type)) return 'Payment';
  if (/(REMINDER|STARTING_SOON)/.test(type)) return 'Reminder';
  if (/^EVENT_/.test(type)) return 'Event update';
  if (/(MARKETING|PROMO|ON_SALE)/.test(type)) return 'Marketing';
  return 'System';
}

export const CATEGORY_ICON: Record<NoteCategory, IconName> = {
  Tickets: 'ticket',
  Payment: 'check-circle',
  Reminder: 'clock',
  'Event update': 'info',
  Marketing: 'bell',
  System: 'warning',
};
