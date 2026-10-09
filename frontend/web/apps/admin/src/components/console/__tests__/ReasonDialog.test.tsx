import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { ReasonDialog } from '../ReasonDialog';

const base = { open: true, title: 'Reject event?', confirmLabel: 'Reject event', onClose: vi.fn() };

describe('ReasonDialog', () => {
  it('blocks confirmation until a reason of the minimum length is given', async () => {
    const onConfirm = vi.fn();
    render(<ReasonDialog {...base} onConfirm={onConfirm} />);
    fireEvent.click(screen.getByRole('button', { name: 'Reject event' }));
    expect(await screen.findByText(/at least 5 characters/)).toBeInTheDocument();
    expect(onConfirm).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Reason'), { target: { value: 'Venue not confirmed' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reject event' }));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith('Venue not confirmed'));
  });
  it('allows an empty comment when the rule is optional', async () => {
    const onConfirm = vi.fn();
    render(<ReasonDialog {...base} required={false} reasonLabel="Comments" onConfirm={onConfirm} />);
    expect(screen.getByLabelText('Comments (optional)')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Reject event' }));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith(''));
  });
  it('renders nothing when closed and closes on Cancel', () => {
    const onClose = vi.fn();
    const { rerender } = render(<ReasonDialog {...base} open={false} onConfirm={vi.fn()} />);
    expect(screen.queryByRole('dialog')).toBeNull();
    rerender(<ReasonDialog {...base} onClose={onClose} onConfirm={vi.fn()} />);
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    expect(onClose).toHaveBeenCalled();
  });
});
