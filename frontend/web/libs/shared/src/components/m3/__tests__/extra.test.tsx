// @vitest-environment jsdom
import { act, render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { CommentThread, Countdown, Heatmap, HeroCarousel, OtpInput, PhoneField, StackedBarChart, countryForPhone, isValidPhone } from '../Extra';

function Otp({ onComplete }: { onComplete?: (v: string) => void }) {
  const [v, setV] = useState('');
  return <OtpInput value={v} onChange={setV} onComplete={onComplete} />;
}

describe('OtpInput', () => {
  it('is a labelled group of six digit boxes', () => {
    render(<Otp />);
    expect(screen.getByRole('group', { name: 'Verification code' })).toBeInTheDocument();
    expect(screen.getAllByRole('textbox')).toHaveLength(6);
  });
  it('types, advances focus and completes', async () => {
    const done = vi.fn();
    render(<Otp onComplete={done} />);
    await userEvent.click(screen.getByLabelText('Digit 1 of 6'));
    await userEvent.keyboard('123456');
    expect(done).toHaveBeenCalledWith('123456');
  });
  it('paste fills every box and ignores non-digits', async () => {
    const done = vi.fn();
    render(<Otp onComplete={done} />);
    await userEvent.click(screen.getByLabelText('Digit 1 of 6'));
    await userEvent.paste('12 34-56');
    expect(done).toHaveBeenCalledWith('123456');
  });
  it('Backspace walks back', async () => {
    render(<Otp />);
    await userEvent.click(screen.getByLabelText('Digit 1 of 6'));
    await userEvent.keyboard('12{Backspace}');
    expect(screen.getByLabelText('Digit 1 of 6')).toHaveValue('1');
  });
  it('announces errors', () => {
    render(<OtpInput value="" onChange={() => undefined} errorText="Wrong code" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Wrong code');
  });
});

describe('Countdown', () => {
  it('shows mm:ss, turns urgent and fires onExpire once', () => {
    vi.useFakeTimers();
    let t = 0;
    const now = () => t;
    const exp = vi.fn();
    render(<Countdown until={65000} now={now} onExpire={exp} label="Held for" />);
    expect(screen.getByRole('timer')).toHaveTextContent('Held for 01:05');
    act(() => { t = 10000; vi.advanceTimersByTime(1000); });
    expect(screen.getByRole('timer')).toHaveAttribute('data-urgent', 'true');
    act(() => { t = 70000; vi.advanceTimersByTime(1000); });
    expect(screen.getByRole('timer')).toHaveTextContent('00:00');
    expect(exp).toHaveBeenCalledTimes(1);
    vi.useRealTimers();
  });
});

describe('charts', () => {
  it('StackedBarChart labels each column and offers a table', () => {
    render(<StackedBarChart title="Sales" labels={['Jan', 'Feb']} series={[{ label: 'VIP', values: [1, 2] }, { label: 'GA', values: [3, 0] }]} />);
    expect(screen.getByRole('img', { name: /Jan: VIP 1, GA 3, total 4/ })).toBeInTheDocument();
    expect(screen.getByText('View as table')).toBeInTheDocument();
  });
  it('Heatmap prints values in cells', () => {
    render(<Heatmap title="Scans" rows={['Mon']} cols={['9', '10']} values={[[0, 5]]} />);
    expect(screen.getByRole('columnheader', { name: '10' })).toBeInTheDocument();
    expect(screen.getByRole('cell', { name: '5' })).toHaveAttribute('data-level', '5');
  });
});

describe('CommentThread', () => {
  it('empty and read-only', () => {
    render(<CommentThread comments={[]} />);
    expect(screen.getByText('No comments yet.')).toBeInTheDocument();
    expect(screen.queryByRole('button')).toBeNull();
  });
  it('posts a trimmed comment and clears', async () => {
    const send = vi.fn().mockResolvedValue(undefined);
    render(<CommentThread comments={[{ id: '1', author: 'Ann', time: 'now', body: 'Hi' }]} onSubmit={send} />);
    const post = screen.getByRole('button', { name: 'Post comment' });
    expect(post).toBeDisabled();
    await userEvent.type(screen.getByRole('textbox'), '  Looks good ');
    await userEvent.click(post);
    expect(send).toHaveBeenCalledWith('Looks good');
  });
});

describe('HeroCarousel', () => {
  it('navigates slides and exposes semantics', async () => {
    render(<HeroCarousel label="Featured" slides={[<p key="a">One</p>, <p key="b">Two</p>]} />);
    expect(screen.getByRole('group', { name: 'Featured' })).toHaveAttribute('aria-roledescription', 'carousel');
    expect(screen.getByText('One')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next slide' }));
    expect(screen.getByText('Two')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Next slide' }));
    expect(screen.getByText('One')).toBeInTheDocument();
  });
});

describe('PhoneField', () => {
  // Fixtures live in the test: the component has no list of its own.
  const COUNTRIES = [
    { code: 'ZM', name: 'Zambia', dial: '260' },
    { code: 'ZW', name: 'Zimbabwe', dial: '263' },
    { code: 'GB', name: 'United Kingdom', dial: '44' },
  ];
  it('defaults to Zambia, lists the countries it is given, and emits E.164', async () => {
    const fn = vi.fn();
    render(<PhoneField countries={COUNTRIES} onChange={fn} />);
    const select = screen.getByRole('combobox', { name: 'Country calling code' });
    expect(select).toHaveValue('ZM');
    expect(Array.from((select as HTMLSelectElement).options).map((o) => o.textContent)).toEqual([
      'Zambia (+260)',
      'Zimbabwe (+263)',
      'United Kingdom (+44)',
    ]);
    await userEvent.type(screen.getByLabelText('Phone number'), '971234567');
    expect(fn).toHaveBeenLastCalledWith('+260971234567');
  });
  it('offers no invented list while the platform list is unavailable: one disabled option', () => {
    render(<PhoneField countries={[]} onChange={() => undefined} />);
    const select = screen.getByRole('combobox', { name: 'Country calling code' }) as HTMLSelectElement;
    expect(select).toBeDisabled();
    expect(select.options).toHaveLength(1);
  });
  it('shows an error', () => {
    render(<PhoneField countries={COUNTRIES} onChange={() => undefined} errorText="Invalid number" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Invalid number');
  });
  it('helpers', () => {
    expect(isValidPhone('+260971234567')).toBe(true);
    expect(isValidPhone('123')).toBe(false);
    expect(countryForPhone('+260971234567')).toBe('ZM');
  });
});
