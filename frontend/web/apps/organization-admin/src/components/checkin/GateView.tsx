'use client';

import { useState } from 'react';
import { Banner, Button, Card, CardHeader, DataTable, EmptyState, LinearProgress, StatCard } from '@pml.tickets/shared/components/m3';
import type { CheckInRow, ConflictRow } from '@/lib/api/bookings';
import { formatDateTime } from '@/lib/bookings/format';
import { Status, statusLabel } from '@/components/console/Status';
import { Form, TextAreaRHF, TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import { gateSchema } from './schemas';
import { ReasonDialog } from '@/components/bookings/ReasonDialog';

export interface GateSummary {
  issued: number;
  admitted: number;
  conflicts: number;
  openConflicts: number;
  manualAdmissions: number;
  lastCheckInAt: string | null;
}

export interface GateResult {
  outcome: string;
  message: string;
  code: string;
  manual: boolean;
}

/** Outcome word, tone and instruction for the last result card. */
export const OUTCOMES: Record<string, { tone: 'success' | 'warning' | 'error'; title: string; hint: string }> = {
  ADMITTED: { tone: 'success', title: 'Admitted', hint: 'Let the guest in.' },
  ALREADY_ADMITTED: { tone: 'warning', title: 'Already admitted', hint: 'This ticket was used earlier. A conflict was logged.' },
  ALREADY_RECORDED: { tone: 'warning', title: 'Already recorded', hint: 'This check-in was already saved.' },
  WRONG_EVENT: { tone: 'error', title: 'Wrong event', hint: 'This ticket is for a different event.' },
  INVALID_STATE: { tone: 'error', title: 'Ticket not valid', hint: 'Refunded, cancelled or transferred tickets cannot enter.' },
  NOT_FOUND: { tone: 'error', title: 'Ticket not found', hint: 'No ticket matches this code.' },
  UNREACHABLE: { tone: 'error', title: 'Could not check in', hint: 'The server did not answer. The ticket was not checked in.' },
};

export interface GateViewProps {
  /** Check-in is allowed only while the event is live. */
  open: boolean;
  summary: GateSummary | null;
  summaryLoading: boolean;
  result: GateResult | null;
  busy: boolean;
  onCheckIn: (input: { code: string; manual: boolean; reason?: string }) => void | Promise<void>;
  recent: CheckInRow[];
  recentLoading: boolean;
  conflicts: ConflictRow[];
  onReviewConflict: (id: string, note: string) => Promise<unknown> | void;
}

const n = (v: number | null | undefined) => (v ?? 0).toLocaleString('en-GB');

export function GateView(p: GateViewProps) {
  const form = useZodForm(gateSchema, { defaultValues: { code: '', manual: false, reason: '' } });
  const manual = form.watch('manual');
  const [reviewing, setReviewing] = useState<ConflictRow | null>(null);
  const s = p.summary;
  const outcome = p.result ? (OUTCOMES[p.result.outcome] ?? OUTCOMES.INVALID_STATE!) : null;

  const submit = async (v: { code: string; manual: boolean; reason: string }) => {
    await p.onCheckIn({ code: v.code, manual: v.manual, reason: v.manual ? v.reason : undefined });
    form.reset({ code: '', manual: v.manual, reason: '' });
  };

  return (
    <div className="m3-stack">
      <div className="oc-cols" role="group" aria-label="Check-in summary">
        <StatCard label="Issued" value={n(s?.issued)} />
        <StatCard label="Admitted" value={n(s?.admitted)} progress={s && s.issued ? (s.admitted / s.issued) * 100 : 0} />
        <StatCard label="Manual admissions" value={n(s?.manualAdmissions)} />
        <StatCard label="Conflicts" value={`${n(s?.conflicts)} · ${n(s?.openConflicts)} open`} />
        <StatCard label="Last check-in" value={s?.lastCheckInAt ? formatDateTime(s.lastCheckInAt) : '—'} />
      </div>
      {p.summaryLoading && !s ? <LinearProgress label="Loading summary" /> : null}

      <div className="oc-cols--2-1">
        <Card>
          <CardHeader title="Gate" />
          {!p.open ? <Banner tone="info">Check-in opens when the event is live. You can still review the summary and conflicts.</Banner> : null}
          <Form form={form} onSubmit={submit} guardLeave={false} disabled={!p.open} aria-label="Check in a ticket">
            <div className="m3-stack">
              <TextFieldRHF name="code" label="Ticket number or code" placeholder="TK-AB12CD34" autoComplete="off" />
              <div className="m3-row">
                <Button type="submit" variant="filled" disabled={!p.open || p.busy}>
                  Check in
                </Button>
                <Button
                  type="button"
                  variant="outlined"
                  aria-pressed={manual}
                  disabled={!p.open}
                  onClick={() => form.setValue('manual', !manual, { shouldDirty: true })}
                >
                  {manual ? 'Hide manual admission' : 'Admit manually…'}
                </Button>
              </div>
              {manual ? (
                <TextAreaRHF
                  name="reason"
                  label="Reason for manual admission"
                  rows={2}
                  helperText="Counted separately from scanned entries. A typed reason is required."
                />
              ) : null}
            </div>
          </Form>
          {p.result && outcome ? (
            <div className="oc-section">
              <Banner tone={outcome.tone} title={outcome.title}>
                {p.result.message || outcome.hint} <span className="m3-mono">{p.result.code}</span>
                {p.result.manual ? ' · manual' : ''}
              </Banner>
            </div>
          ) : (
            <p className="m3-muted oc-section">Type or paste a ticket number, then press Check in.</p>
          )}
          <div className="oc-section">
            <Banner tone="info" title="Camera scanning is not available yet.">
              Use ticket number entry for now. Offline scanning is also not available.
            </Banner>
          </div>
        </Card>

        <Card>
          <CardHeader title="Recent check-ins" subtitle={`Latest ${p.recent.length}`} />
          <DataTable
            caption="Recent check-ins"
            rows={p.recent}
            getRowId={(r) => r.id}
            loading={p.recentLoading && p.recent.length === 0}
            empty={<EmptyState icon="qr" title="Nobody has been admitted yet." />}
            columns={[
              { id: 'no', header: 'Ticket', rowHeader: true, cell: (r) => <span className="m3-mono">{r.ticketNumber ?? '—'}</span> },
              { id: 'time', header: 'Time', cell: (r) => formatDateTime(r.scannedAt ?? r.recordedAt) },
              {
                id: 'method',
                header: 'Method',
                cell: (r) => (
                  <>
                    {r.method === 'MANUAL' ? 'Manual' : r.method === 'QR_OFFLINE' ? 'QR (offline)' : 'Code entry'}
                    {r.method === 'MANUAL' && r.reason ? <div className="m3-muted">{r.reason}</div> : null}
                  </>
                ),
              },
            ]}
          />
        </Card>
      </div>

      <Card>
        <CardHeader title="Conflicts" subtitle="Duplicate scans and invalid tickets for staff to review." />
        <DataTable
          caption="Check-in conflicts"
          rows={p.conflicts}
          getRowId={(c) => c.id}
          empty={<EmptyState icon="check-circle" title="No conflicts. Everything checked in cleanly." />}
          columns={[
            { id: 'type', header: 'Type', rowHeader: true, cell: (c) => statusLabel(c.type) },
            { id: 'code', header: 'Ticket', cell: (c) => <span className="m3-mono">{c.presentedCode ?? '—'}</span> },
            { id: 'when', header: 'When', cell: (c) => formatDateTime(c.detectedAt) },
            { id: 'status', header: 'Status', cell: (c) => <Status status={c.status === 'OPEN' ? 'OPEN' : 'REVIEWED'} label={c.status === 'OPEN' ? 'Open' : 'Reviewed'} /> },
            { id: 'note', header: 'Review note', cell: (c) => c.reviewNote || '—' },
          ]}
          rowActions={(c) =>
            c.status === 'OPEN' ? (
              <Button size="sm" variant="tonal" onClick={() => setReviewing(c)} aria-label={`Mark conflict ${c.presentedCode ?? c.id} reviewed`}>
                Mark reviewed
              </Button>
            ) : null
          }
        />
      </Card>

      <ReasonDialog
        key={reviewing?.id ?? 'none'}
        open={Boolean(reviewing)}
        title="Mark conflict reviewed"
        description="Add a note for the record, for example what you did at the gate."
        confirmLabel="Mark reviewed"
        onClose={() => setReviewing(null)}
        onConfirm={async (note) => {
          if (reviewing) await p.onReviewConflict(reviewing.id, note);
          setReviewing(null);
        }}
      />
    </div>
  );
}
