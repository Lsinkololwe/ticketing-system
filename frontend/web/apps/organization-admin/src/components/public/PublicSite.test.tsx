import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { FeaturesView, LandingView } from './PublicSite';

describe('public pages', () => {
  it('landing: hero, CTAs, features and steps', () => {
    render(<LandingView />);
    expect(screen.getByRole('heading', { level: 1, name: 'Sell out your next event' })).toBeInTheDocument();
    expect(screen.getByTestId('landing-apply')).toHaveAttribute('href', '/login');
    expect(screen.getByTestId('landing-signin')).toHaveAttribute('href', '/login');
    expect(screen.getAllByRole('listitem').length).toBeGreaterThanOrEqual(9);
    expect(screen.getByRole('list', { name: 'How it works' })).toBeInTheDocument();
  });
  it('header and footer navigation', () => {
    render(<FeaturesView />);
    expect(screen.getByRole('heading', { level: 1, name: 'Features' })).toBeInTheDocument();
    const nav = screen.getByRole('navigation', { name: 'Main' });
    expect(nav.querySelector('a[href="/features"]')).toHaveAttribute('aria-current', 'page');
    expect(nav.querySelector('a[href="/login"]')).toBeTruthy();
    expect(screen.getByTestId('features-apply')).toHaveAttribute('href', '/login');
    expect(screen.getByRole('link', { name: 'Contact support' })).toHaveAttribute('href', 'mailto:support@myticket.zm');
  });
});
