'use client';

import type { ReactNode } from 'react';
import type { NavLinkComponent } from '../components/m3/Navigation';
import { Brand } from './Brand';

export interface SiteNavLink {
  id: string;
  label: string;
  href: string;
  current?: boolean;
}

export interface SiteHeaderProps {
  /** Product name text beside the mark. */
  name: string;
  homeHref?: string;
  links: SiteNavLink[];
  /** Right side: icon tools, cart/resume chips, sign-in. Use SiteTool for each. */
  tools?: ReactNode;
  linkAs?: NavLinkComponent;
}

/** Sticky storefront header on the deep surface. */
export function SiteHeader({ name, homeHref = '/', links, tools, linkAs: LinkAs = 'a' }: SiteHeaderProps) {
  return (
    <header className="m3-site-header">
      <div className="m3-site-wrap m3-site-header__bar">
        <LinkAs href={homeHref} className="m3-site-logo" aria-label={`${name} home`}>
          <Brand />
          {name}
        </LinkAs>
        <nav className="m3-site-nav" aria-label="Main">
          {links.map((l) => (
            <LinkAs key={l.id} href={l.href} aria-current={l.current ? 'page' : undefined}>
              {l.label}
            </LinkAs>
          ))}
        </nav>
        {tools ? <div className="m3-site-tools">{tools}</div> : null}
      </div>
    </header>
  );
}

export interface SiteToolProps {
  label: string;
  children: ReactNode;
  onClick?: () => void;
  href?: string;
  tone?: 'default' | 'accent';
  linkAs?: NavLinkComponent;
}
/** A header tool (bell, theme, cart, sign in): 40px tall, translucent on the deep surface. */
export function SiteTool({ label, children, onClick, href, tone = 'default', linkAs: LinkAs = 'a' }: SiteToolProps) {
  const data = tone === 'accent' ? 'accent' : undefined;
  if (href) {
    return (
      <LinkAs href={href} className="m3-site-tool m3-state" data-tone={data} aria-label={label}>
        {children}
      </LinkAs>
    );
  }
  return (
    <button type="button" className="m3-site-tool m3-state" data-tone={data} aria-label={label} onClick={onClick}>
      {children}
    </button>
  );
}

export interface SiteFooterProps {
  columns: Array<{ heading: string; links?: Array<{ label: string; href: string }>; text?: ReactNode }>;
  legal?: ReactNode;
  linkAs?: NavLinkComponent;
}
/** Storefront footer with link columns and legal line. */
export function SiteFooter({ columns, legal, linkAs: LinkAs = 'a' }: SiteFooterProps) {
  return (
    <footer className="m3-site-footer">
      <div className="m3-site-wrap">
        <div className="m3-site-footer__cols">
          {columns.map((c) => (
            <section key={c.heading} aria-label={c.heading}>
              <h4>{c.heading}</h4>
              {c.text ? <p>{c.text}</p> : null}
              {c.links?.map((l) => (
                <LinkAs key={l.label} href={l.href}>
                  {l.label}
                </LinkAs>
              ))}
            </section>
          ))}
        </div>
        {legal ? <small>{legal}</small> : null}
      </div>
    </footer>
  );
}

export interface PublicLayoutProps {
  header: ReactNode;
  footer?: ReactNode;
  /** Mesh gradient page background (storefront default). */
  mesh?: boolean;
  /** Banner slot under the header (sign-in nudges, resume checkout). */
  banner?: ReactNode;
  children: ReactNode;
}

/** Buyer site frame: skip link, header, main, footer. Pair with data-app="buyer". */
export function PublicLayout({ header, footer, mesh = true, banner, children }: PublicLayoutProps) {
  return (
    <div className={mesh ? 'm3-site-body' : undefined}>
      <a className="m3-skip" href="#m3-main">
        Skip to content
      </a>
      {header}
      {banner}
      <main id="m3-main" tabIndex={-1}>
        {children}
      </main>
      {footer}
    </div>
  );
}
