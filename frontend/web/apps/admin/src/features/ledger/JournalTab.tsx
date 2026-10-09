'use client';

import { RowActions } from '@/components/console/RowActions';
import { useState } from 'react';
import { useFieldArray } from 'react-hook-form';
import { z } from 'zod';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import {
  Button,
  ConfirmDialog,
  DataTable,
  Dialog,
  FormCell,
  FormGrid,
  IconButton,
  Select,
  StatusPill,
  TextField,
  useSnackbar,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import {
  toNumber,
  useChartOfAccounts,
  useJournalActions,
  useJournalEntries,
  type JournalEntryStatus,
  type JournalEntryType,
  type LedgerJournalEntry,
  type LedgerJournalLine,
} from '@pml.tickets/shared/api/admin/modules/ledger';
import { ReasonDialog } from '@/components/console';
import { formatDate, humanize, money } from '@/lib/format';
import { FormDialog } from './FormDialog';
import { ListCard } from './ListCard';
import { JOURNAL_ENTRY_TYPE_LABELS, enumOptions, enumSchema } from '@/lib/enumLabels';

const todayIso = () => new Date().toISOString().slice(0, 10);

const amount = (s: string) => {
  const n = parseFloat(s);
  return Number.isFinite(n) ? n : 0;
};

const lineSchema = z.object({ accountCode: z.string(), debit: z.string(), credit: z.string() });

export const journalSchema = z
  .object({
    description: z.string().trim().min(1, 'Describe the entry'),
    date: z.string().min(1, 'Choose a date'),
    // Reversals are made from an existing entry, never typed in here.
    type: enumSchema(JOURNAL_ENTRY_TYPE_LABELS).refine((t): t is Exclude<JournalEntryType, 'REVERSAL'> => t !== 'REVERSAL', 'Choose a standard or adjusting entry'),
    lines: z.array(lineSchema).min(2),
  })
  .superRefine((v, ctx) => {
    v.lines.forEach((l, i) => {
      const d = amount(l.debit);
      const c = amount(l.credit);
      if (!l.accountCode) ctx.addIssue({ code: 'custom', path: ['lines', i, 'accountCode'], message: 'Choose an account' });
      if (d > 0 && c > 0) ctx.addIssue({ code: 'custom', path: ['lines', i, 'debit'], message: 'Use debit or credit, not both' });
      else if (d <= 0 && c <= 0) ctx.addIssue({ code: 'custom', path: ['lines', i, 'debit'], message: 'Enter an amount' });
    });
    const t = totalsOf(v.lines);
    if (!t.ok && v.lines.every((l) => amount(l.debit) > 0 !== amount(l.credit) > 0)) {
      ctx.addIssue({ code: 'custom', path: ['lines'], message: `The entry is out of balance by ${money(Math.abs(t.d - t.c))}` });
    }
  });

export function totalsOf(lines: Array<{ debit: string; credit: string }>) {
  const d = lines.reduce((a, l) => a + amount(l.debit), 0);
  const c = lines.reduce((a, l) => a + amount(l.credit), 0);
  return { d, c, ok: d > 0 && Math.abs(d - c) < 0.005 };
}

function NewEntryBody({ onClose }: { onClose: () => void }) {
  const { items: accounts } = useChartOfAccounts();
  const { createEntry } = useJournalActions();
  const snackbar = useSnackbar();
  const form = useZodForm(journalSchema, {
    defaultValues: {
      description: '',
      date: todayIso(),
      type: 'STANDARD',
      lines: [{ accountCode: '', debit: '', credit: '' }, { accountCode: '', debit: '', credit: '' }],
    },
  });
  const { register, control, watch, formState: { errors } } = form;
  const { fields, append, remove } = useFieldArray({ control, name: 'lines' });
  const t = totalsOf(watch('lines') ?? []);
  return (
    <FormDialog
      title="New journal entry"
      form={form}
      wide
      onClose={onClose}
      submitLabel="Save draft"
      onSubmit={async (v) => {
        const entry = await createEntry({
          correlationId: `admin-${globalThis.crypto?.randomUUID?.() ?? Date.now()}`,
          entryDate: new Date(`${v.date}T00:00:00Z`).toISOString(),
          description: v.description,
          type: v.type,
          lines: v.lines.map((l) => ({
            accountCode: l.accountCode,
            accountName: accounts.find((a) => a.accountCode === l.accountCode)?.accountName ?? l.accountCode,
            debit: amount(l.debit) > 0 ? amount(l.debit) : null,
            credit: amount(l.credit) > 0 ? amount(l.credit) : null,
          })),
        });
        snackbar.show(`${entry?.entryNumber ?? 'Entry'} saved as a draft. Post it when ready`);
        onClose();
      }}
    >
      <FormGrid>
        <FormCell span={12}>
          <TextField label="Description" density="form" {...register('description')} errorText={errors.description?.message} />
        </FormCell>
        <FormCell span={6}>
          <TextField label="Entry date" type="date" density="form" {...register('date')} errorText={errors.date?.message} />
        </FormCell>
        <FormCell span={6}>
          <Select label="Type" density="form" {...register('type')}>
            {enumOptions(JOURNAL_ENTRY_TYPE_LABELS).filter((o) => o.value !== 'REVERSAL').map((o) => <option key={o.value} value={o.value}>{o.label}</option>)}
          </Select>
        </FormCell>
      </FormGrid>
      <div className="m3-stack" role="group" aria-label="Entry lines">
        {fields.map((f, i) => (
          <FormGrid key={f.id}>
            <FormCell span={6}>
              <Select label={`Account, line ${i + 1}`} density="form" {...register(`lines.${i}.accountCode`)} errorText={errors.lines?.[i]?.accountCode?.message}>
                <option value="">Choose account</option>
                {accounts.filter((a) => a.isActive).map((a) => (<option key={a.id} value={a.accountCode}>{a.accountCode} {a.accountName}</option>))}
              </Select>
            </FormCell>
            <FormCell span={3}>
              <TextField label={`Debit (K), line ${i + 1}`} density="form" inputMode="decimal" {...register(`lines.${i}.debit`)} errorText={errors.lines?.[i]?.debit?.message} />
            </FormCell>
            <FormCell span={3}>
              <div className="m3-row">
                <TextField label={`Credit (K), line ${i + 1}`} density="form" inputMode="decimal" {...register(`lines.${i}.credit`)} />
                <IconButton icon="close" label={`Remove line ${i + 1}`} disabled={fields.length <= 2} onClick={() => remove(i)} />
              </div>
            </FormCell>
          </FormGrid>
        ))}
        <div>
          <Button variant="text" size="sm" icon="add" onClick={() => append({ accountCode: '', debit: '', credit: '' })}>Add line</Button>
        </div>
      </div>
      <p className="m3-muted" aria-live="polite" data-testid="journal-totals">
        Debits <b className="m3-mono">{money(t.d)}</b> · Credits <b className="m3-mono">{money(t.c)}</b> ·{' '}
        {t.ok ? (
          <StatusPill tone="success">Balanced</StatusPill>
        ) : (
          <StatusPill tone="error">{t.d || t.c ? `Out of balance by ${money(Math.abs(t.d - t.c))}` : 'Enter amounts'}</StatusPill>
        )}
      </p>
      {errors.lines?.message || errors.lines?.root?.message ? <p role="alert" className="m3-muted">{errors.lines?.message ?? errors.lines?.root?.message}</p> : null}
    </FormDialog>
  );
}

function ViewDialog({ entry, onClose }: { entry: LedgerJournalEntry | null; onClose: () => void }) {
  const cols: Array<DataColumn<LedgerJournalLine>> = [
    { id: 'acct', header: 'Account', cell: (l) => <><span className="m3-mono">{l.accountCode}</span> {l.accountName}</> },
    { id: 'dr', header: 'Debit', align: 'end', cell: (l) => <span className="m3-mono">{toNumber(l.debit) ? money(toNumber(l.debit)) : '—'}</span> },
    { id: 'cr', header: 'Credit', align: 'end', cell: (l) => <span className="m3-mono">{toNumber(l.credit) ? money(toNumber(l.credit)) : '—'}</span> },
  ];
  return (
    <Dialog open={entry !== null} onClose={onClose} title={`Journal entry ${entry?.entryNumber ?? ''}`} wide
      actions={<Button variant="filled" onClick={onClose}>Close</Button>}>
      {entry ? (
        <div className="m3-stack">
          <div className="m3-row"><StatusPill status={entry.status} /><StatusPill status={entry.type} /></div>
          <p>{entry.description} · {formatDate(entry.entryDate)}{entry.correlationId ? <> · <span className="m3-mono">{entry.correlationId}</span></> : null}</p>
          <DataTable caption="Entry lines" columns={cols} rows={entry.lines} getRowId={(l) => `${l.accountCode}-${l.debit}-${l.credit}`} density="compact" />
          <p><b>Total</b> debits <span className="m3-mono">{money(toNumber(entry.totalDebits))}</span>, credits <span className="m3-mono">{money(toNumber(entry.totalCredits))}</span></p>
        </div>
      ) : null}
    </Dialog>
  );
}

export function JournalTab() {
  const [filters, setFilters] = useState<Record<string, string>>({});
  const [page, setPage] = useState(1);
  const status = (filters.st && filters.st !== 'all' ? filters.st : undefined) as JournalEntryStatus | undefined;
  const type = (filters.ty && filters.ty !== 'all' ? filters.ty : undefined) as JournalEntryType | undefined;
  const { items, pageInfo, loading, error, refetch } = useJournalEntries({ status, type }, page - 1, 20);
  const { postEntry, reverseEntry, busy } = useJournalActions();
  const snackbar = useSnackbar();
  const [creating, setCreating] = useState(false);
  const [viewing, setViewing] = useState<LedgerJournalEntry | null>(null);
  const [posting, setPosting] = useState<LedgerJournalEntry | null>(null);
  const [reversing, setReversing] = useState<LedgerJournalEntry | null>(null);

  const columns: Array<DataColumn<LedgerJournalEntry>> = [
    { id: 'entry', header: 'Entry', cell: (j) => <><span className="m3-mono">{j.entryNumber}</span><br /><span className="m3-muted">{formatDate(j.entryDate)}</span></> },
    {
      id: 'desc',
      header: 'Description',
      cell: (j) => (
        <>
          {j.description}
          {j.reversalEntryId || j.type === 'REVERSAL' ? <><br /><span className="m3-muted">Reversal entry</span></> : null}
          {j.reversedByEntryId ? <><br /><span className="m3-muted">Reversed by {j.reversedByEntryId}</span></> : null}
        </>
      ),
    },
    { id: 'type', header: 'Type', cell: (j) => humanize(j.type) },
    { id: 'lines', header: 'Lines', cell: (j) => j.lines.length },
    { id: 'amount', header: 'Amount', align: 'end', cell: (j) => <span className="m3-mono">{money(toNumber(j.totalDebits))}</span> },
    { id: 'status', header: 'Status', cell: (j) => <StatusPill status={j.status} /> },
  ];

  const run = async (fn: () => Promise<void>, ok: string) => {
    try {
      await fn();
      snackbar.show(ok);
    } catch (e) {
      snackbar.show((e as Error).message || 'That did not go through');
    }
  };

  return (
    <>
      <ListCard
        title="Journal entries"
        subtitle="Entries must balance. Post a draft to make it count; reverse a posted entry with a reason."
        caption="Journal entries"
        toolbar={<Button variant="filled" size="sm" icon="add" onClick={() => setCreating(true)}>New entry</Button>}
        rows={items}
        columns={columns}
        getRowId={(j) => j.id}
        searchLabel="Search entries"
        searchText={(j) => `${j.entryNumber} ${j.description} ${j.correlationId ?? ''}`}
        filters={[
          { id: 'st', label: 'Status', options: ['DRAFT', 'POSTED', 'REVERSED'].map((v) => ({ value: v, label: humanize(v) })) },
          { id: 'ty', label: 'Type', options: enumOptions(JOURNAL_ENTRY_TYPE_LABELS) },
        ]}
        onFilterChange={(v) => {
          setFilters(v);
          setPage(1);
        }}
        serverPage={{ page, total: pageInfo.totalCount, onPage: setPage }}
        pageSize={20}
        loading={loading}
        error={error}
        onRetry={refetch}
        empty={{ title: 'No journal entries yet.', action: <Button variant="filled" onClick={() => setCreating(true)}>New entry</Button> }}
        rowActions={(j) => (
          <RowActions
            name={`entry ${j.entryNumber}`}
            primary={{ label: 'View', onSelect: () => setViewing(j) }}
            items={[
              ...(j.status === 'DRAFT' ? [{ id: 'post', label: 'Post', onSelect: () => setPosting(j) }] : []),
              ...(j.status === 'POSTED' && j.type !== 'REVERSAL' ? [{ id: 'reverse', label: 'Reverse…', danger: true, onSelect: () => setReversing(j) }] : []),
            ]}
          />
        )}
      />
      {creating ? <NewEntryBody onClose={() => setCreating(false)} /> : null}
      <ViewDialog entry={viewing} onClose={() => setViewing(null)} />
      <ConfirmDialog
        open={posting !== null}
        onClose={() => setPosting(null)}
        title={`Post ${posting?.entryNumber ?? ''}?`}
        description={`Posts ${money(toNumber(posting?.totalDebits))} to the ledger. Posted entries cannot be edited, only reversed.`}
        confirmLabel="Post entry"
        loading={busy}
        onConfirm={async () => {
          const j = posting;
          setPosting(null);
          if (j) await run(() => postEntry(j.id), `${j.entryNumber} posted`);
        }}
      />
      <ReasonDialog
        open={reversing !== null}
        onClose={() => setReversing(null)}
        title={`Reverse ${reversing?.entryNumber ?? ''}?`}
        body="Creates a mirror entry with debits and credits swapped. The original is marked reversed."
        confirmLabel="Reverse entry"
        danger
        loading={busy}
        onConfirm={async (reason) => {
          const j = reversing;
          setReversing(null);
          if (j) await run(() => reverseEntry(j.id, reason), `${j.entryNumber} reversed`);
        }}
      />
    </>
  );
}
