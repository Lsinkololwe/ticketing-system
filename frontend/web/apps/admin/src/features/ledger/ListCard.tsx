'use client';

import { useEffect, useMemo, useState, type ReactNode } from 'react';
import {
  BulkBar,
  Button,
  Card,
  CardHeader,
  DataTable,
  EmptyState,
  ErrorState,
  useSnackbar,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import { FilterBar, type FilterDef } from '@/components/console';
import { csvText } from '@/lib/format';

export interface ListCardProps<T> {
  title: string;
  subtitle?: ReactNode;
  caption: string;
  /** Buttons shown beside the title (New entry, Start run ...). */
  toolbar?: ReactNode;
  rows: T[];
  columns: Array<DataColumn<T>>;
  getRowId: (row: T) => string;
  rowActions?: (row: T) => ReactNode;
  searchLabel: string;
  /** Text searched for the query; omit to hide the search field. */
  searchText?: (row: T) => string;
  /** Server-side search: when set the query is reported here and rows are not filtered locally. */
  onSearchQuery?: (query: string) => void;
  filters?: Array<FilterDef & { match?: (row: T, value: string) => boolean }>;
  /** Called when a filter changes so server-filtered lists can refetch. */
  onFilterChange?: (values: Record<string, string>) => void;
  loading?: boolean;
  error?: Error;
  onRetry?: () => void;
  empty: { title: string; description?: string; action?: ReactNode };
  pageSize?: number;
  /** Server paging: rows are already one page. */
  serverPage?: { page: number; total: number; onPage: (page: number) => void };
  csv?: { name: string; header: string[]; row: (row: T) => Array<string | number | null | undefined> };
  selectable?: boolean;
  /** Bulk actions shown when rows are selected. */
  bulk?: (ids: string[], clear: () => void) => ReactNode;
  level?: 2 | 3;
}

/** Card with title, filter bar, table, paging and optional CSV/bulk actions: the list pattern of the ledger and transactions pages. */
export function ListCard<T>({
  title,
  subtitle,
  caption,
  toolbar,
  rows,
  columns,
  getRowId,
  rowActions,
  searchLabel,
  searchText,
  onSearchQuery,
  filters = [],
  onFilterChange,
  loading,
  error,
  onRetry,
  empty,
  pageSize = 10,
  serverPage,
  csv,
  selectable,
  bulk,
}: ListCardProps<T>) {
  const snackbar = useSnackbar();
  const [query, setQuery] = useState('');
  const [values, setValues] = useState<Record<string, string>>({});
  const [page, setPage] = useState(1);
  const [selected, setSelected] = useState<Set<string>>(new Set());

  useEffect(() => {
    onFilterChange?.(values);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [values]);

  const filtered = useMemo(() => {
    const q = query.trim().toLowerCase();
    return rows.filter((r) => {
      if (q && searchText && !onSearchQuery && !searchText(r).toLowerCase().includes(q)) return false;
      return filters.every((f) => {
        const v = values[f.id];
        return !v || v === 'all' || !f.match || f.match(r, v);
      });
    });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [rows, query, values]);

  const pages = Math.max(1, Math.ceil(filtered.length / pageSize));
  const current = serverPage ? serverPage.page : Math.min(page, pages);
  const visible = serverPage ? filtered : filtered.slice((current - 1) * pageSize, current * pageSize);
  const total = serverPage ? serverPage.total : filtered.length;
  const filtering = query !== '' || Object.values(values).some((v) => v && v !== 'all');

  const copyCsv = async () => {
    if (!csv) return;
    const text = csvText([csv.header, ...filtered.map(csv.row)]);
    try {
      await navigator.clipboard.writeText(text);
      snackbar.show(`${csv.name} copied`);
    } catch {
      snackbar.show('Could not copy to the clipboard');
    }
  };

  const clearAll = () => {
    setQuery('');
    onSearchQuery?.('');
    setValues({});
    setPage(1);
  };

  return (
    <Card>
      <CardHeader title={title} subtitle={subtitle} actions={toolbar} />
      <FilterBar
        searchLabel={searchLabel}
        query={query}
        onQuery={(q) => {
          setQuery(q);
          setPage(1);
          onSearchQuery?.(q);
        }}
        filters={filters}
        values={values}
        onFilter={(id, v) => {
          setValues((prev) => ({ ...prev, [id]: v }));
          setPage(1);
          serverPage?.onPage(1);
        }}
        onClear={clearAll}
        actions={
          csv ? (
            <Button variant="tonal" size="sm" icon="copy" onClick={copyCsv}>
              Copy CSV
            </Button>
          ) : undefined
        }
      />
      {bulk && selected.size > 0 ? (
        <BulkBar count={selected.size}>
          {bulk([...selected], () => setSelected(new Set()))}
          <Button variant="text" size="sm" onClick={() => setSelected(new Set())}>
            Clear selection
          </Button>
        </BulkBar>
      ) : null}
      <DataTable
        caption={caption}
        columns={columns}
        rows={visible}
        getRowId={getRowId}
        loading={loading && rows.length === 0}
        error={error && rows.length === 0 ? <ErrorState error={error} onRetry={onRetry} /> : undefined}
        empty={
          <EmptyState
            title={filtering ? 'Nothing matches these filters' : empty.title}
            description={filtering ? 'Clear the filters to see everything.' : empty.description}
            action={filtering ? <Button variant="text" onClick={clearAll}>Clear filters</Button> : empty.action}
          />
        }
        rowActions={rowActions}
        selectable={selectable}
        selectedIds={selected}
        onSelectionChange={setSelected}
        pagination={
          total > pageSize
            ? {
                page: current,
                pageSize,
                total,
                onPageChange: (p) => (serverPage ? serverPage.onPage(p) : setPage(p)),
                label: `${title} pages`,
              }
            : undefined
        }
      />
    </Card>
  );
}
