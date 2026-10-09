import { z } from 'zod';
import type { PlatformConfigurationRow } from '@pml.tickets/shared/api/admin/modules/platform-config';

/** The approval fields the backend can read and write (everything else on the page has no operation yet). */
export type ApprovalDraft = Omit<PlatformConfigurationRow, 'id' | 'version' | 'updatedAt' | 'updatedBy' | 'commissionDefault' | 'minimumPayout'> & {
  commissionDefault: number;
  minimumPayout: number;
};

export const APPROVAL_KEYS = [
  'approvalSlaHours',
  'approvalWarningThresholdHours',
  'autoEscalationEnabled',
  'escalationDelayHours',
  'escalationRecipientRole',
  'escalationReminderIntervalHours',
  'maxEscalationReminders',
  'organizerNotificationChannel',
  'adminNotificationChannel',
  'sendSlaWarningNotifications',
  'sendEscalationNotifications',
  'requireCommentsOnRejection',
  'requireCommentsOnChangesRequested',
  'allowSelfApproval',
  'commissionDefault',
  'minimumPayout',
  'currency',
  'reservationHoldMinutes',
  'reservationGraceMinutes',
  'escrowHoldDays',
  'refundCutoffHours',
  'maxTicketsPerBooking',
  'rescheduleLimit',
  'refundPolicies',
] as const satisfies ReadonlyArray<keyof ApprovalDraft>;

export const APPROVAL_LABELS: Record<(typeof APPROVAL_KEYS)[number], string> = {
  approvalSlaHours: 'Approval target',
  approvalWarningThresholdHours: 'Warning threshold',
  autoEscalationEnabled: 'Automatic escalation',
  escalationDelayHours: 'Escalation delay',
  escalationRecipientRole: 'Escalation recipient',
  escalationReminderIntervalHours: 'Reminder interval',
  maxEscalationReminders: 'Maximum reminders',
  organizerNotificationChannel: 'Organizer notification channel',
  adminNotificationChannel: 'Admin notification channel',
  sendSlaWarningNotifications: 'SLA warnings',
  sendEscalationNotifications: 'Escalation notifications',
  requireCommentsOnRejection: 'Comments on rejection',
  requireCommentsOnChangesRequested: 'Comments on requested changes',
  allowSelfApproval: 'Self-approval',
  commissionDefault: 'Default commission',
  minimumPayout: 'Minimum payout',
  currency: 'Currency',
  reservationHoldMinutes: 'Reservation hold',
  reservationGraceMinutes: 'Payment grace period',
  escrowHoldDays: 'Escrow hold',
  refundCutoffHours: 'Refund cutoff',
  maxTicketsPerBooking: 'Tickets per booking',
  rescheduleLimit: 'Reschedule limit',
  refundPolicies: 'Refund policies',
};

/** Built-in approval defaults used by "Reset to defaults". */
export const APPROVAL_DEFAULTS: ApprovalDraft = {
  approvalSlaHours: 48,
  approvalWarningThresholdHours: 12,
  autoEscalationEnabled: true,
  escalationDelayHours: 12,
  escalationRecipientRole: 'FINANCE_LEAD',
  escalationReminderIntervalHours: 12,
  maxEscalationReminders: 3,
  organizerNotificationChannel: 'BOTH',
  adminNotificationChannel: 'BOTH',
  sendSlaWarningNotifications: true,
  sendEscalationNotifications: true,
  requireCommentsOnRejection: true,
  requireCommentsOnChangesRequested: true,
  allowSelfApproval: false,
  commissionDefault: 5,
  minimumPayout: 10,
  currency: 'ZMW',
  reservationHoldMinutes: 10,
  reservationGraceMinutes: 5,
  escrowHoldDays: 7,
  refundCutoffHours: 24,
  maxTicketsPerBooking: 8,
  rescheduleLimit: 3,
  refundPolicies: [
    { code: 'FLEXIBLE', label: 'Flexible', summary: 'Full refund until 24 hours before the event.', rules: [{ daysBefore: 1, percent: 100 }] },
    { code: 'MODERATE', label: 'Moderate', summary: 'Full refund until 7 days before, 50% until 24 hours before.', rules: [{ daysBefore: 7, percent: 100 }, { daysBefore: 1, percent: 50 }] },
    { code: 'STRICT', label: 'Strict', summary: '50% refund until 7 days before, then no refund.', rules: [{ daysBefore: 7, percent: 50 }] },
    { code: 'NO_REFUNDS', label: 'No refunds', summary: 'Tickets cannot be refunded unless the event is cancelled.', rules: [] },
  ],
};

export function draftOf(c: PlatformConfigurationRow): ApprovalDraft {
  const d = Object.fromEntries(APPROVAL_KEYS.map((k) => [k, c[k]])) as unknown as ApprovalDraft;
  return {
    ...d,
    commissionDefault: Number(c.commissionDefault ?? APPROVAL_DEFAULTS.commissionDefault),
    minimumPayout: Number(c.minimumPayout ?? APPROVAL_DEFAULTS.minimumPayout),
    refundPolicies: (c.refundPolicies ?? []).map((p) => ({ code: p.code, label: p.label, summary: p.summary, rules: p.rules.map((r) => ({ daysBefore: r.daysBefore, percent: r.percent })) })),
  };
}

export function changedKeys(a: ApprovalDraft, b: ApprovalDraft): Array<(typeof APPROVAL_KEYS)[number]> {
  return APPROVAL_KEYS.filter((k) => JSON.stringify(a[k]) !== JSON.stringify(b[k]));
}

const hours = (min: number, max: number, unit = 'hours') =>
  z
    .number({ error: 'Enter a number' })
    .int('Enter a whole number')
    .min(min, min === 1 ? `Enter at least 1 ${unit.replace(/s$/, '')}` : `Enter ${min} or more`)
    .max(max, `Enter ${max} ${unit} or fewer`);

const text = (label: string) => z.string({ error: label }).min(1, label);

/** Zod schema of the approval form (the only platform rules the backend stores today). */
export const approvalSchema = z
  .object({
    approvalSlaHours: hours(1, 240),
    approvalWarningThresholdHours: hours(1, 240),
    autoEscalationEnabled: z.boolean(),
    escalationDelayHours: hours(1, 240),
    escalationRecipientRole: text('Choose who escalations go to'),
    escalationReminderIntervalHours: hours(1, 72),
    maxEscalationReminders: z.number({ error: 'Enter a number' }).int('Enter a whole number').min(0, 'Enter 0 or more').max(20, 'Enter 20 or fewer'),
    organizerNotificationChannel: text('Choose a channel'),
    adminNotificationChannel: text('Choose a channel'),
    sendSlaWarningNotifications: z.boolean(),
    sendEscalationNotifications: z.boolean(),
    requireCommentsOnRejection: z.boolean(),
    requireCommentsOnChangesRequested: z.boolean(),
    allowSelfApproval: z.boolean(),
    commissionDefault: z.number({ error: 'Enter a percentage' }).min(0, 'Enter 0 or more').max(100, 'Enter 100 or fewer'),
    minimumPayout: z.number({ error: 'Enter an amount' }).min(0, 'Enter 0 or more'),
    currency: text('Currency is required'),
    reservationHoldMinutes: hours(1, 120, 'minutes'),
    reservationGraceMinutes: hours(0, 60, 'minutes'),
    escrowHoldDays: hours(0, 90, 'days'),
    refundCutoffHours: hours(0, 720),
    maxTicketsPerBooking: hours(1, 50, 'tickets'),
    rescheduleLimit: hours(0, 20, 'reschedules'),
    refundPolicies: z.array(
      z.object({
        code: z.string(),
        label: text('Give the policy a name'),
        summary: text('Describe the policy for buyers'),
        rules: z.array(z.object({ daysBefore: z.number().int().min(0), percent: z.number().int().min(0).max(100) })),
      })
    ),
  })
  .refine((d) => d.approvalWarningThresholdHours < d.approvalSlaHours, {
    path: ['approvalWarningThresholdHours'],
    message: 'Must be less than the approval target',
  });
