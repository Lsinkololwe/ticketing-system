// @vitest-environment jsdom
import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { z } from 'zod';
import { Form, FormActions } from '../Form';
import {
  CheckboxRHF,
  ChipsRHF,
  ComboboxRHF,
  DateRHF,
  FileRHF,
  MoneyRHF,
  OtpRHF,
  PhoneRHF,
  RadioGroupRHF,
  SegmentedRHF,
  SelectRHF,
  SwitchRHF,
  TextAreaRHF,
  TextFieldRHF,
  TimeRHF,
} from '../fields';
import { files, isoDate, isoTime, moneyMinor, otp6, phoneE164 } from '../schemas';
import { useZodForm } from '../useZodForm';

const schema = z.object({
  title: z.string().min(1, 'Title needed'),
  count: z.number({ error: 'Enter a number' }),
  about: z.string().min(5, 'Say more'),
  city: z.string().min(1, 'Pick a city'),
  venue: z.string({ error: 'Pick a venue' }).min(1, 'Pick a venue'),
  agree: z.boolean().refine((v) => v, 'You must agree'),
  notify: z.boolean().refine((v) => v, 'Turn on'),
  plan: z.enum(['free', 'paid'], { error: 'Choose a plan' }),
  mode: z.enum(['one', 'two'], { error: 'Choose a mode' }),
  day: isoDate(),
  at: isoTime(),
  price: moneyMinor({ minMinor: 100 }),
  phone: phoneE164(),
  code: otp6(),
  docs: files({ max: 2 }),
  tags: z.array(z.string()).min(1, 'Add a tag'),
});

type Out = z.output<typeof schema>;

function Kit({ onSubmit = vi.fn(), defaults = {} }: { onSubmit?: (v: Out) => void; defaults?: Record<string, unknown> }) {
  const form = useZodForm(schema, {
    defaultValues: { title: '', count: undefined, about: '', city: '', venue: null as never, agree: false, notify: false, plan: '' as never, mode: '' as never, day: '', at: '', price: undefined, phone: '', code: '', docs: [], tags: [], ...defaults } as never,
  });
  return (
    <Form form={form as never} onSubmit={onSubmit as never} aria-label="kit">
      <TextFieldRHF name="title" label="Title" />
      <TextFieldRHF name="count" label="Count" type="number" />
      <TextAreaRHF name="about" label="About" />
      <SelectRHF name="city" label="City" placeholder="Choose" options={[{ value: 'lsk', label: 'Lusaka' }]} />
      <ComboboxRHF name="venue" label="Venue" options={[{ value: 'v1', label: 'Mulungushi' }]} />
      <CheckboxRHF name="agree" label="I agree" />
      <SwitchRHF name="notify" label="Notify me" />
      <RadioGroupRHF name="plan" legend="Plan" options={[{ value: 'free', label: 'Free' }, { value: 'paid', label: 'Paid' }]} />
      <SegmentedRHF name="mode" label="Mode" options={[{ value: 'one', label: 'One' }, { value: 'two', label: 'Two' }]} />
      <DateRHF name="day" label="Day" />
      <TimeRHF name="at" label="Time" />
      <MoneyRHF name="price" label="Price" />
      <PhoneRHF name="phone" label="Phone" countries={[{ code: 'ZM', name: 'Zambia', dial: '260' }]} />
      <OtpRHF name="code" />
      <FileRHF name="docs" label="Documents" multiple />
      <ChipsRHF name="tags" label="Tags" />
      <FormActions submitLabel="Go" />
    </Form>
  );
}

describe('RHF field wrappers: errors, aria and focus', () => {
  it('every wrapper shows its own error with role=alert after a failed submit', async () => {
    const user = userEvent.setup();
    render(<Kit />);
    await user.click(screen.getByRole('button', { name: 'Go' }));
    const form = screen.getByRole('form', { name: 'kit' });
    for (const text of ['Title needed', 'Enter a number', 'Say more', 'Pick a city', 'Pick a venue', 'You must agree', 'Turn on', 'Choose a plan', 'Choose a mode', 'Choose a date', 'Choose a time', 'Enter an amount', 'Enter a phone number', 'Enter the 6-digit code', 'Choose a file', 'Add a tag']) {
      const hits = within(form).getAllByText(text).filter((el) => el.getAttribute('role') === 'alert' || el.closest('[role="alert"]'));
      expect(hits.length, text).toBeGreaterThan(0);
    }
    expect(screen.getByLabelText('Title')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Count')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('About')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('City')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('checkbox', { name: 'I agree' })).toHaveAttribute('aria-invalid', 'true');
    const agreeDesc = screen.getByRole('checkbox', { name: 'I agree' }).getAttribute('aria-describedby')!;
    expect(document.getElementById(agreeDesc)).toHaveTextContent('You must agree');
    expect(screen.getByRole('switch', { name: 'Notify me' })).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByRole('radiogroup', { name: 'Plan' })).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Digit 1 of 6')).toHaveAttribute('aria-invalid', 'true');
    expect(screen.getByLabelText('Title')).toHaveFocus();
  });

  it('helper text is described until an error replaces it', async () => {
    const user = userEvent.setup();
    function H() {
      const form = useZodForm(z.object({ t: z.string().min(1, 'Req') }), { defaultValues: { t: '' } });
      return (
        <Form form={form} onSubmit={() => undefined}>
          <TextFieldRHF name="t" label="T" helperText="Hint" />
          <FormActions />
        </Form>
      );
    }
    render(<H />);
    expect(document.getElementById(screen.getByLabelText('T').getAttribute('aria-describedby')!)).toHaveTextContent('Hint');
    await user.click(screen.getByRole('button', { name: 'Save' }));
    await waitFor(() => expect(document.getElementById(screen.getByLabelText('T').getAttribute('aria-describedby')!)).toHaveTextContent('Req'));
  });

  it('submits correctly typed values from every wrapper', async () => {
    const user = userEvent.setup();
    const onSubmit = vi.fn();
    render(<Kit onSubmit={onSubmit} />);
    await user.type(screen.getByLabelText('Title'), 'Gala');
    await user.type(screen.getByLabelText('Count'), '12');
    await user.type(screen.getByLabelText('About'), 'A long night');
    await user.selectOptions(screen.getByLabelText('City'), 'lsk');
    await user.click(screen.getByRole('combobox', { name: 'Venue' }));
    await user.click(await screen.findByRole('option', { name: /Mulungushi/ }));
    await user.click(screen.getByRole('checkbox', { name: 'I agree' }));
    await user.click(screen.getByRole('switch', { name: 'Notify me' }));
    await user.click(screen.getByRole('radio', { name: 'Paid' }));
    await user.click(screen.getByRole('radio', { name: 'Two' }));
    await user.type(screen.getByLabelText('Day'), '2026-12-01');
    await user.type(screen.getByLabelText('Time'), '18:30');
    await user.type(screen.getByLabelText('Price'), '12.345');
    expect(screen.getByLabelText('Price')).toHaveValue('12.34');
    await user.type(screen.getByLabelText('Phone'), '0971234567');
    await user.click(screen.getByLabelText('Digit 1 of 6'));
    await user.keyboard('123456');
    await user.upload(document.querySelector('input[type="file"]') as HTMLInputElement, new File(['x'], 'id.pdf', { type: 'application/pdf' }));
    const tagInput = within(screen.getByRole('group', { name: 'Tags' })).getByRole('textbox');
    await user.type(tagInput, 'vip,');
    await user.click(screen.getByRole('button', { name: 'Go' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalled());
    const v = onSubmit.mock.calls[0][0] as Out;
    expect(v).toMatchObject({ title: 'Gala', count: 12, about: 'A long night', city: 'lsk', venue: 'v1', agree: true, notify: true, plan: 'paid', mode: 'two', day: '2026-12-01', at: '18:30', price: 1234, phone: '+260971234567', code: '123456', tags: ['vip'] });
    expect(v.docs).toHaveLength(1);
    expect(v.docs[0].name).toBe('id.pdf');
  });

  it('MoneyRHF follows external reset and formats on blur', async () => {
    const user = userEvent.setup();
    render(<Kit defaults={{ price: 125050 }} />);
    const price = screen.getByLabelText('Price');
    expect(price).toHaveValue('1250.50');
    await user.clear(price);
    await user.type(price, '5');
    await user.tab();
    expect(price).toHaveValue('5.00');
  });

  it('FileRHF lists chosen files and removes them', async () => {
    const user = userEvent.setup();
    render(<Kit />);
    await user.upload(document.querySelector('input[type="file"]') as HTMLInputElement, new File(['x'], 'a.pdf', { type: 'application/pdf' }));
    expect(screen.getByText('a.pdf')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Remove a.pdf' }));
    expect(screen.queryByText('a.pdf')).not.toBeInTheDocument();
  });

  it('PhoneRHF shows an invalid-number message from the schema', async () => {
    const user = userEvent.setup();
    render(<Kit />);
    await user.type(screen.getByLabelText('Phone'), '12');
    await user.tab();
    expect(await screen.findByText('Enter a valid phone number')).toBeInTheDocument();
  });
});
