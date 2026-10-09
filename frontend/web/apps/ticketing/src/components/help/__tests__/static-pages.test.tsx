// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { render, screen } from '@testing-library/react';

vi.mock('next/link', () => ({ default: ({ href, children, ...r }: { href: string; children: React.ReactNode }) => <a href={href} {...r}>{children}</a> }));
vi.mock('@/components/shell/SiteShell', () => ({ SiteShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));

vi.mock('@/lib/auth/session-context', () => ({ useBuyerAuth: () => ({ authenticated: false }) }));
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  usePlatformRules: () => ({
    rules: {
      refundCutoffHours: 48,
      refundPolicies: [
        { code: 'FLEXIBLE', label: 'Flexible', summary: 'Full refund until 24 hours before.', rules: [{ daysBefore: 1, percent: 100 }] },
        { code: 'STRICT', label: 'Strict', summary: 'Half back until 7 days before.', rules: [{ daysBefore: 7, percent: 50 }] },
      ],
    },
    loading: false, error: undefined, refetch: vi.fn(),
  }),
}));

import { HelpPage } from '../HelpPage';
import { LegalPage } from '@/components/LegalPage';
import { QueueClient } from '@/components/checkout/QueueClient';
import { QrCode } from '@pml.tickets/shared/components/m3';
import { NotAvailable } from '@/components/NotAvailable';

describe('static and fallback pages', () => {
  it('help lists the booking topics and the four refund policies', () => {
    render(<HelpPage />);
    expect(screen.getByRole('heading', { name: 'Need help with your booking?' })).toBeInTheDocument();
    for (const t of ['Refunds', 'Lost access to a ticket', 'Payment taken but no tickets']) expect(screen.getByText(t)).toBeInTheDocument();
    for (const p of ['Flexible', 'Strict']) expect(screen.getByText(p)).toBeInTheDocument();
    expect(screen.getByText('Full refund until 24 hours before.')).toBeInTheDocument();
    expect(screen.getByText(/close 2 days before the event/)).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to My tickets' })).toHaveAttribute('href', '/my-tickets');
    expect(screen.getByRole('link', { name: 'Back to events' })).toHaveAttribute('href', '/');
  });
  it('legal pages keep the draft banner and sections', () => {
    render(<LegalPage title="Terms of Use" updated="October 2026" sections={[{ heading: 'Your account', body: ['Be careful.'] }]} />);
    expect(screen.getByTestId('legal-draft-banner')).toHaveTextContent('Draft for legal review.');
    expect(screen.getByRole('heading', { name: 'Your account' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: /Back to Showstop/ })).toHaveAttribute('href', '/');
  });
  it('the waiting room is honest that no queue exists yet', () => {
    render(<QueueClient eventId="e1" />);
    expect(screen.getByText('Not available yet')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Back to the event' })).toHaveAttribute('href', '/events/e1');
  });
  it('renders a labelled QR image and the not-available marker', () => {
    const { container } = render(<><QrCode value="QR-TKT-1" label="Ticket QR" /><NotAvailable what="x" /></>);
    expect(screen.getByRole('img', { name: 'Ticket QR' })).toBeInTheDocument();
    expect(container.querySelector('path')?.getAttribute('d')).toMatch(/^M\d/);
  });
});
