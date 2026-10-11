import { formatMoney } from '@/lib/format/figure';

/** Pure helpers for payouts and bank accounts. */

export { OPEN_PAYOUT_STATUSES } from '@/lib/format/enumLabels';

/**
 * The event's name now, when it differs from the name a money record was created under. The record
 * keeps its own name; this is shown beside it so a renamed event can still be found.
 */
export function currentEventName(
  recorded: string | null | undefined,
  event: { title?: string | null } | null | undefined,
): string | null {
  const now = event?.title?.trim();
  return now && now !== (recorded ?? '').trim() ? now : null;
}

/** "****7890" for account numbers; leaves short or empty values alone. */
export function maskAccount(value: string | null | undefined): string {
  if (!value) return '—';
  const digits = value.replace(/\s/g, '');
  return digits.length <= 4 ? digits : `****${digits.slice(-4)}`;
}

/** Why a payout is blocked, in the organizer's words. `minimum` is the platform floor in kwacha. */
export function blockedMessage(reason: string, minimum?: string | number | null): string {
  switch (reason) {
    case 'NO_ESCROW_ACCOUNT':
      return 'This event has no escrow account yet.';
    case 'EVENT_NOT_COMPLETED':
      return 'The event has not finished yet.';
    case 'HOLD_NOT_ELAPSED':
      return 'The hold after the event has not elapsed.';
    case 'OPEN_DISPUTES':
      return 'There are open disputes on this event.';
    case 'BELOW_MINIMUM':
      return minimum != null ? `The available balance is below the K ${Number(minimum).toLocaleString()} minimum.` : 'The available balance is below the minimum.';
    case 'PAYOUT_ALREADY_REQUESTED':
      return 'A payout request is already open for this escrow.';
    default:
      return 'This escrow is not eligible for a payout.';
  }
}

/** What the table can say about eligibility from the escrow status alone. */
export function escrowEligibilityLabel(status: string): { eligible: boolean; text: string } {
  switch (status) {
    case 'PAYOUT_ELIGIBLE':
      return { eligible: true, text: 'Eligible' };
    case 'HOLD':
      return { eligible: false, text: 'Hold not elapsed' };
    case 'ACTIVE':
      return { eligible: false, text: 'Event not completed' };
    case 'SUSPENDED':
      return { eligible: false, text: 'Suspended by the platform' };
    default:
      return { eligible: false, text: 'Closed' };
  }
}

export function sumAmounts(values: Array<string | number | null | undefined>): number {
  return values.reduce<number>((a, v) => a + (Number.isFinite(Number(v)) ? Number(v) : 0), 0);
}

/** "+K 150.00" / "-K 50.00": sign first, then the currency, as the ledger prints it. */
export function signedMoney(amount: string | number, currency?: string | null): string {
  const n = Number(amount);
  return `${n < 0 ? '-' : '+'}${formatMoney(Math.abs(n), currency, { decimals: 2 })}`;
}
