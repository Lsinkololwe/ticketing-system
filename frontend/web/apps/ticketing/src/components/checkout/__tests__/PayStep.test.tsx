// @vitest-environment jsdom
import { describe, expect, it, vi } from 'vitest';
import { fireEvent, render, screen, waitFor } from '@testing-library/react';

vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/__tests__/referenceMock')).referenceModule());

import { PayStep } from '../PayStep';

const mount = (onPay = vi.fn()) => {
  render(<PayStep total={300} declined={null} busy={false} onBack={vi.fn()} onPay={onPay} />);
  return onPay;
};

describe('PayStep, with the platform operator list', () => {
  it('offers each listed operator with a hint derived from its prefixes', () => {
    mount();
    expect(screen.getByRole('radio', { name: /MTN Mobile Money/ })).toBeInTheDocument();
    expect(screen.getByText('Numbers starting 096 / 076')).toBeInTheDocument();
    expect(screen.getByText('Numbers starting 097 / 077')).toBeInTheDocument();
    expect(screen.getByText('Numbers starting 095 / 055')).toBeInTheDocument();
  });
  it('starts on the first operator and switches to the one the typed number belongs to', async () => {
    mount();
    expect(screen.getByRole('radio', { name: /MTN Mobile Money/ })).toBeChecked();
    fireEvent.change(screen.getByLabelText('Mobile money number'), { target: { value: '97 123 4567' } });
    await waitFor(() => expect(screen.getByRole('radio', { name: /Airtel Money/ })).toBeChecked());
  });
  it('pays with the local number and the operator code', async () => {
    const onPay = mount();
    fireEvent.change(screen.getByLabelText('Mobile money number'), { target: { value: '95 123 4567' } });
    fireEvent.click(screen.getByRole('button', { name: /Pay/ }));
    await waitFor(() => expect(onPay).toHaveBeenCalledWith('0951234567', 'ZAMTEL'));
  });
});
