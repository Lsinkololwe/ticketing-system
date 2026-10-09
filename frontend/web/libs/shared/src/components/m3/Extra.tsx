'use client';

import {
  useEffect,
  useId,
  useMemo,
  useRef,
  useState,
  type ClipboardEvent,
  type KeyboardEvent,
  type ReactNode,
} from 'react';
import {
  AsYouType,
  getCountryCallingCode,
  isValidPhoneNumber,
  parsePhoneNumberFromString,
  type CountryCode,
} from 'libphonenumber-js';
import { m3ChartColors, m3HeatColors } from '../../styles/m3.tokens';
import { Button } from './Button';
import { TextArea, TextField } from './Fields';
import { ChartFrame } from './Insights';
import { Icon } from './icons';

/* ---------------------------------------------------------------- OtpInput */

export interface OtpInputProps {
  /** Group label, announced with the group. */
  label?: string;
  length?: number;
  value: string;
  onChange: (value: string) => void;
  /** Called once when every digit has been entered. */
  onComplete?: (value: string) => void;
  errorText?: ReactNode;
  disabled?: boolean;
  autoFocus?: boolean;
}

/** One-time code entry: one box per digit, paste fills all, Backspace walks back. */
export function OtpInput({
  label = 'Verification code',
  length = 6,
  value,
  onChange,
  onComplete,
  errorText,
  disabled,
  autoFocus,
}: OtpInputProps) {
  const id = useId();
  const refs = useRef<Array<HTMLInputElement | null>>([]);
  const digits = Array.from({ length }, (_, i) => value[i] ?? '');

  const commit = (next: string) => {
    const clean = next.replace(/\D/g, '').slice(0, length);
    onChange(clean);
    if (clean.length === length) onComplete?.(clean);
  };
  const focusAt = (i: number) => refs.current[Math.max(0, Math.min(length - 1, i))]?.focus();

  const onKeyDown = (i: number, e: KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'Backspace' && !digits[i]) {
      e.preventDefault();
      commit(value.slice(0, Math.max(0, i - 1)));
      focusAt(i - 1);
    } else if (e.key === 'ArrowLeft') {
      e.preventDefault();
      focusAt(i - 1);
    } else if (e.key === 'ArrowRight') {
      e.preventDefault();
      focusAt(i + 1);
    }
  };
  const onPaste = (e: ClipboardEvent<HTMLInputElement>) => {
    e.preventDefault();
    const text = e.clipboardData.getData('text');
    commit(text);
    focusAt(Math.min(length - 1, text.replace(/\D/g, '').length));
  };

  return (
    <div className="m3-otp">
      <div role="group" aria-label={label} className="m3-otp__boxes" aria-describedby={errorText ? `${id}-e` : undefined}>
        {digits.map((d, i) => (
          <input
            key={i}
            ref={(el) => {
              refs.current[i] = el;
            }}
            className="m3-otp__box"
            inputMode="numeric"
            autoComplete={i === 0 ? 'one-time-code' : 'off'}
            maxLength={1}
            value={d}
            disabled={disabled}
            autoFocus={autoFocus && i === 0}
            aria-label={`Digit ${i + 1} of ${length}`}
            aria-invalid={errorText ? true : undefined}
            onPaste={onPaste}
            onKeyDown={(e) => onKeyDown(i, e)}
            onChange={(e) => {
              const c = e.target.value.replace(/\D/g, '');
              if (!c) return;
              const next = value.slice(0, i) + c[c.length - 1] + value.slice(i + 1);
              commit(next);
              if (i < length - 1) focusAt(i + 1);
            }}
            onFocus={(e) => e.target.select()}
          />
        ))}
      </div>
      {errorText ? (
        <p className="m3-field__error" id={`${id}-e`} role="alert">
          {errorText}
        </p>
      ) : null}
    </div>
  );
}

/* --------------------------------------------------------------- Countdown */

export interface CountdownProps {
  /** Epoch milliseconds the countdown ends at. */
  until: number;
  onExpire?: () => void;
  /** Prefix text, e.g. "Held for". */
  label?: string;
  /** Clock source, injectable for tests. */
  now?: () => number;
}

function clock(ms: number): string {
  const total = Math.max(0, Math.ceil(ms / 1000));
  const m = Math.floor(total / 60);
  const s = total % 60;
  return `${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}`;
}

/** mm:ss timer (reservation hold, resend cooldown). Switches to the warning tone in the last minute. */
export function Countdown({ until, onExpire, label, now = Date.now }: CountdownProps) {
  const [left, setLeft] = useState(() => until - now());
  const fired = useRef(false);
  useEffect(() => {
    fired.current = false;
    setLeft(until - now());
    const t = setInterval(() => {
      const l = until - now();
      setLeft(l);
      if (l <= 0) {
        clearInterval(t);
        if (!fired.current) {
          fired.current = true;
          onExpire?.();
        }
      }
    }, 1000);
    return () => clearInterval(t);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [until]);
  const text = clock(left);
  const urgent = left > 0 && left <= 60000;
  return (
    <span
      className="m3-countdown m3-num"
      role="timer"
      aria-label={label ? `${label} ${text}` : text}
      data-urgent={urgent ? 'true' : undefined}
      data-expired={left <= 0 ? 'true' : undefined}
    >
      {label ? <span className="m3-countdown__label">{label} </span> : null}
      {text}
    </span>
  );
}

/* -------------------------------------------------------- StackedBarChart */

export interface StackedSeries {
  label: string;
  values: number[];
}
export interface StackedBarChartProps {
  title: string;
  description?: string;
  labels: string[];
  series: StackedSeries[];
  format?: (n: number) => string;
}

const SW = 720;
const SH = 270;
const SP = { l: 64, r: 8, t: 14, b: 28 };

/** Stacked columns, one segment per series, zero baseline, keyboard focusable columns. */
export function StackedBarChart({ title, description, labels, series, format = (n) => String(n) }: StackedBarChartProps) {
  const totals = labels.map((_, i) => series.reduce((s, x) => s + (x.values[i] ?? 0), 0));
  const rawMax = Math.max(0, ...totals);
  const exp = rawMax > 0 ? Math.pow(10, Math.floor(Math.log10(rawMax))) : 1;
  const f = rawMax / exp;
  const max = rawMax <= 0 ? 1 : (f <= 1 ? 1 : f <= 2 ? 2 : f <= 2.5 ? 2.5 : f <= 5 ? 5 : 10) * exp;
  const iw = SW - SP.l - SP.r;
  const ih = SH - SP.t - SP.b;
  const step = iw / Math.max(1, labels.length);
  const bw = Math.min(28, step * 0.6);
  const y = (v: number) => SP.t + ih - (v / max) * ih;
  return (
    <ChartFrame
      title={title}
      description={description}
      legend={series.map((s, i) => ({ label: s.label, color: m3ChartColors[i % m3ChartColors.length] }))}
      table={{
        columns: ['Period', ...series.map((s) => s.label), 'Total'],
        rows: labels.map((l, i) => [l, ...series.map((s) => format(s.values[i] ?? 0)), format(totals[i])]),
      }}
    >
      <svg className="m3-chart" viewBox={`0 0 ${SW} ${SH}`} role="group" aria-label={title}>
        {[0, 0.25, 0.5, 0.75, 1].map((f2) => (
          <g key={f2}>
            <line className="m3-chart__grid" x1={SP.l} x2={SW - SP.r} y1={y(f2 * max)} y2={y(f2 * max)} />
            <text x={SP.l - 8} y={y(f2 * max) + 4} textAnchor="end">
              {format(f2 * max)}
            </text>
          </g>
        ))}
        {labels.map((l, i) => {
          const x = SP.l + step * i + (step - bw) / 2;
          let acc = 0;
          return (
            <g
              key={l}
              className="m3-chart__group"
              tabIndex={0}
              role="img"
              aria-label={`${l}: ${series.map((s) => `${s.label} ${format(s.values[i] ?? 0)}`).join(', ')}, total ${format(totals[i])}`}
            >
              {series.map((s, k) => {
                const v = s.values[i] ?? 0;
                const h = (v / max) * ih;
                const top = y(acc + v);
                acc += v;
                return v > 0 ? (
                  <rect key={s.label} className="m3-chart__mark" x={x} y={top} width={bw} height={h} fill={m3ChartColors[k % m3ChartColors.length]} />
                ) : null;
              })}
              <text x={x + bw / 2} y={SH - 8} textAnchor="middle">
                {l}
              </text>
            </g>
          );
        })}
      </svg>
    </ChartFrame>
  );
}

/* ----------------------------------------------------------------- Heatmap */

export interface HeatmapProps {
  title: string;
  description?: string;
  rows: string[];
  cols: string[];
  /** values[row][col] */
  values: number[][];
  format?: (n: number) => string;
}

/** Sequential 5-step grid. Every cell prints its value, so colour is never the only channel. */
export function Heatmap({ title, description, rows, cols, values, format = (n) => String(n) }: HeatmapProps) {
  const max = Math.max(0, ...values.flat());
  const level = (v: number) => (max <= 0 || v <= 0 ? 0 : Math.min(m3HeatColors.length, Math.ceil((v / max) * m3HeatColors.length)));
  return (
    <figure className="m3-heat" aria-label={title}>
      <figcaption className="m3-sr-only">{description ?? title}</figcaption>
      <table className="m3-heat__grid">
        <thead>
          <tr>
            <td />
            {cols.map((c) => (
              <th key={c} scope="col">
                {c}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map((r, i) => (
            <tr key={r}>
              <th scope="row">{r}</th>
              {cols.map((c, j) => {
                const v = values[i]?.[j] ?? 0;
                return (
                  <td key={c} className="m3-heat__cell m3-num" data-level={level(v)} title={`${r}, ${c}: ${format(v)}`}>
                    {format(v)}
                  </td>
                );
              })}
            </tr>
          ))}
        </tbody>
      </table>
    </figure>
  );
}

/* ----------------------------------------------------------- CommentThread */

export interface ThreadComment {
  id: string;
  author: string;
  /** Already formatted time, e.g. "12 Jun, 14:05". */
  time: string;
  body: ReactNode;
}
export interface CommentThreadProps {
  label?: string;
  comments: ThreadComment[];
  /** When omitted the thread is read-only. */
  onSubmit?: (text: string) => void | Promise<void>;
  placeholder?: string;
  submitLabel?: string;
  emptyText?: string;
  disabled?: boolean;
}

/** Reviewer / internal comment thread with an optional composer. */
export function CommentThread({
  label = 'Comments',
  comments,
  onSubmit,
  placeholder = 'Write a comment',
  submitLabel = 'Post comment',
  emptyText = 'No comments yet.',
  disabled,
}: CommentThreadProps) {
  const [text, setText] = useState('');
  const [busy, setBusy] = useState(false);
  const send = async () => {
    const t = text.trim();
    if (!t || !onSubmit) return;
    setBusy(true);
    try {
      await onSubmit(t);
      setText('');
    } finally {
      setBusy(false);
    }
  };
  return (
    <section className="m3-thread" aria-label={label}>
      {comments.length === 0 ? (
        <p className="m3-muted">{emptyText}</p>
      ) : (
        <ul className="m3-thread__list">
          {comments.map((c) => (
            <li key={c.id} className="m3-thread__item">
              <div className="m3-thread__meta">
                <strong>{c.author}</strong>
                <span className="m3-muted">{c.time}</span>
              </div>
              <div>{c.body}</div>
            </li>
          ))}
        </ul>
      )}
      {onSubmit ? (
        <div className="m3-thread__composer">
          <TextArea label={placeholder} value={text} onChange={(e) => setText(e.target.value)} disabled={disabled || busy} />
          <Button variant="filled" size="sm" onClick={send} disabled={disabled || !text.trim()} loading={busy}>
            {submitLabel}
          </Button>
        </div>
      ) : null}
    </section>
  );
}

/* ------------------------------------------------------------ HeroCarousel */

export interface HeroCarouselProps {
  label: string;
  slides: ReactNode[];
  /** Controlled index; uncontrolled when omitted. */
  index?: number;
  onIndexChange?: (i: number) => void;
}

/** Manual carousel with previous/next and slide dots. No autoplay (motion is opt-in). */
export function HeroCarousel({ label, slides, index, onIndexChange }: HeroCarouselProps) {
  const [inner, setInner] = useState(0);
  const i = index ?? inner;
  const go = (n: number) => {
    const next = (n + slides.length) % Math.max(1, slides.length);
    if (index === undefined) setInner(next);
    onIndexChange?.(next);
  };
  if (slides.length === 0) return null;
  return (
    <div className="m3-carousel" role="group" aria-roledescription="carousel" aria-label={label}>
      <div className="m3-carousel__slide" role="group" aria-roledescription="slide" aria-label={`${i + 1} of ${slides.length}`}>
        {slides[i]}
      </div>
      {slides.length > 1 ? (
        <div className="m3-carousel__controls">
          <button type="button" className="m3-iconbtn m3-state" aria-label="Previous slide" onClick={() => go(i - 1)}>
            <Icon name="chevron-left" />
          </button>
          <div className="m3-carousel__dots">
            {slides.map((_, k) => (
              <button
                key={k}
                type="button"
                className="m3-carousel__dot"
                aria-label={`Go to slide ${k + 1}`}
                aria-current={k === i ? 'true' : undefined}
                onClick={() => go(k)}
              />
            ))}
          </div>
          <button type="button" className="m3-iconbtn m3-state" aria-label="Next slide" onClick={() => go(i + 1)}>
            <Icon name="chevron-right" />
          </button>
        </div>
      ) : null}
    </div>
  );
}

/* -------------------------------------------------------------- PhoneField */

/** Whether a value is a valid E.164 phone number. */
export function isValidPhone(value: string | undefined | null): boolean {
  if (!value) return false;
  try {
    return isValidPhoneNumber(value);
  } catch {
    return false;
  }
}

/** ISO country of an E.164 number, or undefined. */
export function countryForPhone(value: string | undefined | null): string | undefined {
  if (!value) return undefined;
  try {
    return parsePhoneNumberFromString(value)?.country;
  } catch {
    return undefined;
  }
}

/** One selectable country, as the platform's reference data lists it (`COUNTRY`): ISO code, name, calling code without "+". */
export interface PhoneCountry {
  code: string;
  name: string;
  dial: string;
}

export interface PhoneFieldProps {
  /**
   * The countries on offer, in the order to show them. They come from the backend's `COUNTRY` reference list
   * (`useCountryOptions()`); the field carries no list of its own. While it is empty (loading, or the list
   * could not be read) the select shows only the chosen country and is disabled.
   */
  countries: readonly PhoneCountry[];
  label?: string;
  /** Canonical E.164 ("+260971234567") or empty. */
  value?: string;
  /** E.164, or undefined when the field is empty or not yet parseable. */
  onChange: (value: string | undefined) => void;
  onCountryChange?: (country: string) => void;
  defaultCountry?: CountryCode;
  helperText?: ReactNode;
  errorText?: ReactNode;
  disabled?: boolean;
  id?: string;
  name?: string;
  density?: 'compact' | 'form';
}

/** Country calling code select + national number input. Always emits E.164. */
export function PhoneField({
  countries,
  label = 'Phone number',
  value,
  onChange,
  onCountryChange,
  defaultCountry = 'ZM',
  helperText,
  errorText,
  disabled,
  id,
  name,
  density,
}: PhoneFieldProps) {
  const initial = useMemo(() => {
    const p = value ? parsePhoneNumberFromString(value) : undefined;
    return { country: (p?.country ?? defaultCountry) as CountryCode, national: p ? p.formatNational() : '' };
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);
  const [country, setCountry] = useState<CountryCode>(initial.country);
  const [national, setNational] = useState(initial.national);
  const listed = countries.find((c) => c.code === country);
  // Never invent a list: until the platform's arrives the select offers the chosen country alone, and is disabled.
  const options: readonly PhoneCountry[] = countries.length > 0 ? countries : [{ code: country, name: country, dial: String(getCountryCallingCode(country)) }];
  const dial = listed?.dial ?? String(getCountryCallingCode(country));
  const emit = (text: string, c: CountryCode) => {
    const p = text.trim() ? parsePhoneNumberFromString(text, c) : undefined;
    onChange(p?.number);
  };
  return (
    <div className="m3-phone">
      <div className="m3-phone__country">
        <label className="m3-sr-only" htmlFor={id ? `${id}-country` : undefined}>
          Country calling code
        </label>
        <select
          id={id ? `${id}-country` : undefined}
          className="m3-phone__select"
          aria-label="Country calling code"
          value={country}
          disabled={disabled || countries.length === 0}
          onChange={(e) => {
            const c = e.target.value as CountryCode;
            setCountry(c);
            onCountryChange?.(c);
            emit(national, c);
          }}
        >
          {options.map((o) => (
            <option key={o.code} value={o.code}>
              {countries.length > 0 ? `${o.name} (+${o.dial})` : `+${o.dial}`}
            </option>
          ))}
        </select>
      </div>
      <TextField
        id={id}
        name={name}
        type="tel"
        autoComplete="tel-national"
        inputMode="tel"
        label={label}
        density={density}
        prefix={`+${dial}`}
        value={national}
        disabled={disabled}
        helperText={helperText}
        errorText={errorText}
        wrapperClassName="m3-phone__input"
        onChange={(e) => {
          const text = e.target.value;
          const formatted = text.startsWith('+') ? text : new AsYouType(country).input(text);
          setNational(formatted);
          emit(formatted, country);
        }}
      />
    </div>
  );
}
