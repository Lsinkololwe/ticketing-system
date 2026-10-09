'use client';

import { useId, type ReactNode } from 'react';
import { Button, IconButton } from './Button';
import { Icon } from './icons';
import { SummaryLine, StatusPill } from './Display';
import type { NavLinkComponent } from './Navigation';
import { cx } from './utils';

/* ------------------------------------------------------------------- Hero */

export interface HeroBannerProps {
  image: string;
  /** Short accent tag, e.g. "Featured - Music". */
  tag?: string;
  title: string;
  /** Date and venue line. */
  meta?: ReactNode;
  actions?: ReactNode;
  /** Slot at the bottom edge, usually the SearchBar. */
  footer?: ReactNode;
}

/** Full-bleed hero with scrim for legible text. `image` should be decorative (alt is empty). */
export function HeroBanner({ image, tag, title, meta, actions, footer }: HeroBannerProps) {
  return (
    <section className="m3-hero" aria-label="Featured event">
      {image ? <img className="m3-hero__image" src={image} alt="" /> : null}
      <div className="m3-site-wrap m3-hero__content">
        <div>
          {tag ? <span className="m3-hero__tag">{tag}</span> : null}
          <h1 className="m3-hero__title">{title}</h1>
          {meta ? <p className="m3-hero__meta">{meta}</p> : null}
          {actions ? <div className="m3-row">{actions}</div> : null}
        </div>
        {footer}
      </div>
    </section>
  );
}

/* -------------------------------------------------------------- SearchBar */

export interface SearchBarProps {
  query: string;
  onQueryChange: (value: string) => void;
  city: string;
  onCityChange: (value: string) => void;
  cities: Array<{ value: string; label: string }>;
  when: string;
  onWhenChange: (value: string) => void;
  whenOptions: Array<{ value: string; label: string }>;
  onSearch: () => void;
  onMoreFilters?: () => void;
}

/** What / Where / When search strip. Each cell is a labelled form control. */
export function SearchBar({
  query,
  onQueryChange,
  city,
  onCityChange,
  cities,
  when,
  onWhenChange,
  whenOptions,
  onSearch,
  onMoreFilters,
}: SearchBarProps) {
  const id = useId();
  return (
    <form
      className="m3-site-search"
      role="search"
      onSubmit={(e) => {
        e.preventDefault();
        onSearch();
      }}
    >
      <label className="m3-site-search__field" htmlFor={`${id}-q`}>
        <span>What</span>
        <input id={`${id}-q`} type="search" value={query} placeholder="Artist, event or venue" onChange={(e) => onQueryChange(e.target.value)} />
      </label>
      <label className="m3-site-search__field" htmlFor={`${id}-c`}>
        <span>Where</span>
        <select id={`${id}-c`} value={city} onChange={(e) => onCityChange(e.target.value)}>
          {cities.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </label>
      <label className="m3-site-search__field" htmlFor={`${id}-w`}>
        <span>When</span>
        <select id={`${id}-w`} value={when} onChange={(e) => onWhenChange(e.target.value)}>
          {whenOptions.map((c) => (
            <option key={c.value} value={c.value}>
              {c.label}
            </option>
          ))}
        </select>
      </label>
      <Button type="submit" variant="accent">
        Search
      </Button>
      {onMoreFilters ? (
        <Button variant="outlined" onClick={onMoreFilters}>
          More filters
        </Button>
      ) : null}
    </form>
  );
}

/* ------------------------------------------------------ section + cards */

export interface SectionHeaderProps {
  eyebrow?: string;
  title: string;
  description?: ReactNode;
  actions?: ReactNode;
  /** 1 is the page title (buyer .h1s); 2 and 3 are section headings. */
  level?: 1 | 2 | 3;
}
/** Eyebrow + heading + optional actions that start every storefront section. */
export function SectionHeader({ eyebrow, title, description, actions, level = 2 }: SectionHeaderProps) {
  const Heading = level === 1 ? 'h1' : level === 2 ? 'h2' : 'h3';
  return (
    <div className="m3-site-section__head">
      <div>
        {eyebrow ? <div className="m3-eyebrow">{eyebrow}</div> : null}
        <Heading className={level === 1 ? 'm3-site-section__title m3-site-section__title--page' : 'm3-site-section__title'}>{title}</Heading>
        {description ? <p className="m3-page-sub">{description}</p> : null}
      </div>
      {actions ? <div className="m3-row">{actions}</div> : null}
    </div>
  );
}

export interface EventCardProps {
  title: string;
  href: string;
  image: string;
  /** Calendar chip. */
  date: { month: string; day: string };
  /** e.g. "Music - Lusaka". */
  category: string;
  /** e.g. "Lusaka Showgrounds - 17:00". */
  venue: string;
  priceFrom?: string;
  /** Right side of the price row ("In 23 days"). */
  note?: string;
  /** Overlay label in the corner ("Early bird sold out"). */
  ribbon?: string;
  linkAs?: NavLinkComponent;
}

/**
 * Event card. The whole card is one link: the title anchor stretches over the
 * card, so there is a single tab stop and no nested interactive content.
 */
export function EventCard({
  title,
  href,
  image,
  date,
  category,
  venue,
  priceFrom,
  note,
  ribbon,
  linkAs: LinkAs = 'a',
}: EventCardProps) {
  return (
    <article className="m3-event-card">
      <div className="m3-event-card__media">
        {image ? <img src={image} alt="" loading="lazy" /> : null}
        <span className="m3-event-card__date" aria-hidden="true">
          <small>{date.month}</small>
          {date.day}
        </span>
        {ribbon ? (
          <span className="m3-event-card__ribbon">
            <StatusPill tone="info">{ribbon}</StatusPill>
          </span>
        ) : null}
      </div>
      <div className="m3-event-card__body">
        <span className="m3-event-card__meta">{category}</span>
        <h3 className="m3-event-card__title">
          <LinkAs href={href}>
            {title}
            <span className="m3-sr-only">, {`${date.month} ${date.day}`}</span>
          </LinkAs>
        </h3>
        <span className="m3-event-card__meta">{venue}</span>
        {priceFrom || note ? (
          <div className="m3-event-card__price">
            <span>
              {priceFrom ? (
                <>
                  From <b>{priceFrom}</b>
                </>
              ) : null}
            </span>
            {note ? <span className="m3-event-card__meta">{note}</span> : null}
          </div>
        ) : null}
      </div>
    </article>
  );
}

/** Responsive auto-fill grid of cards (250px min column). */
export function EventGrid({ children, label }: { children: ReactNode; label: string }) {
  return (
    <div className="m3-site-grid" role="list" aria-label={label}>
      {children}
    </div>
  );
}

export function CtaBanner({ title, children, action }: { title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <section className="m3-banner-cta" aria-label={title}>
      <div>
        <h2 className="m3-site-section__title">{title}</h2>
        {children ? <p>{children}</p> : null}
      </div>
      {action}
    </section>
  );
}

/* --------------------------------------------------- tickets + checkout */

export interface TierRowProps {
  name: string;
  description?: string;
  /** Formatted price, e.g. "K 100". */
  price: string;
  /** Remaining tickets; 0 means sold out. */
  available: number;
  quantity: number;
  onQuantityChange: (next: number) => void;
  maxPerOrder?: number;
}

/** Ticket tier with a quantity stepper (minus / count / plus). */
export function TierRow({ name, description, price, available, quantity, onQuantityChange, maxPerOrder = 10 }: TierRowProps) {
  const max = Math.min(available, maxPerOrder);
  const soldOut = available <= 0;
  return (
    <div className="m3-tier" role="group" aria-label={name}>
      <div className="m3-tier__info">
        <b>{name}</b>
        {description ? <span className="m3-muted">{description}</span> : null}
        {soldOut ? <StatusPill tone="error">Sold out</StatusPill> : available <= 10 ? <StatusPill tone="warning">{available} left</StatusPill> : null}
      </div>
      <b className="m3-num">{price}</b>
      <div className="m3-tier__qty">
        <IconButton icon="close" label={`Remove one ${name}`} variant="outlined" disabled={quantity <= 0} onClick={() => onQuantityChange(quantity - 1)} />
        <output aria-live="polite" aria-label={`${name} quantity`}>
          {quantity}
        </output>
        <IconButton icon="add" label={`Add one ${name}`} variant="outlined" disabled={soldOut || quantity >= max} onClick={() => onQuantityChange(quantity + 1)} />
      </div>
    </div>
  );
}

export interface OrderSummaryProps {
  title?: string;
  lines: Array<{ label: ReactNode; value: ReactNode }>;
  totalLabel?: string;
  total: ReactNode;
  note?: ReactNode;
  action?: ReactNode;
}
/** Sticky-friendly panel: line items, total, note and the single primary action. */
export function OrderSummary({ title = 'Order summary', lines, totalLabel = 'Total', total, note, action }: OrderSummaryProps) {
  return (
    <aside className="m3-panel" aria-label={title}>
      <h2 className="m3-card__title">{title}</h2>
      <div>
        {lines.map((l, i) => (
          <div key={i} className="m3-order-line">
            <span>{l.label}</span>
            <span className="m3-num">{l.value}</span>
          </div>
        ))}
        <SummaryLine label={<b>{totalLabel}</b>} value={<b className="m3-total">{total}</b>} strong />
      </div>
      {note ? <p className="m3-page-sub">{note}</p> : null}
      {action}
    </aside>
  );
}

/** Deterministic QR-look placeholder. Replace via the `qr` slot of WalletTicketCard with a real code. */
export function QrPlaceholder({ value, label = 'QR code' }: { value: string; label?: string }) {
  const n = 21;
  let h = 2166136261;
  for (let i = 0; i < value.length; i += 1) h = Math.imul(h ^ value.charCodeAt(i), 16777619);
  const cells: Array<[number, number]> = [];
  let s = h >>> 0;
  const finder = (x: number, y: number) =>
    (x < 8 && y < 8) || (x >= n - 8 && y < 8) || (x < 8 && y >= n - 8);
  for (let y = 0; y < n; y += 1)
    for (let x = 0; x < n; x += 1) {
      s = (Math.imul(s, 1664525) + 1013904223) >>> 0;
      if (!finder(x, y) && s >>> 28 > 6) cells.push([x, y]);
    }
  const eye = (x: number, y: number) => (
    <g key={`${x}-${y}`} transform={`translate(${x} ${y})`}>
      <rect width={7} height={7} fill="currentColor" />
      <rect x={1} y={1} width={5} height={5} fill="var(--m3-white)" />
      <rect x={2} y={2} width={3} height={3} fill="currentColor" />
    </g>
  );
  return (
    <svg className="m3-qr" viewBox={`0 0 ${n} ${n}`} role="img" aria-label={label} shapeRendering="crispEdges">
      {cells.map(([x, y]) => (
        <rect key={`${x}-${y}`} x={x} y={y} width={1} height={1} fill="currentColor" />
      ))}
      {eye(0, 0)}
      {eye(n - 7, 0)}
      {eye(0, n - 7)}
    </svg>
  );
}

export interface WalletTicketCardProps {
  eventTitle: string;
  dateLine: string;
  venue: string;
  tier: string;
  holder?: string;
  /** Short human-readable ticket code printed under the QR. */
  code: string;
  status?: string;
  /** Real QR element. Falls back to a placeholder pattern. */
  qr?: ReactNode;
  actions?: ReactNode;
}

/** Ticket stub: details on the left, perforated QR panel on the right (stacks on phones). */
export function WalletTicketCard({ eventTitle, dateLine, venue, tier, holder, code, status, qr, actions }: WalletTicketCardProps) {
  return (
    <article className="m3-ticket" aria-label={`Ticket: ${eventTitle}`}>
      <div className="m3-ticket__main m3-stack">
        <div className="m3-row">
          <StatusPill tone="info">{tier}</StatusPill>
          {status ? <StatusPill status={status} /> : null}
        </div>
        <h3 className="m3-card__title">{eventTitle}</h3>
        <span className="m3-row">
          <Icon name="calendar" /> {dateLine}
        </span>
        <span className="m3-row">
          <Icon name="location" /> {venue}
        </span>
        {holder ? <span className="m3-muted">Holder: {holder}</span> : null}
        {actions ? <div className="m3-row">{actions}</div> : null}
      </div>
      <div className="m3-ticket__stub">
        <div className="m3-stack" style={{ alignItems: 'center' }}>
          {qr ?? <QrPlaceholder value={code} label={`QR code for ticket ${code}`} />}
          <span className={cx('m3-mono')}>{code}</span>
        </div>
      </div>
    </article>
  );
}
