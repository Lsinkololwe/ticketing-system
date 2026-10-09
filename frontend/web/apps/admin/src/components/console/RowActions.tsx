'use client';

import { Button, RowMenu, type MenuEntry } from '@pml.tickets/shared/components/m3';

/**
 * The prototype's table action cell: one tonal primary button (Open, Quick view, Edit, Review) and an
 * overflow menu with everything else. `name` makes the accessible names unique per row.
 */
export function RowActions({ primary, name, items = [] }: { primary?: { label: string; onSelect: () => void; disabled?: boolean }; name: string; items?: MenuEntry[] }) {
  return (
    <span className="m3-row">
      {primary ? (
        <Button variant="tonal" size="sm" disabled={primary.disabled} aria-label={`${primary.label} ${name}`} onClick={primary.onSelect}>
          {primary.label}
        </Button>
      ) : null}
      {items.length > 0 ? <RowMenu label={`More actions for ${name}`} items={items} /> : null}
    </span>
  );
}
