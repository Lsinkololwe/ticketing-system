'use client';

import { useEffect, useState } from 'react';
import { Banner, Button, DataTable, Dialog, EmptyState, StatusPill } from '@pml.tickets/shared/components/m3';
import { useEscrowConsistencyCheck, type EscrowVerification } from '@pml.tickets/shared/api/admin/modules/finance-ops';
import { money } from '@/lib/format';
import { asNumber, Mono } from './shared';

const SHOWN = 12;

/** Escrow consistency check: compares each escrow balance with its journal balance. */
export function ConsistencyDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  const check = useEscrowConsistencyCheck();
  const [state, setState] = useState<{ rows: EscrowVerification[]; error: string | null } | null>(null);

  useEffect(() => {
    if (!open) return;
    let live = true;
    setState(null);
    void check().then((r) => live && setState(r));
    return () => {
      live = false;
    };
  }, [open, check]);

  const rows = state?.rows ?? [];
  const bad = rows.filter((r) => !r.isConsistent);
  return (
    <Dialog open={open} onClose={onClose} title="Escrow consistency check" wide actions={<Button variant="filled" onClick={onClose}>Close</Button>}>
      {!state ? <p aria-busy="true">Checking every escrow account against the journal…</p> : null}
      {state?.error ? <Banner tone="error">{state.error}</Banner> : null}
      {state && !state.error ? (
        <>
          <p>
            {rows.length === 0
              ? 'There are no escrow accounts to check.'
              : bad.length === 0
                ? `All ${rows.length} escrow accounts match their journal.`
                : `${bad.length} account(s) differ from the journal.`}
          </p>
          <DataTable<EscrowVerification>
            caption="Escrow consistency results"
            density="compact"
            rows={[...bad, ...rows.filter((r) => r.isConsistent)].slice(0, SHOWN)}
            getRowId={(r) => r.escrowAccountId ?? r.eventId}
            empty={<EmptyState title="Nothing to show." />}
            columns={[
              { id: 'acct', header: 'Account', rowHeader: true, cell: (r) => <Mono>{(r.escrowAccountId ?? r.eventId).slice(-8)}</Mono> },
              { id: 'stored', header: 'Stored', align: 'end', cell: (r) => <Mono>{r.escrowBalance == null ? '—' : money(asNumber(r.escrowBalance))}</Mono> },
              { id: 'ledger', header: 'Ledger', align: 'end', cell: (r) => <Mono>{r.journalBalance == null ? '—' : money(asNumber(r.journalBalance))}</Mono> },
              { id: 'res', header: 'Result', cell: (r) => <StatusPill tone={r.isConsistent ? 'success' : 'error'}>{r.isConsistent ? 'Consistent' : 'Mismatch'}</StatusPill> },
            ]}
          />
          {rows.length > SHOWN ? <p className="m3-muted">Showing the first {SHOWN} of {rows.length} accounts, mismatches first.</p> : null}
        </>
      ) : null}
    </Dialog>
  );
}
