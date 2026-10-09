'use client';

import { useMemo, useState, type ReactNode } from 'react';
import { Button, Card, CardHeader, DataTable, EmptyState, ErrorState, type DataColumn } from '@pml.tickets/shared/components/m3';
import { FilterBar, type FilterDef } from '@/components/console';

export interface CrudCardProps<T> {
  title: string;
  subtitle: string;
  singular: string;
  plural: string;
  rows: T[];
  loading: boolean;
  error?: Error;
  onRetry: () => void;
  columns: DataColumn<T>[];
  getRowId: (row: T) => string;
  searchText: (row: T) => string;
  filters?: Array<FilterDef & { match: (row: T, value: string) => boolean }>;
  rowActions: (row: T) => ReactNode;
  onNew: () => void;
  canEdit: boolean;
  pageSize?: number;
}

/** A titled card with search, optional filters, "New ..." button and an action-column table (client paging). */
export function CrudCard<T>({
  title,
  subtitle,
  singular,
  plural,
  rows,
  loading,
  error,
  onRetry,
  columns,
  getRowId,
  searchText,
  filters = [],
  rowActions,
  onNew,
  canEdit,
  pageSize: pageSizeProp = 8,
}: CrudCardProps<T>) {
  const [pageSize, setPageSize] = useState(pageSizeProp);
  const [query, setQuery] = useState('');
  const [values, setValues] = useState<Record<string, string>>({});
  const [page, setPage] = useState(0);

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows.filter(
      (r) =>
        (!q || searchText(r).toLowerCase().includes(q)) &&
        filters.every((f) => !values[f.id] || values[f.id] === 'all' || f.match(r, values[f.id])),
    );
  }, [rows, query, values, filters, searchText]);
  const pageRows = shown.slice(page * pageSize, page * pageSize + pageSize);
  const hasFilter = query !== '' || Object.values(values).some((v) => v && v !== 'all');

  return (
    <Card as="section" aria-label={title}>
      <CardHeader title={title} subtitle={subtitle} />
      <FilterBar
        searchLabel={`Search ${plural}`}
        query={query}
        onQuery={(q) => {
          setQuery(q);
          setPage(0);
        }}
        filters={filters.map(({ match: _match, ...f }) => f)}
        values={values}
        onFilter={(id, v) => {
          setValues((s) => ({ ...s, [id]: v }));
          setPage(0);
        }}
        onClear={() => {
          setQuery('');
          setValues({});
          setPage(0);
        }}
        actions={
          <Button variant="filled" size="sm" icon="add" disabled={!canEdit} onClick={onNew}>
            {`New ${singular}`}
          </Button>
        }
      />
      <DataTable
        caption={title}
        columns={columns}
        rows={pageRows}
        getRowId={getRowId}
        loading={loading && rows.length === 0}
        error={error && rows.length === 0 ? <ErrorState error={error} onRetry={onRetry} /> : undefined}
        empty={
          <EmptyState
            title={hasFilter ? `No ${plural} match your search.` : `No ${plural} yet.`}
            action={
              !hasFilter && canEdit ? (
                <Button variant="tonal" onClick={onNew}>{`New ${singular}`}</Button>
              ) : undefined
            }
          />
        }
        rowActions={rowActions}
        pagination={{ page: page + 1, pageSize, total: shown.length, onPageChange: (n) => setPage(n - 1), onPageSizeChange: (n) => { setPageSize(n); setPage(0); }, pageSizeOptions: [8, 16, 32] }}
      />
    </Card>
  );
}
