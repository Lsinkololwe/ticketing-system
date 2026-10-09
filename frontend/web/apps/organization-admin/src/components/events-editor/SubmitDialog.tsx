'use client';

import { Button, Dialog } from '@pml.tickets/shared/components/m3';
import { BLOCKER_TEXT, type Blocker } from './model';

export interface SubmitDialogProps {
  open: boolean;
  title: string;
  resubmit: boolean;
  blockers: Blocker[];
  reviewHours?: number;
  requireComments?: boolean;
  submitting?: boolean;
  onClose: () => void;
  onConfirm: () => void;
}

/** Confirmation before sending the event to a platform reviewer. */
export function SubmitDialog({
  open,
  title,
  resubmit,
  blockers,
  reviewHours,
  requireComments,
  submitting,
  onClose,
  onConfirm,
}: SubmitDialogProps) {
  const label = blockers.length ? 'Submit anyway' : resubmit ? 'Resubmit for approval' : 'Submit for approval';
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={`${resubmit ? 'Resubmit' : 'Submit'} “${title || 'this event'}” for approval?`}
      actions={
        <>
          <Button variant="text" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="filled" loading={submitting} onClick={onConfirm}>
            {label}
          </Button>
        </>
      }
    >
      {blockers.length ? (
        <>
          <p>These items will stop an admin approving it:</p>
          <ul>
            {blockers.map((b) => (
              <li key={b}>{BLOCKER_TEXT[b]}</li>
            ))}
          </ul>
          <p>You can still submit, but fix them to speed things up.</p>
        </>
      ) : (
        <p>
          A platform reviewer checks the event. {reviewHours != null ? <b>Typical review time: {reviewHours} hours.</b> : null} You will be told if changes are needed.
          {requireComments ? ' The reviewer must leave a comment when asking for changes, so you will see exactly what to fix.' : ''}
        </p>
      )}
    </Dialog>
  );
}
