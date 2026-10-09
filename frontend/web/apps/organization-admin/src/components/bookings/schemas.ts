import { z } from 'zod';
import { nonEmptyTrimmed } from '@pml.tickets/shared';

/** A required reason for a destructive action (refund, cancel, mark reviewed). */
export const REASON_MESSAGE = 'Enter a reason (at least 3 characters)';

export const reasonField = () =>
  nonEmptyTrimmed('Reason', { max: 500 }).pipe(z.string().min(3, REASON_MESSAGE));

export const reasonSchema = z.object({ reason: reasonField() });
export type ReasonValues = z.output<typeof reasonSchema>;

/** Process-refund page: the tickets to refund plus why, and an optional partial amount for one ticket. */
export function refundBookingSchema(maxKwacha: number, label = (n: number) => `K ${n.toFixed(2)}`) {
  return z
    .object({
      ticketNumbers: z.array(z.string()).min(1, 'Choose at least one ticket to refund'),
      reason: reasonField(),
      /** Kwacha, optional. Only for a single ticket: refunds part of it instead of the policy amount. */
      amount: z
        .string()
        .trim()
        .refine((v) => v === '' || (/^\d+(\.\d{1,2})?$/.test(v) && Number(v) > 0), { error: 'Enter an amount in kwacha, for example 25.50' }),
    })
    .superRefine((v, ctx) => {
      if (!v.amount) return;
      if (v.ticketNumbers.length !== 1) ctx.addIssue({ code: 'custom', path: ['amount'], message: 'A partial amount applies to one ticket. Select a single ticket.' });
      else if (Number(v.amount) > maxKwacha) ctx.addIssue({ code: 'custom', path: ['amount'], message: `The most you can refund is ${label(maxKwacha)}` });
    });
}
export type RefundBookingValues = z.output<ReturnType<typeof refundBookingSchema>>;
