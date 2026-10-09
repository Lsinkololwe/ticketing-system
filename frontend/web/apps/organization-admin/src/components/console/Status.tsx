import { StatusPill } from '@pml.tickets/shared/components/m3';
import { humanizeStatus } from '@/lib/format/figure';

/** Product wording that differs from the enum name. */
const LABELS: Record<string, string> = {
  PUBLISHED: 'Live',
  NON_PROFIT: 'Non-profit',
  PAYOUT_ELIGIBLE: 'Payout eligible',
  PLATFORM_FEE: 'Commission',
  TICKET_SALE: 'Ticket sale',
  NO_REFUNDS: 'No refunds',
};

export function statusLabel(status?: string | null): string {
  return humanizeStatus(status, LABELS) || '—';
}

/** Status chip: humanised text plus a tone from the design system. Text is always shown. */
export function Status({ status, label }: { status?: string | null; label?: string }) {
  if (!status) return <span className="m3-muted">—</span>;
  return <StatusPill status={status}>{label ?? statusLabel(status)}</StatusPill>;
}
