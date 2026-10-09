import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { BankAccountDialog, BankVerifyDialog } from './BankAccountDialogs';
import { bankAccountSchema, walletSchemaFor } from './schemas';

const gqlError = (extensions: Record<string, unknown>) => ({ errors: [{ message: 'x', extensions }] });

describe('BankAccountDialog (react-hook-form + zod)', () => {
  it('shows messages, focuses the first invalid field and does not submit', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn();
    render(<BankAccountDialog banks={["Zanaco", "Stanbic Bank"]} onSave={onSave} onClose={() => undefined} />);
    await user.click(screen.getByRole('button', { name: 'Add account' }));
    await waitFor(() => expect(screen.getByLabelText('Account holder')).toHaveFocus());
    expect(screen.getByText('Account numbers have 10 to 16 digits')).toBeInTheDocument();
    expect(screen.getAllByText('Required').length).toBeGreaterThan(0);
    expect(onSave).not.toHaveBeenCalled();
  });

  it('rejects a bad SWIFT code', async () => {
    const user = userEvent.setup();
    render(<BankAccountDialog banks={["Zanaco", "Stanbic Bank"]} onSave={vi.fn()} onClose={() => undefined} />);
    await user.type(screen.getByLabelText('SWIFT code'), 'ABC');
    await user.tab();
    expect(await screen.findByText('SWIFT codes have 8 or 11 characters')).toBeInTheDocument();
  });

  it('submits the parsed payload (spaces stripped, SWIFT upper-cased) and closes', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    const onClose = vi.fn();
    render(<BankAccountDialog banks={["Zanaco", "Stanbic Bank"]} onSave={onSave} onClose={onClose} />);
    await user.type(screen.getByLabelText('Account holder'), 'Fixture Ltd');
    await user.type(screen.getByLabelText('Account number'), '0123 4567 8482 1');
    await user.type(screen.getByLabelText('SWIFT code'), 'znco zmlu'.replace(' ', ''));
    await user.click(screen.getByRole('button', { name: 'Add account' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onSave).toHaveBeenCalledWith(
      expect.objectContaining({ holder: 'Fixture Ltd', number: '0123456784821', swift: 'ZNCOZMLU', bankName: 'Zanaco', currency: 'ZMW' })
    );
    await waitFor(() => expect(onClose).toHaveBeenCalled());
  });

  it('maps a server field violation onto the input and keeps the dialog open', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockRejectedValue(
      gqlError({ errorCode: 'COMMAND_NOT_WELL_FORMED', classification: 'BAD_REQUEST', fields: [{ path: 'input.accountNumber', constraint: 'NotBlank' }] })
    );
    const onClose = vi.fn();
    render(<BankAccountDialog banks={["Zanaco", "Stanbic Bank"]} onSave={onSave} onClose={onClose} />);
    await user.type(screen.getByLabelText('Account holder'), 'Fixture Ltd');
    await user.type(screen.getByLabelText('Account number'), '0123456784821');
    await user.click(screen.getByRole('button', { name: 'Add account' }));
    await waitFor(() => expect(screen.getByLabelText('Account number')).toHaveAttribute('aria-invalid', 'true'));
    expect(onClose).not.toHaveBeenCalled();
  });

  it('guards against a double submit', async () => {
    const user = userEvent.setup();
    let release: () => void = () => undefined;
    const onSave = vi.fn().mockImplementation(() => new Promise<void>((r) => (release = r)));
    render(<BankAccountDialog banks={["Zanaco", "Stanbic Bank"]} onSave={onSave} onClose={() => undefined} />);
    await user.type(screen.getByLabelText('Account holder'), 'Fixture Ltd');
    await user.type(screen.getByLabelText('Account number'), '0123456784821');
    const submit = screen.getByRole('button', { name: 'Add account' });
    await user.click(submit);
    await user.click(submit);
    expect(onSave).toHaveBeenCalledTimes(1);
    release();
  });

  it('locks the account number when editing and skips validating it', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<BankAccountDialog banks={["Zanaco", "Stanbic Bank"]} initial={{ id: 'b1', holder: 'X Ltd', number: '4821' }} onSave={onSave} onClose={() => undefined} />);
    expect(screen.getByLabelText('Account number')).toHaveAttribute('readonly');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(onSave).toHaveBeenCalled());
  });
});

describe('BankVerifyDialog', () => {
  it('requires an amount and focuses it', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn();
    render(<BankVerifyDialog accountNumber="0123456784821" onConfirm={onConfirm} onClose={() => undefined} />);
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(screen.getByLabelText('Deposit amount')).toHaveFocus());
    expect(onConfirm).not.toHaveBeenCalled();
  });

  it('submits the amount in ngwee', async () => {
    const user = userEvent.setup();
    const onConfirm = vi.fn().mockResolvedValue(undefined);
    render(<BankVerifyDialog accountNumber="0123456784821" onConfirm={onConfirm} onClose={() => undefined} />);
    await user.type(screen.getByLabelText('Deposit amount'), '0.37');
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    await waitFor(() => expect(onConfirm).toHaveBeenCalledWith(37));
  });

  it('shows a wrong-amount answer on the field and stays open', async () => {
    const user = userEvent.setup();
    const onClose = vi.fn();
    render(<BankVerifyDialog accountNumber="0123456784821" onConfirm={async () => ({ amountError: 'That is not the amount we sent.' })} onClose={onClose} />);
    await user.type(screen.getByLabelText('Deposit amount'), '0.10');
    await user.click(screen.getByRole('button', { name: 'Confirm' }));
    expect(await screen.findByText('That is not the amount we sent.')).toBeInTheDocument();
    expect(onClose).not.toHaveBeenCalled();
  });
});

import { WalletDialog } from './BankAccountDialogs';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});

describe('WalletDialog', () => {
  it('validates the number and submits the E.164 payload', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<WalletDialog onSave={onSave} onClose={() => undefined} />);
    await user.type(screen.getByLabelText('Wallet holder'), 'Fixture Holder');
    await user.type(screen.getByLabelText('Mobile number'), '123');
    await user.click(screen.getByRole('button', { name: 'Add account' }));
    expect(await screen.findByText('Enter a valid Zambian mobile number')).toBeInTheDocument();
    await user.clear(screen.getByLabelText('Mobile number'));
    await user.type(screen.getByLabelText('Mobile number'), '0971234567');
    await user.selectOptions(screen.getByLabelText('Network'), 'AIRTEL');
    await user.click(screen.getByRole('button', { name: 'Add account' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith({ holder: 'Fixture Holder', network: 'AIRTEL', phone: '+260971234567' }));
  });
  it('switches back to the bank form when adding', async () => {
    const user = userEvent.setup();
    const onSwitchToBank = vi.fn();
    render(<WalletDialog onSave={vi.fn()} onSwitchToBank={onSwitchToBank} onClose={() => undefined} />);
    await user.click(screen.getByRole('radio', { name: 'Bank account' }));
    expect(onSwitchToBank).toHaveBeenCalled();
  });
});

describe('currencies and mobile money networks come from the platform lists', () => {
  it('offers the listed currencies and starts on the first one', async () => {
    render(<BankAccountDialog banks={['Zanaco']} onSave={vi.fn()} onClose={() => undefined} />);
    expect(screen.getByRole('option', { name: 'Zambian Kwacha' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'US Dollar' })).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Currency')).toHaveValue('ZMW'));
  });

  it('checks a currency against the loaded list, and lets the server judge when the list is unavailable', () => {
    const input = { holder: 'Fixture Ltd', bankName: 'Zanaco', branchCode: '', number: '0123456784821', swift: '', currency: 'EUR' };
    expect(bankAccountSchema(false, ['ZMW', 'USD']).safeParse(input).success).toBe(false);
    expect(bankAccountSchema(false, ['ZMW', 'USD']).safeParse({ ...input, currency: 'USD' }).success).toBe(true);
    expect(bankAccountSchema(false).safeParse(input).success).toBe(true);
  });

  it('wallet: offers the listed networks, shows the number ranges they own, and validates the network', async () => {
    const user = userEvent.setup();
    const onSave = vi.fn().mockResolvedValue(undefined);
    render(<WalletDialog onSave={onSave} onClose={() => undefined} />);
    expect(screen.getByRole('option', { name: 'Airtel Money' })).toBeInTheDocument();
    expect(screen.getByText('+260 55, 76, 77, 95, 96, 97')).toBeInTheDocument();
    await waitFor(() => expect(screen.getByLabelText('Network')).toHaveValue('MTN'));
    await user.type(screen.getByLabelText('Wallet holder'), 'Fixture Ltd');
    await user.type(screen.getByLabelText('Mobile number'), '0961234567');
    await user.click(screen.getByRole('button', { name: 'Add account' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledWith(expect.objectContaining({ network: 'MTN', phone: '+260961234567' })));
    expect(walletSchemaFor(['MTN']).safeParse({ holder: 'Fixture Ltd', network: 'VODAFONE', phone: '+260961234567' }).success).toBe(false);
  });
});
