/** SLA clock helpers for the approvals queues. Pure; the clock inputs come from platform configuration. */

export type SlaKind = 'ok' | 'warn' | 'overdue' | 'paused';
export type SlaTone = 'success' | 'warning' | 'error' | 'neutral';

export interface Sla {
  kind: SlaKind;
  label: string;
  tone: SlaTone;
  /** Hours remaining (negative when overdue); null while paused/unknown. */
  remaining: number | null;
}

/** 0.4 -> "24 min", 30 -> "30 h", 72 -> "3 d". */
export function durStr(hours: number): string {
  const h = Math.abs(hours);
  if (h < 1) return `${Math.max(1, Math.round(h * 60))} min`;
  if (h < 48) return `${Math.round(h)} h`;
  return `${Math.round(h / 24)} d`;
}

export function deadlineFrom(submittedAt: string | null | undefined, slaHours: number | null | undefined): string | null {
  if (!submittedAt || slaHours == null) return null;
  const t = new Date(submittedAt).getTime();
  if (Number.isNaN(t)) return null;
  return new Date(t + slaHours * 36e5).toISOString();
}

export function slaOf(deadline: string | null | undefined, warnHours: number | null | undefined, opts: { paused?: boolean; now?: Date } = {}): Sla | null {
  if (opts.paused) return { kind: 'paused', label: 'Clock paused', tone: 'neutral', remaining: null };
  if (!deadline) return null;
  const end = new Date(deadline).getTime();
  if (Number.isNaN(end)) return null;
  const rem = (end - (opts.now ?? new Date()).getTime()) / 36e5;
  if (rem < 0) return { kind: 'overdue', label: `Overdue by ${durStr(rem)}`, tone: 'error', remaining: rem };
  if (warnHours != null && rem <= warnHours) return { kind: 'warn', label: `Due in ${durStr(rem)}`, tone: 'warning', remaining: rem };
  return { kind: 'ok', label: `Due in ${durStr(rem)}`, tone: 'success', remaining: rem };
}
