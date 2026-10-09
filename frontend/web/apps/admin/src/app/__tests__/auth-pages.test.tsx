import { describe, expect, it, vi, beforeEach } from 'vitest';
import { render, screen } from '@testing-library/react';

const getSession = vi.fn();
const redirect = vi.fn((url: string) => {
  throw new Error(`NEXT_REDIRECT:${url}`);
});

vi.mock('next/navigation', () => ({ redirect: (u: string) => redirect(u), useRouter: () => ({ push: vi.fn() }) }));
vi.mock('@/lib/bff', () => ({ bff: { getSession: () => getSession() }, STAFF_ACCESS_ROLES: [] }));

import LoginPage, { loginMessage } from '../login/page';
import UnauthorizedPage from '../unauthorized/page';
import Home from '../page';

const renderLogin = async (search: Record<string, string> = {}) =>
  render(await LoginPage({ searchParams: Promise.resolve(search) }));

beforeEach(() => {
  vi.clearAllMocks();
  getSession.mockResolvedValue(null);
});

describe('login', () => {
  it('renders the staff SSO card with the single h1', async () => {
    await renderLogin();
    expect(screen.getByRole('heading', { level: 1, name: /platform admin/i })).toBeInTheDocument();
    expect(screen.getByTestId('admin-login-sso-button')).toHaveTextContent('Sign in with SSO');
    expect(screen.getByText(/Staff only/)).toBeInTheDocument();
  });
  it('starts the BFF flow with the validated return path', async () => {
    await renderLogin({ next: '/finance/payouts' });
    expect(screen.getByTestId('admin-login-sso-button')).toHaveAttribute('href', '/api/auth/start?next=%2Ffinance%2Fpayouts');
  });
  it.each(['https://evil.example/x', '//evil.example', '/api/auth/logout'])('refuses to carry an unsafe return target %s', async (next) => {
    await renderLogin({ next });
    expect(screen.getByTestId('admin-login-sso-button')).toHaveAttribute('href', '/api/auth/start?next=%2F');
  });
  it('shows a failed sign-in banner for BFF error codes', async () => {
    await renderLogin({ error: 'LOGIN_FAILED' });
    expect(screen.getByRole('alert')).toHaveTextContent(/did not work/);
  });
  it('maps FORBIDDEN and SERVICE_UNAVAILABLE to specific copy', () => {
    expect(loginMessage('FORBIDDEN')).toMatch(/staff role/);
    expect(loginMessage('SERVICE_UNAVAILABLE')).toMatch(/temporarily unavailable/);
    expect(loginMessage(undefined)).toBeNull();
  });
  it('sends an existing session on to the requested page', async () => {
    getSession.mockResolvedValue({ sessionId: 's' });
    await expect(LoginPage({ searchParams: Promise.resolve({ next: '/users' }) })).rejects.toThrow('NEXT_REDIRECT:/users');
  });
});

describe('root', () => {
  it('goes to the dashboard when signed in and to login otherwise', async () => {
    getSession.mockResolvedValue({ sessionId: 's' });
    await expect(Home()).rejects.toThrow('NEXT_REDIRECT:/dashboard');
    getSession.mockResolvedValue(null);
    await expect(Home()).rejects.toThrow('NEXT_REDIRECT:/login');
  });
});

describe('unauthorized', () => {
  it('explains the missing role and signs out through a POST form to the BFF', () => {
    const { container } = render(<UnauthorizedPage />);
    expect(screen.getByRole('heading', { level: 1, name: 'You do not have access' })).toBeInTheDocument();
    const form = container.querySelector('form')!;
    expect(form).toHaveAttribute('method', 'post');
    expect(form).toHaveAttribute('action', '/api/auth/logout');
    expect(screen.getByTestId('unauthorized-switch-account-button')).toHaveAttribute('type', 'submit');
  });
});
