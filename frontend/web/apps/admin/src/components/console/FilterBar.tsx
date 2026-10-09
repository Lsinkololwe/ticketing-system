'use client';

import type { ReactNode } from 'react';
import { Button, Select, TextField } from '@pml.tickets/shared/components/m3';

export interface FilterDef {
  id: string;
  label: string;
  options: Array<{ value: string; label: string }>;
}

export interface FilterBarProps {
  searchLabel: string;
  query: string;
  onQuery: (q: string) => void;
  filters?: FilterDef[];
  values?: Record<string, string>;
  onFilter?: (id: string, value: string) => void;
  onClear?: () => void;
  /** Right-aligned actions (New user, Copy CSV...). */
  actions?: ReactNode;
}

/** Search + filter selects + "Clear filters" + actions: the toolbar above every list. */
export function FilterBar({ searchLabel, query, onQuery, filters = [], values = {}, onFilter, onClear, actions }: FilterBarProps) {
  const active = query !== '' || Object.values(values).some((v) => v && v !== 'all');
  return (
    <div className="m3-toolbar adm-filterbar" role="search" aria-label={searchLabel}>
      <TextField
        label={searchLabel}
        density="compact"
        type="search"
        value={query}
        onChange={(e) => onQuery(e.target.value)}
      />
      {filters.map((f) => (
        <Select
          key={f.id}
          label={f.label}
          density="compact"
          value={values[f.id] ?? 'all'}
          onChange={(e) => onFilter?.(f.id, e.target.value)}
        >
          <option value="all">All</option>
          {f.options.map((o) => (
            <option key={o.value} value={o.value}>
              {o.label}
            </option>
          ))}
        </Select>
      ))}
      {active && onClear ? (
        <Button variant="text" size="sm" onClick={onClear}>
          Clear filters
        </Button>
      ) : null}
      <span style={{ flex: 1 }} />
      {actions}
    </div>
  );
}
