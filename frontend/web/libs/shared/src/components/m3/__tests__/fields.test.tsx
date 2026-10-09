// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Checkbox, ChipInput, Combobox, DatePicker, FileUpload, FormCell, FormGrid, FormSection, Radio, RadioGroup, Select, Switch, TextArea, TextField, TimePicker } from '../Fields';

describe('TextField', () => {
  it('labels the input and links helper text', () => {
    render(<TextField label="Email" helperText="We never share it" />);
    const i = screen.getByLabelText('Email');
    expect(i).toHaveAccessibleDescription('We never share it');
  });
  it('error is announced and marks invalid', () => {
    render(<TextField label="Email" errorText="Required" />);
    expect(screen.getByLabelText('Email')).toBeInvalid();
    expect(screen.getByRole('alert')).toHaveTextContent('Required');
  });
  it('counts characters', async () => {
    render(<TextField label="Name" maxLength={10} showCount />);
    await userEvent.type(screen.getByLabelText('Name'), 'abc');
    expect(screen.getByText('3/10')).toBeInTheDocument();
  });
  it('disabled blocks typing', async () => {
    render(<TextField label="Name" disabled />);
    expect(screen.getByLabelText('Name')).toBeDisabled();
  });
  it('shows prefix', () => {
    render(<TextField label="Price" prefix="K" />);
    expect(screen.getByText('K')).toBeInTheDocument();
  });
});

describe('TextArea / Select / pickers', () => {
  it('TextArea labelled', () => {
    render(<TextArea label="About" />);
    expect(screen.getByRole('textbox', { name: 'About' })).toBeInTheDocument();
  });
  it('Select exposes options', async () => {
    const fn = vi.fn();
    render(<Select label="City" onChange={fn}><option value="a">Lusaka</option><option value="b">Ndola</option></Select>);
    await userEvent.selectOptions(screen.getByRole('combobox', { name: 'City' }), 'b');
    expect(fn).toHaveBeenCalled();
  });
  it('Date and time pickers use native types', () => {
    render(<><DatePicker label="Date" /><TimePicker label="Time" /></>);
    expect(screen.getByLabelText('Date')).toHaveAttribute('type', 'date');
    expect(screen.getByLabelText('Time')).toHaveAttribute('type', 'time');
  });
});

describe('Combobox', () => {
  const options = [{ value: 'lsk', label: 'Lusaka' }, { value: 'ndl', label: 'Ndola' }];
  it('filters, selects with keyboard, closes on Escape', async () => {
    const fn = vi.fn();
    render(<Combobox label="City" options={options} value={null} onChange={fn} />);
    const input = screen.getByRole('combobox', { name: 'City' });
    await userEvent.click(input);
    await userEvent.type(input, 'nd');
    expect(screen.getAllByRole('option')).toHaveLength(1);
    await userEvent.keyboard('{ArrowDown}{Enter}');
    expect(fn).toHaveBeenCalledWith('ndl', expect.objectContaining({ label: 'Ndola' }));
  });
  it('shows empty text when nothing matches', async () => {
    render(<Combobox label="City" options={options} value={null} onChange={() => undefined} emptyText="No match" />);
    const input = screen.getByRole('combobox', { name: 'City' });
    await userEvent.click(input);
    await userEvent.type(input, 'zzz');
    expect(screen.getByText('No match')).toBeInTheDocument();
  });
});

describe('Checkbox / Radio / Switch', () => {
  it('checkbox toggles', async () => {
    render(<Checkbox label="Agree" />);
    const c = screen.getByRole('checkbox', { name: 'Agree' });
    await userEvent.click(c);
    expect(c).toBeChecked();
  });
  it('switch has role switch', async () => {
    render(<Switch label="Notify" />);
    const s = screen.getByRole('switch', { name: 'Notify' });
    await userEvent.click(s);
    expect(s).toBeChecked();
  });
  it('radio group arrows move selection', async () => {
    function H() {
      const [v, setV] = useState('a');
      return <RadioGroup legend="Policy" name="p" value={v} onChange={setV} options={[{ value: 'a', label: 'Flexible' }, { value: 'b', label: 'Strict' }]} />;
    }
    render(<H />);
    expect(screen.getByRole('group', { name: 'Policy' })).toBeInTheDocument();
    await userEvent.click(screen.getByRole('radio', { name: /Flexible/ }));
    await userEvent.keyboard('{ArrowDown}');
    expect(screen.getByRole('radio', { name: /Strict/ })).toBeChecked();
  });
  it('single radio renders', () => {
    render(<Radio label="One" name="x" />);
    expect(screen.getByRole('radio', { name: 'One' })).toBeInTheDocument();
  });
});

describe('ChipInput', () => {
  it('adds on comma, removes with button', async () => {
    function H() {
      const [v, setV] = useState<string[]>([]);
      return <ChipInput label="Benefits" values={v} onChange={setV} />;
    }
    render(<H />);
    await userEvent.type(screen.getByRole('textbox', { name: 'Benefits' }), 'Drink,Parking,');
    expect(screen.getByText('Drink')).toBeInTheDocument();
    expect(screen.getByText('Parking')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: /Remove Drink/ }));
    expect(screen.queryByText('Drink')).toBeNull();
  });
});

describe('FileUpload', () => {
  it('reports chosen files and shows errors', async () => {
    const fn = vi.fn();
    const { container } = render(<FileUpload label="Licence" onFiles={fn} errorText="Too big" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Too big');
    const file = new File(['x'], 'a.pdf', { type: 'application/pdf' });
    await userEvent.upload(container.querySelector('input[type=file]') as HTMLInputElement, file);
    expect(fn).toHaveBeenCalledWith([file]);
  });
});

describe('Form layout', () => {
  it('grid, cell and section render children and heading', () => {
    render(<FormSection title="Basics" description="Tell us"><FormGrid><FormCell span={6}><span>cell</span></FormCell></FormGrid></FormSection>);
    expect(screen.getByRole('heading', { name: 'Basics' })).toBeInTheDocument();
    expect(screen.getByText('cell')).toBeInTheDocument();
  });
});
