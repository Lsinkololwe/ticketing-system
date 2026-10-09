import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { CancelEventDialog, DuplicateEventDialog, RescheduleDialog } from './LifecycleForms';
import { PromoDialog } from './PromoDialog';
import { TierDialog } from './TierDialog';

const tiers = [{ id: 't1', name: 'General' }] as never;
const serverError = (code: string, fields?: Array<{ path: string; constraint: string }>) =>
  Object.assign(new Error('Server said no'), { graphQLErrors: [{ message: 'Server said no', extensions: { errorCode: code, fields } }] });

describe('TierDialog (react-hook-form + zod)', () => {
  it('shows messages, focuses the first invalid field and does not submit', async () => {
    const onSave = vi.fn();
    const user = userEvent.setup();
    render(<TierDialog onClose={() => undefined} onSave={onSave} />);
    await user.click(screen.getByRole('button', { name: 'Add tier' }));
    expect(await screen.findByText('Required')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Name')).toHaveFocus());
    expect(onSave).not.toHaveBeenCalled();
  });

  it('submits the Kwacha payload and closes', async () => {
    const onSave = vi.fn(async () => undefined);
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<TierDialog onClose={onClose} onSave={onSave} />);
    await user.type(screen.getByLabelText('Name'), 'VIP');
    await user.type(screen.getByLabelText('Price'), '500');
    await user.type(screen.getByLabelText('Quantity'), '5');
    await user.click(screen.getByRole('button', { name: 'Add tier' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ name: 'VIP', price: '500.00', quantity: 5, accessCode: null }));
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('requires an access code for hidden tiers', async () => {
    const user = userEvent.setup();
    render(<TierDialog onClose={() => undefined} onSave={vi.fn()} />);
    await user.type(screen.getByLabelText('Name'), 'VIP');
    await user.type(screen.getByLabelText('Price'), '10');
    await user.type(screen.getByLabelText('Quantity'), '5');
    await user.click(screen.getByRole('switch', { name: /Hidden tier/ }));
    await user.click(screen.getByRole('button', { name: 'Add tier' }));
    expect((await screen.findAllByText(/access code of 4 or more/)).length).toBeGreaterThan(0);
  });

  it('maps a server error onto the dialog and keeps it open', async () => {
    const onSave = vi.fn(async () => { throw serverError('INTERNAL_ERROR'); });
    const onClose = vi.fn();
    const user = userEvent.setup();
    render(<TierDialog onClose={onClose} onSave={onSave} />);
    await user.type(screen.getByLabelText('Name'), 'VIP');
    await user.type(screen.getByLabelText('Price'), '10');
    await user.type(screen.getByLabelText('Quantity'), '5');
    await user.click(screen.getByRole('button', { name: 'Add tier' }));
    await waitFor(() => expect(onSave).toHaveBeenCalled());
    expect(onClose).not.toHaveBeenCalled();
  });

  it('guards against a double submit', async () => {
    let release: () => void = () => undefined;
    const onSave = vi.fn(() => new Promise<void>((r) => { release = r; }));
    const user = userEvent.setup();
    render(<TierDialog onClose={() => undefined} onSave={onSave} />);
    await user.type(screen.getByLabelText('Name'), 'VIP');
    await user.type(screen.getByLabelText('Price'), '10');
    await user.type(screen.getByLabelText('Quantity'), '5');
    const btn = screen.getByRole('button', { name: 'Add tier' });
    await user.click(btn);
    await user.click(btn);
    release();
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
  });
});

describe('PromoDialog', () => {
  it('rejects short and duplicate codes, and maps a field violation from the server', async () => {
    const onSave = vi.fn(async () => { throw serverError('COMMAND_NOT_WELL_FORMED', [{ path: 'input.code', constraint: 'Pattern' }]); });
    const user = userEvent.setup();
    render(<PromoDialog tiers={tiers} existingCodes={['SUNSET10']} onClose={() => undefined} onSave={onSave} />);
    await user.clear(screen.getByLabelText('Code'));
    await user.type(screen.getByLabelText('Code'), 'ab');
    await user.click(screen.getByRole('button', { name: 'Create code' }));
    expect(await screen.findAllByText('Use 4 to 20 letters or numbers')).not.toHaveLength(0);
    await waitFor(() => expect(screen.getByLabelText('Code')).toHaveFocus());

    await user.clear(screen.getByLabelText('Code'));
    await user.type(screen.getByLabelText('Code'), 'sunset10');
    await user.click(screen.getByRole('button', { name: 'Create code' }));
    expect((await screen.findAllByText('This event already has that code')).length).toBeGreaterThan(0);

    await user.clear(screen.getByLabelText('Code'));
    await user.type(screen.getByLabelText('Code'), 'NEWCODE');
    await user.click(screen.getByRole('button', { name: 'Create code' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ code: 'NEWCODE', discountType: 'PERCENTAGE', discountValue: '10' }), true);
    await waitFor(() => expect(screen.getByLabelText('Code')).toHaveAttribute('aria-invalid', 'true'));
  });
});

describe('lifecycle forms', () => {
  it('reschedule needs a future start and a reason', async () => {
    const onSubmit = vi.fn(async () => undefined);
    const user = userEvent.setup();
    render(<RescheduleDialog title="Fest" rescheduleLimit={3} sold={0} currentStart="2026-11-01T10:00:00Z" onClose={() => undefined} onSubmit={onSubmit} />);
    await user.click(screen.getByRole('button', { name: 'Reschedule' }));
    expect(await screen.findAllByText('Choose a future start')).not.toHaveLength(0);
    await waitFor(() => expect(screen.getByLabelText('New start')).toHaveFocus());
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('cancel requires a message when the reason is Other, then submits', async () => {
    const onSubmit = vi.fn(async () => undefined);
    const user = userEvent.setup();
    render(<CancelEventDialog title="Fest" sold={3} reasons={['Venue unavailable', 'Other']} onClose={() => undefined} onSubmit={onSubmit} />);
    await user.selectOptions(screen.getByLabelText('Reason'), 'Other');
    await user.click(screen.getByRole('button', { name: 'Cancel event' }));
    expect(await screen.findAllByText('Add a short message for ticket holders')).not.toHaveLength(0);
    await user.type(screen.getByLabelText('Message to ticket holders'), 'Artist ill');
    await user.click(screen.getByRole('button', { name: 'Cancel event' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledWith({ reason: 'Other', note: 'Artist ill' }, expect.anything()));
  });

  it('duplicate maps a server error into the dialog banner', async () => {
    const onSubmit = vi.fn(async () => { throw serverError('INTERNAL_ERROR'); });
    const user = userEvent.setup();
    render(<DuplicateEventDialog title="Fest" onClose={() => undefined} onSubmit={onSubmit} />);
    expect(screen.getByLabelText('Title of the copy')).toHaveValue('Fest (copy)');
    await user.click(screen.getByRole('button', { name: 'Duplicate' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    await user.clear(screen.getByLabelText('Title of the copy'));
    await user.click(screen.getByRole('button', { name: 'Duplicate' }));
    expect((await screen.findAllByText('Required')).length).toBeGreaterThan(0);
  });
});
