'use client';

import { ReadinessList } from '@pml.tickets/shared/components/m3';
import { BLOCKER_HINT, BLOCKER_LABEL } from './helpers';

/** What still stands between an event and its approval (backend `approvalBlockers`). */
export function ApprovalChecklist({ blockers }: { blockers: string[] }) {
  return (
    <ReadinessList
      label="Approval checklist"
      items={Object.keys(BLOCKER_LABEL).map((k) => ({
        id: k,
        label: BLOCKER_LABEL[k],
        done: !blockers.includes(k),
        hint: blockers.includes(k) ? BLOCKER_HINT[k] : undefined,
      }))}
    />
  );
}
