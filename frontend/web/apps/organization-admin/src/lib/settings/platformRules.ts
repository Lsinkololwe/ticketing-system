/**
 * Platform rules the organizer reads (set by the platform, never edited here).
 * "New" chips compare the current values with what the organizer last viewed;
 * the last-seen snapshot is a per-browser convenience in localStorage.
 */
export interface PlatformRulesView {
  updatedAt: string | null;
  updatedBy: string | null;
  commissionRate: number | null;
  minimumPayout: number | null;
  escrowHoldDays: number;
  currency: string;
  reservationHoldMinutes: number;
  reservationGraceMinutes: number;
  maxTicketsPerBooking: number;
  refundCutoffHours: number;
  rescheduleLimit: number;
  approvalSlaHours: number;
  approvalWarnHours: number;
  autoEscalation: boolean;
  escalationDelayHours: number;
  requireCommentsOnChangesRequested: boolean;
  requireCommentsOnRejection: boolean;
  refundPolicies: Array<{ code: string; label: string; summary: string }>;
  categories: string[];
  provinces: string[];
  cities: string[];
  banks: string[];
  documentTypes: string[];
  cancellationReasons: string[];
}

export type FlatRules = Record<string, string>;

export function flattenRules(r: PlatformRulesView): FlatRules {
  const f: FlatRules = {
    commissionRate: r.commissionRate == null ? '' : String(r.commissionRate),
    minimumPayout: r.minimumPayout == null ? '' : String(r.minimumPayout),
    escrowHoldDays: String(r.escrowHoldDays),
    currency: r.currency,
    reservationHoldMinutes: String(r.reservationHoldMinutes),
    reservationGraceMinutes: String(r.reservationGraceMinutes),
    maxTicketsPerBooking: String(r.maxTicketsPerBooking),
    refundCutoffHours: String(r.refundCutoffHours),
    rescheduleLimit: String(r.rescheduleLimit),
    approvalSlaHours: String(r.approvalSlaHours),
    approvalWarnHours: String(r.approvalWarnHours),
    autoEscalation: `${r.autoEscalation}|${r.escalationDelayHours}`,
    requireCommentsOnChangesRequested: String(r.requireCommentsOnChangesRequested),
    requireCommentsOnRejection: String(r.requireCommentsOnRejection),
    categories: r.categories.join(', '),
    provinces: r.provinces.join(', '),
    cities: r.cities.join(', '),
    banks: r.banks.join(', '),
    documentTypes: r.documentTypes.join(', '),
    cancellationReasons: r.cancellationReasons.join(', '),
  };
  r.refundPolicies.forEach((p) => {
    f[`refund.${p.code}`] = `${p.label}|${p.summary}`;
  });
  return f;
}

/** Keys whose value differs from the last-seen snapshot. No snapshot means nothing is new. */
export function changedKeys(current: FlatRules, seen: FlatRules | null): Set<string> {
  const out = new Set<string>();
  if (!seen) return out;
  for (const k of Object.keys(current)) {
    if (seen[k] !== undefined && seen[k] !== current[k]) out.add(k);
  }
  return out;
}

const STORAGE_KEY = 'pml.organizer.platformRulesSeen';

export function readSeen(): FlatRules | null {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    return raw ? (JSON.parse(raw) as FlatRules) : null;
  } catch {
    return null;
  }
}

export function writeSeen(flat: FlatRules): void {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(flat));
  } catch {
    /* storage unavailable: the chips simply do not persist */
  }
}

/** Reference lists the rules page shows beside the numbers (catalog reference data). */
export interface RulesLists {
  categories: string[];
  provinces: string[];
  cities: string[];
  banks: string[];
  documentTypes: string[];
  cancellationReasons: string[];
}

interface RulesSource {
  updatedAt?: string | null;
  updatedBy?: string | null;
  currency: string;
  commissionDefault: number;
  commissionRate?: number | null;
  minimumPayout?: string | null;
  reservationHoldMinutes: number;
  reservationGraceMinutes: number;
  escrowHoldDays: number;
  refundCutoffHours: number;
  maxTicketsPerBooking: number;
  rescheduleLimit: number;
  refundPolicies: Array<{ code: string; label: string; summary: string }>;
  approval: {
    slaHours: number;
    warnHours: number;
    autoEscalation: boolean;
    escalationDelayHours: number;
    requireCommentsOnRejection: boolean;
    requireCommentsOnChangesRequested: boolean;
  };
}

/** The backend's rules plus the reference lists, in the shape the rules page renders. */
export function toRulesView(r: RulesSource, lists: RulesLists): PlatformRulesView {
  return {
    updatedAt: r.updatedAt ?? null,
    updatedBy: r.updatedBy ?? null,
    commissionRate: r.commissionRate ?? r.commissionDefault,
    minimumPayout: r.minimumPayout == null ? null : Number(r.minimumPayout),
    escrowHoldDays: r.escrowHoldDays,
    currency: r.currency,
    reservationHoldMinutes: r.reservationHoldMinutes,
    reservationGraceMinutes: r.reservationGraceMinutes,
    maxTicketsPerBooking: r.maxTicketsPerBooking,
    refundCutoffHours: r.refundCutoffHours,
    rescheduleLimit: r.rescheduleLimit,
    approvalSlaHours: r.approval.slaHours,
    approvalWarnHours: r.approval.warnHours,
    autoEscalation: r.approval.autoEscalation,
    escalationDelayHours: r.approval.escalationDelayHours,
    requireCommentsOnChangesRequested: r.approval.requireCommentsOnChangesRequested,
    requireCommentsOnRejection: r.approval.requireCommentsOnRejection,
    refundPolicies: r.refundPolicies.map((p) => ({ code: p.code, label: p.label, summary: p.summary })),
    ...lists,
  };
}
