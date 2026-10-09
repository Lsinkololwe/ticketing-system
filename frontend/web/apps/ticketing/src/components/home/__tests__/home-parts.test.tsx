// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useZodForm } from '@pml.tickets/shared/forms';
import type { EventCardRow } from '@pml.tickets/shared';
import { EventsSection } from '../EventsSection';
import { DiscoverSearch } from '../DiscoverSearch';
import { FeaturedCarousel } from '../FeaturedCarousel';
import { BecauseRail, TrendingRail } from '../Rails';
import { CategoryTiles } from '../CategoryTiles';
import { EMPTY_FILTERS, activeCount, filterSchema, moreCount, toDiscoverFilter, whenRange } from '../filters';

const ev = (o: Partial<EventCardRow> & { id: string }): EventCardRow => ({
  title: `Event ${o.id}`,
  description: '',
  status: 'PUBLISHED',
  featured: false,
  eventDateTime: '2026-11-14T15:00:00Z',
  endDateTime: '2026-11-14T19:00:00Z',
  cityName: 'Lusaka',
  locationName: 'Showgrounds',
  bannerImageUrl: null,
  galleryImages: null,
  organizerName: 'Org',
  soldTickets: 10,
  totalCapacity: 100,
  availableTickets: 90,
  minTicketPrice: 120,
  maxTicketPrice: 600,
  currency: 'ZMW',
  soldOut: false,
  category: { id: 'c1', name: 'Music' },
  ...o,
});

const cats = [
  { id: 'c1', name: 'Music' },
  { id: 'c2', name: 'Comedy' },
];

function section(over: Partial<React.ComponentProps<typeof EventsSection>> = {}) {
  const props = {
    filters: EMPTY_FILTERS,
    onChange: vi.fn(),
    onClear: vi.fn(),
    categories: cats,
    events: [ev({ id: '1' }), ev({ id: '2', soldOut: true, availableTickets: 0 }), ev({ id: '3', availableTickets: 5 })],
    total: 3,
    hasNext: false,
    loading: false,
    error: null,
    onRetry: vi.fn(),
    onLoadMore: vi.fn(),
    ...over,
  };
  render(<EventsSection {...props} />);
  return props;
}

describe('EventsSection', () => {
  it('renders the populated grid with ribbons, links and the showing count', () => {
    section();
    const list = screen.getByRole('list', { name: 'Upcoming events' });
    expect(within(list).getAllByRole('listitem')).toHaveLength(3);
    expect(screen.getByRole('link', { name: /Event 1/ })).toHaveAttribute('href', '/events/1');
    expect(screen.getByText('Sold out')).toBeInTheDocument();
    expect(screen.getByText('Selling fast')).toBeInTheDocument();
    expect(screen.getByText(/Showing 3 of 3 events/)).toBeInTheDocument();
    expect(screen.queryByRole('button', { name: 'Load more' })).toBeNull();
  });
  it('chips filter by category and All clears it', () => {
    const p = section();
    fireEvent.click(screen.getByRole('button', { name: 'Comedy' }));
    expect(p.onChange).toHaveBeenCalledWith({ categoryId: 'c2' });
    fireEvent.click(screen.getByRole('button', { name: 'All' }));
    expect(p.onChange).toHaveBeenCalledWith({ categoryId: '' });
  });
  it('offers Load more when there is a next page', () => {
    const p = section({ hasNext: true, total: 12 });
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }));
    expect(p.onLoadMore).toHaveBeenCalled();
  });
  it('shows the designed empty state with Clear filters', () => {
    const p = section({ events: [], total: 0 });
    expect(screen.getByText('No events match your filters')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Clear filters' }).pop()!);
    expect(p.onClear).toHaveBeenCalled();
  });
  it('shows a busy skeleton grid while loading the first page', () => {
    section({ events: [], loading: true });
    expect(screen.getByLabelText('Loading events')).toHaveAttribute('aria-busy', 'true');
  });
  it('shows the error state with retry', () => {
    const p = section({ events: [], error: { message: 'Network request failed' } as never });
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
    const retry = screen.queryByTestId('error-state-retry');
    if (retry) {
      fireEvent.click(retry);
      expect(p.onRetry).toHaveBeenCalled();
    }
  });
  it('reports active filters', () => {
    section({ filters: { ...EMPTY_FILTERS, city: 'Ndola', min: '10' } });
    expect(screen.getByText('2 filters active')).toBeInTheDocument();
  });
});

describe('server-side sort', () => {
  it('maps the Sort control to the catalog order', async () => {
    const { SORT_TO_API } = await import('../filters');
    expect(SORT_TO_API).toEqual({ '': 'SOONEST', hot: 'POPULAR', price_asc: 'PRICE_ASC', price_desc: 'PRICE_DESC' });
  });
});

describe('DiscoverSearch', () => {
  function setup(more = false) {
    const p = { onWhen: vi.fn(), onSearch: vi.fn(), onClear: vi.fn(), onToggleMore: vi.fn() };
    let formRef!: ReturnType<typeof useZodForm<typeof filterSchema>>;
    function Host() {
      formRef = useZodForm(filterSchema, { defaultValues: EMPTY_FILTERS });
      return <DiscoverSearch form={formRef as never} cities={[{ id: 'c-lsk', name: 'Lusaka' }, { id: 'c-ndl', name: 'Ndola' }]} categories={cats} moreOpen={more} {...p} />;
    }
    render(<Host />);
    return { ...p, form: () => formRef };
  }
  it('has What, Where and When controls and submits a search', async () => {
    const p = setup();
    expect(screen.getByRole('search', { name: 'Search and filter events' })).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('What'), { target: { value: 'jazz' } });
    expect(p.form().getValues('q')).toBe('jazz');
    fireEvent.change(screen.getByLabelText('Where'), { target: { value: 'c-ndl' } });
    expect(p.form().getValues('city')).toBe('c-ndl');
    fireEvent.change(screen.getByLabelText('When'), { target: { value: 'week' } });
    expect(p.onWhen).toHaveBeenCalledWith('week');
    fireEvent.click(screen.getByRole('button', { name: 'Search' }));
    await waitFor(() => expect(p.onSearch).toHaveBeenCalled());
  });
  it('toggles and fills the More filters panel', () => {
    const p = setup(true);
    fireEvent.click(screen.getByRole('button', { name: /More filters/ }));
    expect(p.onToggleMore).toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Max price (K)'), { target: { value: '200' } });
    expect(p.form().getValues('max')).toBe('200');
    fireEvent.click(screen.getByRole('button', { name: 'Clear filters' }));
    expect(p.onClear).toHaveBeenCalled();
  });
});

describe('FeaturedCarousel', () => {
  it('shows one slide at a time and moves with the arrows and dots', () => {
    render(<FeaturedCarousel events={[ev({ id: '1', title: 'First' }), ev({ id: '2', title: 'Second', soldOut: true })]} />);
    expect(screen.getByRole('region', { name: 'Featured events' })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'First' })).toBeVisible();
    fireEvent.click(screen.getByRole('button', { name: 'Next slide' }));
    expect(screen.getByRole('heading', { name: 'Second' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Go to slide 1' }));
    expect(screen.getByRole('button', { name: 'Go to slide 1' })).toHaveAttribute('aria-current', 'true');
    expect(screen.getAllByRole('link', { name: /Event details/ })[0]).toHaveAttribute('href', '/events/1');
  });
  it('renders nothing without featured events', () => {
    const { container } = render(<FeaturedCarousel events={[]} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('TrendingRail', () => {
  it('ranks events and links to them', () => {
    render(<TrendingRail events={[ev({ id: '1', soldTickets: 1200 }), ev({ id: '2' })]} />);
    expect(screen.getByRole('list', { name: 'Trending events, ranked' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Number 1 trending: Event 1/ })).toHaveAttribute('href', '/events/1');
    expect(screen.getByText('1,200 tickets sold')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Scroll Trending events, ranked left' })).toBeDisabled();
  });
});

describe('filters', () => {
  it('maps UI filters to the discovery filter', () => {
    expect(toDiscoverFilter({ ...EMPTY_FILTERS, q: ' jazz ', city: 'c-ndl', from: '2026-11-01', to: '2026-11-30', min: '10', max: '90', categoryId: 'c1' })).toEqual({
      searchQuery: 'jazz',
      categoryId: 'c1',
      cityId: 'c-ndl',
      startDate: '2026-11-01T00:00:00+02:00',
      endDate: '2026-11-30T23:59:59+02:00',
      minPrice: 10,
      maxPrice: 90,
    });
    expect(toDiscoverFilter(EMPTY_FILTERS)).toEqual({});
  });
  it('counts filters and resolves date windows', () => {
    expect(activeCount({ ...EMPTY_FILTERS, q: 'x', city: 'y' })).toBe(2);
    expect(moreCount({ ...EMPTY_FILTERS, categoryId: 'c1', max: '9' })).toBe(2);
    const now = Date.parse('2026-10-02T09:00:00Z');
    expect(whenRange('week', '', '', now)).toEqual({ from: '2026-10-02', to: '2026-10-09' });
    expect(whenRange('month', '', '', now).to).toBe('2026-10-31');
    expect(whenRange('', 'a', 'b', now)).toEqual({ from: '', to: '' });
  });
});

describe('BecauseRail', () => {
  it('names the booked event and lists the recommendations', () => {
    render(<BecauseRail titles={new Map([['b1', 'Lusaka Sunset']])} items={[{ reason: 'BECAUSE_YOU_BOOKED', basedOnEventId: 'b1', event: ev({ id: 'r1' }) }]} />);
    expect(screen.getByRole('heading', { name: 'Because you booked Lusaka Sunset' })).toBeInTheDocument();
    expect(screen.getByText('Event r1')).toBeInTheDocument();
  });
  it('stays out of the way when the catalog only has trending to offer', () => {
    const { container } = render(<BecauseRail titles={new Map()} items={[{ reason: 'TRENDING', basedOnEventId: null, event: ev({ id: 'r2' }) }]} />);
    expect(container).toBeEmptyDOMElement();
  });
});

describe('CategoryTiles', () => {
  it('shows the platform tile image when the category has one', () => {
    const pick = vi.fn();
    const { container } = render(<CategoryTiles onPick={pick} categories={[{ id: 'c1', name: 'Music', imageUrl: 'https://img.test/music.jpg' }, { id: 'c2', name: 'Comedy', imageUrl: null }]} />);
    expect(container.querySelectorAll('img')).toHaveLength(1);
    fireEvent.click(screen.getByRole('button', { name: 'Comedy' }));
    expect(pick).toHaveBeenCalledWith('c2');
  });
});
