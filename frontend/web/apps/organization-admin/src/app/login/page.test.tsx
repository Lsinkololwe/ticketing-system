import { render, screen, waitFor } from '@testing-library/react';
import { beforeEach, describe, expect, it, vi } from 'vitest';

let session: { data: unknown; isPending: boolean } = { data: null, isPending: false };
let query = '';
const replace = vi.fn();
vi.mock('@/lib/session', async (orig) => ({ ...(await orig<typeof import('@/lib/session')>()), useSession: () => session }));
vi.mock('next/navigation', () => ({ useRouter: () => ({ replace }), useSearchParams: () => new URLSearchParams(query) }));
import LoginPage from './page';

const assign = vi.fn();
beforeEach(() => {
  assign.mockReset();
  replace.mockReset();
  query = '';
  session = { data: null, isPending: false };
  Object.defineProperty(window, 'location', { value: { assign }, writable: true });
});

describe('login page (BFF start)', () => {
  it('signed out: goes straight to the BFF start route', async () => {
    render(<LoginPage />);
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/api/auth/start?next=%2Fdashboard'));
  });
  it('keeps a same-origin next and drops unsafe ones', async () => {
    query = 'next=/finance';
    render(<LoginPage />);
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/api/auth/start?next=%2Ffinance'));
  });
  it('unsafe next falls back to the dashboard', async () => {
    query = 'next=//evil.example';
    render(<LoginPage />);
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/api/auth/start?next=%2Fdashboard'));
  });
  it('error code shows the designed error state with retry', async () => {
    query = 'error=FORBIDDEN';
    render(<LoginPage />);
    expect(screen.getByRole('alert')).toHaveTextContent('not an organizer account');
    screen.getByTestId('login-retry-button').click();
    await waitFor(() => expect(assign).toHaveBeenCalledWith('/api/auth/start?next=%2Fdashboard'));
  });
  it('already signed in: continues to /welcome', async () => {
    session = { data: { user: { id: 'u' } }, isPending: false };
    render(<LoginPage />);
    await waitFor(() => expect(replace).toHaveBeenCalledWith('/welcome'));
    expect(assign).not.toHaveBeenCalled();
  });
});
