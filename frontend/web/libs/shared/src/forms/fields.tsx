'use client';

import { useEffect, useId, useState, type ReactNode } from 'react';
import { useController, useFormContext, type FieldValues, type Path } from 'react-hook-form';
import { Button } from '../components/m3/Button';
import {
  Checkbox,
  ChipInput,
  Combobox,
  DatePicker,
  FileUpload,
  RadioGroup,
  Select,
  Switch,
  TextArea,
  TextField,
  TimePicker,
  type CheckboxProps,
  type ChipInputProps,
  type ComboboxOption,
  type ComboboxProps,
  type FileUploadProps,
  type RadioGroupOption,
  type SelectProps,
  type SwitchProps,
  type TextAreaProps,
  type TextFieldProps,
} from '../components/m3/Fields';
import { OtpInput, PhoneField, type OtpInputProps, type PhoneFieldProps } from '../components/m3/Extra';
import { SegmentedButton, type SegmentOption } from '../components/m3/Display';
import { useFormUi } from './Form';
import { minorToKwachaString, parseKwachaToMinor } from './schemas/money';

/**
 * RHF-bound M3 fields. Every wrapper takes `name`, reads its value and error from the surrounding
 * `<Form>` context, and passes the message to the m3 field's `errorText` (which owns aria-invalid,
 * aria-describedby and role="alert"). Do not pass value/onChange/errorText yourself.
 */

type Name = string;

function useBound(name: Name) {
  const { control } = useFormContext();
  const { field, fieldState } = useController({ name: name as Path<FieldValues>, control });
  const { disabled } = useFormUi();
  return { field, error: fieldState.error?.message, disabled };
}

/** Error text for widgets whose m3 component has no `errorText` prop. */
function FieldError({ id, children }: { id: string; children?: ReactNode }) {
  if (!children) return null;
  return (
    <span className="m3-field__error" id={id} role="alert">
      {children}
    </span>
  );
}

/** Reports blur of anything inside to RHF (touched state) without adding layout. */
function BlurScope({ name, label, invalid, describedBy, onBlur, children }: { name: Name; label?: string; invalid?: boolean; describedBy?: string; onBlur: () => void; children: ReactNode }) {
  return (
    <div
      style={{ display: 'contents' }}
      data-field={name}
      data-label={label}
      data-rhf-invalid={invalid ? 'true' : undefined}
      aria-describedby={describedBy}
      onBlur={onBlur}
    >
      {children}
    </div>
  );
}

/* ---------------------------------------------------------------- text */

type Omitted = 'name' | 'value' | 'defaultValue' | 'onChange' | 'onBlur' | 'errorText' | 'checked';

export interface TextFieldRHFProps extends Omit<TextFieldProps, Omitted> {
  name: Name;
}
/** Text input. `type="number"` stores a number (or undefined when empty). */
export function TextFieldRHF({ name, type = 'text', disabled, ...rest }: TextFieldRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  return (
    <TextField
      {...rest}
      type={type}
      name={field.name}
      ref={field.ref}
      value={field.value ?? ''}
      disabled={disabled || formDisabled}
      errorText={error}
      onBlur={field.onBlur}
      onChange={(e) => {
        const raw = e.target.value;
        field.onChange(type === 'number' ? (raw === '' ? undefined : Number(raw)) : raw);
      }}
    />
  );
}

export interface TextAreaRHFProps extends Omit<TextAreaProps, Omitted> {
  name: Name;
}
export function TextAreaRHF({ name, disabled, ...rest }: TextAreaRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  return (
    <TextArea
      {...rest}
      name={field.name}
      ref={field.ref}
      value={field.value ?? ''}
      disabled={disabled || formDisabled}
      errorText={error}
      onBlur={field.onBlur}
      onChange={(e) => field.onChange(e.target.value)}
    />
  );
}

export interface SelectRHFProps extends Omit<SelectProps, Omitted | 'children'> {
  name: Name;
  options: Array<{ value: string; label: string; disabled?: boolean }>;
  /** Adds a leading empty option with this text (value ""). */
  placeholder?: string;
}
/** Native select (<= 12 options). Stores the option value string ("" when the placeholder is chosen). */
export function SelectRHF({ name, options, placeholder, disabled, ...rest }: SelectRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  return (
    <Select
      {...rest}
      name={field.name}
      ref={field.ref}
      value={field.value ?? ''}
      disabled={disabled || formDisabled}
      errorText={error}
      onBlur={field.onBlur}
      onChange={(e) => field.onChange(e.target.value)}
    >
      {placeholder !== undefined ? <option value="">{placeholder}</option> : null}
      {options.map((o) => (
        <option key={o.value} value={o.value} disabled={o.disabled}>
          {o.label}
        </option>
      ))}
    </Select>
  );
}

export interface ComboboxRHFProps extends Omit<ComboboxProps, 'value' | 'onChange' | 'errorText'> {
  name: Name;
  options: ComboboxOption[];
  onSelect?: (value: string | null, option: ComboboxOption | null) => void;
}
/** Searchable select. Stores the option value string, or null when cleared. */
export function ComboboxRHF({ name, onSelect, disabled, label, ...rest }: ComboboxRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  return (
    <BlurScope name={name} label={label} onBlur={field.onBlur}>
      <Combobox
        {...rest}
        label={label}
        disabled={disabled || formDisabled}
        value={(field.value as string | null | undefined) ?? null}
        errorText={error}
        onChange={(v, o) => {
          field.onChange(v);
          onSelect?.(v, o);
        }}
      />
    </BlurScope>
  );
}

/* ---------------------------------------------------------- toggles */

export interface CheckboxRHFProps extends Omit<CheckboxProps, Omitted | 'type'> {
  name: Name;
}
export function CheckboxRHF({ name, disabled, ...rest }: CheckboxRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  const errId = useId();
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <Checkbox
        {...rest}
        name={field.name}
        ref={field.ref}
        checked={Boolean(field.value)}
        disabled={disabled || formDisabled}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        onBlur={field.onBlur}
        onChange={(e) => field.onChange(e.target.checked)}
      />
      <FieldError id={errId}>{error}</FieldError>
    </div>
  );
}

export interface SwitchRHFProps extends Omit<SwitchProps, Omitted> {
  name: Name;
}
export function SwitchRHF({ name, disabled, ...rest }: SwitchRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  const errId = useId();
  return (
    <div style={{ display: 'flex', flexDirection: 'column' }}>
      <Switch
        {...rest}
        name={field.name}
        ref={field.ref}
        checked={Boolean(field.value)}
        disabled={disabled || formDisabled}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errId : undefined}
        onBlur={field.onBlur}
        onChange={(e) => field.onChange(e.target.checked)}
      />
      <FieldError id={errId}>{error}</FieldError>
    </div>
  );
}

export interface RadioGroupRHFProps {
  name: Name;
  legend: string;
  options: RadioGroupOption[];
}
export function RadioGroupRHF({ name, legend, options }: RadioGroupRHFProps) {
  const { field, error } = useBound(name);
  return (
    <BlurScope name={name} label={legend} onBlur={field.onBlur}>
      <RadioGroup
        legend={legend}
        name={field.name}
        options={options}
        value={(field.value as string | undefined) ?? ''}
        onChange={field.onChange}
        errorText={error}
      />
    </BlurScope>
  );
}

export interface SegmentedRHFProps<V extends string> {
  name: Name;
  label: string;
  options: SegmentOption<V>[];
}
export function SegmentedRHF<V extends string>({ name, label, options }: SegmentedRHFProps<V>) {
  const { field, error } = useBound(name);
  const errId = useId();
  return (
    <BlurScope name={name} label={label} invalid={Boolean(error)} describedBy={error ? errId : undefined} onBlur={field.onBlur}>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        <SegmentedButton label={label} options={options} value={field.value as V} onChange={field.onChange} />
        <FieldError id={errId}>{error}</FieldError>
      </div>
    </BlurScope>
  );
}

/* ------------------------------------------------------ date and time */

export type DateRHFProps = Omit<TextFieldProps, Omitted | 'type'> & { name: Name };
/** Stores `YYYY-MM-DD`. Use the `isoDate()` schema. */
export function DateRHF({ name, disabled, ...rest }: DateRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  return (
    <DatePicker
      {...rest}
      name={field.name}
      ref={field.ref}
      value={field.value ?? ''}
      disabled={disabled || formDisabled}
      errorText={error}
      onBlur={field.onBlur}
      onChange={(e) => field.onChange(e.target.value)}
    />
  );
}

/** Stores `HH:mm`. Use the `isoTime()` schema. */
export function TimeRHF({ name, disabled, ...rest }: DateRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  return (
    <TimePicker
      {...rest}
      name={field.name}
      ref={field.ref}
      value={field.value ?? ''}
      disabled={disabled || formDisabled}
      errorText={error}
      onBlur={field.onBlur}
      onChange={(e) => field.onChange(e.target.value)}
    />
  );
}

/* ------------------------------------------------------------- money */

export interface MoneyRHFProps extends Omit<TextFieldProps, Omitted | 'type' | 'prefix' | 'inputMode'> {
  name: Name;
}
/**
 * Kwacha input that stores integer ngwee (minor units) or undefined. Shows "K" and keeps at most 2 decimals
 * while typing. Validate with `moneyMinor()`.
 */
export function MoneyRHF({ name, disabled, ...rest }: MoneyRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  const minor = field.value as number | undefined | null;
  const [text, setText] = useState(() => (typeof minor === 'number' ? minorToKwachaString(minor) : ''));

  // Follow external changes (reset, setValue) without fighting the user's typing.
  useEffect(() => {
    const current = parseKwachaToMinor(text);
    if ((minor ?? undefined) !== current) setText(typeof minor === 'number' ? minorToKwachaString(minor) : '');
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [minor]);

  return (
    <TextField
      {...rest}
      name={field.name}
      ref={field.ref}
      type="text"
      inputMode="decimal"
      autoComplete="off"
      prefix="K"
      value={text}
      disabled={disabled || formDisabled}
      errorText={error}
      onBlur={() => {
        const m = parseKwachaToMinor(text);
        if (m !== undefined) setText(minorToKwachaString(m));
        field.onBlur();
      }}
      onChange={(e) => {
        let next = e.target.value.replace(/[^\d.,]/g, '').replace(/,/g, '');
        const dot = next.indexOf('.');
        if (dot >= 0) next = next.slice(0, dot + 1) + next.slice(dot + 1).replace(/\./g, '').slice(0, 2);
        setText(next);
        field.onChange(next === '' || next === '.' ? undefined : parseKwachaToMinor(next.endsWith('.') ? next.slice(0, -1) : next));
      }}
    />
  );
}

/* ------------------------------------------------------------- phone */

export interface PhoneRHFProps extends Omit<PhoneFieldProps, 'value' | 'onChange' | 'errorText' | 'name'> {
  name: Name;
}
/** Country select + national input; stores E.164 (`+260971234567`) or undefined. Use `phoneE164()`. */
export function PhoneRHF({ name, disabled, label, ...rest }: PhoneRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  return (
    <BlurScope name={name} label={label ?? 'Phone number'} onBlur={field.onBlur}>
      <PhoneField
        {...rest}
        label={label}
        name={field.name}
        value={(field.value as string | undefined) ?? ''}
        disabled={disabled || formDisabled}
        errorText={error}
        onChange={(v) => field.onChange(v ?? '')}
      />
    </BlurScope>
  );
}

/* --------------------------------------------------------------- otp */

export interface OtpRHFProps extends Omit<OtpInputProps, 'value' | 'onChange' | 'errorText'> {
  name: Name;
}
/** Stores the digits typed so far as a string. Use `otp6()`. */
export function OtpRHF({ name, disabled, label, ...rest }: OtpRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  return (
    <BlurScope name={name} label={label ?? 'Verification code'} onBlur={field.onBlur}>
      <OtpInput
        {...rest}
        label={label}
        disabled={disabled || formDisabled}
        value={(field.value as string | undefined) ?? ''}
        errorText={error}
        onChange={field.onChange}
      />
    </BlurScope>
  );
}

/* -------------------------------------------------------------- file */

export interface FileRHFProps extends Omit<FileUploadProps, 'onFiles' | 'errorText'> {
  name: Name;
}
/** Stores `File[]`. Use the `files()` schema. Lists chosen files with a remove button each. */
export function FileRHF({ name, disabled, multiple, label, ...rest }: FileRHFProps) {
  const { field, error, disabled: formDisabled } = useBound(name);
  const errId = useId();
  const current: File[] = Array.isArray(field.value) ? (field.value as File[]) : [];
  return (
    <BlurScope name={name} label={label} invalid={Boolean(error)} describedBy={error ? errId : undefined} onBlur={field.onBlur}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: 'var(--m3-sp-8)' }}>
        <FileUpload
          {...rest}
          label={label}
          multiple={multiple}
          disabled={disabled || formDisabled}
          onFiles={(files) => field.onChange(multiple ? [...current, ...files] : files.slice(0, 1))}
        />
        {current.length > 0 ? (
          <ul aria-label={`${label} selected`} style={{ listStyle: 'none', margin: 0, padding: 0 }}>
            {current.map((f, i) => (
              <li key={`${f.name}-${i}`} style={{ display: 'flex', alignItems: 'center', gap: 'var(--m3-sp-8)' }}>
                <span>{f.name}</span>
                <Button
                  variant="text"
                  size="sm"
                  aria-label={`Remove ${f.name}`}
                  onClick={() => field.onChange(current.filter((_, j) => j !== i))}
                >
                  Remove
                </Button>
              </li>
            ))}
          </ul>
        ) : null}
        <FieldError id={errId}>{error}</FieldError>
      </div>
    </BlurScope>
  );
}

/* ------------------------------------------------------------- chips */

export interface ChipsRHFProps extends Omit<ChipInputProps, 'values' | 'onChange'> {
  name: Name;
}
/** Stores `string[]`. */
export function ChipsRHF({ name, label, helperText, ...rest }: ChipsRHFProps) {
  const { field, error } = useBound(name);
  const errId = useId();
  return (
    <BlurScope name={name} label={label} invalid={Boolean(error)} describedBy={error ? errId : undefined} onBlur={field.onBlur}>
      <div style={{ display: 'flex', flexDirection: 'column' }}>
        <ChipInput
          {...rest}
          label={label}
          helperText={error ? undefined : helperText}
          values={Array.isArray(field.value) ? (field.value as string[]) : []}
          onChange={field.onChange}
        />
        <FieldError id={errId}>{error}</FieldError>
      </div>
    </BlurScope>
  );
}
