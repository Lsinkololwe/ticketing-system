import { describe, expect, it } from 'vitest';

import {
  formatCount,
  formatKwacha,
  formatKwachaCompact,
  humanizeEnum,
  statusTone,
} from './format';

/**
 * The renderings the design system fixes platform-wide.
 *
 * These are not cosmetic assertions. The design system states three rules that a
 * screen cannot restate for itself: money reads `K 125,430` and never `ZMW` or
 * `$`; an enum never reaches the DOM in SCREAMING_SNAKE; and a missing value
 * renders as an em dash rather than as `undefined` or `NaN`.
 *
 * Each of those has a failure mode that looks like working software. `ZMW 1,200`
 * is a legible amount in the wrong currency notation. `PENDING_REVIEW` on a badge
 * is readable if you already know the schema. `NaN` in a revenue tile is a number
 * shaped hole. All three ship happily without a test.
 */
describe('formatKwacha', () => {
  it('renders the Kwacha symbol, a single space and grouped figures', () => {
    expect(formatKwacha(125430)).toBe('K 125,430');
    expect(formatKwacha(0)).toBe('K 0');
    expect(formatKwacha(1)).toBe('K 1');
  });

  it('never emits a currency code or a dollar sign, whatever the input', () => {
    // The locale database is free to render en-ZM currency as "ZMW 1,200.00";
    // the prefix is applied by hand precisely so that cannot happen.
    for (const amount of [1200, 0.5, -99, 1_000_000]) {
      const rendered = formatKwacha(amount);
      expect(rendered).not.toContain('ZMW');
      expect(rendered).not.toContain('$');
      expect(rendered.startsWith('K ')).toBe(true);
    }
  });

  it('accepts the string amounts GraphQL sends for BigDecimal', () => {
    // Money crosses the wire as a decimal string, so a screen
    // handing this the raw field is the normal case rather than an edge one.
    expect(formatKwacha('2500')).toBe('K 2,500');
    expect(formatKwacha('1234.56', 2)).toBe('K 1,234.56');
  });

  it('renders an em dash for absent or unparseable values', () => {
    expect(formatKwacha(null)).toBe('—');
    expect(formatKwacha(undefined)).toBe('—');
    expect(formatKwacha(Number.NaN)).toBe('—');
    expect(formatKwacha('not a number')).toBe('—');
  });

  it('honours the requested precision', () => {
    expect(formatKwacha(1234.567, 2)).toBe('K 1,234.57');
    expect(formatKwacha(1234.5, 0)).toBe('K 1,235');
  });

  it('keeps the sign on a negative amount', () => {
    // Refunds and reversals are negative, and dropping the sign turns money out
    // into money in on a ledger screen.
    expect(formatKwacha(-350)).toContain('350');
    expect(formatKwacha(-350)).toMatch(/-/);
  });
});

describe('formatKwachaCompact', () => {
  it('stays exact below ten thousand', () => {
    expect(formatKwachaCompact(9999)).toBe('K 9,999');
    expect(formatKwachaCompact(1200)).toBe('K 1,200');
  });

  it('abbreviates above it, dropping a trailing .0', () => {
    expect(formatKwachaCompact(2_400_000)).toBe('K 2.4M');
    expect(formatKwachaCompact(2_000_000)).toBe('K 2M');
    expect(formatKwachaCompact(15_000)).toBe('K 15K');
    expect(formatKwachaCompact(3_100_000_000)).toBe('K 3.1B');
  });

  it('renders an em dash for absent values', () => {
    expect(formatKwachaCompact(null)).toBe('—');
    expect(formatKwachaCompact(Number.NaN)).toBe('—');
  });
});

describe('formatCount', () => {
  it('groups without a currency prefix', () => {
    expect(formatCount(125430)).toBe('125,430');
    expect(formatCount(0)).toBe('0');
  });

  it('is not currency', () => {
    // A ticket count rendered as "K 1,200" is the mistake this separation exists
    // to prevent.
    expect(formatCount(1200)).not.toContain('K');
  });

  it('renders an em dash for absent values', () => {
    expect(formatCount(null)).toBe('—');
    expect(formatCount(undefined)).toBe('—');
  });
});

describe('humanizeEnum', () => {
  it('title-cases a SCREAMING_SNAKE enum', () => {
    expect(humanizeEnum('PENDING_REVIEW')).toBe('Pending Review');
    expect(humanizeEnum('BUSINESS_LICENSE')).toBe('Business License');
    expect(humanizeEnum('AWAITING_DOCUMENTS')).toBe('Awaiting Documents');
  });

  it('applies the domain overrides rather than the literal word', () => {
    // "Live" is the operator-facing word; a screen saying "Published" is using
    // the schema's vocabulary rather than the product's.
    expect(humanizeEnum('PUBLISHED')).toBe('Live');
    expect(humanizeEnum('UNPUBLISHED')).toBe('Unlisted');
  });

  it('leaves acronyms alone instead of title-casing them', () => {
    expect(humanizeEnum('KYC')).toBe('KYC');
    expect(humanizeEnum('NRC')).toBe('NRC');
    expect(humanizeEnum('TPIN')).toBe('TPIN');
  });

  it('normalises separators and case so any spelling of a status agrees', () => {
    // The same status arrives lowercase from one resolver and hyphenated from
    // another; two spellings of one status on one screen reads as two statuses.
    expect(humanizeEnum('under_review')).toBe('Under Review');
    expect(humanizeEnum('UNDER-REVIEW')).toBe('Under Review');
    expect(humanizeEnum('  under review  ')).toBe('Under Review');
  });

  it('keeps minor words lowercase inside a phrase', () => {
    expect(humanizeEnum('AWAITING_PROOF_OF_ADDRESS')).toBe('Awaiting Proof of Address');
  });

  it('renders an em dash rather than "undefined" for a missing status', () => {
    expect(humanizeEnum(null)).toBe('—');
    expect(humanizeEnum(undefined)).toBe('—');
    expect(humanizeEnum('')).toBe('—');
  });

  it('never returns SCREAMING_SNAKE', () => {
    // The compliance rule, stated directly: whatever comes in, nothing that
    // still looks like a raw enum comes out.
    const enums = [
      'PENDING_REVIEW', 'CHANGES_REQUESTED', 'PAYOUT_ELIGIBLE',
      'SOME_STATUS_NOBODY_MAPPED', 'A_B_C',
    ];
    for (const value of enums) {
      expect(humanizeEnum(value)).not.toMatch(/[A-Z]{2,}_/);
    }
  });
});

describe('statusTone', () => {
  it('maps settled states to green and blocked ones to red', () => {
    expect(statusTone('APPROVED')).toBe('green');
    expect(statusTone('ACTIVE')).toBe('green');
    expect(statusTone('REJECTED')).toBe('red');
    expect(statusTone('SUSPENDED')).toBe('red');
  });

  it('separates "waiting on a person" from "in flight"', () => {
    expect(statusTone('PENDING_REVIEW')).toBe('amber');
    expect(statusTone('UNDER_REVIEW')).toBe('blue');
  });

  it('reads PAYOUT_ELIGIBLE as settled and CLOSED as inert', () => {
    // These two are the difference between money an organizer can draw
    // and an account that is finished. Grey for both would make them
    // indistinguishable at a glance, which is the glance that matters.
    expect(statusTone('PAYOUT_ELIGIBLE')).toBe('green');
    expect(statusTone('CLOSED')).toBe('gray');
    expect(statusTone('HOLD')).toBe('amber');
  });

  it('falls back to neutral for a status nobody mapped', () => {
    // Neutral, not a throw: an unmapped status should render plainly rather than
    // take a screen down, and grey is the honest colour for "no opinion".
    expect(statusTone('SOME_NEW_BACKEND_STATUS')).toBe('gray');
    expect(statusTone(null)).toBe('gray');
    expect(statusTone(undefined)).toBe('gray');
  });

  it('returns only tones the Badge contract permits', () => {
    // The design system closes this set. A tone outside it is a prop the
    // component will reject at runtime, on whichever screen happens to hit it.
    const permitted = new Set(['gray', 'accent', 'green', 'amber', 'red', 'blue']);
    const statuses = [
      'APPROVED', 'PENDING', 'UNDER_REVIEW', 'REJECTED', 'DRAFT',
      'PAYOUT_ELIGIBLE', 'CLOSED', 'HOLD', 'ANYTHING_ELSE',
    ];
    for (const status of statuses) {
      expect(permitted.has(statusTone(status))).toBe(true);
    }
  });
});
