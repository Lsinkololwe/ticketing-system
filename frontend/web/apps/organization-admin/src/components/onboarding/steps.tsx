import { Stepper } from '@pml.tickets/shared/components/m3';

export const WIZARD_LABELS = [
  { id: 'info', label: 'Organization & business info' },
  { id: 'docs', label: 'KYB documents' },
  { id: 'review', label: 'Review & submit' },
];

/** Wizard progress; `current` is 0 (info), 1 (documents) or 2 (review). */
export function WizardSteps({ current }: { current: number }) {
  return <Stepper steps={WIZARD_LABELS} current={current} label="Application steps" />;
}

const TRACKER_INDEX: Record<string, number> = {
  DRAFT: 0,
  PENDING_DOCUMENTS: 0,
  PENDING_REVIEW: 1,
  CHANGES_REQUESTED: 2,
  REJECTED: 3,
  APPROVED: 3,
  ACTIVE: 3,
};

/** Application status tracker: Draft, Submitted, Changes requested, final decision. */
export function TrackerSteps({ status }: { status: string }) {
  const rejected = status === 'REJECTED';
  const steps = [
    { id: 'draft', label: 'Draft' },
    { id: 'submitted', label: 'Submitted' },
    { id: 'changes', label: 'Changes requested' },
    { id: 'final', label: rejected ? 'Rejected' : 'Approved' },
  ];
  const current = TRACKER_INDEX[status] ?? 0;
  const done = status === 'ACTIVE' || status === 'APPROVED';
  return <Stepper steps={steps} current={done ? steps.length : current} label="Application status" />;
}
