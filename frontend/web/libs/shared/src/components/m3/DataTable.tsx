'use client';

import { useId, type ReactNode } from 'react';
import { IconButton } from './Button';
import { Icon } from './icons';
import { Checkbox } from './Fields';
import { EmptyState, Skeleton } from './Display';
import { cx } from './utils';

/* -------------------------------------------------------------- Pagination */

export interface PaginationProps {
  /** 1-based current page. */
  page: number;
  pageSize: number;
  total: number;
  onPageChange: (page: number) => void;
  onPageSizeChange?: (size: number) => void;
  pageSizeOptions?: number[];
  label?: string;
}

function pageWindow(page: number, pages: number): Array<number | 'gap'> {
  if (pages <= 5) return Array.from({ length: pages }, (_, i) => i + 1);
  const set = new Set([1, pages, page - 1, page, page + 1]);
  const list = [...set].filter((n) => n >= 1 && n <= pages).sort((a, b) => a - b);
  const out: Array<number | 'gap'> = [];
  list.forEach((n, i) => {
    if (i > 0 && n - list[i - 1] > 1) out.push('gap');
    out.push(n);
  });
  return out;
}

/** Rows-per-page, range text and numbered pager. Page buttons carry aria-current. */
export function Pagination({
  page,
  pageSize,
  total,
  onPageChange,
  onPageSizeChange,
  pageSizeOptions = [6, 12, 24, 50],
  label = 'Pagination',
}: PaginationProps) {
  const id = useId();
  const pages = Math.max(1, Math.ceil(total / pageSize));
  const from = total === 0 ? 0 : (page - 1) * pageSize + 1;
  const to = Math.min(total, page * pageSize);
  const go = (n: number) => onPageChange(Math.min(pages, Math.max(1, n)));
  return (
    <nav className="m3-pagination" aria-label={label}>
      {onPageSizeChange ? (
        <div className="m3-pagination__size">
          <label htmlFor={`${id}-size`}>Rows per page</label>
          <select id={`${id}-size`} value={pageSize} onChange={(e) => onPageSizeChange(Number(e.target.value))}>
            {pageSizeOptions.map((n) => (
              <option key={n} value={n}>
                {n}
              </option>
            ))}
          </select>
        </div>
      ) : null}
      <span aria-live="polite">
        {from}&ndash;{to} of {total}
      </span>
      <div className="m3-pagination__pages">
        <PagerButton icon="first-page" label="First page" disabled={page <= 1} onClick={() => go(1)} />
        <PagerButton icon="chevron-left" label="Previous page" disabled={page <= 1} onClick={() => go(page - 1)} />
        {pageWindow(page, pages).map((n, i) =>
          n === 'gap' ? (
            <span key={`gap-${i}`} aria-hidden="true">
              &hellip;
            </span>
          ) : (
            <button
              key={n}
              type="button"
              className="m3-pagination__btn m3-state"
              aria-current={n === page ? 'page' : undefined}
              aria-label={`Page ${n}`}
              onClick={() => go(n)}
            >
              {n}
            </button>
          )
        )}
        <PagerButton icon="chevron-right" label="Next page" disabled={page >= pages} onClick={() => go(page + 1)} />
        <PagerButton icon="last-page" label="Last page" disabled={page >= pages} onClick={() => go(pages)} />
      </div>
    </nav>
  );
}

function PagerButton({
  icon,
  label,
  disabled,
  onClick,
}: {
  icon: 'first-page' | 'chevron-left' | 'chevron-right' | 'last-page';
  label: string;
  disabled: boolean;
  onClick: () => void;
}) {
  return (
    <button
      type="button"
      className="m3-pagination__btn m3-state"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
    >
      <Icon name={icon} />
    </button>
  );
}

/* --------------------------------------------------------------- DataTable */

export type SortDirection = 'asc' | 'desc';
export interface SortState {
  columnId: string;
  direction: SortDirection;
}

export interface DataColumn<T> {
  id: string;
  header: ReactNode;
  cell: (row: T) => ReactNode;
  sortable?: boolean;
  align?: 'start' | 'end';
  /** Primary cell of the row: rendered as the row header (<th scope=row>) for screen readers. */
  rowHeader?: boolean;
}

export interface DataTableProps<T> {
  /** Accessible name of the table (visually hidden caption). */
  caption: string;
  columns: DataColumn<T>[];
  rows: T[];
  getRowId: (row: T) => string;
  loading?: boolean;
  /** Number of skeleton rows while loading. */
  skeletonRows?: number;
  /** Shown instead of rows when set. Include a retry action. */
  error?: ReactNode;
  /** Shown when there are no rows. */
  empty?: ReactNode;
  sort?: SortState | null;
  onSortChange?: (sort: SortState) => void;
  selectable?: boolean;
  selectedIds?: ReadonlySet<string>;
  onSelectionChange?: (ids: Set<string>) => void;
  /**
   * Dedicated action column, rendered per row. This is the ONLY way to act on
   * a row: rows are never clickable. Use Button, IconButton or RowMenu.
   */
  rowActions?: (row: T) => ReactNode;
  actionsHeader?: string;
  stickyHeader?: boolean;
  density?: 'medium' | 'compact';
  pagination?: PaginationProps;
}

/**
 * Data table. Sortable headers are buttons (aria-sort on the th), selection is
 * a real checkbox column, and actions live in a dedicated column. There is no
 * `onRowClick` by design.
 */
export function DataTable<T>({
  caption,
  columns,
  rows,
  getRowId,
  loading,
  skeletonRows = 5,
  error,
  empty,
  sort,
  onSortChange,
  selectable,
  selectedIds,
  onSelectionChange,
  rowActions,
  actionsHeader = 'Actions',
  stickyHeader,
  density = 'medium',
  pagination,
}: DataTableProps<T>) {
  const selected = selectedIds ?? new Set<string>();
  const ids = rows.map(getRowId);
  const allSelected = ids.length > 0 && ids.every((id) => selected.has(id));
  const someSelected = ids.some((id) => selected.has(id));
  const colCount = columns.length + (selectable ? 1 : 0) + (rowActions ? 1 : 0);

  const toggleAll = () => {
    const next = new Set(selected);
    if (allSelected) ids.forEach((id) => next.delete(id));
    else ids.forEach((id) => next.add(id));
    onSelectionChange?.(next);
  };
  const toggleOne = (id: string) => {
    const next = new Set(selected);
    if (next.has(id)) next.delete(id);
    else next.add(id);
    onSelectionChange?.(next);
  };
  const nextSort = (col: DataColumn<T>): SortState => ({
    columnId: col.id,
    direction: sort?.columnId === col.id && sort.direction === 'asc' ? 'desc' : 'asc',
  });

  let body: ReactNode;
  if (error) {
    body = (
      <tr>
        <td colSpan={colCount}>{error}</td>
      </tr>
    );
  } else if (loading) {
    body = Array.from({ length: skeletonRows }, (_, r) => (
      <tr key={`sk-${r}`} aria-hidden="true">
        {Array.from({ length: colCount }, (_, c) => (
          <td key={c}>
            <Skeleton width={c === 0 ? '70%' : '50%'} />
          </td>
        ))}
      </tr>
    ));
  } else if (rows.length === 0) {
    body = (
      <tr>
        <td colSpan={colCount}>{empty ?? <EmptyState title="Nothing to show" />}</td>
      </tr>
    );
  } else {
    body = rows.map((row) => {
      const id = getRowId(row);
      const isSelected = selected.has(id);
      return (
        <tr key={id} aria-selected={selectable ? isSelected : undefined}>
          {selectable ? (
            <td className="m3-table__select">
              <Checkbox
                aria-label={`Select row ${id}`}
                checked={isSelected}
                onChange={() => toggleOne(id)}
              />
            </td>
          ) : null}
          {columns.map((col) =>
            col.rowHeader ? (
              <th key={col.id} scope="row" data-align={col.align === 'end' ? 'end' : undefined}>
                {col.cell(row)}
              </th>
            ) : (
              <td key={col.id} data-align={col.align === 'end' ? 'end' : undefined}>
                {col.cell(row)}
              </td>
            )
          )}
          {rowActions ? (
            <td>
              <div className="m3-table__actions">{rowActions(row)}</div>
            </td>
          ) : null}
        </tr>
      );
    });
  }

  return (
    <div>
      <div
        className="m3-table-wrap"
        data-sticky={stickyHeader ? 'true' : undefined}
        aria-busy={loading || undefined}
        // Keyboard users must be able to scroll a wide table (WCAG 2.1.1).
        role="region"
        aria-label={caption}
        tabIndex={0}
      >
        <table
          className="m3-table"
          data-sticky={stickyHeader ? 'true' : undefined}
          data-density={density === 'compact' ? 'compact' : undefined}
        >
          <caption className="m3-sr-only">{caption}</caption>
          <thead>
            <tr>
              {selectable ? (
                <th scope="col" className="m3-table__select">
                  <Checkbox
                    aria-label="Select all rows"
                    checked={allSelected}
                    indeterminate={!allSelected && someSelected}
                    onChange={toggleAll}
                    disabled={rows.length === 0}
                  />
                </th>
              ) : null}
              {columns.map((col) => {
                const active = sort?.columnId === col.id;
                return (
                  <th
                    key={col.id}
                    scope="col"
                    data-align={col.align === 'end' ? 'end' : undefined}
                    aria-sort={col.sortable ? (active ? (sort!.direction === 'asc' ? 'ascending' : 'descending') : 'none') : undefined}
                  >
                    {col.sortable ? (
                      <button type="button" className="m3-table__sort" onClick={() => onSortChange?.(nextSort(col))}>
                        {col.header}
                        <Icon
                          name={active ? (sort!.direction === 'asc' ? 'arrow-up' : 'arrow-down') : 'sort'}
                          className={cx('m3-icon')}
                        />
                      </button>
                    ) : (
                      col.header
                    )}
                  </th>
                );
              })}
              {rowActions ? (
                <th scope="col" data-align="end">
                  <span className="m3-sr-only">{actionsHeader}</span>
                </th>
              ) : null}
            </tr>
          </thead>
          <tbody>{body}</tbody>
        </table>
      </div>
      {pagination && !error ? <Pagination {...pagination} /> : null}
    </div>
  );
}

export { IconButton as TableIconButton };
