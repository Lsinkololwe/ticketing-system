'use client';

import type { ReactNode } from 'react';

export interface TileSpec {
  id: string;
  label: ReactNode;
  value: ReactNode;
  /** Makes the tile a button (opens the queue it counts). */
  onSelect?: () => void;
  'aria-label'?: string;
}

/** Tonal count tiles (the prototype's `.tiles`): label over a number, optionally a button. */
export function Tiles({ items, label }: { items: TileSpec[]; label?: string }) {
  return (
    <div className="adm-tiles" role="group" aria-label={label}>
      {items.map((t) =>
        t.onSelect ? (
          <button key={t.id} type="button" className="adm-tile m3-state" onClick={t.onSelect} aria-label={t['aria-label']}>
            <span>{t.label}</span>
            <b>{t.value}</b>
          </button>
        ) : (
          <div key={t.id} className="adm-tile">
            <span>{t.label}</span>
            <b>{t.value}</b>
          </div>
        )
      )}
    </div>
  );
}
