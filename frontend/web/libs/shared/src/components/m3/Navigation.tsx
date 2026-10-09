'use client';

import { Fragment, useRef, useState, type AnchorHTMLAttributes, type ElementType, type ReactNode } from 'react';
import { IconButton } from './Button';
import { Badge } from './Display';
import { Icon, type IconName } from './icons';
import { cx, useModalBehavior } from './utils';

/** One destination. Use `href` for routes (rendered by `linkAs`) or `onSelect` for actions. */
export interface NavItem {
  id: string;
  label: string;
  icon: IconName;
  href?: string;
  onSelect?: () => void;
  /** Count shown as a badge (pending approvals, unread). */
  badge?: number;
  /** Mark the destination for the current route. */
  current?: boolean;
}

export interface NavSection {
  id: string;
  /** Overline label above the group. */
  label?: string;
  items: NavItem[];
}

/** Component used to render route links, e.g. next/link. Defaults to <a>. */
export type NavLinkComponent = ElementType<AnchorHTMLAttributes<HTMLAnchorElement> & { href: string }>;

function NavEntry({
  item,
  className,
  linkAs: LinkAs = 'a',
  children,
}: {
  item: NavItem;
  className: string;
  linkAs?: NavLinkComponent;
  children: ReactNode;
}) {
  const aria = item.current ? ('page' as const) : undefined;
  if (item.href) {
    return (
      <LinkAs href={item.href} className={className} aria-current={aria} onClick={item.onSelect}>
        {children}
      </LinkAs>
    );
  }
  return (
    <button type="button" className={className} aria-current={aria} onClick={item.onSelect}>
      {children}
    </button>
  );
}

/* ------------------------------------------------------- NavigationDrawer */

export interface NavigationDrawerProps {
  brand: string;
  /** Collapse to a rail (desktop). Omit to hide the control. */
  onCollapse?: () => void;
  /** Organisation/account switcher or any card under the brand. */
  header?: ReactNode;
  /** Prominent accent action ("Create event"). */
  primaryAction?: { label: string; icon: IconName; href?: string; onSelect?: () => void; linkAs?: NavLinkComponent };
  sections: NavSection[];
  /** Bottom group (switch app, theme). */
  utility?: NavItem[];
  /** User card at the very bottom. */
  footer?: ReactNode;
  linkAs?: NavLinkComponent;
  /** Open as a modal sheet (compact screens). */
  modal?: boolean;
  onClose?: () => void;
  label?: string;
}

/** Dark navigation drawer (236px at medium density). */
export function NavigationDrawer({
  brand,
  onCollapse,
  header,
  primaryAction,
  sections,
  utility,
  footer,
  linkAs,
  modal,
  onClose,
  label = 'Main navigation',
}: NavigationDrawerProps) {
  const [node, setNode] = useState<HTMLElement | null>(null);
  useModalBehavior(node, Boolean(modal), onClose);
  const CtaLink = primaryAction?.linkAs ?? linkAs ?? 'a';
  return (
    <aside ref={setNode} className="m3-drawer" data-modal={modal ? 'true' : undefined} {...(modal ? { role: 'dialog', 'aria-modal': true, 'aria-label': label, tabIndex: -1 } : {})}>
      <div className="m3-drawer__head">
        <span className="m3-brandmark" aria-hidden="true" />
        <span className="m3-brandtext">{brand}</span>
        {onCollapse ? (
          <button type="button" className="m3-drawer__collapse" aria-label="Collapse navigation" onClick={onCollapse}>
            <Icon name="chevron-left" />
          </button>
        ) : null}
        {modal && onClose ? (
          <button type="button" className="m3-drawer__collapse" aria-label="Close navigation" onClick={onClose}>
            <Icon name="close" />
          </button>
        ) : null}
      </div>
      {header}
      {primaryAction ? (
        primaryAction.href ? (
          <CtaLink href={primaryAction.href} className="m3-drawer__cta m3-state" onClick={primaryAction.onSelect}>
            <Icon name={primaryAction.icon} />
            {primaryAction.label}
          </CtaLink>
        ) : (
          <button type="button" className="m3-drawer__cta m3-state" onClick={primaryAction.onSelect}>
            <Icon name={primaryAction.icon} />
            {primaryAction.label}
          </button>
        )
      ) : null}
      <nav className="m3-drawer__nav" aria-label={label}>
        {sections.map((s) => (
          <Fragment key={s.id}>
            {s.label ? <span className="m3-drawer__section">{s.label}</span> : null}
            {s.items.map((item) => (
              <NavEntry key={item.id} item={item} className="m3-navitem" linkAs={linkAs}>
                <Icon name={item.icon} />
                <span>{item.label}</span>
                {item.badge ? <Badge count={item.badge} aria-label={`${item.badge} pending`} /> : null}
              </NavEntry>
            ))}
          </Fragment>
        ))}
      </nav>
      {utility && utility.length > 0 ? (
        <div className="m3-drawer__utility">
          {utility.map((item) => (
            <NavEntry key={item.id} item={item} className="m3-navitem" linkAs={linkAs}>
              <Icon name={item.icon} />
              <span>{item.label}</span>
            </NavEntry>
          ))}
        </div>
      ) : null}
      {footer}
    </aside>
  );
}

/** Org or user card for the drawer: avatar, two lines, optional action. */
export function DrawerCard({
  name,
  detail,
  avatar,
  kind = 'org',
  onClick,
  trailing,
}: {
  name: string;
  detail?: string;
  avatar: ReactNode;
  kind?: 'org' | 'user';
  onClick?: () => void;
  trailing?: ReactNode;
}) {
  const inner = (
    <>
      {avatar}
      <span className="m3-drawer__card-text">
        <b>{name}</b>
        {detail ? <small>{detail}</small> : null}
      </span>
      {trailing}
    </>
  );
  return onClick ? (
    <button type="button" className="m3-drawer__card" data-kind={kind} onClick={onClick}>
      {inner}
    </button>
  ) : (
    <div className="m3-drawer__card" data-kind={kind}>
      {inner}
    </div>
  );
}

/* --------------------------------------------- NavigationRail / BottomNav */

export interface NavigationRailProps {
  items: NavItem[];
  linkAs?: NavLinkComponent;
  label?: string;
  /** Leading content (logo mark, FAB). */
  header?: ReactNode;
  footer?: ReactNode;
}

/** Compact vertical rail (76px): icon pill + label. */
export function NavigationRail({ items, linkAs, label = 'Main navigation', header, footer }: NavigationRailProps) {
  return (
    <div className="m3-rail">
      {header}
      <nav aria-label={label} style={{ display: 'contents' }}>
        {items.map((item) => (
          <RailItem key={item.id} item={item} linkAs={linkAs} />
        ))}
      </nav>
      {footer}
    </div>
  );
}

function RailItem({ item, linkAs }: { item: NavItem; linkAs?: NavLinkComponent }) {
  return (
    <NavEntry item={item} className="m3-rail__item" linkAs={linkAs}>
      <span className="m3-rail__pill">
        <Icon name={item.icon} />
        {item.badge ? <Badge count={item.badge} aria-label={`${item.badge} pending`} /> : null}
      </span>
      <span>{item.label}</span>
    </NavEntry>
  );
}

/** Phone bottom bar. Shown only <= 599px by the shell. */
export function BottomNav({ items, linkAs, label = 'Main navigation' }: Omit<NavigationRailProps, 'header' | 'footer'>) {
  return (
    <nav className="m3-bottomnav" aria-label={label}>
      {items.map((item) => (
        <RailItem key={item.id} item={item} linkAs={linkAs} />
      ))}
    </nav>
  );
}

/* ---------------------------------------------------- TopAppBar, PageHeader */

export interface TopAppBarProps {
  title: ReactNode;
  subtitle?: ReactNode;
  /** Leading control (back button, menu toggle). */
  leading?: ReactNode;
  actions?: ReactNode;
  /** Stay pinned under the viewport top while scrolling (default true). */
  sticky?: boolean;
}

/** Page title bar: 60px min height, 20px title, 12px subtitle, actions on the right. */
export function TopAppBar({ title, subtitle, leading, actions, sticky = true }: TopAppBarProps) {
  return (
    <header className="m3-topbar" style={sticky ? undefined : { position: 'static' }}>
      {leading}
      <div className="m3-topbar__title">
        <h1 className="m3-page-title">{title}</h1>
        {subtitle ? <p className="m3-page-sub">{subtitle}</p> : null}
      </div>
      {actions ? <div className="m3-topbar__actions">{actions}</div> : null}
    </header>
  );
}

export interface Crumb {
  label: string;
  href?: string;
}
export interface BreadcrumbsProps {
  items: Crumb[];
  linkAs?: NavLinkComponent;
}
/** Breadcrumb trail; the last crumb is the current page. */
export function Breadcrumbs({ items, linkAs: LinkAs = 'a' }: BreadcrumbsProps) {
  return (
    <nav className="m3-breadcrumbs" aria-label="Breadcrumb">
      <ol>
        {items.map((c, i) => {
          const last = i === items.length - 1;
          return (
            <li key={`${c.label}-${i}`}>
              {c.href && !last ? (
                <LinkAs href={c.href}>{c.label}</LinkAs>
              ) : (
                <span aria-current={last ? 'page' : undefined}>{c.label}</span>
              )}
            </li>
          );
        })}
      </ol>
    </nav>
  );
}

export interface PageHeaderProps extends TopAppBarProps {
  breadcrumbs?: Crumb[];
  linkAs?: NavLinkComponent;
  /** Back button handler: renders a leading icon button. */
  onBack?: () => void;
  backLabel?: string;
}

/** Top app bar plus optional breadcrumbs and back button. The one h1 of the page. */
export function PageHeader({ breadcrumbs, linkAs, onBack, backLabel = 'Back', leading, ...bar }: PageHeaderProps) {
  return (
    <>
      <TopAppBar
        {...bar}
        leading={
          <>
            <NavToggle />
            {onBack ? <IconButton icon="arrow-left" label={backLabel} onClick={onBack} /> : null}
            {leading}
          </>
        }
      />
      {breadcrumbs && breadcrumbs.length > 0 ? <Breadcrumbs items={breadcrumbs} linkAs={linkAs} /> : null}
    </>
  );
}

/* ----------------------------------------------------- shell nav toggle */

/** Hook-free bridge so PageHeader can open the compact drawer without prop drilling. */
export const navToggleEvent = 'm3:toggle-nav';

/** Hamburger shown only on compact screens; AppShell listens for its event. */
export function NavToggle({ className }: { className?: string }) {
  const ref = useRef<HTMLButtonElement>(null);
  return (
    <button
      ref={ref}
      type="button"
      className={cx('m3-iconbtn m3-state m3-navtoggle', className)}
      aria-label="Open navigation"
      onClick={() => ref.current?.dispatchEvent(new CustomEvent(navToggleEvent, { bubbles: true }))}
    >
      <Icon name="menu" />
    </button>
  );
}
