// @vitest-environment jsdom
import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button } from '../Button';
import { DataTable, Pagination, type DataColumn } from '../DataTable';

interface Row { id: string; name: string; sold: number }
const rows: Row[] = [
  { id: 'a', name: 'Alpha', sold: 3 },
  { id: 'b', name: 'Beta', sold: 9 },
];
const columns: DataColumn<Row>[] = [
  { id: 'name', header: 'Name', cell: (r) => r.name, sortable: true, rowHeader: true },
  { id: 'sold', header: 'Sold', cell: (r) => r.sold, align: 'end' },
];

describe('DataTable', () => {
  it('renders a captioned table with row headers', () => {
    render(<DataTable caption="Events" columns={columns} rows={rows} getRowId={(r) => r.id} />);
    const t = screen.getByRole('table', { name: 'Events' });
    expect(within(t).getAllByRole('row')).toHaveLength(3);
    expect(within(t).getByRole('rowheader', { name: 'Alpha' })).toBeInTheDocument();
  });
  it('rows are NOT clickable; actions live in a dedicated column', async () => {
    const act = vi.fn();
    render(
      <DataTable
        caption="Events"
        columns={columns}
        rows={rows}
        getRowId={(r) => r.id}
        rowActions={(r) => <Button size="sm" onClick={() => act(r.id)}>Edit {r.name}</Button>}
      />
    );
    expect(screen.getByRole('columnheader', { name: 'Actions' })).toBeInTheDocument();
    const row = screen.getByRole('rowheader', { name: 'Alpha' }).closest('tr') as HTMLElement;
    expect(row).not.toHaveAttribute('tabindex');
    expect(row).not.toHaveAttribute('role', 'button');
    await userEvent.click(row);
    expect(act).not.toHaveBeenCalled();
    await userEvent.click(screen.getByRole('button', { name: 'Edit Beta' }));
    expect(act).toHaveBeenCalledWith('b');
  });
  it('sortable headers are buttons and expose aria-sort', async () => {
    const onSort = vi.fn();
    render(
      <DataTable caption="E" columns={columns} rows={rows} getRowId={(r) => r.id} sort={{ columnId: 'name', direction: 'asc' }} onSortChange={onSort} />
    );
    expect(screen.getByRole('columnheader', { name: /Name/ })).toHaveAttribute('aria-sort', 'ascending');
    await userEvent.click(within(screen.getByRole('columnheader', { name: /Name/ })).getByRole('button'));
    expect(onSort).toHaveBeenCalledWith({ columnId: 'name', direction: 'desc' });
  });
  it('selection uses real checkboxes', async () => {
    const onSel = vi.fn();
    render(<DataTable caption="E" columns={columns} rows={rows} getRowId={(r) => r.id} selectable selectedIds={new Set()} onSelectionChange={onSel} />);
    const boxes = screen.getAllByRole('checkbox');
    expect(boxes.length).toBe(3);
    await userEvent.click(boxes[1]);
    expect(onSel).toHaveBeenCalledWith(new Set(['a']));
  });
  it('loading, empty and error states', () => {
    const { rerender } = render(<DataTable caption="E" columns={columns} rows={[]} getRowId={(r) => r.id} loading />);
    expect(screen.getByRole('table').closest('[aria-busy]')).toHaveAttribute('aria-busy', 'true');
    rerender(<DataTable caption="E" columns={columns} rows={[]} getRowId={(r) => r.id} empty={<p>Nothing here</p>} />);
    expect(screen.getByText('Nothing here')).toBeInTheDocument();
    rerender(<DataTable caption="E" columns={columns} rows={[]} getRowId={(r) => r.id} error={<p>Failed</p>} empty={<p>Nothing here</p>} />);
    expect(screen.getByText('Failed')).toBeInTheDocument();
    expect(screen.queryByText('Nothing here')).toBeNull();
  });
});

describe('Pagination', () => {
  it('marks the current page and changes page', async () => {
    const fn = vi.fn();
    render(<Pagination page={2} pageSize={10} total={50} onPageChange={fn} />);
    expect(screen.getByRole('button', { name: 'Page 2' })).toHaveAttribute('aria-current', 'page');
    await userEvent.click(screen.getByRole('button', { name: 'Page 3' }));
    expect(fn).toHaveBeenCalledWith(3);
  });
});
