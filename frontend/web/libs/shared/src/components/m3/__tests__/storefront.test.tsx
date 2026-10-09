// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CtaBanner, EventCard, EventGrid, HeroBanner, OrderSummary, QrPlaceholder, SearchBar, SectionHeader, TierRow, WalletTicketCard } from '../Storefront';

describe('Storefront', () => {
  it('EventCard is a single link', () => {
    render(<EventCard title="Gala" href="/e/1" image="/i.jpg" date={{ month: 'JUN', day: '12' }} category="Music" venue="Showgrounds" priceFrom="From K 50" ribbon="Sold out" />);
    expect(screen.getAllByRole('link')).toHaveLength(1);
    expect(screen.getByRole('link', { name: /Gala/ })).toHaveAttribute('href', '/e/1');
    expect(screen.getByText('Sold out')).toBeInTheDocument();
  });
  it('EventGrid is a labelled list region', () => {
    render(<EventGrid label="Upcoming"><div>card</div></EventGrid>);
    expect(screen.getByLabelText('Upcoming')).toBeInTheDocument();
  });
  it('HeroBanner shows title and actions', () => {
    render(<HeroBanner image="/h.jpg" title="Big night" tag="Featured" actions={<button>Get tickets</button>} />);
    expect(screen.getByRole('heading', { name: 'Big night' })).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Get tickets' })).toBeInTheDocument();
  });
  it('SectionHeader level', () => {
    render(<SectionHeader title="Upcoming" level={3} eyebrow="Events" />);
    expect(screen.getByRole('heading', { level: 3, name: 'Upcoming' })).toBeInTheDocument();
  });
  it('CtaBanner', () => {
    render(<CtaBanner title="Host an event" action={<button>Apply</button>}>text</CtaBanner>);
    expect(screen.getByText('Host an event')).toBeInTheDocument();
  });
});

describe('SearchBar', () => {
  it('fires search and exposes labelled controls', async () => {
    const onSearch = vi.fn();
    render(
      <SearchBar query="" onQueryChange={() => undefined} city="" onCityChange={() => undefined} cities={[{ value: '', label: 'All cities' }]} when="" onWhenChange={() => undefined} whenOptions={[{ value: '', label: 'Any time' }]} onSearch={onSearch} />
    );
    await userEvent.click(screen.getByRole('button', { name: /search/i }));
    expect(onSearch).toHaveBeenCalled();
  });
});

describe('TierRow', () => {
  it('stepper respects min and max', async () => {
    const fn = vi.fn();
    const { rerender } = render(<TierRow name="VIP" price="K 100" available={5} quantity={0} onQuantityChange={fn} />);
    expect(screen.getByRole('button', { name: /decrease|minus|remove|fewer/i })).toBeDisabled();
    await userEvent.click(screen.getByRole('button', { name: /increase|plus|add|more/i }));
    expect(fn).toHaveBeenCalledWith(1);
    rerender(<TierRow name="VIP" price="K 100" available={2} quantity={2} onQuantityChange={fn} />);
    expect(screen.getByRole('button', { name: /increase|plus|add|more/i })).toBeDisabled();
  });
  it('sold out tier cannot be increased', () => {
    render(<TierRow name="GA" price="K 10" available={0} quantity={0} onQuantityChange={() => undefined} />);
    expect(screen.getByRole('button', { name: /increase|plus|add|more/i })).toBeDisabled();
  });
});

describe('OrderSummary / tickets', () => {
  it('OrderSummary shows lines and total', () => {
    render(<OrderSummary lines={[{ label: '2 x VIP', value: 'K 200' }]} total="K 200" action={<button>Pay</button>} />);
    expect(screen.getByText('2 x VIP')).toBeInTheDocument();
    expect(screen.getByText('Total')).toBeInTheDocument();
  });
  it('QrPlaceholder is labelled', () => {
    render(<QrPlaceholder value="abc" />);
    expect(screen.getByRole('img', { name: 'QR code' })).toBeInTheDocument();
  });
  it('WalletTicketCard shows code and status text', () => {
    render(<WalletTicketCard eventTitle="Gala" dateLine="12 Jun" venue="V" tier="VIP" code="TKT-1" status="ISSUED" />);
    expect(screen.getByText('TKT-1')).toBeInTheDocument();
    expect(screen.getByText('Issued')).toBeInTheDocument();
  });
});
