/**
 * Zod schemas and mappers for the event dialogs (tiers, promo codes, lifecycle reasons).
 * Money is integer ngwee in the form (MoneyRHF) and a Kwacha string on the wire.
 */
import { DISCOUNT_TYPE_LABELS } from '@/lib/format/enumLabels';
import { z } from 'zod';
import { minorToKwachaString, moneyMinor, nonEmptyTrimmed, parseKwachaToMinor } from '@pml.tickets/shared';
import type { OrgTier, TierInput } from '@/lib/api/events';
import type { DiscountType, PromoInput, PromoRow } from '@/lib/api/promos';

const kwacha = (minor: number) => minorToKwachaString(minor);
const local = (iso: string | null | undefined) => (iso ? iso.slice(0, 16) : '');
const toIso = (v: string | undefined) => (v ? new Date(v).toISOString() : null);

/* ----------------------------------------------------------------- tier */

/** `sold` is the number already sold: quantity cannot drop below it. */
export function tierSchema(sold = 0) {
  return z
    .object({
      name: nonEmptyTrimmed('Name').min(2, 'Enter a tier name'),
      description: z.string(),
      price: moneyMinor({ allowZero: true, belowMinMessage: () => 'Enter a price of K 0 or more' }),
      quantity: z
        .number({ error: 'Enter a quantity of at least 1' })
        .int('Enter a whole number')
        .refine((n) => n >= Math.max(1, sold), {
          error: sold ? `Quantity cannot be below the ${sold} already sold` : 'Enter a quantity of at least 1',
        }),
      minPerOrder: z.number({ error: 'At least 1' }).int().min(1, 'At least 1'),
      maxPerOrder: z.number({ error: 'Must be at least the minimum' }).int(),
      salesStartAt: z.string(),
      salesEndAt: z.string(),
      earlyBirdPrice: moneyMinor({ allowZero: true }).optional(),
      earlyBirdEndsAt: z.string(),
      benefits: z.array(z.string()),
      isHidden: z.boolean(),
      accessCode: z.string(),
    })
    .superRefine((f, ctx) => {
      const add = (path: string, message: string) => ctx.addIssue({ code: 'custom', path: [path], message });
      if (f.maxPerOrder < f.minPerOrder) add('maxPerOrder', 'Must be at least the minimum');
      if (f.salesStartAt && f.salesEndAt && f.salesEndAt <= f.salesStartAt) add('salesEndAt', 'Sales must end after they start');
      if (f.earlyBirdPrice !== undefined && f.earlyBirdPrice >= f.price) add('earlyBirdPrice', 'Early-bird price must be below the price');
      if (f.earlyBirdPrice !== undefined && !f.earlyBirdEndsAt) add('earlyBirdEndsAt', 'Choose when the early-bird price ends');
      if (f.isHidden && f.accessCode.trim().length < 4) add('accessCode', 'Hidden tiers need an access code of 4 or more characters');
    });
}

export type TierFormInput = z.input<ReturnType<typeof tierSchema>>;
export type TierFormValues = z.output<ReturnType<typeof tierSchema>>;

export function tierDefaults(t?: OrgTier | null): TierFormInput {
  return {
    name: t?.name ?? '',
    description: t?.description ?? '',
    price: t ? (parseKwachaToMinor(t.price) as number) : (undefined as never),
    quantity: t ? t.quantity : (undefined as never),
    minPerOrder: t?.minPerOrder ?? 1,
    maxPerOrder: t?.maxPerOrder ?? 8,
    salesStartAt: local(t?.salesStartAt),
    salesEndAt: local(t?.salesEndAt),
    earlyBirdPrice: t?.earlyBirdPrice ? parseKwachaToMinor(t.earlyBirdPrice) : undefined,
    earlyBirdEndsAt: local(t?.earlyBirdEndsAt),
    benefits: t?.benefits ?? [],
    isHidden: t?.isHidden ?? false,
    accessCode: t?.accessCode ?? '',
  };
}

export function tierToInput(f: TierFormValues): TierInput {
  return {
    name: f.name,
    description: f.description.trim() || null,
    price: kwacha(f.price),
    quantity: f.quantity,
    minPerOrder: f.minPerOrder,
    maxPerOrder: f.maxPerOrder,
    benefits: f.benefits,
    salesStartAt: toIso(f.salesStartAt),
    salesEndAt: toIso(f.salesEndAt),
    earlyBirdPrice: f.earlyBirdPrice !== undefined ? kwacha(f.earlyBirdPrice) : null,
    earlyBirdEndsAt: f.earlyBirdPrice !== undefined ? toIso(f.earlyBirdEndsAt) : null,
    isHidden: f.isHidden,
    accessCode: f.isHidden ? f.accessCode.trim() : null,
  };
}

/* ---------------------------------------------------------------- promo */

export function promoSchema(existing: string[], editing: boolean) {
  const taken = existing.map((c) => c.toUpperCase());
  return z
    .object({
      code: z
        .string({ error: 'Use 4 to 20 letters or numbers' })
        .trim()
        .transform((s) => s.toUpperCase())
        .refine((s) => /^[A-Z0-9]{4,20}$/.test(s), { error: 'Use 4 to 20 letters or numbers' })
        .refine((s) => editing || !taken.includes(s), { error: 'This event already has that code' }),
      discountType: z.string().refine((v): v is DiscountType => v in DISCOUNT_TYPE_LABELS, { error: 'Choose a discount type' }),
      discountValue: z.number({ error: 'Enter a discount' }),
      maxUses: z.number().int().min(0).optional(),
      validFrom: z.string(),
      validUntil: z.string(),
      minPurchaseAmount: moneyMinor({ allowZero: true }).optional(),
      maxDiscountAmount: moneyMinor({ allowZero: true }).optional(),
      applicableTiers: z.array(z.string()),
      isActive: z.boolean(),
    })
    .superRefine((f, ctx) => {
      const pct = f.discountType === 'PERCENTAGE';
      if (!(f.discountValue > 0) || (pct && f.discountValue > 100)) {
        ctx.addIssue({ code: 'custom', path: ['discountValue'], message: pct ? 'Enter a percentage from 1 to 100' : 'Enter an amount above K 0' });
      }
      if (f.validFrom && f.validUntil && f.validUntil < f.validFrom) {
        ctx.addIssue({ code: 'custom', path: ['validUntil'], message: 'Valid until must be on or after valid from' });
      }
    });
}

export type PromoFormInput = z.input<ReturnType<typeof promoSchema>>;
export type PromoFormValues = z.output<ReturnType<typeof promoSchema>>;

const day = (iso: string | null | undefined) => (iso ? iso.slice(0, 10) : '');
const minorOrUndefined = (s: string | null | undefined) => (s && Number(s) > 0 ? parseKwachaToMinor(s) : undefined);

export function promoDefaults(p?: PromoRow | null): PromoFormInput {
  return {
    code: p?.code ?? '',
    discountType: (p?.discountType ?? 'PERCENTAGE') as DiscountType,
    discountValue: p ? Number(p.discountValue) : 10,
    maxUses: p?.maxUses ? p.maxUses : undefined,
    validFrom: day(p?.validFrom),
    validUntil: day(p?.validUntil),
    minPurchaseAmount: minorOrUndefined(p?.minPurchaseAmount),
    maxDiscountAmount: minorOrUndefined(p?.maxDiscountAmount),
    applicableTiers: p?.applicableTiers ?? [],
    isActive: p?.isActive ?? true,
  };
}

export function promoToInput(f: PromoFormValues): PromoInput {
  const pos = (n: number | undefined) => (n && n > 0 ? kwacha(n) : null);
  return {
    code: f.code,
    discountType: f.discountType,
    discountValue: String(f.discountValue),
    maxUses: f.maxUses && f.maxUses > 0 ? Math.floor(f.maxUses) : null,
    validFrom: f.validFrom ? `${f.validFrom}T00:00:00Z` : null,
    validUntil: f.validUntil ? `${f.validUntil}T23:59:59Z` : null,
    minPurchaseAmount: pos(f.minPurchaseAmount),
    maxDiscountAmount: f.discountType === 'PERCENTAGE' ? pos(f.maxDiscountAmount) : null,
    applicableTiers: f.applicableTiers,
  };
}

/* ---------------------------------------------------------- lifecycle */

export const reasonSchema = z.object({ reason: nonEmptyTrimmed('Reason').min(3, 'Enter a reason (at least 3 characters)') });
export type ReasonValues = z.output<typeof reasonSchema>;

export function rescheduleSchema(currentStart: string, now: () => Date = () => new Date()) {
  return z
    .object({
      start: z.string().min(1, 'Choose a future start'),
      reason: nonEmptyTrimmed('Reason').min(3, 'Enter a reason (at least 3 characters)'),
    })
    .superRefine((f, ctx) => {
      if (f.start && new Date(f.start).getTime() <= now().getTime()) ctx.addIssue({ code: 'custom', path: ['start'], message: 'Choose a future start' });
      else if (f.start && currentStart && new Date(f.start).getTime() === new Date(currentStart).getTime()) {
        ctx.addIssue({ code: 'custom', path: ['start'], message: 'Pick a different date or time' });
      }
    });
}
export type RescheduleValues = z.output<ReturnType<typeof rescheduleSchema>>;

export const duplicateSchema = z.object({ title: nonEmptyTrimmed('Title').min(3, 'Enter a title of at least 3 characters') });
export type DuplicateValues = z.output<typeof duplicateSchema>;

export function cancelSchema(reasons: readonly string[]) {
  return z
    .object({
      reason: z.string().refine((r) => reasons.includes(r), { error: 'Choose a reason' }),
      note: z.string().trim(),
    })
    .superRefine((f, ctx) => {
      if (f.reason.trim().toLowerCase() === 'other' && f.note.length < 3) ctx.addIssue({ code: 'custom', path: ['note'], message: 'Add a short message for ticket holders' });
    });
}
export type CancelValues = z.output<ReturnType<typeof cancelSchema>>;
