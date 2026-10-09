'use client';

import { RowActions } from '@/components/console/RowActions';
import { useState } from 'react';
import {
  Button,
  KpiCard,
  KpiGrid,
  Tabs,
  StatusPill,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import {
  IN_REVIEW_STATUSES,
  attemptAgeMinutes,
  usePaymentAttemptActions,
  type TxPaymentAttempt,
} from '@pml.tickets/shared/api/admin/modules/transactions';
import {
  useDualControlQueue,
  usePaymentAttemptSearch,
  usePaymentRecoveryActions,
  useStuckTransactions,
} from '@pml.tickets/shared/api/admin/modules/payments-ops';
import { useRecoveryQueue } from '@pml.tickets/shared/api/admin/modules/finance';
import type { RecoveryPayoutRow } from '@pml.tickets/shared/api/admin/modules/finance/recovery.hooks';
import { useStaff } from '@/components/console';
import { ReasonDialog } from '@/components/console/ReasonDialog';
import { useStepUp } from '@/lib/useStepUp';
import { DualControlList } from './DualControlList';
import { ConfirmDialog, Card, CardHeader } from '@pml.tickets/shared/components/m3';
import { ListCard } from '@/features/ledger/ListCard';
import { formatDateTime, humanize, money } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { PaymentDialog, PaymentSheet, ageText, reviewOf, useRun, type PaymentDialogKind } from './PaymentParts';

type View = 'stuck' | 'queue' | 'payouts' | 'approval';

const attemptCols = (extra: Array<DataColumn<TxPaymentAttempt>>): Array<DataColumn<TxPaymentAttempt>> => [
  { id: 'attempt', header: 'Attempt', cell: (a) => <><span className="m3-mono">{a.attemptNumber}</span><br /><span className="m3-mono m3-muted">{a.ticketId}</span></> },
  { id: 'buyer', header: 'Buyer', cell: (a) => <><span className="m3-mono">{a.buyerId}</span><br /><span className="m3-muted">{humanize(a.provider)}</span></> },
  { id: 'amount', header: 'Amount', align: 'end', cell: (a) => <span className="m3-mono">{money(Number(a.amount))}</span> },
  ...extra,
  { id: 'status', header: 'Status', cell: (a) => <StatusPill status={a.status} /> },
  { id: 'issue', header: 'Issue', cell: (a) => (a.failureCode ? humanize(a.failureCode) : '—') },
];

function PayoutsView() {
  const { items, loading, error, refetch } = useRecoveryQueue('stuck');
  const cols: Array<DataColumn<RecoveryPayoutRow>> = [
    { id: 'req', header: 'Request', cell: (p) => <span className="m3-mono">{p.requestId}</span> },
    { id: 'org', header: 'Organizer', cell: (p) => <>{p.organizerName}<br /><span className="m3-muted">{p.eventTitle}</span></> },
    { id: 'amt', header: 'Amount', align: 'end', cell: (p) => <span className="m3-mono">{money(Number(p.requestedAmount))}</span> },
    { id: 'status', header: 'Status', cell: (p) => <StatusPill status={String(p.status)} /> },
    { id: 'req_at', header: 'Requested', cell: (p) => formatDateTime(p.requestedAt) },
  ];
  return (
    <ListCard
      title="Stuck payouts"
      subtitle="Payout requests that stopped moving. Resume and resolve them from Finance."
      caption="Stuck payouts"
      rows={items}
      columns={cols}
      getRowId={(p) => p.id}
      searchLabel="Search stuck payouts"
      searchText={(p) => `${p.requestId} ${p.organizerName} ${p.eventTitle}`}
      loading={loading}
      error={error}
      onRetry={refetch}
      empty={{ title: 'No stuck payouts. Everything is moving.' }}
    />
  );
}

function useReviewQueue() {
  const a = usePaymentAttemptSearch({ filter: { reviewStatus: 'PENDING_REVIEW' }, size: 50 });
  const b = usePaymentAttemptSearch({ filter: { reviewStatus: 'UNDER_REVIEW' }, size: 50 });
  const c = usePaymentAttemptSearch({ filter: { reviewStatus: 'ESCALATED' }, size: 50 });
  const items = [...a.items, ...b.items, ...c.items].sort((x, y) => (y.createdAt ?? '').localeCompare(x.createdAt ?? '')) as unknown as TxPaymentAttempt[];
  return {
    items,
    loading: a.loading || b.loading || c.loading,
    error: a.error ?? b.error ?? c.error,
    refetch: () => { a.refetch(); b.refetch(); c.refetch(); },
  };
}

type Pending = { kind: 'resume'; attempt: TxPaymentAttempt } | { kind: 'retry'; ids: string[]; clear: () => void } | { kind: 'force'; ids: string[]; clear: () => void };

export function RecoveryTab() {
  const { can } = useStaff();
  const { guard } = useStepUp();
  const stuckQ = useStuckTransactions({ minutes: 30, size: 50 });
  const review = useReviewQueue();
  const dual = useDualControlQueue();
  const recovery = usePaymentRecoveryActions();
  const { setReviewStatus } = usePaymentAttemptActions();
  const run = useRun();
  const [view, setView] = useState<View>('stuck');
  const [openId, setOpenId] = useState<string | null>(null);
  const [dialog, setDialog] = useState<PaymentDialogKind | null>(null);
  const [resolveFor, setResolveFor] = useState<TxPaymentAttempt | null>(null);
  const [pending, setPending] = useState<Pending | null>(null);

  const stuck = stuckQ.items as unknown as TxPaymentAttempt[];
  const queue = review.items.filter((a) => (IN_REVIEW_STATUSES as readonly string[]).includes(reviewOf(a)));
  const open = resolveFor ?? [...stuck, ...queue].find((a) => a.id === openId) ?? null;
  const oldest = stuck.length ? Math.max(...stuck.map((a) => attemptAgeMinutes(a))) : null;
  const waiting = dual.proposals.filter((p) => p.status === 'PENDING').length;
  const idsOf = (rowIds: string[]) => stuck.filter((a) => rowIds.includes(a.id)).map((a) => a.depositId);

  const closeAll = () => { setOpenId(null); setDialog(null); setResolveFor(null); };
  const confirm = async () => {
    if (!pending) return;
    const p = pending;
    if (p.kind === 'resume') {
      await run(() => guard(() => recovery.resume(p.attempt.depositId)), `${p.attempt.attemptNumber} resumed`);
    } else if (p.kind === 'retry') {
      const ok = await run(async () => {
        const out = await guard(() => recovery.retry(p.ids));
        const failed = out.filter((o) => o.result !== 'RETRIED');
        if (failed.length) throw new Error(`${out.length - failed.length} of ${out.length} retried. ${failed[0].detail ?? humanize(failed[0].result)}`);
      }, `${p.ids.length} transaction${p.ids.length === 1 ? '' : 's'} retried`);
      if (ok) p.clear();
    }
    setPending(null);
  };
  const force = async (reason: string) => {
    if (pending?.kind !== 'force') return;
    const p = pending;
    const ok = await run(() => guard(() => recovery.forceComplete(p.ids, reason)), 'Force-complete proposed. A different super admin or finance lead must confirm it.');
    if (ok) { p.clear(); setView('approval'); }
    setPending(null);
  };

  return (
    <div className="m3-stack">
      <Card>
      <KpiGrid flat>
        <KpiCard label="Stuck over 30 minutes" value={stuckQ.pageInfo.totalCount} />
        <KpiCard label="Oldest stuck" value={oldest === null ? '—' : ageText(oldest)} />
        <KpiCard label="In review queue" value={queue.length} />
        <KpiCard label="Value stuck" value={<span className="m3-mono">{money(stuck.reduce((s, a) => s + Number(a.amount), 0))}</span>} />
      </KpiGrid>
      </Card>
      <div className="adm-tabsrow">
        <Tabs
          label="Recovery views"
          variant="seg"
          value={view}
          onChange={(v) => setView(v as View)}
          tabs={[
            { id: 'stuck', label: 'Stuck list' },
            { id: 'queue', label: 'Review queue' },
            { id: 'payouts', label: 'Stuck payouts' },
            { id: 'approval', label: waiting ? `Second approval (${waiting})` : 'Second approval' },
          ]}
        />
      </div>
      {view === 'payouts' ? (
        <PayoutsView />
      ) : view === 'approval' ? (
        <Card>
          <CardHeader title="Second approval" subtitle="Recovery actions proposed by someone else. The person who proposed an action cannot confirm it." />
          <DualControlList proposals={dual.proposals} loading={dual.loading} error={dual.error} onRetry={dual.refetch} />
        </Card>
      ) : view === 'stuck' ? (
        <ListCard
          title="Stuck transactions"
          subtitle={`Created, awaiting approval or processing for more than 30 minutes.${can('forceComplete') ? '' : ` ${needText('forceComplete')}.`}`}
          caption="Stuck transactions"
          rows={stuck}
          columns={attemptCols([{ id: 'age', header: 'Stuck for', cell: (a) => <StatusPill tone={attemptAgeMinutes(a) > 240 ? 'error' : 'warning'}>{ageText(attemptAgeMinutes(a))}</StatusPill> }])}
          getRowId={(a) => a.id}
          searchLabel="Search stuck transactions"
          searchText={(a) => `${a.attemptNumber} ${a.ticketId} ${a.buyerId}`}
          selectable
          bulk={(rowIds, clear) => (
            <>
              <Button variant="tonal" size="sm" onClick={() => setPending({ kind: 'retry', ids: idsOf(rowIds), clear })}>Retry selected</Button>
              {can('forceComplete') ? <Button variant="filled" size="sm" danger onClick={() => setPending({ kind: 'force', ids: idsOf(rowIds), clear })}>Force-complete…</Button> : null}
            </>
          )}
          loading={stuckQ.loading}
          error={stuckQ.error}
          onRetry={stuckQ.refetch}
          empty={{ title: 'No stuck transactions. Everything is moving.' }}
          rowActions={(a) => (
            <RowActions
              name={`attempt ${a.attemptNumber}`}
              primary={{ label: 'Resume', onSelect: () => setPending({ kind: 'resume', attempt: a }) }}
              items={[
                { id: 'resolve', label: 'Resolve…', onSelect: () => { setResolveFor(a); setDialog('resolve'); } },
                { id: 'review', label: 'Mark for review', onSelect: () => void run(() => setReviewStatus(a.depositId, 'PENDING_REVIEW'), `${a.attemptNumber} marked for review`) },
              ]}
            />
          )}
        />
      ) : (
        <ListCard
          title="Review queue"
          subtitle="Attempts flagged for a person to look at."
          caption="Review queue"
          rows={queue}
          columns={attemptCols([{ id: 'review', header: 'Review', cell: (a) => <StatusPill status={reviewOf(a)} /> }])}
          getRowId={(a) => a.id}
          searchLabel="Search review queue"
          searchText={(a) => `${a.attemptNumber} ${a.ticketId} ${a.buyerId}`}
          loading={review.loading}
          error={review.error}
          onRetry={review.refetch}
          empty={{ title: 'The review queue is empty.' }}
          rowActions={(a) => (
            <RowActions
              name={`attempt ${a.attemptNumber}`}
              primary={{ label: 'Open', onSelect: () => setOpenId(a.id) }}
              items={[
                { id: 'resolve', label: 'Resolve…', onSelect: () => { setResolveFor(a); setDialog('resolve'); } },
                ...(reviewOf(a) !== 'ESCALATED' ? [{ id: 'escalate', label: 'Escalate', onSelect: () => void run(() => setReviewStatus(a.depositId, 'ESCALATED'), `${a.attemptNumber} escalated`) }] : []),
              ]}
            />
          )}
        />
      )}
      <PaymentSheet attempt={resolveFor ? null : open} onClose={closeAll} onDialog={setDialog} />
      <PaymentDialog kind={dialog} attempt={open} onClose={() => { setDialog(null); setResolveFor(null); }} />
      <ConfirmDialog
        open={pending?.kind === 'resume' || pending?.kind === 'retry'}
        onClose={() => setPending(null)}
        onConfirm={() => void confirm()}
        title={pending?.kind === 'resume' ? `Resume ${pending.attempt.attemptNumber}?` : `Retry ${pending?.kind === 'retry' ? pending.ids.length : 0} transaction${pending?.kind === 'retry' && pending.ids.length === 1 ? '' : 's'}?`}
        description={pending?.kind === 'resume' ? 'Re-checks the payment with PawaPay and continues the booking if it was paid.' : 'Each one is re-checked with PawaPay.'}
        confirmLabel={pending?.kind === 'resume' ? 'Resume' : 'Retry all'}
        loading={recovery.busy}
      />
      <ReasonDialog
        open={pending?.kind === 'force'}
        onClose={() => setPending(null)}
        onConfirm={(r) => void force(r)}
        title={`Force-complete ${pending?.kind === 'force' ? pending.ids.length : 0} transaction${pending?.kind === 'force' && pending.ids.length === 1 ? '' : 's'}?`}
        body="Marks them completed without checking PawaPay. Use only when the money is confirmed received. A second approver must confirm before anything changes."
        confirmLabel="Propose force-complete"
        danger
        minLength={10}
        loading={recovery.busy}
      />
    </div>
  );
}
