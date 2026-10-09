import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => '/',
  useSearchParams: () => new URLSearchParams(),
}));
const lookup = vi.fn();
vi.mock('@pml.tickets/shared/api/admin/modules/identity-admin', async (orig) => ({
  ...(await orig<object>()),
  useBuyerLookup: () => ({ lookup, loading: false }),
}));
import { BuyerLookupDialog } from '../BuyerLookupDialog';

describe('Buyer lookup', () => {
  it('requires a value, finds a buyer with masked contacts and opens the profile', async () => {
    lookup.mockResolvedValue({ id: 'u7', fullName: 'Buyer One', email: 'buyer@example.test', phoneNumber: '+260971234567', roles: ['CUSTOMER'], accountStatus: 'ACTIVE' });
    renderConsole(<BuyerLookupDialog open onClose={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Find account' }));
    expect(await screen.findByText('Enter an email address')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'buyer@example.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Find account' }));
    const res = await screen.findByTestId('lookup-result');
    expect(lookup).toHaveBeenCalledWith('email', 'buyer@example.test');
    expect(within(res).queryByText('buyer@example.test')).not.toBeInTheDocument();
    fireEvent.click(within(res).getByRole('button', { name: 'Open full profile' }));
    await waitFor(() => expect(push).toHaveBeenCalledWith('/user/u7'));
  });

  it('shows the empty result', async () => {
    lookup.mockResolvedValue(null);
    renderConsole(<BuyerLookupDialog open onClose={vi.fn()} />);
    fireEvent.change(screen.getByLabelText('Email address'), { target: { value: 'x@y.test' } });
    fireEvent.click(screen.getByRole('button', { name: 'Find account' }));
    expect(await screen.findByText('No account found')).toBeInTheDocument();
  });
});
