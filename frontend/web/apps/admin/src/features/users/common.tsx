'use client';

import { useEffect, useState, type ReactNode } from 'react';
import { Button, StatusPill } from '@pml.tickets/shared/components/m3';
import { humanize, maskEmail, maskPhone } from '@/lib/format';

export const ERR_TEXT = 'Something went wrong. Try again.';

/** Debounce a fast-changing value (search boxes) so the server is queried once typing pauses. */
export function useDebounced<T>(value: T, ms = 300): T {
  const [v, setV] = useState(value);
  useEffect(() => {
    const t = setTimeout(() => setV(value), ms);
    return () => clearTimeout(t);
  }, [value, ms]);
  return v;
}

export function errorMessage(e: unknown): string {
  const m = (e as { message?: string } | null)?.message;
  return m && m.trim() ? m : ERR_TEXT;
}

/** Two-column responsive grid of cards (collapses on narrow screens). */
export function CardGrid({ children }: { children: ReactNode }) {
  return (
    <div className="m3-grid" style={{ gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 24rem), 1fr))' }}>
      {children}
    </div>
  );
}

export function PersonCell({ name, sub }: { name: string; sub?: string | null }) {
  return (
    <div className="m3-stack" style={{ gap: 'var(--m3-sp-0)' }}>
      <strong>{name}</strong>
      {sub ? <span className="m3-muted">{sub}</span> : null}
    </div>
  );
}

type Kind = 'email' | 'phone';

/**
 * Contact value, masked by default. Reveal is an explicit, per-value control
 * (the backend returns the full value to staff; nothing is stored on reveal).
 */
export function MaskedValue({ kind, value, label }: { kind: Kind; value?: string | null; label: string }) {
  const [shown, setShown] = useState(false);
  if (!value) return <span>—</span>;
  const masked = kind === 'email' ? maskEmail(value) : maskPhone(value);
  return (
    <span className="m3-row">
      <span className={kind === 'phone' ? 'm3-mono' : undefined}>{shown ? value : masked}</span>
      <Button variant="text" size="sm" aria-pressed={shown} aria-label={`${shown ? 'Hide' : 'Reveal'} ${label}`} onClick={() => setShown((s) => !s)}>
        {shown ? 'Hide' : 'Reveal'}
      </Button>
    </span>
  );
}

export function VerifiedPill({ verified }: { verified: boolean }) {
  return verified ? <StatusPill status="VERIFIED" /> : <StatusPill tone="warning">Unverified</StatusPill>;
}

export function RolePills({ roles }: { roles: string[] }) {
  const shown = roles.filter((r) => r !== 'CUSTOMER' || roles.length === 1);
  return (
    <span className="m3-row" style={{ gap: 'var(--m3-sp-4)' }}>
      {shown.map((r) => (
        <StatusPill key={r} tone="neutral">
          {humanize(r)}
        </StatusPill>
      ))}
    </span>
  );
}

export function optionsOf(values: readonly string[]) {
  return values.map((v) => ({ value: v, label: humanize(v) }));
}

/** Commission text. `rate` is the backend fraction (0.05 = 5%); null means the platform default applies. */
export function rateText(rate: number | null | undefined): string {
  if (rate == null) return 'Platform default';
  const pct = Math.round(rate * 10000) / 100;
  return `${pct}%`;
}

/** The organization's own rate (a percentage, 5 = 5%) when set, else the payout configuration rate (a fraction), else the platform default. */
export function orgRateText(org: { commissionRate?: number | null; payoutConfig?: { commissionRate?: number | null } | null }): string {
  if (org.commissionRate != null) return `${org.commissionRate}%`;
  return rateText(org.payoutConfig?.commissionRate);
}

export function copyText(text: string) {
  if (typeof navigator !== 'undefined' && navigator.clipboard) void navigator.clipboard.writeText(text);
}
