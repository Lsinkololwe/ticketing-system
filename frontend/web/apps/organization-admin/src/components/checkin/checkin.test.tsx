import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { AttendeesView } from './AttendeesView';
import { GateView, type GateViewProps } from './GateView';

// Fixtures live in this test only.
const base: GateViewProps = {
  open: true,
  summary: { issued: 100, admitted: 40, conflicts: 2, openConflicts: 1, manualAdmissions: 3, lastCheckInAt: '2026-10-01T10:00:00Z' },
  summaryLoading: false,
  result: null,
  busy: false,
  onCheckIn: () => undefined,
  recent: [{ id: 'c1', ticketNumber: 'TK-1', method: 'MANUAL', reason: 'Phone flat', scannedAt: null, recordedAt: '2026-10-01T10:00:00Z' }],
  recentLoading: false,
  conflicts: [
    { id: 'x1', presentedCode: 'TK-9', type: 'DUPLICATE_SCAN', status: 'OPEN', detectedAt: '2026-10-01T10:00:00Z', reviewNote: null },
    { id: 'x2', presentedCode: 'TK-8', type: 'INVALID_STATE', status: 'REVIEWED', detectedAt: '2026-10-01T10:00:00Z', reviewNote: 'Bought new' },
  ],
  onReviewConflict: () => undefined,
};

describe('GateView', () => {
  it('shows summary tiles, recent check-ins and conflicts', () => {
    render(<GateView {...base} />);
    const summary = screen.getByRole('group', { name: 'Check-in summary' });
    expect(summary).toHaveTextContent('100');
    expect(summary).toHaveTextContent('2 · 1 open');
    expect(screen.getByText('Phone flat')).toBeInTheDocument();
    expect(screen.getByText('TK-9')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /Mark conflict/ })).toHaveLength(1);
  });

  it('submits a code, normalising case and QR prefix', async () => {
    const user = userEvent.setup();
    const onCheckIn = vi.fn();
    render(<GateView {...base} onCheckIn={onCheckIn} />);
    await user.type(screen.getByLabelText('Ticket number or code'), 'qr:tk-ab12');
    await user.click(screen.getByRole('button', { name: 'Check in' }));
    await waitFor(() => expect(onCheckIn).toHaveBeenCalledWith({ code: 'TK-AB12', manual: false, reason: undefined }));
    await waitFor(() => expect(screen.getByLabelText('Ticket number or code')).toHaveValue(''));
  });

  it('rejects an empty code (focus on it) and requires a reason for manual admission', async () => {
    const user = userEvent.setup();
    const onCheckIn = vi.fn();
    render(<GateView {...base} onCheckIn={onCheckIn} />);
    await user.click(screen.getByRole('button', { name: 'Check in' }));
    expect(await screen.findByText('Enter a ticket number or code')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Ticket number or code')).toHaveFocus());
    await user.click(screen.getByRole('button', { name: 'Admit manually…' }));
    await user.type(screen.getByLabelText('Ticket number or code'), 'tk-1');
    await user.click(screen.getByRole('button', { name: 'Check in' }));
    expect(await screen.findByText('A reason is required for manual admission')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Reason for manual admission')).toHaveFocus());
    await user.type(screen.getByLabelText('Reason for manual admission'), 'Phone flat');
    await user.click(screen.getByRole('button', { name: 'Check in' }));
    await waitFor(() => expect(onCheckIn).toHaveBeenCalledWith({ code: 'TK-1', manual: true, reason: 'Phone flat' }));
  });

  it('maps a server error onto the code field and submits once on double click', async () => {
    const user = userEvent.setup();
    let reject!: (e: unknown) => void;
    const onCheckIn = vi.fn(() => new Promise<void>((_, r) => (reject = r)));
    render(<GateView {...base} onCheckIn={onCheckIn} />);
    await user.type(screen.getByLabelText('Ticket number or code'), 'tk-1');
    await user.dblClick(screen.getByRole('button', { name: 'Check in' }));
    expect(onCheckIn).toHaveBeenCalledTimes(1);
    reject({ errors: [{ message: 'x', extensions: { errorCode: 'COMMAND_NOT_WELL_FORMED', classification: 'BAD_REQUEST', fields: [{ path: 'input.code', constraint: 'Pattern' }] } }] });
    await waitFor(() => expect(screen.getByLabelText('Ticket number or code')).toHaveAttribute('aria-invalid', 'true'));
  });

  it('disables entry and explains when the event is not live', () => {
    render(<GateView {...base} open={false} />);
    expect(screen.getByText(/Check-in opens when the event is live/)).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Check in' })).toBeDisabled();
  });

  it('renders the outcome card for each result', () => {
    const { rerender } = render(<GateView {...base} result={{ outcome: 'ADMITTED', message: 'Welcome', code: 'TK-1', manual: false }} />);
    expect(screen.getAllByRole('status').some((e) => e.textContent?.includes('Admitted'))).toBe(true);
    rerender(<GateView {...base} result={{ outcome: 'ALREADY_ADMITTED', message: '', code: 'TK-1', manual: false }} />);
    expect(screen.getByText('Already admitted')).toBeInTheDocument();
  });

  it('marks a conflict reviewed with a note, requiring one first', async () => {
    const user = userEvent.setup();
    const onReview = vi.fn();
    render(<GateView {...base} onReviewConflict={onReview} />);
    await user.click(screen.getByRole('button', { name: /Mark conflict TK-9 reviewed/ }));
    await user.click(screen.getByRole('button', { name: 'Mark reviewed' }));
    expect(await screen.findByText('Required')).toBeInTheDocument();
    expect(onReview).not.toHaveBeenCalled();
    await user.type(screen.getByLabelText('Reason'), 'Checked ID');
    await user.click(screen.getByRole('button', { name: 'Mark reviewed' }));
    await waitFor(() => expect(onReview).toHaveBeenCalledWith('x1', 'Checked ID'));
  });

  it('shows empty states', () => {
    render(<GateView {...base} recent={[]} conflicts={[]} />);
    expect(screen.getByText('Nobody has been admitted yet.')).toBeInTheDocument();
    expect(screen.getByText('No conflicts. Everything checked in cleanly.')).toBeInTheDocument();
  });
});

describe('AttendeesView', () => {
  const a = (o: object) => ({ id: 'a', ticketNumber: 'TK-1', buyerName: 'Ann', buyerEmail: 'ann@example.test', ticketCategoryName: 'VIP', status: 'ISSUED', purchaseDate: null, validatedAt: null, ...o });
  const props = { attendees: [a({}), a({ id: 'b', ticketNumber: 'TK-2', buyerName: 'Bob', validatedAt: '2026-10-01T10:00:00Z' })], total: 2, hasMore: false, loading: false };
  it('lists and filters attendees', () => {
    render(<AttendeesView {...props} />);
    expect(screen.getByText('Ann')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Check-in'), { target: { value: 'in' } });
    expect(screen.queryByText('Ann')).toBeNull();
    expect(screen.getByText('Bob')).toBeInTheDocument();
    fireEvent.change(screen.getByLabelText('Search attendees'), { target: { value: 'zzz' } });
    expect(screen.getByText('No attendees match.')).toBeInTheDocument();
  });
  it('notes truncated rosters, loading and errors', () => {
    const { rerender, container } = render(<AttendeesView {...props} hasMore total={500} />);
    expect(screen.getByText(/Showing the first 2 of 500/)).toBeInTheDocument();
    rerender(<AttendeesView {...props} attendees={[]} loading />);
    expect(container.querySelector('[aria-busy="true"]')).not.toBeNull();
    rerender(<AttendeesView {...props} attendees={[]} error={{ message: 'x' }} />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
});
