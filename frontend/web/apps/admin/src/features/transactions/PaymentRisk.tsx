'use client';

import { ErrorState, KpiCard, Card, KpiGrid, StatusPill } from '@pml.tickets/shared/components/m3';
import { usePaymentRiskSummary } from '@pml.tickets/shared/api/admin/modules/payments-ops';
import type { TxPaymentAttempt } from '@pml.tickets/shared/api/admin/modules/transactions';
import { formatNumber, humanize, money } from '@/lib/format';

/** Risk summary tiles for the last 24 hours (paymentRiskSummary). */
export function PaymentRiskSummary({ windowHours = 24 }: { windowHours?: number }) {
  const { summary, loading, error, refetch } = usePaymentRiskSummary(windowHours);
  if (error && !summary) return <ErrorState error={error} onRetry={refetch} />;
  const v = (n: number | undefined) => (summary ? formatNumber(n ?? 0) : loading ? '…' : '—');
  return (
    <section aria-label="Payment risk">
      <Card>
      <KpiGrid flat>
        <KpiCard label={`Evaluated, last ${windowHours} hours`} value={v(summary?.evaluated)} />
        <KpiCard label="Flagged" value={v(summary?.flagged)} caption={summary ? `${formatNumber(summary.high)} high · ${formatNumber(summary.medium)} medium · ${formatNumber(summary.low)} low` : undefined} />
        <KpiCard label="Amount at risk" value={summary ? <span className="m3-mono">{money(Number(summary.amountAtRisk))}</span> : loading ? '…' : '—'} />
        <KpiCard
          label="Top signals"
          value={summary ? (summary.topFlags.length ? summary.topFlags.slice(0, 3).map((f) => `${humanize(f.flag)} (${f.count})`).join(', ') : 'None') : loading ? '…' : '—'}
        />
      </KpiGrid>
      </Card>
    </section>
  );
}

/** Risk score and flags of one attempt, for the detail sheet. */
export function RiskSignals({ attempt }: { attempt: Pick<TxPaymentAttempt, 'riskScore' | 'riskLevel' | 'riskFlags'> }) {
  const flags = attempt.riskFlags ?? [];
  if (attempt.riskScore == null && !attempt.riskLevel && flags.length === 0) return <p className="m3-muted">This attempt has not been scored.</p>;
  return (
    <div className="m3-stack">
      <div className="m3-row">
        {attempt.riskLevel ? <StatusPill status={attempt.riskLevel.toUpperCase()} /> : null}
        {attempt.riskScore != null ? <span>Score <b className="m3-mono">{attempt.riskScore}</b> of 100</span> : null}
      </div>
      {flags.length ? (
        <ul>{flags.map((f) => <li key={f}>{humanize(f)}</li>)}</ul>
      ) : (
        <p className="m3-muted">No signals raised.</p>
      )}
    </div>
  );
}
