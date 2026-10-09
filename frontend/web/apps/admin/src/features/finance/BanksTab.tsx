'use client';

import { RowActions } from '@/components/console/RowActions';
import { useState } from 'react';
import { StatusPill, useSnackbar, type DataColumn } from '@pml.tickets/shared/components/m3';
import { usePayoutAccountActions, usePayoutAccounts, type PayoutAccountRow } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { useOrgAdminActions } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { ReasonDialog, useStaff } from '@/components/console';
import { ConfirmDialog } from '@pml.tickets/shared/components/m3';
import { ListCard } from '@/features/ledger/ListCard';
import { formatDateTime, humanize } from '@/lib/format';
import { useStepUp } from '@/lib/useStepUp';
import { PAYOUT_ACCOUNT_STATUS_LABELS, enumOptions } from '@/lib/enumLabels';

type Action = { kind: 'reject' | 'suspend' | 'verify' | 'reinstate'; account: PayoutAccountRow };


const COLUMNS: Array<DataColumn<PayoutAccountRow>> = [
  { id: 'org', header: 'Organization', rowHeader: true, cell: (a) => <><b>{a.organizationName}</b><br /><span className="m3-muted">{a.accountHolderName ?? ''}</span></> },
  { id: 'bank', header: 'Bank or network', cell: (a) => (a.method === 'MOBILE_MONEY' ? humanize(a.network ?? '') || '—' : a.bankName ?? '—') },
  { id: 'acct', header: 'Account', cell: (a) => <span className="m3-mono">{a.method === 'MOBILE_MONEY' ? a.phoneMasked : a.accountNumberMasked}</span> },
  { id: 'type', header: 'Type', cell: (a) => humanize(a.method) },
  { id: 'at', header: 'Submitted', cell: (a) => formatDateTime(a.updatedAt) },
  {
    id: 'status',
    header: 'Status',
    cell: (a) => (
      <>
        <StatusPill status={a.status} />
        {a.status === 'REJECTED' && a.rejectionReason ? <><br /><span className="m3-muted">{a.rejectionReason}</span></> : null}
        {a.status === 'SUSPENDED' && a.suspendedReason ? <><br /><span className="m3-muted">{a.suspendedReason}</span></> : null}
      </>
    ),
  },
];

/** Payout account verification queue (bankAccounts). Verify, reject, suspend and reinstate; sensitive, so each needs a recent sign-in. */
export function BanksTab() {
  const { can } = useStaff();
  const { guard } = useStepUp();
  const snackbar = useSnackbar();
  const [status, setStatus] = useState('');
  const [search, setSearch] = useState('');
  const [page, setPage] = useState(0);
  const q = usePayoutAccounts({ status: (status || null) as PayoutAccountRow['status'] | null, search, page, size: 20 });
  const acts = usePayoutAccountActions();
  const orgActs = useOrgAdminActions();
  const [action, setAction] = useState<Action | null>(null);
  const allowed = can('payoutDecide');

  const done = (msg: string) => { snackbar.show(msg); setAction(null); };
  const fail = (e: unknown) => snackbar.show((e as Error).message || 'That did not go through');
  const withReason = async (reason: string) => {
    if (!action) return;
    const a = action.account;
    try {
      if (action.kind === 'suspend') await guard(() => acts.suspendBankAccount(a.organizationId, reason));
      else if (a.method === 'MOBILE_MONEY') await guard(() => acts.rejectPayoutAccount(a.organizationId, reason));
      else await guard(() => acts.rejectBankAccount(a.organizationId, reason));
      done(action.kind === 'suspend' ? `${a.organizationName} payout account suspended` : `${a.organizationName} payout account rejected`);
    } catch (e) { fail(e); }
  };
  const confirm = async () => {
    if (!action) return;
    const a = action.account;
    try {
      if (action.kind === 'verify') await guard(() => orgActs.verifyPayoutAccount(a.organizationId, true));
      else await guard(() => acts.reinstateBankAccount(a.organizationId));
      done(action.kind === 'verify' ? `${a.organizationName} payout account verified` : `${a.organizationName} payout account reinstated`);
    } catch (e) { fail(e); }
  };

  return (
    <>
      <ListCard<PayoutAccountRow>
        title="Payout account verification"
        subtitle="A payout is only approved to a verified account. Organizers confirm a micro-deposit; admins can verify, reject or suspend."
        caption="Payout accounts"
        rows={q.accounts}
        columns={COLUMNS}
        getRowId={(a) => a.organizationId}
        searchLabel="Search accounts"
        onSearchQuery={(v) => { setSearch(v); setPage(0); }}
        searchText={(a) => `${a.organizationName} ${a.bankName ?? ''}`}
        filters={[{ id: 'status', label: 'Status', options: enumOptions(PAYOUT_ACCOUNT_STATUS_LABELS) }]}
        onFilterChange={(v) => { setStatus(v.status && v.status !== 'all' ? v.status : ''); setPage(0); }}
        serverPage={{ page: page + 1, total: q.pageInfo.totalCount, onPage: (p) => setPage(p - 1) }}
        pageSize={20}
        loading={q.loading}
        error={q.error}
        onRetry={q.refetch}
        empty={{ title: 'No payout accounts to verify.' }}
        rowActions={(a) => (
          <RowActions
            name={`payout account ${a.organizationName}`}
            primary={['PENDING', 'REJECTED'].includes(a.status) ? { label: 'Verify', disabled: !allowed, onSelect: () => setAction({ kind: 'verify', account: a }) } : a.status === 'SUSPENDED' ? { label: 'Reinstate', disabled: !allowed, onSelect: () => setAction({ kind: 'reinstate', account: a }) } : undefined}
            items={[
              ...(['PENDING', 'VERIFIED'].includes(a.status) ? [{ id: 'reject', label: 'Reject…', danger: true, disabled: !allowed, onSelect: () => setAction({ kind: 'reject', account: a }) }] : []),
              ...(a.status === 'VERIFIED' ? [{ id: 'suspend', label: 'Suspend…', danger: true, disabled: !allowed, onSelect: () => setAction({ kind: 'suspend', account: a }) }] : []),
            ]}
          />
        )}
      />
      <ReasonDialog
        open={action?.kind === 'reject' || action?.kind === 'suspend'}
        onClose={() => setAction(null)}
        onConfirm={(r) => void withReason(r)}
        title={action ? `${action.kind === 'reject' ? 'Reject' : 'Suspend'} the payout account of ${action.account.organizationName}?` : ''}
        body={action?.kind === 'suspend' ? 'No payout is approved to this account until it is reinstated.' : 'The organizer is told why and must submit new details.'}
        confirmLabel={action?.kind === 'suspend' ? 'Suspend account' : 'Reject account'}
        danger
        loading={acts.busy}
      />
      <ConfirmDialog
        open={action?.kind === 'verify' || action?.kind === 'reinstate'}
        onClose={() => setAction(null)}
        onConfirm={() => void confirm()}
        title={action ? `${action.kind === 'verify' ? 'Verify' : 'Reinstate'} the payout account of ${action.account.organizationName}?` : ''}
        description={action?.kind === 'verify' ? 'Payouts can then be approved to this account.' : 'Payouts to this account can be approved again.'}
        confirmLabel={action?.kind === 'verify' ? 'Verify account' : 'Reinstate account'}
        loading={acts.busy}
      />
    </>
  );
}
