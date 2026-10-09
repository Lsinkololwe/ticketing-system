import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { UnavailableView } from './UnavailableView';
import { WelcomeView } from './WelcomeView';

describe('WelcomeView', () => {
  it('greets and starts setup', () => {
    const onStart = vi.fn();
    render(<WelcomeView firstName="Mutinta" onStart={onStart} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Welcome, Mutinta' })).toBeInTheDocument();
    expect(screen.getAllByTestId('feature-item')).toHaveLength(3);
    fireEvent.click(screen.getByTestId('get-started-button'));
    expect(onStart).toHaveBeenCalled();
  });
});

describe('UnavailableView', () => {
  it('offers retry and support', () => {
    const onRetry = vi.fn();
    render(<UnavailableView attempts={1} retrying={false} onRetry={onRetry} />);
    expect(screen.getByRole('alert')).toHaveTextContent('Still unavailable');
    fireEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(onRetry).toHaveBeenCalled();
    expect(screen.getByRole('link', { name: 'Contact support' })).toHaveAttribute('href', 'mailto:support@myticket.zm');
  });
});
