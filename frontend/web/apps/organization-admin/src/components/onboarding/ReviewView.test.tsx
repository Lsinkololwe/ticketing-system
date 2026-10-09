import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { ReviewView, type ReviewViewProps } from './ReviewView';

const base = (o: Partial<ReviewViewProps> = {}): ReviewViewProps => ({
  loading: false,
  summary: { type: 'Business', name: 'Fixture Org', tpin: '1002345678', registrationNumber: '', phone: '+260971234567', email: 'a@b.co', address: 'Lusaka, Lusaka' },
  docs: [{ type: 'NATIONAL_ID', name: 'National ID', fileName: 'id.pdf', uploaded: true }],
  missingFields: [], onBack: vi.fn(), onEdit: vi.fn(), onSubmit: vi.fn(async () => undefined), ...o,
});

describe('ReviewView', () => {
  it('asks for the confirmation, then submits', async () => {
    const p = base();
    render(<ReviewView {...p} />);
    const user = userEvent.setup();
    expect(screen.getByText('Fixture Org')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Submit for review' }));
    expect(await screen.findByText('Confirm the details are accurate to submit')).toBeInTheDocument();
    expect(p.onSubmit).not.toHaveBeenCalled();
    await waitFor(() => expect(screen.getByRole('checkbox')).toHaveFocus());
    await user.click(screen.getByRole('checkbox'));
    await user.click(screen.getByRole('button', { name: 'Submit for review' }));
    await waitFor(() => expect(p.onSubmit).toHaveBeenCalledTimes(1));
  });

  it('blocks on missing fields and documents and shows the resubmit label', async () => {
    const p = base({ missingFields: ['Phone'], docs: [{ type: 'X', name: 'Tax certificate', uploaded: false }], resubmit: true });
    render(<ReviewView {...p} />);
    expect(screen.getByText(/Missing: Phone/)).toBeInTheDocument();
    expect(screen.getByText('Missing')).toBeInTheDocument();
    const btn = screen.getByRole('button', { name: 'Resubmit for review' });
    await userEvent.click(btn);
    expect(p.onSubmit).not.toHaveBeenCalled();
  });

  it('maps a server refusal to the banner and guards double submit', async () => {
    let calls = 0;
    const onSubmit = vi.fn(async () => {
      calls += 1;
      await new Promise((r) => setTimeout(r, 30));
      throw { graphQLErrors: [{ message: 'x', extensions: { errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION' } }] };
    });
    render(<ReviewView {...base({ onSubmit })} />);
    const user = userEvent.setup();
    await user.click(screen.getByRole('checkbox'));
    await user.dblClick(screen.getByRole('button', { name: 'Submit for review' }));
    expect(await screen.findByText('Those tickets have sold out.')).toBeInTheDocument();
    expect(calls).toBe(1);
  });

  it('navigates back/edit and shows loading', async () => {
    const p = base();
    const { rerender } = render(<ReviewView {...p} />);
    await userEvent.click(screen.getByTestId('review-edit'));
    await userEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(p.onEdit).toHaveBeenCalled();
    expect(p.onBack).toHaveBeenCalled();
    rerender(<ReviewView {...base({ loading: true })} />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
  });
});
