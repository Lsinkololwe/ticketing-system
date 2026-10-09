'use client';

import type { ReactNode } from 'react';
import { Button, Dialog, StatusPill } from '@pml.tickets/shared/components/m3';

export interface CompareRow {
  label: string;
  a: ReactNode;
  b: ReactNode;
  /** Plain-text value used to decide whether the row differs. */
  aKey: string;
  bKey: string;
}

export interface CompareDialogProps {
  open: boolean;
  title: string;
  nameA: string;
  nameB: string;
  rows: CompareRow[];
  onClose: () => void;
}

/** Side-by-side comparison of exactly two items; rows that differ are flagged in text. */
export function CompareDialog({ open, title, nameA, nameB, rows, onClose }: CompareDialogProps) {
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      wide
      actions={
        <Button variant="filled" onClick={onClose}>
          Close
        </Button>
      }
    >
      <div className="m3-table-wrap">
      <table className="m3-table" aria-label={title}>
        <thead>
          <tr>
            <th scope="col">Field</th>
            <th scope="col">{nameA}</th>
            <th scope="col">{nameB}</th>
          </tr>
        </thead>
        <tbody>
          {rows.map((r) => {
            const differs = r.aKey !== r.bKey;
            return (
              <tr key={r.label} data-differs={differs ? 'true' : undefined}>
                <th scope="row">
                  {r.label}
                  {differs ? (
                    <>
                      {' '}
                      <StatusPill tone="warning">Differs</StatusPill>
                    </>
                  ) : null}
                </th>
                <td>{r.a}</td>
                <td>{r.b}</td>
              </tr>
            );
          })}
        </tbody>
      </table>
      </div>
      <p className="m3-muted">Rows marked &ldquo;Differs&rdquo; are not the same in both.</p>
    </Dialog>
  );
}
