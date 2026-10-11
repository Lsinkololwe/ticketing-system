'use client';

import { enumValues, ESCROW_STATUS_LABELS, PAYOUT_STATUS_LABELS } from '@/lib/format/enumLabels';
import { useMemo, useState, type ReactNode } from 'react';
import {
  Banner,
  Button,
  Card,
  CardHeader,
  ConfirmDialog,
  DataTable,
  KpiCard,
  KpiGrid,
  PageHeader,
  Select,
  TextField,
} from '@pml.tickets/shared/components/m3';
import type { PayoutRowVM } from '@pml.tickets/shared/api/organization-admin/modules/finance';
import type { EscrowAccountRow } from '@/lib/api/finance';
import { OPEN_PAYOUT_STATUSES, currentEventName, escrowEligibilityLabel, maskAccount, sumAmounts } from '@/lib/finance/payouts';
import { formatEventDate, formatMoney } from '@/lib/format/figure';
import { Status } from '@/components/console/Status';
import { DataState } from '@/components/console/DataState';
import { usePaged } from './usePaged';

const ESCROW_STATUSES = enumValues(ESCROW_STATUS_LABELS);
const PAYOUT_STATUSES = enumValues(PAYOUT_STATUS_LABELS);

export interface PayoutsViewProps {
  accounts: EscrowAccountRow[];
  payouts: PayoutRowVM[];
  loading: boolean;
  error?: Error | null;
  onRetry?: () => void;
  /** Organization is approved, so payouts may be requested. */
  orgActive: boolean;
  /** Role may request payouts. */
  canRequest: boolean;
  commissionNote?: string;
  onCancelPayout: (payoutId: string, reason: string) => Promise<void> | void;
  /** Renders the ledger side sheet for an account. */
  renderLedger: (account: EscrowAccountRow, close: () => void) => ReactNode;
  /** Renders the payout dialog; `escrowId` preselects an escrow. */
  renderPayoutDialog: (escrowId: string | null, close: () => void) => ReactNode;
}

const money = (v: string | number | null | undefined, c?: string | null) => formatMoney(v, c, { decimals: 2 });

export function PayoutsView(p: PayoutsViewProps) {
  const [escQ, setEscQ] = useState('');
  const [escSt, setEscSt] = useState('all');
  const [poQ, setPoQ] = useState('');
  const [poSt, setPoSt] = useState('all');
  const [ledgerFor, setLedgerFor] = useState<EscrowAccountRow | null>(null);
  const [requestFor, setRequestFor] = useState<{ escrowId: string | null } | null>(null);
  const [cancelFor, setCancelFor] = useState<PayoutRowVM | null>(null);

  const held = sumAmounts(p.accounts.filter((a) => ['ACTIVE', 'HOLD'].includes(a.status)).map((a) => a.currentBalance));
  const withdrawable = sumAmounts(p.accounts.filter((a) => a.status === 'PAYOUT_ELIGIBLE').map((a) => a.currentBalance));
  const open = p.payouts.filter((x) => (OPEN_PAYOUT_STATUSES as readonly string[]).includes(x.status));
  const pending = sumAmounts(open.map((x) => x.requestedAmount));
  const paid = sumAmounts(p.payouts.filter((x) => x.status === 'COMPLETED').map((x) => x.settledAmount));

  const escList = useMemo(
    () =>
      p.accounts.filter(
        (a) => (escSt === 'all' || a.status === escSt) && (!escQ || `${a.eventTitle ?? ''} ${a.event?.title ?? ''}`.toLowerCase().includes(escQ.toLowerCase()))
      ),
    [p.accounts, escSt, escQ]
  );
  const poList = useMemo(
    () =>
      p.payouts.filter(
        (x) => (poSt === 'all' || x.status === poSt) && (!poQ || `${x.requestId} ${x.eventTitle ?? ''} ${x.event?.title ?? ''}`.toLowerCase().includes(poQ.toLowerCase()))
      ),
    [p.payouts, poSt, poQ]
  );
  const escPaged = usePaged(escList, 5);
  const poPaged = usePaged(poList, 5);
  const canAct = p.canRequest && p.orgActive;

  return (
    <div data-testid="payouts-page">
      <PageHeader
        title="Escrow & payouts"
        subtitle="One escrow account per event. Funds are released after the event."
        actions={canAct ? <Button variant="filled" onClick={() => setRequestFor({ escrowId: null })}>Request payout</Button> : null}
      />
      <DataState loading={p.loading && p.accounts.length === 0 && p.payouts.length === 0} error={p.error} onRetry={p.onRetry}>
        <KpiGrid>
          <KpiCard label="Held in escrow" icon="wallet" value={money(held)} caption="Live and recently completed events" />
          <KpiCard label="Withdrawable now" icon="money" value={money(withdrawable)} caption="Eligible escrow accounts" />
          <KpiCard label="Pending payouts" icon="clock" value={money(pending)} caption={`${open.length} open request${open.length === 1 ? '' : 's'}`} />
          <KpiCard label="Paid out to date" icon="bank" value={money(paid)} caption="Completed payouts" />
        </KpiGrid>
        {!p.canRequest ? (
          <Banner tone="info">You can view finances but not request payouts.</Banner>
        ) : !p.orgActive ? (
          <Banner tone="warning">Payout requests unlock once your organization is approved.</Banner>
        ) : null}

        <div className="oc-section">
          <Card>
            <CardHeader
              title="Escrow accounts"
              subtitle={`Money from ticket sales stays here until the hold after the event has elapsed. There is no early release.${p.commissionNote ? ` ${p.commissionNote}` : ''}`}
            />
            <div className="m3-toolbar">
              <TextField density="compact" label="Search escrow accounts" placeholder="Event title" value={escQ} onChange={(e) => { setEscQ(e.target.value); escPaged.reset(); }} />
              <Select density="compact" label="Escrow status" value={escSt} onChange={(e) => { setEscSt(e.target.value); escPaged.reset(); }}>
                <option value="all">All statuses</option>
                {ESCROW_STATUSES.map((s) => <option key={s} value={s}>{ESCROW_STATUS_LABELS[s]}</option>)}
              </Select>
            </div>
            <DataTable
              caption="Escrow accounts"
              rows={escPaged.rows}
              getRowId={(a) => a.id}
              empty={<span>No escrow accounts match.</span>}
              pagination={escPaged.pagination}
              actionsHeader="Actions"
              columns={[
                { id: 'event', header: 'Event', rowHeader: true, cell: (a) => {
                    const now = currentEventName(a.eventTitle, a.event);
                    return (
                      <>
                        <b>{a.eventTitle ?? a.accountNumber}</b>
                        {now ? <div className="m3-caption">Now: {now}</div> : null}
                      </>
                    );
                  },
                },
                { id: 'status', header: 'Status', cell: (a) => <Status status={a.status} /> },
                { id: 'balance', header: 'Balance', align: 'end', cell: (a) => <span className="m3-mono">{money(a.currentBalance, a.currency)}</span> },
                { id: 'lock', header: 'Held until', cell: (a) => (a.lockUntil ? formatEventDate(a.lockUntil) : '—') },
                { id: 'elig', header: 'Payout eligible', cell: (a) => (a.payoutEligibleAt ? formatEventDate(a.payoutEligibleAt) : 'After the event and hold') },
                {
                  id: 'e2',
                  header: 'Eligibility',
                  cell: (a) => {
                    const l = escrowEligibilityLabel(a.status);
                    return <Status status={l.eligible ? 'APPROVED' : 'PENDING'} label={l.text} />;
                  },
                },
              ]}
              rowActions={(a) => (
                <div className="m3-row">
                  <Button size="sm" variant="tonal" onClick={() => setLedgerFor(a)}>Ledger</Button>
                  {canAct ? (
                    <Button size="sm" variant={a.status === 'PAYOUT_ELIGIBLE' ? 'filled' : 'tonal'} onClick={() => setRequestFor({ escrowId: a.id })}>
                      Request payout
                    </Button>
                  ) : null}
                </div>
              )}
            />
          </Card>
        </div>

        <div className="oc-section">
          <Card>
            <CardHeader title="Payout requests" subtitle="Platform finance approves payouts. One open request per escrow, no payout fee." />
            <div className="m3-toolbar">
              <TextField density="compact" label="Search payouts" placeholder="Request or event" value={poQ} onChange={(e) => { setPoQ(e.target.value); poPaged.reset(); }} />
              <Select density="compact" label="Status" value={poSt} onChange={(e) => { setPoSt(e.target.value); poPaged.reset(); }}>
                <option value="all">All statuses</option>
                {PAYOUT_STATUSES.map((s) => <option key={s} value={s}>{PAYOUT_STATUS_LABELS[s]}</option>)}
              </Select>
            </div>
            <DataTable
              caption="Payout requests"
              rows={poPaged.rows}
              getRowId={(x) => x.id}
              empty={<span>No payout requests yet.</span>}
              pagination={poPaged.pagination}
              columns={[
                { id: 'req', header: 'Request', rowHeader: true, cell: (x) => <span className="m3-mono">{x.requestId}</span> },
                {
                  id: 'event',
                  header: 'Event',
                  cell: (x) => {
                    const now = currentEventName(x.eventTitle, x.event);
                    return (
                      <>
                        {x.eventTitle ?? '—'}
                        {now ? <div className="m3-caption">Now: {now}</div> : null}
                      </>
                    );
                  },
                },
                { id: 'dest', header: 'Destination', cell: (x) => `${x.bankName ?? x.bankAccountName ?? '—'} ${maskAccount(x.accountNumber)}` },
                { id: 'amount', header: 'Amount', align: 'end', cell: (x) => <span className="m3-mono">{money(x.requestedAmount, x.currency)}</span> },
                {
                  id: 'status',
                  header: 'Status',
                  cell: (x) => (
                    <>
                      <Status status={x.status} />
                      {x.rejectionReason ? <div className="m3-muted">{x.rejectionReason}</div> : null}
                    </>
                  ),
                },
                { id: 'at', header: 'Requested', cell: (x) => formatEventDate(x.requestedAt) },
              ]}
              rowActions={(x) =>
                x.status === 'PENDING' && p.canRequest ? (
                  <Button size="sm" variant="text" onClick={() => setCancelFor(x)}>Cancel</Button>
                ) : null
              }
            />
          </Card>
        </div>
      </DataState>

      {ledgerFor ? p.renderLedger(ledgerFor, () => setLedgerFor(null)) : null}
      {requestFor ? p.renderPayoutDialog(requestFor.escrowId, () => setRequestFor(null)) : null}
      {cancelFor ? (
        <ConfirmDialog
          open
          title={`Cancel payout ${cancelFor.requestId}?`}
          confirmLabel="Cancel payout"
          cancelLabel="Keep request"
          danger
          description="The request returns to your escrow balance and you can raise a new one."
          onClose={() => setCancelFor(null)}
          onConfirm={async () => {
            const id = cancelFor.id;
            setCancelFor(null);
            await p.onCancelPayout(id, 'Cancelled by organizer');
          }}
        />
      ) : null}
    </div>
  );
}
