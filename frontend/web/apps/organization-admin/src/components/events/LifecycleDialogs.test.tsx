import { act, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';

const api = { submit: vi.fn(async () => ({})), publish: vi.fn(async () => ({})), unpublish: vi.fn(), reschedule: vi.fn(), cancel: vi.fn(async () => ({})), duplicate: vi.fn(), remove: vi.fn() };
const payouts = vi.fn(async () => [] as unknown[]);
vi.mock('@/lib/api/events', () => ({ useEventLifecycle: () => api, useEventPayoutsLazy: () => payouts }));
vi.mock('@/lib/api/platform', () => ({
  useReferenceList: () => ({ items: [{ name: 'Venue unavailable' }, { name: 'Other' }] }),
  usePlatformRules: () => ({ rules: { rescheduleLimit: 3 } }),
}));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn() }) }));

import { useLifecycle, type LifecycleEvent } from './LifecycleDialogs';

const ev = (o: Partial<LifecycleEvent> = {}): LifecycleEvent => ({ id: 'e1', title: 'Fest', status: 'APPROVED', eventDateTime: '2026-11-01T10:00:00Z', soldTickets: 0, locationName: 'V', totalCapacity: 10, ticketTiers: [{ id: 't', isActive: true }], ...o });

function Host({ event, action, onDone }: { event: LifecycleEvent; action: Parameters<ReturnType<typeof useLifecycle>['run']>[0]; onDone?: () => void }) {
  const { run, dialogs } = useLifecycle(onDone);
  return (
    <>
      <button onClick={() => void run(action, event)}>go</button>
      {dialogs}
    </>
  );
}
const open = async (el: React.ReactElement) => {
  render(<SnackbarProvider>{el}</SnackbarProvider>);
  await act(async () => { fireEvent.click(screen.getByText('go')); });
};

describe('lifecycle dialogs', () => {
  it('blocks publish when the event is not approved', async () => {
    await open(<Host event={ev({ status: 'DRAFT' })} action="publish" />);
    expect(screen.getByRole('dialog', { name: 'Cannot publish yet' })).toBeInTheDocument();
    expect(api.publish).not.toHaveBeenCalled();
  });

  it('publishes after confirmation', async () => {
    const onDone = vi.fn();
    await open(<Host event={ev()} action="publish" onDone={onDone} />);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Publish' })); });
    expect(api.publish).toHaveBeenCalledWith('e1');
    expect(onDone).toHaveBeenCalledWith('publish', 'e1');
  });

  it('refuses to unpublish when tickets are sold and offers reschedule', async () => {
    await open(<Host event={ev({ status: 'PUBLISHED', soldTickets: 4 })} action="unpublish" />);
    expect(screen.getByText('Cannot unpublish')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Reschedule…' }));
    expect(screen.getByText(/Reschedule “Fest”/)).toBeInTheDocument();
  });

  it('explains cancellation is blocked while a payout is open', async () => {
    payouts.mockResolvedValueOnce([{ requestId: 'PO-1', status: 'PENDING', requestedAmount: '100', currency: 'ZMW' }]);
    await open(<Host event={ev({ status: 'PUBLISHED' })} action="cancel" />);
    expect(screen.getByText('Cannot cancel while a payout is open')).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Go to payouts' })).toHaveAttribute('href', '/finance');
  });

  it('cancels with a reason', async () => {
    await open(<Host event={ev({ status: 'PUBLISHED', soldTickets: 3 })} action="cancel" />);
    await act(async () => { fireEvent.click(screen.getByRole('button', { name: 'Cancel event' })); });
    await waitFor(() => expect(api.cancel).toHaveBeenCalledWith('e1', 'Venue unavailable'));
  });
});
