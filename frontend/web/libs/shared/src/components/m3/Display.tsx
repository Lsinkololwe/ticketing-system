'use client';

import {
  createElement,
  forwardRef,
  memo,
  useId,
  useRef,
  type AnchorHTMLAttributes,
  type ButtonHTMLAttributes,
  type HTMLAttributes,
  type ImgHTMLAttributes,
  type KeyboardEvent,
  type ReactNode,
} from 'react';
import { m3StatusTone, type M3Tone } from '../../styles/m3.tokens';
import { resolveError, type GraphQLLikeError } from '../../lib/errors';
import { humanizeEnum } from '../../lib/format';
import { Button } from './Button';
import { Icon, type IconName } from './icons';
import { cx, useControllable } from './utils';

/* -------------------------------------------------------------------- Chip */

export interface ChipProps extends Omit<ButtonHTMLAttributes<HTMLButtonElement>, 'onChange'> {
  /** 'assist' = action; 'filter' = toggle (aria-pressed, checkmark when on). */
  kind?: 'assist' | 'filter';
  selected?: boolean;
  icon?: IconName;
}

/** Assist or filter chip. Filter chips show a leading check when selected. */
export const Chip = forwardRef<HTMLButtonElement, ChipProps>(function Chip(
  { kind = 'assist', selected, icon, className, children, type = 'button', ...rest },
  ref
) {
  return (
    <button
      ref={ref}
      type={type}
      className={cx('m3-chip m3-state', className)}
      aria-pressed={kind === 'filter' ? Boolean(selected) : undefined}
      {...rest}
    >
      {kind === 'filter' && selected ? <Icon name="check" /> : icon ? <Icon name={icon} /> : null}
      {children}
    </button>
  );
});

export interface ChipGroupProps extends HTMLAttributes<HTMLDivElement> {
  label: string;
}
/** Wrapper that names a set of filter chips. */
export function ChipGroup({ label, className, children, ...rest }: ChipGroupProps) {
  return (
    <div role="group" aria-label={label} className={cx('m3-chips', className)} {...rest}>
      {children}
    </div>
  );
}

export interface StatusPillProps extends HTMLAttributes<HTMLSpanElement> {
  /** Explicit tone; defaults to a tone derived from `status`. */
  tone?: M3Tone;
  /** Backend status string. Shown humanised unless children are given. */
  status?: string;
}
/** Sentence-case label; platform vocabulary overrides (PUBLISHED -> "Live") win over the default. */
function statusLabel(status: string): string {
  const sentence = status.replace(/_/g, ' ').toLowerCase().replace(/^\w/, (c) => c.toUpperCase());
  const human = humanizeEnum(status);
  return human.toLowerCase() === sentence.toLowerCase() ? sentence : human;
}
/** Read-only status label (draft, published, paid ...). Never rely on colour alone: the text is always shown. */
export function StatusPill({ tone, status, className, children, ...rest }: StatusPillProps) {
  const resolved = tone ?? (status ? m3StatusTone(status) : 'neutral');
  const text = children ?? (status ? statusLabel(status) : null);
  return (
    <span className={cx('m3-pill', className)} data-tone={resolved === 'neutral' ? undefined : resolved} {...rest}>
      {text}
    </span>
  );
}

/* --------------------------------------------------------- SegmentedButton */

export interface SegmentOption<V extends string> {
  value: V;
  label: ReactNode;
  disabled?: boolean;
}
export interface SegmentedButtonProps<V extends string> {
  label: string;
  options: SegmentOption<V>[];
  value: V;
  onChange: (value: V) => void;
}

/** Single-select segmented control (radiogroup, arrow-key roving). */
export function SegmentedButton<V extends string>({ label, options, value, onChange }: SegmentedButtonProps<V>) {
  const refs = useRef<Array<HTMLButtonElement | null>>([]);
  const onKey = (e: KeyboardEvent, index: number) => {
    const dir = e.key === 'ArrowRight' || e.key === 'ArrowDown' ? 1 : e.key === 'ArrowLeft' || e.key === 'ArrowUp' ? -1 : 0;
    if (!dir) return;
    e.preventDefault();
    for (let step = 1; step <= options.length; step += 1) {
      const next = (index + dir * step + options.length * step) % options.length;
      if (!options[next].disabled) {
        onChange(options[next].value);
        refs.current[next]?.focus();
        return;
      }
    }
  };
  return (
    <div className="m3-seg" role="radiogroup" aria-label={label}>
      {options.map((o, i) => (
        <button
          key={o.value}
          ref={(el) => {
            refs.current[i] = el;
          }}
          type="button"
          role="radio"
          aria-checked={o.value === value}
          tabIndex={o.value === value ? 0 : -1}
          disabled={o.disabled}
          className="m3-seg__item m3-state"
          onClick={() => onChange(o.value)}
          onKeyDown={(e) => onKey(e, i)}
        >
          {o.label}
        </button>
      ))}
    </div>
  );
}

/* -------------------------------------------------------------------- Tabs */

export interface TabItem {
  id: string;
  label: ReactNode;
  /** Step number badge (editor tabs). */
  number?: number;
  /** Red dot: this tab needs attention. */
  warning?: boolean;
  /** Pending-work count shown as a warning pill after the label (not announced when 0 or absent). */
  count?: number;
  disabled?: boolean;
}
export interface TabsProps {
  label: string;
  tabs: TabItem[];
  value?: string;
  defaultValue?: string;
  onChange?: (id: string) => void;
  /** Stick under the top bar while scrolling. */
  sticky?: boolean;
  /** `segmented` is the storefront's filled, full-width tab bar; `seg` is the console's outlined segmented tab strip. */
  variant?: 'underline' | 'segmented' | 'seg';
  /** Render the active panel (role=tabpanel, labelled by its tab). */
  children?: (activeId: string) => ReactNode;
}

/** Primary tabs with automatic activation and roving tabindex. */
export function Tabs({ label, tabs, value, defaultValue, onChange, sticky, variant, children }: TabsProps) {
  const [active, setActive] = useControllable<string>(value, defaultValue ?? tabs[0]?.id ?? '', onChange);
  const base = useId();
  const refs = useRef<Array<HTMLButtonElement | null>>([]);
  const onKey = (e: KeyboardEvent, index: number) => {
    let next = -1;
    if (e.key === 'ArrowRight') next = (index + 1) % tabs.length;
    else if (e.key === 'ArrowLeft') next = (index - 1 + tabs.length) % tabs.length;
    else if (e.key === 'Home') next = 0;
    else if (e.key === 'End') next = tabs.length - 1;
    if (next < 0) return;
    e.preventDefault();
    if (tabs[next].disabled) return;
    setActive(tabs[next].id);
    refs.current[next]?.focus();
  };
  return (
    <>
      <div className="m3-tabs" role="tablist" aria-label={label} data-sticky={sticky ? 'true' : undefined} data-variant={variant === 'segmented' || variant === 'seg' ? variant : undefined}>
        {tabs.map((t, i) => (
          <button
            key={t.id}
            ref={(el) => {
              refs.current[i] = el;
            }}
            id={`${base}-tab-${t.id}`}
            type="button"
            role="tab"
            aria-selected={t.id === active}
            aria-controls={children && t.id === active ? `${base}-panel-${t.id}` : undefined}
            tabIndex={t.id === active ? 0 : -1}
            disabled={t.disabled}
            className="m3-tab m3-state"
            onClick={() => setActive(t.id)}
            onKeyDown={(e) => onKey(e, i)}
          >
            {t.number !== undefined ? <span className="m3-tab__num">{t.number}</span> : null}
            {t.label}
            {t.warning ? <span className="m3-tab__dot" role="img" aria-label="Needs attention" /> : null}
            {t.count ? (
              <span className="m3-pill m3-tab__count" data-tone="warning" aria-label={`${t.count} pending`}>
                {t.count}
              </span>
            ) : null}
          </button>
        ))}
      </div>
      {children ? (
        <div
          role="tabpanel"
          id={`${base}-panel-${active}`}
          aria-labelledby={`${base}-tab-${active}`}
          className="m3-tabpanel"
          tabIndex={0}
        >
          {children(active)}
        </div>
      ) : null}
    </>
  );
}

/* ----------------------------------------------------- Card, List, Divider */

export interface CardProps extends HTMLAttributes<HTMLElement> {
  variant?: 'elevated' | 'filled' | 'outlined';
  /** Remove padding (for media cards and tables). */
  flush?: boolean;
  padding?: 'md' | 'lg';
  as?: 'div' | 'section' | 'article' | 'aside';
}
/** Surface container (12px radius, 16px padding at medium density). */
export function Card({ variant = 'elevated', flush, padding, as = 'div', className, ...rest }: CardProps) {
  return createElement(as, {
    className: cx('m3-card', className),
    'data-variant': variant === 'elevated' ? undefined : variant,
    'data-flush': flush ? 'true' : undefined,
    'data-pad': padding === 'lg' ? 'lg' : undefined,
    ...rest,
  });
}

export interface CardHeaderProps {
  title: ReactNode;
  subtitle?: ReactNode;
  actions?: ReactNode;
  /** Heading level for the title. */
  level?: 2 | 3 | 4;
}
/** Title row for a Card with optional trailing actions. */
export function CardHeader({ title, subtitle, actions, level = 3 }: CardHeaderProps) {
  return (
    <div className="m3-card__head">
      <div>
        {createElement(`h${level}`, { className: 'm3-card__title' }, title)}
        {subtitle ? <p className="m3-card__subtitle">{subtitle}</p> : null}
      </div>
      {actions ? <div className="m3-row">{actions}</div> : null}
    </div>
  );
}

export function CardFooter({ children }: { children: ReactNode }) {
  return <div className="m3-card__foot">{children}</div>;
}

export function List({ className, ...rest }: HTMLAttributes<HTMLUListElement>) {
  return <ul className={cx('m3-list', className)} {...rest} />;
}
export interface ListItemProps extends Omit<HTMLAttributes<HTMLLIElement>, 'title'> {
  leading?: ReactNode;
  headline: ReactNode;
  support?: ReactNode;
  trailing?: ReactNode;
}
/** One 44px list row: leading, headline + supporting text, trailing action. The row itself is not clickable: put actions in `trailing`. */
export function ListItem({ leading, headline, support, trailing, className, ...rest }: ListItemProps) {
  return (
    <li className={cx('m3-list__item', className)} {...rest}>
      {leading}
      <div className="m3-list__main">
        <span className="m3-list__headline">{headline}</span>
        {support ? <span className="m3-list__support">{support}</span> : null}
      </div>
      {trailing}
    </li>
  );
}

export function Divider({ inset, vertical }: { inset?: boolean; vertical?: boolean }) {
  return (
    <hr
      className="m3-divider"
      data-inset={inset ? 'true' : undefined}
      data-vertical={vertical ? 'true' : undefined}
      aria-orientation={vertical ? 'vertical' : undefined}
    />
  );
}

export interface KeyValueProps {
  items: Array<{ label: string; value: ReactNode }>;
  /** Lay out in auto-fit columns instead of a single column. */
  columns?: boolean;
}
/** Definition list for detail panels (label above value). */
export function KeyValue({ items, columns }: KeyValueProps) {
  return (
    <dl className={columns ? 'm3-kv-grid' : undefined} style={columns ? undefined : { margin: 0 }}>
      {items.map((i) => (
        <div key={i.label} className="m3-kv">
          <dt>{i.label}</dt>
          <dd>{i.value}</dd>
        </div>
      ))}
    </dl>
  );
}

/** Label/value row with a hairline (order summaries, ledgers). */
export function SummaryLine({ label, value, strong }: { label: ReactNode; value: ReactNode; strong?: boolean }) {
  return (
    <div className="m3-line" data-strong={strong ? 'true' : undefined}>
      <span>{label}</span>
      <span className="m3-num">{value}</span>
    </div>
  );
}

export interface TimelineProps {
  items: Array<{ id: string; title: ReactNode; time: string; detail?: ReactNode }>;
  label: string;
}
/** Vertical activity/audit trail. */
export function Timeline({ items, label }: TimelineProps) {
  return (
    <ol className="m3-timeline" aria-label={label}>
      {items.map((i) => (
        <li key={i.id}>
          <span>{i.title}</span>
          <time>{i.time}</time>
          {i.detail ? <span className="m3-muted">{i.detail}</span> : null}
        </li>
      ))}
    </ol>
  );
}

/* ------------------------------------------------- Badge, Avatar, Link */

export interface BadgeProps extends HTMLAttributes<HTMLSpanElement> {
  count?: number;
  /** Dot without a number. */
  dot?: boolean;
  tone?: 'accent' | 'error';
  /** Counts above this show "max+". */
  max?: number;
}
/** Count or dot badge. Announces via the text; add an aria-label when the count is the only content. */
export function Badge({ count, dot, tone = 'accent', max = 99, className, ...rest }: BadgeProps) {
  if (!dot && (count === undefined || count <= 0)) return null;
  return (
    <span
      className={cx('m3-badge', className)}
      data-tone={tone === 'error' ? 'error' : undefined}
      data-dot={dot ? 'true' : undefined}
      {...rest}
    >
      {dot ? null : count! > max ? `${max}+` : count}
    </span>
  );
}

/** Wrap an icon/button to overlay a Badge on its corner. */
export function Badged({ children, badge }: { children: ReactNode; badge: ReactNode }) {
  return (
    <span className="m3-badged">
      {children}
      {badge}
    </span>
  );
}

export interface AvatarProps extends Omit<ImgHTMLAttributes<HTMLImageElement>, 'size'> {
  /** Name used for initials and the accessible label. */
  name: string;
  size?: 'md' | 'sm' | 'lg';
  tone?: 'primary' | 'accent';
}
/** Round avatar: image when `src` is given, otherwise initials. */
export const Avatar = memo(function Avatar({ name, size = 'md', tone = 'primary', src, alt, ...rest }: AvatarProps) {
  const initials = name
    .split(/\s+/)
    .filter(Boolean)
    .slice(0, 2)
    .map((p) => p[0]?.toUpperCase())
    .join('');
  return (
    <span
      className="m3-avatar"
      data-size={size === 'md' ? undefined : size}
      data-tone={tone === 'accent' ? 'accent' : undefined}
      role="img"
      aria-label={alt ?? name}
    >
      {src ? <img src={src} alt="" {...rest} /> : initials}
    </span>
  );
});

export function Link({ className, ...rest }: AnchorHTMLAttributes<HTMLAnchorElement>) {
  return <a className={cx('m3-link', className)} {...rest} />;
}

/* ------------------------------------------------------- Feedback surfaces */

export interface BannerProps {
  tone?: 'warning' | 'success' | 'error' | 'info';
  title?: ReactNode;
  children?: ReactNode;
  actions?: ReactNode;
  /** Use for urgent messages that must interrupt a screen reader. */
  urgent?: boolean;
}
/** Inline banner. role=status by default, role=alert when `urgent`. */
export function Banner({ tone = 'warning', title, children, actions, urgent }: BannerProps) {
  const icon: IconName = tone === 'success' ? 'check-circle' : tone === 'error' ? 'error' : tone === 'info' ? 'info' : 'warning';
  return (
    <div className="m3-banner" data-tone={tone === 'warning' ? undefined : tone} role={urgent ? 'alert' : 'status'}>
      <Icon name={icon} />
      <div className="m3-banner__body">
        {title ? <strong>{title}</strong> : null}
        {title && children ? ' ' : null}
        {children}
      </div>
      {actions ? <div className="m3-banner__actions">{actions}</div> : null}
    </div>
  );
}

export interface EmptyStateProps {
  title: string;
  description?: ReactNode;
  icon?: IconName;
  action?: ReactNode;
}
/** Nothing-here-yet panel with a single next step. */
export function EmptyState({ title, description, icon = 'inbox', action }: EmptyStateProps) {
  return (
    <div className="m3-empty">
      <Icon name={icon} className="m3-empty__icon" />
      <span className="m3-empty__title">{title}</span>
      {description ? <span>{description}</span> : null}
      {action}
    </div>
  );
}

export interface ErrorStateProps {
  error: GraphQLLikeError | null | undefined;
  onRetry?: () => void;
  onSignIn?: () => void;
  variant?: 'inline' | 'banner';
  'data-testid'?: string;
}
/**
 * Renders a failed request using the platform error contract. A retry button
 * exists only when the server marked the error retryable; never decide that in
 * the calling screen. The correlation id is shown so support can find the failure.
 */
export const ErrorState = memo(function ErrorState({
  error,
  onRetry,
  onSignIn,
  variant = 'banner',
  'data-testid': testId = 'error-state',
}: ErrorStateProps) {
  if (!error) return null;
  const resolved = resolveError(error);
  const showRetry = resolved.action === 'retry' && Boolean(onRetry);
  const showSignIn = resolved.action === 'sign-in' && Boolean(onSignIn);
  const body = (
    <div data-testid={testId} data-error-code={resolved.code} className="m3-stack">
      <span>{resolved.message}</span>
      {resolved.correlationId ? (
        <span className="m3-mono m3-muted" data-testid={`${testId}-correlation-id`}>
          {resolved.correlationId}
        </span>
      ) : null}
      {showRetry || showSignIn ? (
        <div className="m3-row">
          {showRetry ? (
            <Button size="sm" variant="tonal" onClick={onRetry} data-testid={`${testId}-retry`}>
              Try again
            </Button>
          ) : null}
          {showSignIn ? (
            <Button size="sm" variant="filled" onClick={onSignIn} data-testid={`${testId}-sign-in`}>
              Sign in
            </Button>
          ) : null}
        </div>
      ) : null}
    </div>
  );
  if (variant === 'inline') return body;
  return (
    <div className="m3-banner" data-tone="error" role="alert" data-testid={`${testId}-callout`}>
      <Icon name="error" />
      <div className="m3-banner__body">{body}</div>
    </div>
  );
});

/** Field-level server error, announced to assistive tech. */
export const FieldError = memo(function FieldError({
  message,
  fieldPath,
}: {
  message: string | undefined;
  fieldPath: string;
}) {
  if (!message) return null;
  return (
    <p className="m3-field__error" role="alert" data-testid={`field-error-${fieldPath}`}>
      {message}
    </p>
  );
});

/* -------------------------------------------------------- Loading + progress */

export interface SkeletonProps {
  /** Width as a CSS length or percentage, e.g. "60%". */
  width?: string;
  shape?: 'line' | 'circle' | 'block';
  className?: string;
}
/** Shimmering placeholder (decorative; mark the loading region with aria-busy). */
export function Skeleton({ width, shape = 'line', className }: SkeletonProps) {
  return (
    <span
      aria-hidden="true"
      className={cx('m3-skeleton', className)}
      data-shape={shape === 'line' ? undefined : shape}
      style={width ? { width } : undefined}
    />
  );
}

export interface LinearProgressProps {
  /** 0-100. Omit for indeterminate. */
  value?: number;
  label: string;
  size?: 'md' | 'lg';
  className?: string;
}
export function LinearProgress({ value, label, size = 'md', className }: LinearProgressProps) {
  const determinate = value !== undefined;
  const pct = determinate ? Math.max(0, Math.min(100, value)) : undefined;
  return (
    <div
      className={cx('m3-linear', className)}
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={pct}
      data-size={size === 'lg' ? 'lg' : undefined}
      data-indeterminate={determinate ? undefined : 'true'}
    >
      <span className="m3-linear__bar" style={determinate ? { width: `${pct}%` } : undefined} />
    </div>
  );
}

export interface CircularProgressProps {
  value?: number;
  label: string;
  size?: 'md' | 'sm';
  /** Show the percentage in the centre (md only). */
  showValue?: boolean;
}
export function CircularProgress({ value, label, size = 'md', showValue }: CircularProgressProps) {
  const determinate = value !== undefined;
  const pct = determinate ? Math.max(0, Math.min(100, value)) : 25;
  const r = 40;
  const c = 2 * Math.PI * r;
  return (
    <span
      className="m3-circular"
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={determinate ? pct : undefined}
      data-size={size === 'sm' ? 'sm' : undefined}
      data-indeterminate={determinate ? undefined : 'true'}
    >
      <svg viewBox="0 0 100 100" aria-hidden="true">
        <circle className="m3-circular__track" cx="50" cy="50" r={r} fill="none" strokeWidth="10" />
        <circle
          className="m3-circular__bar"
          cx="50"
          cy="50"
          r={r}
          fill="none"
          strokeWidth="10"
          strokeDasharray={c}
          strokeDashoffset={c * (1 - pct / 100)}
        />
      </svg>
      {showValue && determinate && size === 'md' ? <span className="m3-circular__label">{Math.round(pct)}%</span> : null}
    </span>
  );
}

export interface StepperStep {
  id: string;
  label: ReactNode;
}
export interface StepperProps {
  steps: StepperStep[];
  /** Index of the current step. */
  current: number;
  label?: string;
  /** `underline` is the storefront's numbered text steps over a progress rule. */
  variant?: 'pills' | 'underline';
}
/** Checkout/wizard progress. Steps before `current` are complete. Read-only: navigation is via the page's own buttons. */
export function Stepper({ steps, current, label = 'Progress', variant = 'pills' }: StepperProps) {
  return (
    <ol className="m3-stepper" aria-label={label} data-variant={variant === 'underline' ? 'underline' : undefined}>
      {steps.map((s, i) => (
        <li
          key={s.id}
          className="m3-step"
          aria-current={i === current ? 'step' : undefined}
          data-state={i < current ? 'complete' : undefined}
        >
          <span className="m3-step__dot" aria-hidden="true">
            {i < current ? <Icon name="check" /> : i + 1}
          </span>
          <span>
            {variant === 'underline' ? `${i + 1}. ` : null}
            {s.label}
            {i < current ? <span className="m3-sr-only"> (completed)</span> : null}
          </span>
        </li>
      ))}
    </ol>
  );
}

/* -------------------------------------------------------- Expansion + misc */

export interface ExpansionItemProps {
  title: ReactNode;
  subtitle?: ReactNode;
  trailing?: ReactNode;
  defaultOpen?: boolean;
  children: ReactNode;
}
/** Collapsible repeater row (ticket tiers, schedule items). Native <details>: keyboard and AT support for free. */
export function ExpansionItem({ title, subtitle, trailing, defaultOpen, children }: ExpansionItemProps) {
  return (
    <details className="m3-expansion" open={defaultOpen}>
      <summary>
        <span style={{ flex: 1, minWidth: 0 }}>
          <b className="m3-list__headline" style={{ display: 'block' }}>
            {title}
          </b>
          {subtitle ? <small className="m3-muted">{subtitle}</small> : null}
        </span>
        {trailing}
      </summary>
      <div className="m3-expansion__body">{children}</div>
    </details>
  );
}

export interface SaveBarProps {
  message: ReactNode;
  onSave: () => void;
  onDiscard?: () => void;
  saving?: boolean;
  saveLabel?: string;
  hidden?: boolean;
}
/** Sticky unsaved-changes bar (dark inverse surface). */
export function SaveBar({ message, onSave, onDiscard, saving, saveLabel = 'Save changes', hidden }: SaveBarProps) {
  if (hidden) return null;
  return (
    <div className="m3-savebar" role="region" aria-label="Unsaved changes">
      <span className="m3-savebar__text">{message}</span>
      {onDiscard ? (
        <Button variant="text" onClick={onDiscard}>
          Discard
        </Button>
      ) : null}
      <Button variant="filled" loading={saving} onClick={onSave}>
        {saveLabel}
      </Button>
    </div>
  );
}

/** Row of filter controls above a table. */
export function Toolbar({ children, label }: { children: ReactNode; label?: string }) {
  return (
    <div className="m3-toolbar" role="group" aria-label={label}>
      {children}
    </div>
  );
}

/** Appears when table rows are selected. */
export function BulkBar({ count, children }: { count: number; children: ReactNode }) {
  if (count <= 0) return null;
  return (
    <div className="m3-bulkbar" role="region" aria-label="Bulk actions">
      <b>{count} selected</b>
      {children}
    </div>
  );
}
