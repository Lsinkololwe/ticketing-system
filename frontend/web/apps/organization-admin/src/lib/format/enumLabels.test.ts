import { readFileSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';
import {
  BOOKING_STATUS_LABELS, DISCOUNT_TYPE_LABELS, ESCROW_STATUS_LABELS, HOLDER_SEGMENT_LABELS, PAYOUT_STATUS_LABELS,
  REFUND_REQUEST_STATUS_LABELS, SALES_BUCKET_LABELS, TICKET_STATUS_LABELS, TRANSACTION_TYPE_LABELS, enumValues,
} from './enumLabels';

const generated = readFileSync(path.resolve(__dirname, '../../../../../libs/shared/src/types/graphql/index.ts'), 'utf8');

/** Members of `export type Name = | 'A' | 'B';` in the generated file. */
function members(name: string): string[] {
  const m = generated.match(new RegExp(`export type ${name} =([^;]*);`));
  expect(m, name).toBeTruthy();
  return [...m![1].matchAll(/'([A-Z_]+)'/g)].map((x) => x[1]);
}

describe('label maps cover exactly the generated enum', () => {
  const cases: Array<[string, Record<string, string>]> = [
    ['TicketStatus', TICKET_STATUS_LABELS],
    ['RefundRequestStatus', REFUND_REQUEST_STATUS_LABELS],
    ['EscrowAccountStatus', ESCROW_STATUS_LABELS],
    ['PayoutRequestStatus', PAYOUT_STATUS_LABELS],
    ['OrganizerTransactionType', TRANSACTION_TYPE_LABELS],
    ['BookingStatus', BOOKING_STATUS_LABELS],
    ['DiscountType', DISCOUNT_TYPE_LABELS],
    ['HolderSegment', HOLDER_SEGMENT_LABELS],
    ['SalesBucket', SALES_BUCKET_LABELS],
  ];
  it.each(cases)('%s', (name, labels) => {
    expect([...enumValues(labels)].sort()).toEqual(members(name).sort());
    for (const label of Object.values(labels)) expect(label.trim()).not.toBe('');
  });
});
