/**
 * Pure editor model: form state, validation, approval blockers, the
 * publishing checklist, commission maths and the mapping to the GraphQL
 * inputs. No React, no network: everything here is unit tested.
 */
import { richToText } from '@pml.tickets/shared';
import type { EditorEventData, EditorTier } from '@/lib/api/event-editor';

export const TAB_IDS = ['basics', 'when', 'venue', 'tiers', 'policy', 'design', 'publish'] as const;
export type TabId = (typeof TAB_IDS)[number];
export const TAB_LABELS: Record<TabId, string> = {
  basics: 'Basics',
  when: 'Date and schedule',
  venue: 'Venue and access',
  tiers: 'Ticket tiers',
  policy: 'Policies and checkout',
  design: 'E-ticket design',
  publish: 'Publish',
};

/** A refund policy as the platform defines it (identity `platformRules.refundPolicies`). */
export interface RefundPolicyOption {
  value: string;
  label: string;
  summary: string;
}

/** The ticket categories the catalog accepts (enum TicketCategory). */
/** A `TICKET_TIER_CATEGORY` code from the platform's list; empty until one is chosen. */
export type TierCategory = string;

export interface TierDraft {
  /** Stable key for React lists; the server id once saved. */
  key: string;
  id: string | null;
  name: string;
  description: string;
  /** Ngwee (minor units); undefined while the field is empty. */
  price: number | undefined;
  quantity: string;
  minPerOrder: string;
  maxPerOrder: string;
  benefits: string[];
  salesStartAt: string;
  salesEndAt: string;
  /** Ngwee; undefined when there is no early-bird price. */
  earlyBirdPrice: number | undefined;
  earlyBirdEndsAt: string;
  isHidden: boolean;
  accessCode: string;
  isActive: boolean;
  sold: number;
  code: string;
  category: TierCategory;
}

export interface AccessibilityDraft {
  wheelchairAccessible: boolean;
  wheelchairSeatsAvailable: string;
  signLanguageInterpreter: boolean;
  hearingLoopAvailable: boolean;
  accessibleParking: boolean;
  accessibleRestrooms: boolean;
  assistanceDogsAllowed: boolean;
  additionalNotes: string;
}

export interface FaqDraft {
  question: string;
  answer: string;
}
export interface RunningOrderDraft {
  /** 24-hour HH:mm in the venue's day. */
  time: string;
  title: string;
}
export interface CheckoutDraft {
  /** Blank = the platform limit applies. */
  maxTicketsPerOrder: string;
  collectHolderNames: boolean;
  extraQuestion: string;
}

export interface EditorForm {
  title: string;
  tagline: string;
  ageRestriction: string;
  categoryId: string;
  description: string;
  bannerImageUrl: string;
  bannerAltText: string;
  galleryImages: string[];
  isVirtual: boolean;
  isFreeEvent: boolean;
  virtualEventUrl: string;
  start: string;
  end: string;
  /** Local time of day the doors open (HH:mm), on the event's start date. */
  doorsOpen: string;
  /** Local date-time the event goes live on its own; blank = publish by hand. */
  publishAt: string;
  runningOrder: RunningOrderDraft[];
  venue: string;
  address: string;
  city: string;
  province: string;
  capacity: string;
  parking: string;
  transport: string;
  bag: string;
  accessibility: AccessibilityDraft;
  refundPolicy: string;
  cancellationPolicy: string;
  termsAndConditions: string;
  checkout: CheckoutDraft;
  faqs: FaqDraft[];
  tiers: TierDraft[];
}

export const emptyAccessibility = (): AccessibilityDraft => ({
  wheelchairAccessible: false,
  wheelchairSeatsAvailable: '',
  signLanguageInterpreter: false,
  hearingLoopAvailable: false,
  accessibleParking: false,
  accessibleRestrooms: false,
  assistanceDogsAllowed: false,
  additionalNotes: '',
});

export function emptyForm(): EditorForm {
  return {
    title: '',
    tagline: '',
    ageRestriction: '',
    categoryId: '',
    description: '',
    bannerImageUrl: '',
    bannerAltText: '',
    galleryImages: [],
    isVirtual: false,
    isFreeEvent: false,
    virtualEventUrl: '',
    start: '',
    end: '',
    doorsOpen: '',
    publishAt: '',
    runningOrder: [],
    venue: '',
    address: '',
    city: '',
    province: '',
    capacity: '',
    parking: '',
    transport: '',
    bag: '',
    accessibility: emptyAccessibility(),
    refundPolicy: '',
    cancellationPolicy: '',
    termsAndConditions: '',
    checkout: { maxTicketsPerOrder: '', collectHolderNames: false, extraQuestion: '' },
    faqs: [],
    tiers: [],
  };
}

let tierSeq = 0;
export function newTier(preset: Partial<TierDraft> = {}): TierDraft {
  tierSeq += 1;
  return {
    key: `new-${tierSeq}`,
    id: null,
    name: '',
    description: '',
    price: 0,
    quantity: '100',
    minPerOrder: '1',
    maxPerOrder: '',
    benefits: [],
    salesStartAt: '',
    salesEndAt: '',
    earlyBirdPrice: undefined,
    earlyBirdEndsAt: '',
    isHidden: false,
    accessCode: '',
    isActive: true,
    sold: 0,
    code: '',
    category: '',
    ...preset,
  };
}

export function duplicateTier(t: TierDraft): TierDraft {
  const { key: _k, ...rest } = t;
  void _k;
  return newTier({ ...rest, id: null, code: '', sold: 0, name: `${t.name} (copy)` });
}

/** ISO instant to the value of a datetime-local input (local time). */
export function toLocalInput(iso: string | null | undefined): string {
  if (!iso) return '';
  const d = new Date(iso);
  if (Number.isNaN(d.getTime())) return '';
  const p = (n: number) => String(n).padStart(2, '0');
  return `${d.getFullYear()}-${p(d.getMonth() + 1)}-${p(d.getDate())}T${p(d.getHours())}:${p(d.getMinutes())}`;
}
export const fromLocalInput = (v: string): string | null => {
  if (!v) return null;
  const d = new Date(v);
  return Number.isNaN(d.getTime()) ? null : d.toISOString();
};

function tierFromServer(t: EditorTier): TierDraft {
  return {
    key: t.id,
    id: t.id,
    code: t.code,
    name: t.name,
    description: t.description ?? '',
    price: Math.round(Number(t.price) * 100),
    quantity: String(t.quantity),
    minPerOrder: String(t.minPerOrder ?? 1),
    maxPerOrder: t.maxPerOrder != null ? String(t.maxPerOrder) : '',
    benefits: t.benefits ?? [],
    salesStartAt: toLocalInput(t.salesStartAt),
    salesEndAt: toLocalInput(t.salesEndAt),
    earlyBirdPrice: t.earlyBirdPrice ? Math.round(Number(t.earlyBirdPrice) * 100) : undefined,
    earlyBirdEndsAt: toLocalInput(t.earlyBirdEndsAt),
    isHidden: t.isHidden,
    accessCode: t.accessCode ?? '',
    isActive: t.isActive,
    sold: t.soldQuantity,
    category: t.category ?? '',
  };
}

export function formFromEvent(e: EditorEventData): EditorForm {
  const a = e.accessibility;
  return {
    title: e.title,
    tagline: e.tagline ?? '',
    ageRestriction: e.ageRestriction ?? '',
    categoryId: e.categoryId ?? '',
    description: e.description,
    bannerImageUrl: e.bannerImageUrl ?? '',
    bannerAltText: e.bannerAltText ?? '',
    galleryImages: e.galleryImages ?? [],
    isVirtual: e.isVirtual,
    isFreeEvent: e.isFreeEvent,
    virtualEventUrl: e.virtualEventUrl ?? '',
    start: toLocalInput(e.eventDateTime),
    end: toLocalInput(e.endDateTime),
    doorsOpen: e.doorsOpenAt ? toLocalInput(e.doorsOpenAt).slice(11) : '',
    publishAt: toLocalInput(e.publishAt),
    runningOrder: (e.runningOrder ?? []).map((r) => ({ time: r.time, title: r.title })),
    venue: e.location?.name ?? '',
    address: e.location?.address ?? '',
    city: e.location?.city ?? '',
    province: e.location?.province ?? '',
    capacity: e.totalCapacity ? String(e.totalCapacity) : '',
    parking: e.parkingInfo ?? '',
    transport: e.gettingThere ?? '',
    bag: e.bagPolicy ?? '',
    accessibility: a
      ? {
          wheelchairAccessible: a.wheelchairAccessible,
          wheelchairSeatsAvailable: a.wheelchairSeatsAvailable != null ? String(a.wheelchairSeatsAvailable) : '',
          signLanguageInterpreter: a.signLanguageInterpreter,
          hearingLoopAvailable: a.hearingLoopAvailable,
          accessibleParking: a.accessibleParking,
          accessibleRestrooms: a.accessibleRestrooms,
          assistanceDogsAllowed: a.assistanceDogsAllowed,
          additionalNotes: a.additionalNotes ?? '',
        }
      : emptyAccessibility(),
    refundPolicy: e.refundPolicy ?? '',
    cancellationPolicy: e.cancellationPolicy ?? '',
    termsAndConditions: e.termsAndConditions ?? '',
    checkout: {
      maxTicketsPerOrder: e.checkoutSettings?.maxTicketsPerOrder != null ? String(e.checkoutSettings.maxTicketsPerOrder) : '',
      collectHolderNames: e.checkoutSettings?.collectHolderNames ?? false,
      extraQuestion: e.checkoutSettings?.extraQuestion ?? '',
    },
    faqs: (e.faqs ?? []).map((f) => ({ question: f.question, answer: f.answer })),
    tiers: [...(e.ticketTiers ?? [])].sort((a1, b1) => a1.sortOrder - b1.sortOrder).map(tierFromServer),
  };
}

/* ------------------------------------------------------------ lock rules */

export const isEditLocked = (status: string | null | undefined): boolean =>
  ['COMPLETED', 'CANCELLED', 'PENDING_APPROVAL', 'REJECTED'].includes(status ?? '');
export const isDateLocked = (status: string | null | undefined): boolean => ['PUBLISHED', 'APPROVED'].includes(status ?? '');
export function lockReason(status: string | null | undefined): string {
  if (status === 'PENDING_APPROVAL')
    return 'This event is with a platform reviewer, so editing is paused. You can edit again if changes are requested.';
  if (status === 'REJECTED') return 'Rejected events cannot be edited. Duplicate it to start a corrected draft.';
  const s = (status ?? '').toLowerCase().replace(/_/g, ' ');
  return `${s.charAt(0).toUpperCase()}${s.slice(1)} events cannot be edited.`;
}

/* -------------------------------------------------------------- commission */

export const num = (v: string): number => {
  const n = parseFloat(v);
  return Number.isFinite(n) ? n : 0;
};
/** Ngwee to kwacha for display and the API (which takes major units). */
export const kwacha = (minor: number | undefined | null): number => (typeof minor === 'number' && Number.isFinite(minor) ? minor / 100 : 0);
const round2 = (n: number) => Math.round(n * 100) / 100;

export function commissionFor(price: number, percent: number) {
  const fee = round2((price * percent) / 100);
  return { fee, net: round2(price - fee) };
}

/** Price a buyer pays right now: the early-bird price while it is running. */
export function currentPrice(t: TierDraft, now: Date = new Date()): number {
  const price = kwacha(t.price);
  const early = kwacha(t.earlyBirdPrice);
  if (early > 0 && t.earlyBirdEndsAt && now.getTime() <= new Date(t.earlyBirdEndsAt).getTime() && early < price) return early;
  return price;
}

export type Blocker = 'NO_PUBLISHED_TIER' | 'NO_LOCATION' | 'NO_CAPACITY';
export const BLOCKER_TEXT: Record<Blocker, string> = {
  NO_PUBLISHED_TIER: 'At least one active ticket tier',
  NO_LOCATION: 'A venue name and city (or a virtual event link)',
  NO_CAPACITY: 'A total capacity greater than zero',
};
export const BLOCKER_TAB: Record<Blocker, TabId> = { NO_PUBLISHED_TIER: 'tiers', NO_LOCATION: 'venue', NO_CAPACITY: 'venue' };

export function blockers(form: EditorForm): Blocker[] {
  const b: Blocker[] = [];
  if (!form.tiers.some((t) => t.isActive)) b.push('NO_PUBLISHED_TIER');
  const hasLocation = form.isVirtual ? /^https?:\/\//.test(form.virtualEventUrl) : form.venue.trim() !== '' && form.city.trim() !== '';
  if (!hasLocation) b.push('NO_LOCATION');
  if (num(form.capacity) <= 0) b.push('NO_CAPACITY');
  return b;
}

export interface ChecklistItem {
  tab: TabId;
  message: string;
  ok: boolean;
  blocker: boolean;
}

/** `tiersValid` comes from the zod schema (schema.ts), which owns every validation rule. */
export function checklist(form: EditorForm, tiersValid = true): ChecklistItem[] {
  const bl = blockers(form);
  const add = (tab: TabId, message: string, ok: boolean, blocker = false): ChecklistItem => ({ tab, message, ok, blocker });
  const acc = form.accessibility;
  const accessibilityGiven =
    acc.wheelchairAccessible || acc.signLanguageInterpreter || acc.hearingLoopAvailable || acc.accessibleParking || acc.accessibleRestrooms || acc.assistanceDogsAllowed || acc.additionalNotes.trim() !== '';
  return [
    add('basics', 'Title of at least 3 characters', form.title.trim().length >= 3),
    add('basics', 'Category chosen', form.categoryId !== ''),
    add('basics', 'Cover image added', form.bannerImageUrl.trim() !== ''),
    add('basics', 'Description of at least 40 characters', richToText(form.description).length >= 40),
    add('when', 'Start and end set, end after start', !!form.start && !!form.end && form.end > form.start),
    add('venue', BLOCKER_TEXT.NO_LOCATION, !bl.includes('NO_LOCATION'), true),
    add('venue', BLOCKER_TEXT.NO_CAPACITY, !bl.includes('NO_CAPACITY'), true),
    add('venue', 'Accessibility information provided', accessibilityGiven),
    add('tiers', BLOCKER_TEXT.NO_PUBLISHED_TIER, !bl.includes('NO_PUBLISHED_TIER'), true),
    add('tiers', 'Every tier is valid (names, prices, quantities)', form.tiers.length > 0 && tiersValid),
    add('policy', 'Refund policy chosen and terms added', form.refundPolicy !== '' && form.termsAndConditions.trim().length >= 20),
  ];
}

/** Tabs holding an unfinished checklist item (the red dot on the tab). */
export function tabsNeedingAttention(form: EditorForm, tiersValid = true): Set<TabId> {
  return new Set(checklist(form, tiersValid).filter((c) => !c.ok).map((c) => c.tab));
}

/* ------------------------------------------------------------ GraphQL maps */

const codeFrom = (name: string, i: number) => `${name.replace(/[^A-Za-z]/g, '').slice(0, 4).toUpperCase() || 'TIER'}${i + 1}${Math.floor(Math.random() * 90 + 10)}`;

export function tierInput(t: TierDraft, i: number, currency = 'ZMW') {
  return {
    code: t.code || codeFrom(t.name, i),
    name: t.name.trim(),
    description: t.description.trim() || null,
    price: String(kwacha(t.price)),
    currency,
    quantity: Math.round(num(t.quantity)),
    minPerOrder: Math.round(num(t.minPerOrder)) || 1,
    maxPerOrder: Math.round(num(t.maxPerOrder)) || null,
    benefits: t.benefits,
    sortOrder: i,
    salesStartAt: fromLocalInput(t.salesStartAt),
    salesEndAt: fromLocalInput(t.salesEndAt),
    earlyBirdPrice: kwacha(t.earlyBirdPrice) > 0 ? String(kwacha(t.earlyBirdPrice)) : null,
    earlyBirdEndsAt: fromLocalInput(t.earlyBirdEndsAt),
    isHidden: t.isHidden,
    accessCode: t.isHidden ? t.accessCode : null,
    category: t.category,
  };
}

export function tierUpdateInput(t: TierDraft, i: number) {
  const { code: _code, currency: _cur, ...rest } = tierInput(t, i);
  void _code;
  void _cur;
  return { ...rest, isActive: t.isActive };
}

export function locationInput(f: EditorForm) {
  if (f.isVirtual && !f.venue.trim()) return { name: 'Online', address: 'Online', city: f.city.trim() || 'Online', province: f.province || null, country: 'Zambia' };
  return { name: f.venue.trim(), address: f.address.trim() || f.venue.trim(), city: f.city.trim(), province: f.province || null, country: 'Zambia' };
}

/** The doors time (HH:mm) on the start date as an instant, or null when not set. */
export function doorsInstant(f: EditorForm): string | null {
  if (!f.doorsOpen || !f.start) return null;
  return fromLocalInput(`${f.start.slice(0, 10)}T${f.doorsOpen}`);
}

/** Extras shared by create and update: the event-page content beyond the basics. */
export function extrasInput(f: EditorForm, maxPerOrderLimit?: number | null) {
  const perOrder = Math.round(num(f.checkout.maxTicketsPerOrder));
  return {
    tagline: f.tagline.trim() || null,
    ageRestriction: f.ageRestriction || null,
    bannerAltText: f.bannerAltText.trim() || null,
    galleryImages: f.galleryImages,
    doorsOpenAt: doorsInstant(f),
    publishAt: fromLocalInput(f.publishAt),
    runningOrder: f.runningOrder.filter((r) => r.time && r.title.trim()).map((r) => ({ time: r.time, title: r.title.trim() })),
    gettingThere: f.transport.trim() || null,
    parkingInfo: f.parking.trim() || null,
    bagPolicy: f.bag.trim() || null,
    faqs: f.faqs.filter((q) => q.question.trim() && q.answer.trim()).map((q) => ({ question: q.question.trim(), answer: q.answer.trim() })),
    checkoutSettings: {
      maxTicketsPerOrder: perOrder > 0 ? (maxPerOrderLimit ? Math.min(perOrder, maxPerOrderLimit) : perOrder) : null,
      collectHolderNames: f.checkout.collectHolderNames,
      extraQuestion: f.checkout.extraQuestion.trim() || null,
    },
  };
}

export function accessibilityInput(a: AccessibilityDraft) {
  return {
    wheelchairAccessible: a.wheelchairAccessible,
    wheelchairSeatsAvailable: a.wheelchairAccessible && a.wheelchairSeatsAvailable !== '' ? Math.round(num(a.wheelchairSeatsAvailable)) : null,
    signLanguageInterpreter: a.signLanguageInterpreter,
    hearingLoopAvailable: a.hearingLoopAvailable,
    accessibleParking: a.accessibleParking,
    accessibleRestrooms: a.accessibleRestrooms,
    assistanceDogsAllowed: a.assistanceDogsAllowed,
    additionalNotes: a.additionalNotes.trim() || null,
  };
}

/**
 * What the service stores as the event's capacity: the sum of its tier quantities (a different figure is
 * refused). The venue-capacity field only bounds that sum on the form; with no tiers it is sent as it is.
 */
export function capacityToSend(f: EditorForm): number {
  if (f.tiers.length === 0) return Math.round(num(f.capacity));
  return f.tiers.reduce((sum, t) => sum + Math.round(num(t.quantity)), 0);
}

export function createInput(f: EditorForm, maxPerOrderLimit?: number | null) {
  return {
    title: f.title.trim(),
    description: f.description,
    categoryId: f.categoryId,
    eventDateTime: fromLocalInput(f.start),
    endDateTime: fromLocalInput(f.end),
    location: locationInput(f),
    totalCapacity: capacityToSend(f),
    ticketTiers: f.tiers.map((t, i) => tierInput(t, i)),
    bannerImageUrl: f.bannerImageUrl.trim() || null,
    isVirtual: f.isVirtual,
    isFreeEvent: f.isFreeEvent,
    virtualEventUrl: f.isVirtual ? f.virtualEventUrl.trim() : null,
    refundPolicy: f.refundPolicy,
    cancellationPolicy: f.cancellationPolicy || null,
    termsAndConditions: f.termsAndConditions || null,
    accessibility: accessibilityInput(f.accessibility),
    ...extrasInput(f, maxPerOrderLimit),
  };
}

export function updateInput(f: EditorForm, opts: { dateLocked?: boolean; maxPerOrderLimit?: number | null } = {}) {
  const base = {
    title: f.title.trim(),
    description: f.description,
    categoryId: f.categoryId,
    location: locationInput(f),
    totalCapacity: capacityToSend(f),
    bannerImageUrl: f.bannerImageUrl.trim() || null,
    isVirtual: f.isVirtual,
    isFreeEvent: f.isFreeEvent,
    virtualEventUrl: f.isVirtual ? f.virtualEventUrl.trim() : null,
    refundPolicy: f.refundPolicy,
    cancellationPolicy: f.cancellationPolicy || null,
    termsAndConditions: f.termsAndConditions || null,
    ...extrasInput(f, opts.maxPerOrderLimit),
  };
  return opts.dateLocked ? base : { ...base, eventDateTime: fromLocalInput(f.start), endDateTime: fromLocalInput(f.end) };
}
