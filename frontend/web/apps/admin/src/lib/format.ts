/**
 * Presentation formatters — MyTicketZM Design System §10 (Content & copy).
 *
 * Currency is ALWAYS "K 125,430": Kwacha symbol, single space, grouped tabular
 * figures. Never "ZMW", never "$". Render the result inside `.ds-amount` (or
 * the <Amount> component) so it picks up Fira Code + tabular figures.
 *
 * Enums are always humanised before they reach the screen: `PENDING_REVIEW`
 * becomes "Pending Review", `PUBLISHED` becomes "Live".
 */

// =============================================================================
// CURRENCY
// =============================================================================

/**
 * Format a Kwacha amount as `K 125,430`.
 *
 * Uses en-ZM grouping with no currency code — the "K " prefix is applied here
 * so the output can never render as "ZMW" or "$", whatever the runtime locale
 * data happens to be.
 *
 * @param amount    value in Kwacha; `null`/`undefined`/NaN yields an em dash
 * @param decimals  fraction digits (default 0 — admin tables read cleaner whole)
 */
export function formatKwacha(
  amount: number | string | null | undefined,
  decimals = 0
): string {
  const value = typeof amount === 'string' ? Number(amount) : amount;

  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }

  return `K ${value.toLocaleString('en-ZM', {
    minimumFractionDigits: decimals,
    maximumFractionDigits: decimals,
  })}`;
}

/**
 * Compact Kwacha for stat tiles where the full figure would wrap: `K 2.4M`.
 * Falls back to the full grouped form below 10,000 so small numbers stay exact.
 */
export function formatKwachaCompact(
  amount: number | string | null | undefined
): string {
  const value = typeof amount === 'string' ? Number(amount) : amount;

  if (value === null || value === undefined || Number.isNaN(value)) {
    return '—';
  }

  const abs = Math.abs(value);
  if (abs < 10_000) return formatKwacha(value);

  const units: Array<[number, string]> = [
    [1_000_000_000, 'B'],
    [1_000_000, 'M'],
    [1_000, 'K'],
  ];

  for (const [threshold, suffix] of units) {
    if (abs >= threshold) {
      const scaled = value / threshold;
      // One decimal, but drop a trailing ".0" so "K 2.0M" reads "K 2M".
      const text = scaled.toFixed(1).replace(/\.0$/, '');
      return `K ${text}${suffix}`;
    }
  }

  return formatKwacha(value);
}

/** Grouped integer for counts (tickets sold, users). Not currency. */
export function formatCount(value: number | null | undefined): string {
  if (value === null || value === undefined || Number.isNaN(value)) return '—';
  return value.toLocaleString('en-ZM');
}

// =============================================================================
// ENUM HUMANISATION
// =============================================================================

/**
 * Domain terms whose humanised form is NOT just title-cased words.
 * Keys are compared case-insensitively after normalising separators.
 */
const ENUM_OVERRIDES: Record<string, string> = {
  // Event lifecycle — "Live" is the operator-facing word for PUBLISHED.
  PUBLISHED: 'Live',
  UNPUBLISHED: 'Unlisted',
  // Acronyms and domain nouns that title-casing would mangle.
  KYC: 'KYC',
  TPIN: 'TPIN',
  NRC: 'NRC',
  API: 'API',
  ID: 'ID',
  URL: 'URL',
  VAT: 'VAT',
  MTN: 'MTN',
  ZAMTEL: 'Zamtel',
  AIRTEL: 'Airtel',
  MOBILE_MONEY: 'Mobile money',
  BANK_TRANSFER: 'Bank transfer',
};

/** Words that stay lowercase inside a humanised phrase. */
const MINOR_WORDS = new Set(['and', 'or', 'of', 'to', 'for', 'in', 'on', 'a', 'an', 'the']);

/**
 * Humanise a backend enum for display.
 *
 *   PENDING_REVIEW   -> "Pending Review"
 *   PUBLISHED        -> "Live"
 *   under_review     -> "Under Review"
 *   BUSINESS_LICENSE -> "Business License"
 *
 * Returns an em dash for empty input so a missing status never renders as
 * "undefined".
 */
export function humanizeEnum(value: string | null | undefined): string {
  if (!value) return '—';

  const key = value.trim().toUpperCase().replace(/[\s-]+/g, '_');
  if (ENUM_OVERRIDES[key]) return ENUM_OVERRIDES[key];

  return key
    .split('_')
    .filter(Boolean)
    .map((word, index) => {
      if (ENUM_OVERRIDES[word]) return ENUM_OVERRIDES[word];
      const lower = word.toLowerCase();
      if (index > 0 && MINOR_WORDS.has(lower)) return lower;
      return lower.charAt(0).toUpperCase() + lower.slice(1);
    })
    .join(' ');
}

// =============================================================================
// STATUS TONE
// =============================================================================

/**
 * Radix Badge colours permitted by the DS Badge contract.
 * `green` is the GENERIC success status. Money (revenue, payouts, escrow,
 * "paid") uses jade via `--color-money` / the `money` tone — never `green`,
 * and `green` must never be used as a brand colour.
 */
export type StatusTone = 'gray' | 'accent' | 'green' | 'amber' | 'red' | 'blue';

const STATUS_TONES: Record<string, StatusTone> = {
  // Settled / approved / healthy
  APPROVED: 'green',
  ACTIVE: 'green',
  VERIFIED: 'green',
  COMPLETED: 'green',
  PUBLISHED: 'green',
  SUCCESS: 'green',
  RESOLVED: 'green',
  ENABLED: 'green',

  // Awaiting an operator
  PENDING: 'amber',
  PENDING_REVIEW: 'amber',
  PENDING_APPROVAL: 'amber',
  AWAITING_DOCUMENTS: 'amber',
  CHANGES_REQUESTED: 'amber',
  ON_HOLD: 'amber',
  // ET-FIN-001 R4: an escrow account counting down its post-event hold. Waiting
  // on the clock rather than on a person, but amber for the same reason — it is
  // not yet money anyone can draw.
  HOLD: 'amber',

  // Escrow, ET-FIN-001 R4. PAYOUT_ELIGIBLE is the one status that means the
  // money is actually available, so it reads as settled rather than neutral —
  // grey here would make "ready to pay" indistinguishable from "closed".
  PAYOUT_ELIGIBLE: 'green',
  CLOSED: 'gray',

  // In flight
  UNDER_REVIEW: 'blue',
  IN_REVIEW: 'blue',
  PROCESSING: 'blue',
  SUBMITTED: 'blue',
  SCHEDULED: 'blue',

  // Failed / blocked
  REJECTED: 'red',
  FAILED: 'red',
  SUSPENDED: 'red',
  CANCELLED: 'red',
  EXPIRED: 'red',
  DISABLED: 'red',
  PENDING_DELETION: 'red',

  // Inert
  DRAFT: 'gray',
  ARCHIVED: 'gray',
  INACTIVE: 'gray',
  UNKNOWN: 'gray',
};

/** Map a status enum onto its DS badge tone. Unknown statuses read neutral. */
export function statusTone(value: string | null | undefined): StatusTone {
  if (!value) return 'gray';
  return STATUS_TONES[value.trim().toUpperCase().replace(/[\s-]+/g, '_')] ?? 'gray';
}
