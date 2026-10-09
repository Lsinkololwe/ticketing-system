import { fireEvent, render, screen, within } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { EventsListView } from './EventsListView';
import type { OrgEventRow } from '@/lib/api/events';

const ev = (o: Partial<OrgEventRow>): OrgEventRow => ({
  id: 'e1', title: 'Alpha Fest', status: 'PUBLISHED', eventDateTime: '2026-11-14T17:00:00Z', endDateTime: null,
  locationName: 'Grounds', cityName: 'Lusaka', bannerImageUrl: null, totalCapacity: 200, soldTickets: 50, revenue: '5000', currency: 'ZMW',
  category: { id: 'c1', name: 'Music' }, ticketTiers: [{ id: 't', isActive: true }], ...o,
});
const events = [ev({}), ev({ id: 'e2', title: 'Beta Draft', status: 'DRAFT', category: { id: 'c2', name: 'Comedy' } }), ev({ id: 'e3', title: 'Gamma', status: 'APPROVED' })];
const props = { events, loading: false, canWrite: true, onAction: vi.fn(), onBulkPublish: vi.fn(), now: new Date('2026-10-01') };

describe('EventsListView', () => {
  it('renders header, create CTA, status chips with counts and rows', () => {
    render(<EventsListView {...props} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Events' })).toBeInTheDocument();
    expect(screen.getAllByRole('link', { name: /Create event/ })[0]).toHaveAttribute('href', '/events/new');
    expect(screen.getByRole('button', { name: /^All · 3/ })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Alpha Fest' })).toHaveAttribute('href', '/events/e1');
    expect(screen.getAllByRole('link', { name: 'Open' })).toHaveLength(3);
  });

  it('filters by status chip and search', () => {
    render(<EventsListView {...props} />);
    fireEvent.click(screen.getByRole('button', { name: /^Draft · 1/ }));
    expect(screen.queryByText('Alpha Fest')).toBeNull();
    expect(screen.getByText('Beta Draft')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /^All/ }));
    fireEvent.change(screen.getByLabelText('Search events'), { target: { value: 'gamma' } });
    expect(screen.getByText('Gamma')).toBeInTheDocument();
    expect(screen.queryByText('Alpha Fest')).toBeNull();
  });

  it('row menu offers lifecycle actions for the status and calls back', () => {
    const onAction = vi.fn();
    render(<EventsListView {...props} onAction={onAction} />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Gamma' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Publish' }));
    expect(onAction).toHaveBeenCalledWith('publish', expect.objectContaining({ id: 'e3' }));
  });

  it('bulk publishes only approved selected events', () => {
    const onBulkPublish = vi.fn();
    render(<EventsListView {...props} onBulkPublish={onBulkPublish} />);
    const row = (t: string) => screen.getByText(t).closest('tr') as HTMLElement;
    fireEvent.click(within(row('Gamma')).getByRole('checkbox'));
    fireEvent.click(within(row('Beta Draft')).getByRole('checkbox'));
    fireEvent.click(screen.getByRole('button', { name: /Publish selected \(1\)/ }));
    expect(onBulkPublish).toHaveBeenCalledWith(['e3']);
  });

  it('shows empty, loading and error states', () => {
    const { rerender } = render(<EventsListView {...props} events={[]} />);
    expect(screen.getByText('No events yet')).toBeInTheDocument();
    rerender(<EventsListView {...props} events={[]} loading />);
    expect(document.querySelector('.m3-skeleton')).not.toBeNull();
    rerender(<EventsListView {...props} events={[]} error={{ message: 'Boom' }} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Boom');
  });

  it('uses server totals for the chips, reports filter changes and pages with the cursor', () => {
    const onFiltersChange = vi.fn();
    const onLoadMore = vi.fn();
    render(<EventsListView {...props} counts={{ all: 40, byStatus: { DRAFT: 7, PUBLISHED: 33 } }} hasMore onLoadMore={onLoadMore} onFiltersChange={onFiltersChange} />);
    expect(screen.getByRole('button', { name: /^All · 40/ })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: /^Draft · 7/ }));
    expect(onFiltersChange).toHaveBeenCalledWith(expect.objectContaining({ status: 'DRAFT' }));
    fireEvent.click(screen.getByRole('button', { name: 'Load more events' }));
    expect(onLoadMore).toHaveBeenCalled();
  });
  it('hides write actions for roles that cannot write', () => {
    render(<EventsListView {...props} canWrite={false} />);
    expect(screen.queryByRole('link', { name: /Create event/ })).toBeNull();
  });
  it('"Duplicate a past event" picks a finished event and starts the duplicate flow', () => {
    const onAction = vi.fn();
    const past = ev({ id: 'p1', title: 'Heroes Day Festival', status: 'COMPLETED', eventDateTime: '2026-07-06T10:00:00Z' });
    render(<EventsListView {...props} onAction={onAction} pastEvents={[past]} />);
    fireEvent.click(screen.getByRole('button', { name: 'Duplicate a past event' }));
    const dlg = screen.getByRole('dialog');
    expect(within(dlg).getByRole('button', { name: 'Continue' })).toBeDisabled();
    fireEvent.change(within(dlg).getByLabelText('Past event'), { target: { value: 'p1' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Continue' }));
    expect(onAction).toHaveBeenCalledWith('duplicate', past);
  });
  it('no past events: the dialog says so', () => {
    render(<EventsListView {...props} pastEvents={[]} />);
    fireEvent.click(screen.getByRole('button', { name: 'Duplicate a past event' }));
    expect(screen.getByText('No past events yet')).toBeInTheDocument();
  });
  it('hides the duplicate button for roles that cannot write', () => {
    render(<EventsListView {...props} canWrite={false} pastEvents={[]} />);
    expect(screen.queryByRole('button', { name: 'Duplicate a past event' })).toBeNull();
  });
});
