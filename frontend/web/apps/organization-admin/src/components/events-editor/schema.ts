/**
 * The event editor's zod schema. One schema validates the whole form; the tab that owns each
 * field is derived from its path so an invalid submit can jump to the right step.
 */
import { z } from 'zod';
import { nonEmptyTrimmed, moneyMinor } from '@pml.tickets/shared';
import { referenceCode } from '@pml.tickets/shared/api/graphql/shared/reference';
import type { TabId } from './model';

export interface EventSchemaOptions {
  /** Start and end are frozen (approved or live events); they are not re-checked. */
  dateLocked?: boolean;
  /** The platform limit for tickets per booking. */
  maxPerOrder?: number;
  /** The TICKET_TIER_CATEGORY codes the platform lists; empty while that list is unavailable (the server then decides). */
  tierCategories?: readonly string[];
  /** Clock, injectable for tests. */
  now?: () => Date;
}

const intText = (msg: string, min: number) =>
  z
    .string()
    .trim()
    .refine((v) => /^\d+$/.test(v) && Number(v) >= min, { error: msg });

const optionalIntText = z
  .string()
  .trim()
  .refine((v) => v === '' || /^\d+$/.test(v), { error: 'Enter a whole number' });

export const accessibilitySchema = z.object({
  wheelchairAccessible: z.boolean(),
  wheelchairSeatsAvailable: optionalIntText,
  signLanguageInterpreter: z.boolean(),
  hearingLoopAvailable: z.boolean(),
  accessibleParking: z.boolean(),
  accessibleRestrooms: z.boolean(),
  assistanceDogsAllowed: z.boolean(),
  additionalNotes: z.string().max(500, 'Use at most 500 characters'),
});

export function tierSchema(maxPerOrder?: number, tierCategories: readonly string[] = []) {
  return z
    .object({
      key: z.string(),
      id: z.string().nullable(),
      code: z.string(),
      sold: z.number(),
      isActive: z.boolean(),
      isHidden: z.boolean(),
      name: nonEmptyTrimmed('Tier name', { max: 40 }).refine((v) => v.length >= 2, { error: 'Enter a tier name' }),
      description: z.string().max(140, 'Use at most 140 characters'),
      price: moneyMinor({ allowZero: true }),
      quantity: intText('Enter a quantity of at least 1', 1),
      minPerOrder: intText('Minimum is 1', 1),
      maxPerOrder: z
        .string()
        .trim()
        .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= 1), { error: 'Minimum is 1' }),
      category: referenceCode(tierCategories, 'Choose one of the listed tier categories', 'Choose a tier category'),
      benefits: z.array(z.string()),
      salesStartAt: z.string(),
      salesEndAt: z.string(),
      earlyBirdPrice: z.number().int().nonnegative().optional(),
      earlyBirdEndsAt: z.string(),
      accessCode: z.string(),
    })
    .superRefine((t, ctx) => {
      const add = (path: string, message: string) => ctx.addIssue({ code: 'custom', path: [path], message });
      const early = t.earlyBirdPrice ?? 0;
      if (early > 0 && early >= t.price) add('earlyBirdPrice', 'Early-bird price must be below the normal price');
      if (early > 0 && !t.earlyBirdEndsAt) add('earlyBirdEndsAt', 'Set when the early-bird price ends');
      const q = Number(t.quantity);
      if (/^\d+$/.test(t.quantity) && q >= 1 && q < t.sold) add('quantity', `Quantity cannot go below the ${t.sold} already sold`);
      const min = Number(t.minPerOrder);
      const max = Number(t.maxPerOrder);
      if (/^\d+$/.test(t.maxPerOrder) && /^\d+$/.test(t.minPerOrder)) {
        if (max < min) add('maxPerOrder', 'Maximum must be at least the minimum');
        else if (maxPerOrder && max > maxPerOrder) add('maxPerOrder', `The platform allows at most ${maxPerOrder} tickets per booking`);
      }
      if (t.salesStartAt && t.salesEndAt && t.salesEndAt <= t.salesStartAt) add('salesEndAt', 'Sales end must be after sales start');
      if (t.isHidden && t.accessCode.trim().length < 4) add('accessCode', 'Hidden tiers need an access code of at least 4 characters');
    });
}

export function makeEventSchema(opts: EventSchemaOptions = {}) {
  const now = opts.now ?? (() => new Date());
  return z
    .object({
      tagline: z.string().max(100, 'Use at most 100 characters'),
      ageRestriction: z.string(),
      bannerAltText: z.string().max(200, 'Use at most 200 characters'),
      galleryImages: z.array(z.string()),
      doorsOpen: z.string(),
      publishAt: z.string(),
      runningOrder: z.array(
        z.object({
          time: z.string().min(1, 'Set a time'),
          title: z.string().trim().min(1, 'Say what happens').max(120, 'Use at most 120 characters'),
        })
      ),
      checkout: z.object({
        maxTicketsPerOrder: z
          .string()
          .trim()
          .refine((v) => v === '' || (/^\d+$/.test(v) && Number(v) >= 1), { error: 'Enter a whole number of at least 1' }),
        collectHolderNames: z.boolean(),
        extraQuestion: z.string().max(80, 'Use at most 80 characters'),
      }),
      faqs: z.array(
        z.object({
          question: z.string().trim().min(1, 'Write the question').max(120, 'Use at most 120 characters'),
          answer: z.string().trim().min(1, 'Write the answer').max(1000, 'Use at most 1000 characters'),
        })
      ),
      title: nonEmptyTrimmed('Title', { max: 90 }).refine((v) => v.length >= 3, { error: 'Add an event title of at least 3 characters.' }),
      categoryId: z.string().min(1, 'Choose a category.'),
      description: z.string(),
      bannerImageUrl: z
        .string()
        .trim()
        .refine((v) => v === '' || /^https?:\/\/\S+$/.test(v), { error: 'Enter a web address starting with https://' }),
      isVirtual: z.boolean(),
      isFreeEvent: z.boolean(),
      virtualEventUrl: z.string().trim(),
      start: z.string().min(1, 'Set a start date and time.'),
      end: z.string().min(1, 'Set an end date and time.'),
      venue: z.string(),
      address: z.string(),
      city: z.string(),
      province: z.string(),
      capacity: optionalIntText,
      parking: z.string().max(240),
      transport: z.string().max(240),
      bag: z.string().max(240),
      accessibility: accessibilitySchema,
      refundPolicy: z.string().min(1, 'Choose a refund policy'),
      cancellationPolicy: z.string(),
      termsAndConditions: z.string(),
      tiers: z.array(tierSchema(opts.maxPerOrder, opts.tierCategories)),
    })
    .superRefine((f, ctx) => {
      const add = (path: Array<string | number>, message: string) => ctx.addIssue({ code: 'custom', path, message });
      if (!opts.dateLocked && f.start && new Date(f.start) <= now()) add(['start'], 'The start must be in the future.');
      if (f.start && f.end && f.end <= f.start) add(['end'], 'The end must be after the start.');
      if (f.publishAt && f.start && f.publishAt >= f.start) add(['publishAt'], 'Go-live must be before the event starts.');
      if (f.publishAt && new Date(f.publishAt) <= now()) add(['publishAt'], 'Go-live must be in the future.');
      const perOrder = Number(f.checkout.maxTicketsPerOrder || 0);
      if (opts.maxPerOrder && perOrder > opts.maxPerOrder) add(['checkout', 'maxTicketsPerOrder'], `The platform allows at most ${opts.maxPerOrder} tickets per booking`);
      if (f.isVirtual && !/^https?:\/\//.test(f.virtualEventUrl)) add(['virtualEventUrl'], 'Enter the virtual event link starting with https://');
      const cap = Number(f.capacity || 0);
      if (cap > 0) {
        const total = f.tiers.filter((t) => t.isActive).reduce((a, t) => a + (Number(t.quantity) || 0), 0);
        if (total > cap)
          f.tiers.forEach((t, i) => {
            if (t.isActive) add(['tiers', i, 'quantity'], `Active tiers total ${total}, more than the capacity of ${cap}`);
          });
      }
    });
}

export type EventFormValues = z.input<ReturnType<typeof makeEventSchema>>;

/** True when every tier row passes the tier rules (drives the checklist). */
export function tiersAreValid(tiers: unknown, maxPerOrder?: number): boolean {
  return z.array(tierSchema(maxPerOrder)).safeParse(tiers).success;
}

const TAB_OF_FIELD: Array<[RegExp, TabId]> = [
  [/^(title|tagline|ageRestriction|categoryId|description|bannerImageUrl|bannerAltText|galleryImages|isVirtual|isFreeEvent)(\.|$)/, 'basics'],
  [/^(start|end|doorsOpen|publishAt|runningOrder)(\.|$)/, 'when'],
  [/^(venue|address|city|province|capacity|parking|transport|bag|virtualEventUrl|accessibility)(\.|$)/, 'venue'],
  [/^tiers(\.|$)/, 'tiers'],
  [/^(refundPolicy|cancellationPolicy|termsAndConditions|checkout|faqs)(\.|$)/, 'policy'],
];

/** The tab that owns a form field path, or null for unknown paths. */
export function tabOfField(path: string): TabId | null {
  for (const [re, tab] of TAB_OF_FIELD) if (re.test(path)) return tab;
  return null;
}

/** Flatten react-hook-form errors to field paths. */
export function errorPaths(errors: unknown, prefix = ''): string[] {
  if (!errors || typeof errors !== 'object') return [];
  const out: string[] = [];
  for (const [k, v] of Object.entries(errors as Record<string, unknown>)) {
    if (k === 'root' || k === 'ref' || k === 'type' || k === 'message' || k === 'types') continue;
    const path = prefix ? `${prefix}.${k}` : k;
    if (v && typeof v === 'object' && 'message' in v && typeof (v as { message?: unknown }).message === 'string') out.push(path);
    else out.push(...errorPaths(v, path));
  }
  return out;
}

/** Tabs holding at least one invalid field, in tab order. */
export function tabsWithErrors(errors: unknown): TabId[] {
  const found = new Set<TabId>();
  for (const p of errorPaths(errors)) {
    const t = tabOfField(p);
    if (t) found.add(t);
  }
  return ['basics', 'when', 'venue', 'tiers', 'policy'].filter((t) => found.has(t as TabId)) as TabId[];
}

/** The tab holding the first invalid field of `values`, or null when the form is valid. */
export function firstInvalidTab(schema: ReturnType<typeof makeEventSchema>, values: unknown): TabId | null {
  const r = schema.safeParse(values);
  if (r.success) return null;
  for (const issue of r.error.issues) {
    const t = tabOfField(issue.path.join('.'));
    if (t) return t;
  }
  return null;
}
