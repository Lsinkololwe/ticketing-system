import { z } from 'zod';
import { REASON_MESSAGE } from '@/components/bookings/schemas';

/** Gate entry: a ticket number or code, optionally admitted manually with a typed reason. */
export const gateSchema = z
  .object({
    code: z
      .string({ error: 'Enter a ticket number or code' })
      .trim()
      .min(1, 'Enter a ticket number or code')
      .transform((c) => c.toUpperCase().replace(/^QR:/, '')),
    manual: z.boolean(),
    reason: z.string().trim(),
  })
  .superRefine((v, ctx) => {
    if (v.manual && v.reason.length < 3) {
      ctx.addIssue({ code: 'custom', path: ['reason'], message: 'A reason is required for manual admission' });
    }
  });
export type GateValues = z.output<typeof gateSchema>;

export { REASON_MESSAGE };
