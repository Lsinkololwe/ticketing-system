import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { LoginView } from './LoginView';

describe('LoginView', () => {
  const fns = () => ({ onSignIn: vi.fn(), onRegister: vi.fn() });
  it('prompt: sign in and apply actions', () => {
    const f = fns();
    render(<LoginView mode="prompt" {...f} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Sign in to your console' })).toBeInTheDocument();
    fireEvent.click(screen.getByTestId('login-signin-button'));
    fireEvent.click(screen.getByTestId('login-apply-button'));
    expect(f.onSignIn).toHaveBeenCalled();
    expect(f.onRegister).toHaveBeenCalled();
  });
  it('error: maps known codes and offers retry', () => {
    const f = fns();
    render(<LoginView mode="error" authError="AccessDenied" {...f} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Access was denied');
    fireEvent.click(screen.getByTestId('login-retry-button'));
    expect(f.onSignIn).toHaveBeenCalled();
    expect(screen.getByTestId('login-register-button')).toBeInTheDocument();
  });
  it('unknown error falls back to generic copy', () => {
    render(<LoginView mode="error" authError="Weird" {...fns()} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Sign-in did not complete');
  });
  it('loading and registered states', () => {
    const { rerender } = render(<LoginView mode="checking" {...fns()} />);
    expect(screen.getByTestId('login-loading')).toHaveTextContent('Checking your session');
    rerender(<LoginView mode="redirecting" {...fns()} />);
    expect(screen.getByTestId('login-loading')).toHaveTextContent('Taking you to sign in');
    rerender(<LoginView mode="registered" {...fns()} />);
    expect(screen.getByRole('status')).toHaveTextContent('Your account was created');
  });
});
