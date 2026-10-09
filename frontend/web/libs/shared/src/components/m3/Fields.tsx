'use client';

import {
  forwardRef,
  useEffect,
  useId,
  useRef,
  useState,
  type ChangeEvent,
  type DragEvent,
  type InputHTMLAttributes,
  type KeyboardEvent,
  type ReactNode,
  type SelectHTMLAttributes,
  type TextareaHTMLAttributes,
} from 'react';
import { Button } from './Button';
import { Icon } from './icons';
import { cx, useOutsideClick } from './utils';

/* ------------------------------------------------------------------ frame */

interface FrameProps {
  id: string;
  label: string;
  helperText?: ReactNode;
  errorText?: ReactNode;
  prefix?: ReactNode;
  suffix?: ReactNode;
  variant?: 'outlined' | 'filled';
  density?: 'compact' | 'form';
  disabled?: boolean;
  floated?: boolean;
  count?: ReactNode;
  className?: string;
  chevron?: boolean;
  children: ReactNode;
}

function FieldFrame({
  id,
  label,
  helperText,
  errorText,
  prefix,
  suffix,
  variant = 'outlined',
  density = 'compact',
  disabled,
  floated,
  count,
  className,
  chevron,
  children,
}: FrameProps) {
  const hasSupport = Boolean(helperText || errorText || count);
  return (
    <div
      className={cx('m3-field', className)}
      data-variant={variant}
      data-size={density === 'form' ? 'form' : undefined}
      data-invalid={errorText ? 'true' : undefined}
      data-disabled={disabled ? 'true' : undefined}
      data-floated={floated ? 'true' : undefined}
    >
      <div className="m3-field__box">
        {prefix ? (
          <span className="m3-field__affix" data-slot="prefix">
            {prefix}
          </span>
        ) : null}
        {children}
        <label className="m3-field__label" htmlFor={id}>
          {label}
        </label>
        {chevron ? <i className="m3-field__chev" aria-hidden="true" /> : null}
        {suffix ? (
          <span className="m3-field__affix" data-slot="suffix">
            {suffix}
          </span>
        ) : null}
      </div>
      {hasSupport ? (
        <div className="m3-field__support">
          {errorText ? (
            <span className="m3-field__error" id={`${id}-error`} role="alert">
              {errorText}
            </span>
          ) : helperText ? (
            <span className="m3-field__help" id={`${id}-help`}>
              {helperText}
            </span>
          ) : (
            <span />
          )}
          {count ? <span className="m3-field__count">{count}</span> : null}
        </div>
      ) : null}
    </div>
  );
}

function describedBy(id: string, helper: ReactNode, error: ReactNode) {
  if (error) return `${id}-error`;
  if (helper) return `${id}-help`;
  return undefined;
}

/* -------------------------------------------------------------- TextField */

export interface TextFieldProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'prefix' | 'size'> {
  /** Visible label (floats). Required for accessibility. */
  label: string;
  helperText?: ReactNode;
  /** When set the field is invalid and the text is announced (role=alert). */
  errorText?: ReactNode;
  /** Static text before the value, e.g. "K" or "+260". */
  prefix?: ReactNode;
  suffix?: ReactNode;
  variant?: 'outlined' | 'filled';
  /** 'compact' = 40px (toolbars/filters); 'form' = 44px (form grids). */
  density?: 'compact' | 'form';
  /** Show a character counter when maxLength is set. */
  showCount?: boolean;
  wrapperClassName?: string;
}

const ALWAYS_FLOATED = new Set(['date', 'time', 'datetime-local', 'month', 'week', 'color', 'file']);

/** Outlined (default) or filled text field with floating label, prefix/suffix and support text. */
export const TextField = forwardRef<HTMLInputElement, TextFieldProps>(function TextField(
  {
    label,
    helperText,
    errorText,
    prefix,
    suffix,
    variant,
    density,
    showCount,
    wrapperClassName,
    id: idProp,
    className,
    disabled,
    maxLength,
    value,
    defaultValue,
    placeholder,
    type = 'text',
    onChange,
    ...rest
  },
  ref
) {
  const auto = useId();
  const id = idProp ?? auto;
  const [len, setLen] = useState(String(value ?? defaultValue ?? '').length);
  const count =
    showCount && maxLength ? `${value !== undefined ? String(value).length : len}/${maxLength}` : undefined;
  return (
    <FieldFrame
      id={id}
      label={label}
      helperText={helperText}
      errorText={errorText}
      prefix={prefix}
      suffix={suffix}
      variant={variant}
      density={density}
      disabled={disabled}
      floated={ALWAYS_FLOATED.has(type) || Boolean(placeholder && placeholder.trim())}
      count={count}
      className={wrapperClassName}
    >
      <input
        ref={ref}
        id={id}
        type={type}
        className={cx('m3-field__control', className)}
        placeholder={placeholder ?? ' '}
        disabled={disabled}
        maxLength={maxLength}
        value={value}
        defaultValue={defaultValue}
        aria-invalid={errorText ? true : undefined}
        aria-describedby={describedBy(id, helperText, errorText)}
        onChange={(e) => {
          setLen(e.target.value.length);
          onChange?.(e);
        }}
        {...rest}
      />
    </FieldFrame>
  );
});

export interface TextAreaProps extends TextareaHTMLAttributes<HTMLTextAreaElement> {
  label: string;
  helperText?: ReactNode;
  errorText?: ReactNode;
  variant?: 'outlined' | 'filled';
  density?: 'compact' | 'form';
  showCount?: boolean;
  wrapperClassName?: string;
}

/** Multi-line field; same chrome as TextField. */
export const TextArea = forwardRef<HTMLTextAreaElement, TextAreaProps>(function TextArea(
  {
    label,
    helperText,
    errorText,
    variant,
    density = 'form',
    showCount,
    wrapperClassName,
    id: idProp,
    className,
    disabled,
    maxLength,
    value,
    defaultValue,
    placeholder,
    rows,
    onChange,
    ...rest
  },
  ref
) {
  const auto = useId();
  const id = idProp ?? auto;
  const [len, setLen] = useState(String(value ?? defaultValue ?? '').length);
  const count =
    showCount && maxLength ? `${value !== undefined ? String(value).length : len}/${maxLength}` : undefined;
  return (
    <FieldFrame
      id={id}
      label={label}
      helperText={helperText}
      errorText={errorText}
      variant={variant}
      density={density}
      disabled={disabled}
      floated={Boolean(placeholder && placeholder.trim())}
      count={count}
      className={wrapperClassName}
    >
      <textarea
        ref={ref}
        id={id}
        rows={rows}
        className={cx('m3-field__control', className)}
        placeholder={placeholder ?? ' '}
        disabled={disabled}
        maxLength={maxLength}
        value={value}
        defaultValue={defaultValue}
        aria-invalid={errorText ? true : undefined}
        aria-describedby={describedBy(id, helperText, errorText)}
        onChange={(e) => {
          setLen(e.target.value.length);
          onChange?.(e);
        }}
        {...rest}
      />
    </FieldFrame>
  );
});

/* ----------------------------------------------------------------- Select */

export interface SelectProps extends Omit<SelectHTMLAttributes<HTMLSelectElement>, 'size'> {
  label: string;
  helperText?: ReactNode;
  errorText?: ReactNode;
  variant?: 'outlined' | 'filled';
  density?: 'compact' | 'form';
  wrapperClassName?: string;
}

/** Native select with M3 chrome. Prefer this over Combobox for <= 12 options. */
export const Select = forwardRef<HTMLSelectElement, SelectProps>(function Select(
  { label, helperText, errorText, variant, density, wrapperClassName, id: idProp, className, disabled, children, ...rest },
  ref
) {
  const auto = useId();
  const id = idProp ?? auto;
  return (
    <FieldFrame
      id={id}
      label={label}
      helperText={helperText}
      errorText={errorText}
      variant={variant}
      density={density}
      disabled={disabled}
      floated
      chevron
      className={wrapperClassName}
    >
      <select
        ref={ref}
        id={id}
        className={cx('m3-field__control', className)}
        disabled={disabled}
        aria-invalid={errorText ? true : undefined}
        aria-describedby={describedBy(id, helperText, errorText)}
        {...rest}
      >
        {children}
      </select>
    </FieldFrame>
  );
});

/* --------------------------------------------------------------- Combobox */

export interface ComboboxOption {
  value: string;
  label: string;
  hint?: string;
  disabled?: boolean;
}

export interface ComboboxProps {
  label: string;
  options: ComboboxOption[];
  value: string | null;
  onChange: (value: string | null, option: ComboboxOption | null) => void;
  helperText?: ReactNode;
  errorText?: ReactNode;
  placeholder?: string;
  disabled?: boolean;
  density?: 'compact' | 'form';
  /** Text shown when the filter matches nothing. */
  emptyText?: string;
  id?: string;
}

/**
 * Searchable single-select (ARIA 1.2 combobox with listbox popup).
 * Up/Down move, Enter selects, Escape closes, typing filters.
 */
export function Combobox({
  label,
  options,
  value,
  onChange,
  helperText,
  errorText,
  placeholder,
  disabled,
  density,
  emptyText = 'No matches',
  id: idProp,
}: ComboboxProps) {
  const auto = useId();
  const id = idProp ?? auto;
  const listId = `${id}-list`;
  const selected = options.find((o) => o.value === value) ?? null;
  const [open, setOpen] = useState(false);
  const [query, setQuery] = useState('');
  const [active, setActive] = useState(0);
  const wrapRef = useRef<HTMLDivElement>(null);
  useOutsideClick(wrapRef, open, () => setOpen(false));

  const filtered = options.filter((o) => o.label.toLowerCase().includes(query.trim().toLowerCase()));
  const shown = open ? query : selected?.label ?? '';

  useEffect(() => setActive(0), [query, open]);

  const commit = (opt: ComboboxOption | undefined) => {
    if (!opt || opt.disabled) return;
    onChange(opt.value, opt);
    setQuery('');
    setOpen(false);
  };

  const onKeyDown = (e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (!open) setOpen(true);
      else setActive((i) => Math.min(filtered.length - 1, i + 1));
    } else if (e.key === 'ArrowUp') {
      e.preventDefault();
      setActive((i) => Math.max(0, i - 1));
    } else if (e.key === 'Enter' && open) {
      e.preventDefault();
      commit(filtered[active]);
    } else if (e.key === 'Escape' && open) {
      e.stopPropagation();
      setOpen(false);
      setQuery('');
    } else if (e.key === 'Tab') {
      setOpen(false);
    }
  };

  return (
    <div ref={wrapRef} className="m3-combobox">
      <FieldFrame
        id={id}
        label={label}
        helperText={helperText}
        errorText={errorText}
        density={density}
        disabled={disabled}
        floated={Boolean(placeholder) || open}
        chevron
      >
        <input
          id={id}
          role="combobox"
          aria-expanded={open}
          aria-controls={listId}
          aria-autocomplete="list"
          aria-activedescendant={open && filtered[active] ? `${id}-opt-${active}` : undefined}
          aria-invalid={errorText ? true : undefined}
          aria-describedby={describedBy(id, helperText, errorText)}
          className="m3-field__control"
          autoComplete="off"
          placeholder={placeholder ?? ' '}
          disabled={disabled}
          value={shown}
          onFocus={() => setOpen(true)}
          onClick={() => setOpen(true)}
          onChange={(e: ChangeEvent<HTMLInputElement>) => {
            setQuery(e.target.value);
            setOpen(true);
          }}
          onKeyDown={onKeyDown}
        />
      </FieldFrame>
      {open ? (
        <div className="m3-menu" data-anchored="true">
          <div role="listbox" id={listId} aria-label={label}>
            {filtered.length === 0 ? (
              <span className="m3-menu__label">{emptyText}</span>
            ) : (
              filtered.map((o, i) => (
                <div
                  key={o.value}
                  id={`${id}-opt-${i}`}
                  role="option"
                  aria-selected={o.value === value}
                  aria-disabled={o.disabled || undefined}
                  data-active={i === active ? 'true' : undefined}
                  className="m3-menu__item"
                  onMouseDown={(e) => e.preventDefault()}
                  onClick={() => commit(o)}
                  onMouseEnter={() => setActive(i)}
                >
                  <span>{o.label}</span>
                  {o.hint ? <span className="m3-menu__hint">{o.hint}</span> : null}
                </div>
              ))
            )}
          </div>
        </div>
      ) : null}
    </div>
  );
}

/* ----------------------------------------------- date / time (native-based) */

export type DatePickerProps = Omit<TextFieldProps, 'type'>;
/** Date field backed by the browser's native picker (always keyboard accessible). */
export const DatePicker = forwardRef<HTMLInputElement, DatePickerProps>(function DatePicker(props, ref) {
  return <TextField ref={ref} type="date" {...props} />;
});
/** Time field backed by the browser's native picker. */
export const TimePicker = forwardRef<HTMLInputElement, DatePickerProps>(function TimePicker(props, ref) {
  return <TextField ref={ref} type="time" {...props} />;
});

/* ------------------------------------------------- checkbox / radio / switch */

export interface CheckboxProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label?: ReactNode;
  hint?: ReactNode;
  indeterminate?: boolean;
  /** Required when `label` is omitted (e.g. table selection checkboxes). */
  'aria-label'?: string;
}

/** Checkbox with optional label/hint; supports indeterminate. */
export const Checkbox = forwardRef<HTMLInputElement, CheckboxProps>(function Checkbox(
  { label, hint, indeterminate, className, ...rest },
  ref
) {
  const inner = useRef<HTMLInputElement | null>(null);
  useEffect(() => {
    if (inner.current) inner.current.indeterminate = Boolean(indeterminate);
  }, [indeterminate]);
  const box = (
    <input
      type="checkbox"
      className={cx('m3-check__input', className)}
      ref={(el) => {
        inner.current = el;
        if (typeof ref === 'function') ref(el);
        else if (ref) ref.current = el;
      }}
      {...rest}
    />
  );
  if (!label) return box;
  return (
    <label className="m3-check">
      {box}
      <span className="m3-check__text">
        <span>{label}</span>
        {hint ? <span className="m3-check__hint">{hint}</span> : null}
      </span>
    </label>
  );
});

export interface RadioProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type'> {
  label: ReactNode;
  hint?: ReactNode;
}

/** Single radio; use inside RadioGroup so the group is named and shares `name`. */
export const Radio = forwardRef<HTMLInputElement, RadioProps>(function Radio(
  { label, hint, className, ...rest },
  ref
) {
  return (
    <label className="m3-check">
      <input ref={ref} type="radio" className={cx('m3-check__input', className)} {...rest} />
      <span className="m3-check__text">
        <span>{label}</span>
        {hint ? <span className="m3-check__hint">{hint}</span> : null}
      </span>
    </label>
  );
});

export interface RadioGroupOption {
  value: string;
  label: ReactNode;
  hint?: ReactNode;
  disabled?: boolean;
}
export interface RadioGroupProps {
  legend: string;
  name: string;
  options: RadioGroupOption[];
  value: string;
  onChange: (value: string) => void;
  errorText?: ReactNode;
}

/** Named radio group (fieldset + legend). Arrow keys move selection natively. */
export function RadioGroup({ legend, name, options, value, onChange, errorText }: RadioGroupProps) {
  return (
    <fieldset className="m3-form-section" style={{ border: 0, padding: 0, margin: 0 }}>
      <legend className="m3-label">{legend}</legend>
      <div role="radiogroup" aria-label={legend} aria-invalid={errorText ? true : undefined}>
        {options.map((o) => (
          <Radio
            key={o.value}
            name={name}
            value={o.value}
            label={o.label}
            hint={o.hint}
            disabled={o.disabled}
            checked={value === o.value}
            onChange={() => onChange(o.value)}
          />
        ))}
      </div>
      {errorText ? (
        <span className="m3-field__error" role="alert">
          {errorText}
        </span>
      ) : null}
    </fieldset>
  );
}

export interface SwitchProps extends Omit<InputHTMLAttributes<HTMLInputElement>, 'type' | 'role'> {
  /** Visible label. When omitted pass `aria-label`. */
  label?: ReactNode;
  hint?: ReactNode;
}

/** 44x24 switch (role=switch). With a label it renders as a settings row. */
export const Switch = forwardRef<HTMLInputElement, SwitchProps>(function Switch(
  { label, hint, className, ...rest },
  ref
) {
  const control = (
    <span className="m3-switch">
      <input ref={ref} type="checkbox" role="switch" className={cx('m3-switch__input', className)} {...rest} />
      <span className="m3-switch__track" />
    </span>
  );
  if (!label) return control;
  return (
    <label className="m3-switch-row">
      <span className="m3-switch-row__text">
        <span className="m3-switch-row__label">{label}</span>
        {hint ? <span className="m3-switch-row__hint">{hint}</span> : null}
      </span>
      {control}
    </label>
  );
});

/* ---------------------------------------------------------------- chip input */

export interface ChipInputProps {
  label: string;
  values: string[];
  onChange: (values: string[]) => void;
  placeholder?: string;
  helperText?: ReactNode;
  /** Characters that commit a chip besides Enter. */
  separators?: string[];
}

/** Free-text tags: Enter or comma adds, Backspace on empty removes the last, each chip has a remove button. */
export function ChipInput({ label, values, onChange, placeholder, helperText, separators = [','] }: ChipInputProps) {
  const id = useId();
  const [draft, setDraft] = useState('');
  const add = (raw: string) => {
    const v = raw.trim();
    if (v && !values.includes(v)) onChange([...values, v]);
    setDraft('');
  };
  return (
    <div className="m3-field">
      <span className="m3-label" id={`${id}-l`}>
        {label}
      </span>
      <div className="m3-chipin" role="group" aria-labelledby={`${id}-l`}>
        {values.map((v) => (
          <span key={v} className="m3-chip m3-chip--input">
            {v}
            <button type="button" aria-label={`Remove ${v}`} onClick={() => onChange(values.filter((x) => x !== v))}>
              <Icon name="close" />
            </button>
          </span>
        ))}
        <input
          aria-labelledby={`${id}-l`}
          value={draft}
          placeholder={placeholder}
          onChange={(e) => {
            const next = e.target.value;
            if (separators.some((s) => next.endsWith(s))) add(next.slice(0, -1));
            else setDraft(next);
          }}
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              e.preventDefault();
              add(draft);
            } else if (e.key === 'Backspace' && !draft && values.length) {
              onChange(values.slice(0, -1));
            }
          }}
          onBlur={() => draft && add(draft)}
        />
      </div>
      {helperText ? <span className="m3-field__support">{helperText}</span> : null}
    </div>
  );
}

/* ---------------------------------------------------------------- FileUpload */

export interface FileUploadProps {
  label: string;
  accept?: string;
  multiple?: boolean;
  /** Called with the chosen or dropped files. */
  onFiles: (files: File[]) => void;
  hint?: ReactNode;
  disabled?: boolean;
  errorText?: ReactNode;
}

/** Drop zone + "Choose file" button. The button is the accessible control. */
export function FileUpload({ label, accept, multiple, onFiles, hint, disabled, errorText }: FileUploadProps) {
  const input = useRef<HTMLInputElement>(null);
  const [active, setActive] = useState(false);
  const id = useId();
  const onDrop = (e: DragEvent<HTMLDivElement>) => {
    e.preventDefault();
    setActive(false);
    if (disabled) return;
    onFiles(Array.from(e.dataTransfer.files));
  };
  return (
    <div
      className="m3-dropzone"
      data-active={active ? 'true' : undefined}
      onDragOver={(e) => {
        e.preventDefault();
        if (!disabled) setActive(true);
      }}
      onDragLeave={() => setActive(false)}
      onDrop={onDrop}
    >
      <Icon name="upload" />
      <span id={`${id}-l`}>{label}</span>
      {hint ? <span className="m3-field__help">{hint}</span> : null}
      <input
        ref={input}
        type="file"
        className="m3-sr-only"
        tabIndex={-1}
        accept={accept}
        multiple={multiple}
        disabled={disabled}
        aria-labelledby={`${id}-l`}
        onChange={(e) => {
          onFiles(Array.from(e.target.files ?? []));
          e.target.value = '';
        }}
      />
      <Button variant="tonal" size="sm" icon="upload" disabled={disabled} onClick={() => input.current?.click()}>
        Choose {multiple ? 'files' : 'file'}
      </Button>
      {errorText ? (
        <span className="m3-field__error" role="alert">
          {errorText}
        </span>
      ) : null}
    </div>
  );
}

/* ----------------------------------------------------------- form layout */

export interface FormGridProps {
  children: ReactNode;
  className?: string;
}
/** 12-column responsive field grid (4-span default; 6 on tablet; full on phone). */
export function FormGrid({ children, className }: FormGridProps) {
  return <div className={cx('m3-form-grid', className)}>{children}</div>;
}
export interface FormCellProps {
  /** Column span of 12: 3, 4 (default), 6, 8 or 12. */
  span?: 3 | 4 | 6 | 8 | 12;
  children: ReactNode;
}
/** Wraps one field in a FormGrid to set its width. */
export function FormCell({ span = 4, children }: FormCellProps) {
  return <div data-span={span === 4 ? undefined : span}>{children}</div>;
}

export interface FormSectionProps {
  title?: string;
  description?: ReactNode;
  children: ReactNode;
  className?: string;
}
/** Titled group of fields inside a Card or editor step. */
export function FormSection({ title, description, children, className }: FormSectionProps) {
  return (
    <section className={cx('m3-form-section', className)}>
      {title ? (
        <div>
          <h3 className="m3-card__title">{title}</h3>
          {description ? <p className="m3-card__subtitle">{description}</p> : null}
        </div>
      ) : null}
      {children}
    </section>
  );
}
