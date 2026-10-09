'use client';

import { useEffect, useMemo, useRef, useState } from 'react';
import { Banner, Card, CardHeader, StatusPill } from '@pml.tickets/shared/components/m3';
import { NotAvailable } from '@/components/console/NotAvailable';
import { formatMoney } from '@/lib/format/figure';
import { changedKeys, flattenRules, readSeen, writeSeen, type FlatRules, type PlatformRulesView } from '@/lib/settings/platformRules';

function Row({ label, value, k, changed }: { label: string; value: React.ReactNode; k?: string; changed: Set<string> }) {
  return (
    <div className="m3-kv">
      <span>
        {label} {k && changed.has(k) ? <StatusPill tone="info">New</StatusPill> : null}
      </span>
      <b>{value}</b>
    </div>
  );
}

function List({ title, k, items, changed }: { title: string; k: string; items: string[]; changed: Set<string> }) {
  return (
    <div className="m3-stack">
      <Row label={title} value="" k={k} changed={changed} />
      <ul className="m3-row m3-list" aria-label={title}>
        {items.map((i) => (
          <li key={i}>
            <StatusPill>{i}</StatusPill>
          </li>
        ))}
      </ul>
    </div>
  );
}

export interface PlatformRulesSectionProps {
  /** null = the organizer cannot read the configuration yet. */
  rules: PlatformRulesView | null;
  /** Override for tests; defaults to the browser snapshot. */
  seen?: FlatRules | null;
  now?: Date;
}

export function PlatformRulesSection({ rules, seen }: PlatformRulesSectionProps) {
  const [snapshot, setSnapshot] = useState<FlatRules | null | undefined>(seen);
  // Read the last-seen snapshot exactly once: under React strict mode (dev) effects run twice, and the
  // write below would otherwise be read back, hiding every "New" chip.
  const read = useRef(false);
  useEffect(() => {
    if (seen === undefined && !read.current) {
      read.current = true;
      setSnapshot(readSeen());
    }
  }, [seen]);
  const flat = useMemo(() => (rules ? flattenRules(rules) : null), [rules]);
  const changed = useMemo(() => (flat ? changedKeys(flat, snapshot ?? null) : new Set<string>()), [flat, snapshot]);
  // Remember what was shown once the page has rendered with the chips.
  useEffect(() => {
    if (flat && seen === undefined) writeSeen(flat);
  }, [flat, seen]);

  if (!rules) return <NotAvailable what="The platform rules" />;
  const r = rules;
  const updated = r.updatedAt ? new Date(r.updatedAt).toLocaleString('en-GB', { day: 'numeric', month: 'short', year: 'numeric', hour: '2-digit', minute: '2-digit' }) : '—';
  return (
    <div className="m3-stack" data-testid="settings-platform">
      <Banner tone="info" title="Set by the platform.">
        These rules apply to every organization and cannot be edited here. Last updated {updated} by {r.updatedBy ?? '—'}.
      </Banner>
      <Card>
        <CardHeader title="Money and escrow" />
        <div className="oc-rules-grid">
        <Row label="Commission on ticket sales" value={r.commissionRate == null ? '—' : `${r.commissionRate}%`} k="commissionRate" changed={changed} />
        <Row label="Minimum payout" value={r.minimumPayout == null ? '—' : formatMoney(r.minimumPayout, r.currency)} k="minimumPayout" changed={changed} />
        <Row label="Escrow hold after the event" value={`${r.escrowHoldDays} days`} k="escrowHoldDays" changed={changed} />
        <Row label="Currency" value={r.currency} changed={changed} />
        </div>
      </Card>
      <Card>
        <CardHeader title="Buying rules" />
        <div className="oc-rules-grid">
        <Row label="Reservation hold" value={`${r.reservationHoldMinutes} minutes`} k="reservationHoldMinutes" changed={changed} />
        <Row label="Grace period after the hold" value={`${r.reservationGraceMinutes} minutes`} k="reservationGraceMinutes" changed={changed} />
        <Row label="Tickets per booking (maximum)" value={r.maxTicketsPerBooking} k="maxTicketsPerBooking" changed={changed} />
        <Row label="Refunds close" value={`${r.refundCutoffHours} hours before the event`} k="refundCutoffHours" changed={changed} />
        <Row label="Reschedules allowed per event" value={r.rescheduleLimit} k="rescheduleLimit" changed={changed} />
        </div>
      </Card>
      <Card>
        <CardHeader title="Refund policies" subtitle="What buyers see when you pick a policy for an event." />
        <div className="oc-rules-grid">
        {r.refundPolicies.map((p) => (
          <Row key={p.code} label={p.label} value={p.summary} k={`refund.${p.code}`} changed={changed} />
        ))}
        </div>
      </Card>
      <Card>
        <CardHeader title="Event approval" />
        <div className="oc-rules-grid">
        <Row label="Typical review time" value={`${r.approvalSlaHours} hours`} k="approvalSlaHours" changed={changed} />
        <Row label="Warning after" value={`${r.approvalWarnHours} hours`} k="approvalWarnHours" changed={changed} />
        <Row label="Automatic escalation" value={r.autoEscalation ? `On, after ${r.escalationDelayHours} hours` : 'Off'} k="autoEscalation" changed={changed} />
        <Row label="Reviewer comment on changes requested" value={r.requireCommentsOnChangesRequested ? 'Always given' : 'Optional'} k="requireCommentsOnChangesRequested" changed={changed} />
        <Row label="Reviewer comment on rejection" value={r.requireCommentsOnRejection ? 'Always given' : 'Optional'} k="requireCommentsOnRejection" changed={changed} />
        </div>
      </Card>
      <Card>
        <CardHeader title="Reference lists" />
        <div className="m3-stack">
          <List title="Event categories" k="categories" items={r.categories} changed={changed} />
          <List title="Provinces" k="provinces" items={r.provinces} changed={changed} />
          <List title="Cities" k="cities" items={r.cities} changed={changed} />
          <List title="Banks for payouts" k="banks" items={r.banks} changed={changed} />
          <List title="Verification document types" k="documentTypes" items={r.documentTypes} changed={changed} />
          <List title="Event cancellation reasons" k="cancellationReasons" items={r.cancellationReasons} changed={changed} />
        </div>
      </Card>
    </div>
  );
}
