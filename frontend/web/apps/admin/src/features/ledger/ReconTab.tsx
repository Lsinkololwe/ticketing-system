'use client';

import { useState } from 'react';
import { z } from 'zod';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import {
  Banner,
  Button,
  ConfirmDialog,
  DataTable,
  Dialog,
  FormCell,
  FormGrid,
  KeyValue,
  Select,
  SideSheet,
  StatusPill,
  TextArea,
  TextField,
  useSnackbar,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import {
  toNumber,
  useReconciliationActions,
  useReconciliationRuns,
  type ReconciliationItemRow,
  type ReconciliationRunRow,
  type ReconciliationStatus,
  type ReconciliationType,
} from '@pml.tickets/shared/api/admin/modules/ledger';
import { ReasonDialog, useStaff } from '@/components/console';
import { formatDate, formatDateTime, humanize, money } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { FormDialog } from './FormDialog';
import { SettlementsCard } from './SettlementsCard';
import { ListCard } from './ListCard';
import { RECON_TYPE_LABELS, RECON_STATUS_LABELS, enumOptions, enumSchema, enumValues } from '@/lib/enumLabels';

export const RECON_TYPES: ReconciliationType[] = enumValues(RECON_TYPE_LABELS);
const yesterday = () => new Date(Date.now() - 864e5).toISOString().slice(0, 10);

export const isOpenItem = (i: ReconciliationItemRow) => i.status !== 'MATCHED' && !i.resolvedAt && !i.resolution;
const itemKey = (i: ReconciliationItemRow) => i.externalId ?? i.internalId ?? '';
const variance = (i: ReconciliationItemRow) => toNumber(i.externalAmount) - toNumber(i.internalAmount);

const startSchema = z.object({
  type: enumSchema(RECON_TYPE_LABELS),
  period: z.string().min(1, 'Choose a date'),
});

function StartRunBody({ onClose }: { onClose: () => void }) {
  const { startRun } = useReconciliationActions();
  const snackbar = useSnackbar();
  const form = useZodForm(startSchema, { defaultValues: { type: 'GATEWAY', period: yesterday() } });
  const { register, formState: { errors } } = form;
  return (
    <FormDialog title="Start reconciliation run" form={form} onClose={onClose} submitLabel="Start run"
      onSubmit={async (v) => {
        const run = await startRun({ reconciliationDate: new Date(`${v.period}T00:00:00Z`).toISOString(), type: v.type });
        snackbar.show(run?.status === 'COMPLETED' ? 'Run finished: everything matched' : `Run finished: ${run?.unmatchedCount ?? 0} differences to review`);
        onClose();
      }}>
      <p className="m3-muted">Matches records for the chosen period and lists any differences.</p>
      <FormGrid>
        <FormCell span={6}>
          <Select label="Type" density="form" {...register('type')}>
            {RECON_TYPES.map((t) => <option key={t} value={t}>{humanize(t)}</option>)}
          </Select>
        </FormCell>
        <FormCell span={6}>
          <TextField label="Period end" type="date" density="form" {...register('period')} errorText={errors.period?.message} />
        </FormCell>
      </FormGrid>
    </FormDialog>
  );
}

const settlementSchema = z
  .object({
    date: z.string().min(1, 'Choose a date'),
    gross: z.string().refine((v) => parseFloat(v) >= 1, 'Enter the gross amount'),
    fees: z.string().refine((v) => v === '' || parseFloat(v) >= 0, 'Enter the fees'),
    ref: z.string().regex(/^PWP-SET-\d{6,10}$/, 'Use the format PWP-SET-20261002'),
    bank: z.string().trim().min(1, 'Enter the bank reference'),
  })
  .refine((v) => parseFloat(v.fees || '0') <= parseFloat(v.gross || '0'), { path: ['fees'], message: 'Fees cannot exceed gross' });

function SettlementBody({ onClose }: { onClose: () => void }) {
  const { recordSettlement } = useReconciliationActions();
  const snackbar = useSnackbar();
  const form = useZodForm(settlementSchema, {
    defaultValues: { date: new Date().toISOString().slice(0, 10), gross: '', fees: '', ref: 'PWP-SET-', bank: '' },
  });
  const { register, formState: { errors } } = form;
  return (
    <FormDialog title="Record gateway settlement" form={form} wide onClose={onClose} submitLabel="Record settlement"
      onSubmit={async (v) => {
        const gross = parseFloat(v.gross);
        const fees = parseFloat(v.fees || '0');
        const je = await recordSettlement({
          settlementId: v.ref,
          grossAmount: gross,
          feeAmount: fees,
          netAmount: gross - fees,
          settlementDate: new Date(`${v.date}T00:00:00Z`).toISOString(),
          bankReference: v.bank,
          currency: 'ZMW',
        });
        snackbar.show(`Settlement recorded${je ? `. Entry ${je.entryNumber} is ready to review` : ''}`);
        onClose();
      }}>
      <p className="m3-muted">Creates the settlement record and a draft journal entry for review.</p>
      <FormGrid>
        <FormCell span={6}><Select label="Provider" density="form" defaultValue="PawaPay"><option value="PawaPay">PawaPay</option></Select></FormCell>
        <FormCell span={6}><TextField label="Settlement date" type="date" density="form" {...register('date')} errorText={errors.date?.message} /></FormCell>
        <FormCell span={6}><TextField label="Gross collected (K)" type="number" density="form" {...register('gross')} errorText={errors.gross?.message} /></FormCell>
        <FormCell span={6}><TextField label="Fees deducted (K)" type="number" density="form" {...register('fees')} errorText={errors.fees?.message} /></FormCell>
        <FormCell span={6}><TextField label="Provider reference" density="form" {...register('ref')} errorText={errors.ref?.message} /></FormCell>
        <FormCell span={6}><TextField label="Bank reference" density="form" {...register('bank')} errorText={errors.bank?.message} /></FormCell>
      </FormGrid>
    </FormDialog>
  );
}

const resolveSchema = z.object({ note: z.string().trim().min(5, 'Give a short note (at least 5 characters)') });

function ResolveItemBody({ target, onClose }: { target: { run: ReconciliationRunRow; item: ReconciliationItemRow }; onClose: () => void }) {
  const { resolveItem } = useReconciliationActions();
  const snackbar = useSnackbar();
  const form = useZodForm(resolveSchema, { defaultValues: { note: '' } });
  return (
    <FormDialog title={`Resolve difference ${itemKey(target.item)}`} form={form} onClose={onClose} submitLabel="Mark resolved"
      onSubmit={async (v) => {
        await resolveItem(target.run.id, itemKey(target.item), v.note);
        snackbar.show('Difference resolved');
        onClose();
      }}>
      <p className="m3-muted">Variance {money(variance(target.item))}. Explain how it was settled.</p>
      <TextArea label="Resolution note" rows={3} {...form.register('note')} errorText={form.formState.errors.note?.message} />
    </FormDialog>
  );
}

function RunSheet({ run, onClose }: { run: ReconciliationRunRow | null; onClose: () => void }) {
  const { completeRun, failRun, busy } = useReconciliationActions();
  const { can } = useStaff();
  const snackbar = useSnackbar();
  const [resolving, setResolving] = useState<ReconciliationItemRow | null>(null);
  const [blocked, setBlocked] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [failing, setFailing] = useState(false);
  const live = run && (run.status === 'REQUIRES_REVIEW' || run.status === 'RUNNING') && can('reconcile');
  const open = run ? run.items.filter(isOpenItem).length : 0;

  const items: Array<DataColumn<ReconciliationItemRow>> = [
    { id: 'ref', header: 'Reference', cell: (i) => <span className="m3-mono">{i.externalId ?? i.internalId ?? '—'}</span> },
    { id: 'res', header: 'Result', cell: (i) => <><StatusPill status={i.status} />{i.resolution ? <><br /><StatusPill tone="success">Resolved</StatusPill><br /><span className="m3-muted">{i.resolution}</span></> : null}</> },
    { id: 'exp', header: 'Expected', align: 'end', cell: (i) => <span className="m3-mono">{money(toNumber(i.internalAmount))}</span> },
    { id: 'act', header: 'Actual', align: 'end', cell: (i) => <span className="m3-mono">{money(toNumber(i.externalAmount))}</span> },
    { id: 'var', header: 'Variance', align: 'end', cell: (i) => <span className="m3-mono">{money(variance(i))}</span> },
  ];

  const run_ = async (fn: () => Promise<void>, ok: string) => {
    try {
      await fn();
      snackbar.show(ok);
      onClose();
    } catch (e) {
      snackbar.show((e as Error).message || 'That did not go through');
    }
  };

  return (
    <>
      <SideSheet
        open={run !== null}
        onClose={onClose}
        title={`Run ${run?.id ?? ''}`}
        actions={
          live ? (
            <>
              <Button variant="filled" onClick={() => (open ? setBlocked(true) : setCompleting(true))}>Complete run</Button>
              <Button variant="outlined" danger onClick={() => setFailing(true)}>Mark failed…</Button>
            </>
          ) : undefined
        }
      >
        {run ? (
          <div className="m3-stack">
            <div className="m3-row"><StatusPill status={run.status} /><StatusPill status={run.type} /></div>
            {run.notes && run.status === 'FAILED' ? <Banner tone="error">{run.notes}</Banner> : null}
            <KeyValue items={[
              { label: 'Period', value: formatDate(run.reconciliationDate) },
              { label: 'Started', value: formatDateTime(run.startedAt) },
              { label: 'Started by', value: run.runBy ?? '—' },
              { label: 'Open differences', value: open },
            ]} />
            <DataTable
              caption="Reconciliation items"
              columns={items}
              rows={run.items}
              getRowId={(i) => itemKey(i) || String(run.items.indexOf(i))}
              density="compact"
              empty={<p className="m3-muted">No items.</p>}
              rowActions={live ? (i) => (isOpenItem(i) ? <Button variant="tonal" size="sm" onClick={() => setResolving(i)}>Resolve</Button> : null) : undefined}
            />
          </div>
        ) : null}
      </SideSheet>
      {run && resolving ? <ResolveItemBody target={{ run, item: resolving }} onClose={() => setResolving(null)} /> : null}
      <Dialog open={blocked} onClose={() => setBlocked(false)} title="Differences still open"
        actions={<Button variant="filled" onClick={() => setBlocked(false)}>Back to run</Button>}>
        <p>{open} item{open > 1 ? 's are' : ' is'} unresolved. Resolve every difference before completing the run.</p>
      </Dialog>
      <ConfirmDialog open={completing} onClose={() => setCompleting(false)} title={`Complete run ${run?.id ?? ''}?`}
        description="The run is closed and its result is final." confirmLabel="Complete run" loading={busy}
        onConfirm={() => { setCompleting(false); if (run) void run_(() => completeRun(run.id), `Run ${run.id} completed`); }} />
      <ReasonDialog open={failing} onClose={() => setFailing(false)} title={`Mark run ${run?.id ?? ''} as failed?`}
        body="Use this when the run cannot be completed, for example a missing statement." confirmLabel="Mark failed" danger loading={busy}
        onConfirm={(reason) => { setFailing(false); if (run) void run_(() => failRun(run.id, reason), `Run ${run.id} marked failed`); }} />
    </>
  );
}

export function ReconTab() {
  const { can } = useStaff();
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(1);
  const st = filters.st && filters.st !== 'all' ? (filters.st as ReconciliationStatus) : undefined;
  const ty = filters.ty && filters.ty !== 'all' ? (filters.ty as ReconciliationType) : undefined;
  const { items, pageInfo, loading, error, refetch } = useReconciliationRuns({ status: st, type: ty }, page - 1, 20);
  const [starting, setStarting] = useState(false);
  const [settling, setSettling] = useState(false);
  const [openId, setOpenId] = useState<string | null>(null);
  const openRun = items.find((r) => r.id === openId) ?? null;

  const columns: Array<DataColumn<ReconciliationRunRow>> = [
    { id: 'run', header: 'Run', cell: (r) => <span className="m3-mono">{r.id.slice(-8)}</span> },
    { id: 'type', header: 'Type', cell: (r) => humanize(r.type) },
    { id: 'period', header: 'Period', cell: (r) => formatDate(r.reconciliationDate) },
    {
      id: 'items',
      header: 'Items',
      cell: (r) => {
        const o = r.items.filter(isOpenItem).length;
        return <>{r.matchedCount + r.unmatchedCount}{o ? <> · <span className="m3-pill" data-tone="error">{o} open</span></> : null}</>;
      },
    },
    { id: 'started', header: 'Started', cell: (r) => <>{formatDateTime(r.startedAt)}<br /><span className="m3-muted">{r.runBy ?? '—'}</span></> },
    { id: 'status', header: 'Status', cell: (r) => <StatusPill status={r.status} /> },
  ];

  return (
    <div className="m3-stack">
      <ListCard
        title="Reconciliation runs"
        subtitle="Compare PawaPay, bank and escrow records with the ledger and resolve differences."
        caption="Reconciliation runs"
        toolbar={
          can('reconcile') ? (
            <Button variant="filled" size="sm" icon="add" onClick={() => setStarting(true)}>Start run</Button>
          ) : (
            <span className="m3-muted">{needText('reconcile')}.</span>
          )
        }
        rows={items}
        columns={columns}
        getRowId={(r) => r.id}
        searchLabel="Search runs"
        searchText={(r) => `${r.id} ${r.type} ${r.reconciliationDate}`}
        filters={[
          { id: 'st', label: 'Status', options: enumOptions(RECON_STATUS_LABELS) },
          { id: 'ty', label: 'Type', options: RECON_TYPES.map((v) => ({ value: v, label: humanize(v) })) },
        ]}
        onFilterChange={(v) => { setFilters(v); setPage(1); }}
        serverPage={{ page, total: pageInfo.totalCount, onPage: setPage }}
        pageSize={20}
        loading={loading}
        error={error}
        onRetry={refetch}
        empty={{ title: 'No reconciliation runs yet.', action: can('reconcile') ? <Button variant="filled" onClick={() => setStarting(true)}>Start run</Button> : undefined }}
        rowActions={(r) => <Button variant="tonal" size="sm" onClick={() => setOpenId(r.id)}>Open</Button>}
      />
      <SettlementsCard toolbar={<Button variant="filled" size="sm" icon="add" onClick={() => setSettling(true)}>Record settlement</Button>} />
      {starting ? <StartRunBody onClose={() => setStarting(false)} /> : null}
      {settling ? <SettlementBody onClose={() => setSettling(false)} /> : null}
      <RunSheet run={openRun} onClose={() => setOpenId(null)} />
    </div>
  );
}
