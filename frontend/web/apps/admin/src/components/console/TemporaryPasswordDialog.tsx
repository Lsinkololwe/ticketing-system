'use client';

import { useCallback, useEffect, useState, type ReactNode } from 'react';
import { Button, Dialog, TextField, useSnackbar } from '@pml.tickets/shared/components/m3';

/** A password that has just been issued, and whose account it belongs to. */
export interface IssuedPassword {
  password: string;
  accountName: string;
  /** `created` for a new staff account, `reset` after a reset. Only changes the heading. */
  kind: 'created' | 'reset';
}

export interface TemporaryPasswordDialogProps {
  issued: IssuedPassword | null;
  onClose: () => void;
}

/**
 * Shows a one-time temporary password. The value lives only in the props of this dialog (which come from
 * its owner's component state): it is not written to storage, the URL or the query cache. The owner clears
 * it by setting `issued` back to null, which every close path (Done and Escape; the scrim is inert so a
 * stray click cannot lose the only copy) does through `onClose`.
 */
export function TemporaryPasswordDialog({ issued, onClose }: TemporaryPasswordDialogProps) {
  const snack = useSnackbar();
  const [shown, setShown] = useState(false);

  // Each new password starts masked.
  useEffect(() => {
    setShown(false);
  }, [issued?.password]);

  const copy = async () => {
    if (!issued) return;
    try {
      await navigator.clipboard.writeText(issued.password);
      snack.show('Password copied');
    } catch {
      setShown(true);
      snack.show({ message: 'Could not copy. Select the password and copy it yourself.', tone: 'error' });
    }
  };

  const title = issued ? (issued.kind === 'created' ? `${issued.accountName} created` : `New password for ${issued.accountName}`) : '';

  return (
    <Dialog
      open={issued !== null}
      onClose={onClose}
      title={title}
      alert
      dismissOnScrim={false}
      actions={
        <Button variant="filled" onClick={onClose} data-testid="temporary-password-done">
          Done
        </Button>
      }
    >
      <div className="m3-stack" data-testid="temporary-password-panel">
        <p role="alert">
          This password is shown once and cannot be retrieved again. Give it to {issued?.accountName ?? 'the person'} through a secure channel.
          They must change it at first sign-in.
        </p>
        <TextField
          label="Temporary password"
          readOnly
          autoComplete="off"
          spellCheck={false}
          type={shown ? 'text' : 'password'}
          value={issued?.password ?? ''}
          data-testid="temporary-password-value"
        />
        <div className="m3-row">
          <Button variant="outlined" size="sm" icon="copy" onClick={() => void copy()} data-testid="temporary-password-copy">
            Copy password
          </Button>
          <Button variant="text" size="sm" aria-pressed={shown} onClick={() => setShown((s) => !s)} data-testid="temporary-password-toggle">
            {shown ? 'Hide' : 'Show'}
          </Button>
        </div>
      </div>
    </Dialog>
  );
}

/**
 * Holds the issued password in component state. `show` opens the panel; `panel` is the element to render
 * once. Closing the panel (or unmounting the owner) drops the value.
 */
export function useTemporaryPassword(): { show: (issued: IssuedPassword) => void; clear: () => void; panel: ReactNode } {
  const [issued, setIssued] = useState<IssuedPassword | null>(null);
  const clear = useCallback(() => setIssued(null), []);
  const show = useCallback((i: IssuedPassword) => setIssued(i), []);
  return { show, clear, panel: <TemporaryPasswordDialog issued={issued} onClose={clear} /> };
}
