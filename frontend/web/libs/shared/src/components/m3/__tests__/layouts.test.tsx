// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AppShell } from '../../../layouts/AppShell';
import { AuthLayout } from '../../../layouts/AuthLayout';
import { Brand } from '../../../layouts/Brand';
import { SiteHeader } from '../../../layouts/PublicLayout';

describe('AuthLayout', () => {
  it('renders the single h1, description, footer and brand', () => {
    render(<AuthLayout product="MyTicketZM" console="Organizer" title="Sign in" description="Use your phone" footer={<span>Terms</span>}><button>Go</button></AuthLayout>);
    expect(screen.getAllByRole('heading', { level: 1 })).toHaveLength(1);
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getByText('Use your phone')).toBeInTheDocument();
    expect(screen.getByText('Terms')).toBeInTheDocument();
    expect(screen.getByText('Organizer')).toBeInTheDocument();
  });
});

describe('Brand', () => {
  it('is decorative', () => {
    const { container } = render(<Brand />);
    expect(container.firstChild).toHaveAttribute('aria-hidden', 'true');
  });
});

describe('AppShell', () => {
  it('has a skip link, navigation and main landmark', () => {
    render(
      <AppShell drawer={{ brand: 'Showstop', sections: [{ id: 's', items: [{ id: 'a', label: 'Overview', icon: 'dashboard', href: '/', current: true }] }] }}>
        <h1>Page</h1>
      </AppShell>
    );
    expect(screen.getByRole('main')).toBeInTheDocument();
    expect(screen.getAllByRole('navigation').length).toBeGreaterThan(0);
    expect(screen.getByRole('link', { name: /skip/i })).toBeInTheDocument();
    expect(screen.getByRole('heading', { name: 'Page' })).toBeInTheDocument();
  });
});

describe('SiteHeader', () => {
  it('lists main links with aria-current', () => {
    render(<SiteHeader name="MyTicketZM" links={[{ id: 'a', label: 'Discover', href: '/', current: true }, { id: 'b', label: 'My tickets', href: '/t' }]} />);
    expect(screen.getByRole('navigation', { name: 'Main' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Discover' })).toHaveAttribute('aria-current', 'page');
    expect(screen.getByRole('link', { name: 'MyTicketZM home' })).toBeInTheDocument();
  });
});

import userEvent from '@testing-library/user-event';
import { vi } from 'vitest';
import { PublicLayout, SiteFooter, SiteTool } from '../../../layouts/PublicLayout';

describe('PublicLayout', () => {
  it('has skip link to main, header, banner, footer', () => {
    render(
      <PublicLayout header={<header>H</header>} banner={<div>Resume checkout</div>} footer={<SiteFooter columns={[{ heading: 'Help', links: [{ label: 'FAQ', href: '/faq' }] }]} legal="2026" />}>
        <p>body</p>
      </PublicLayout>
    );
    expect(screen.getByRole('link', { name: 'Skip to content' })).toHaveAttribute('href', '#m3-main');
    expect(screen.getByRole('main')).toHaveAttribute('id', 'm3-main');
    expect(screen.getByText('Resume checkout')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'FAQ' })).toHaveAttribute('href', '/faq');
  });
});

describe('SiteTool', () => {
  it('button when onClick, link when href', async () => {
    const fn = vi.fn();
    const { rerender } = render(<SiteTool label="Notifications" onClick={fn}>x</SiteTool>);
    await userEvent.click(screen.getByRole('button', { name: 'Notifications' }));
    expect(fn).toHaveBeenCalled();
    rerender(<SiteTool label="Sign in" href="/login">x</SiteTool>);
    expect(screen.getByRole('link', { name: 'Sign in' })).toHaveAttribute('href', '/login');
  });
});
