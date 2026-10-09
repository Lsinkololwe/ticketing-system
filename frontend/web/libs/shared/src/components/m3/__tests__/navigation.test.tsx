// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { BottomNav, Breadcrumbs, NavigationDrawer, NavigationRail, PageHeader, TopAppBar } from '../Navigation';

const items = [
  { id: 'a', label: 'Overview', icon: 'dashboard' as const, href: '/', current: true },
  { id: 'b', label: 'Events', icon: 'calendar' as const, href: '/events', badge: 3 },
  { id: 'c', label: 'Sign out', icon: 'logout' as const, onSelect: vi.fn() },
];

describe('NavigationDrawer', () => {
  it('is a labelled nav with one aria-current item and badge', () => {
    render(<NavigationDrawer brand="Showstop" sections={[{ id: 's', label: 'Main', items }]} />);
    const nav = screen.getByRole('navigation');
    expect(nav).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Overview/ })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: /Events/ })).not.toHaveAttribute('aria-current');
    expect(screen.getByText('3')).toBeInTheDocument();
  });
  it('actions without href render buttons', async () => {
    render(<NavigationDrawer brand="Showstop" sections={[{ id: 's', items }]} />);
    await userEvent.click(screen.getByRole('button', { name: /Sign out/ }));
    expect(items[2].onSelect).toHaveBeenCalled();
  });
  it('supports a custom link component', () => {
    const L = ({ href, children, ...r }: { href: string; children?: React.ReactNode }) => <a data-custom href={href} {...r}>{children}</a>;
    render(<NavigationDrawer brand="X" linkAs={L} sections={[{ id: 's', items: items.slice(0, 2) }]} />);
    expect(document.querySelectorAll('[data-custom]').length).toBeGreaterThan(0);
  });
});

describe('Rail and BottomNav', () => {
  it('rail renders all destinations', () => {
    render(<NavigationRail items={items.slice(0, 2)} />);
    expect(screen.getAllByRole('link')).toHaveLength(2);
  });
  it('bottom nav marks current', () => {
    render(<BottomNav items={items.slice(0, 2)} />);
    expect(screen.getByRole('link', { name: /Overview/ })).toHaveAttribute('aria-current', 'page');
  });
});

describe('TopAppBar / PageHeader / Breadcrumbs', () => {
  it('top bar shows title, subtitle and actions', () => {
    render(<TopAppBar title="Events" subtitle="All of them" actions={<button>New</button>} />);
    expect(screen.getByRole('heading', { name: 'Events' })).toBeInTheDocument();
    expect(screen.getByText('All of them')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'New' })).toBeInTheDocument();
  });
  it('breadcrumbs mark the last crumb as current', () => {
    render(<Breadcrumbs items={[{ label: 'Events', href: '/events' }, { label: 'Gala' }]} />);
    expect(screen.getByRole('navigation', { name: /breadcrumb/i })).toBeInTheDocument();
    expect(screen.getByText('Gala')).toHaveAttribute('aria-current', 'page');
  });
  it('page header back button', async () => {
    const back = vi.fn();
    render(<PageHeader title="Gala" onBack={back} />);
    await userEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(back).toHaveBeenCalled();
  });
});
