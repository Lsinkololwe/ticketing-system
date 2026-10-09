'use client';

import { useEffect, useRef, useState, type ReactNode } from 'react';
import {
  BottomNav,
  NavigationDrawer,
  NavigationRail,
  navToggleEvent,
  type NavigationDrawerProps,
  type NavItem,
  type NavLinkComponent,
} from '../components/m3/Navigation';
import { Icon } from '../components/m3/icons';
import { Brand } from './Brand';

export interface AppShellProps {
  /** Drawer configuration (brand, sections, org card, user card). */
  drawer: Omit<NavigationDrawerProps, 'modal' | 'onClose' | 'onCollapse'>;
  /**
   * Items for the phone bottom bar. Omit to hide it (the drawer is then
   * reachable from the hamburger in PageHeader). Buyer-style and organizer
   * consoles pass their top 4-5 destinations.
   */
  bottomNav?: NavItem[];
  /** Start with the 76px rail instead of the full drawer. */
  defaultCollapsed?: boolean;
  /** Allow the user to collapse the drawer to a rail. */
  collapsible?: boolean;
  skipLinkTarget?: string;
  linkAs?: NavLinkComponent;
  /** Page content: PageHeader first, then sections. */
  children: ReactNode;
  /**
   * Slot rendered after the work area for a SideSheet / Dialog tree so route
   * level overlays share the shell's lifetime.
   */
  overlays?: ReactNode;
}

/**
 * The console frame: navigation drawer (collapses to a rail; modal sheet and
 * optional bottom bar on phones) plus the work area. Render a PageHeader as the
 * first child. Right-hand detail panels are SideSheets, mounted anywhere.
 */
export function AppShell({
  drawer,
  bottomNav,
  defaultCollapsed = false,
  collapsible = true,
  skipLinkTarget = 'm3-main',
  linkAs,
  children,
  overlays,
}: AppShellProps) {
  const [collapsed, setCollapsed] = useState(defaultCollapsed);
  const [mobileOpen, setMobileOpen] = useState(false);
  const ref = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    const open = () => setMobileOpen(true);
    el.addEventListener(navToggleEvent, open);
    return () => el.removeEventListener(navToggleEvent, open);
  }, []);

  const railItems = drawer.sections.flatMap((s) => s.items);

  return (
    <div
      ref={ref}
      className="m3-shell"
      data-nav={collapsed ? 'rail' : undefined}
      data-bottomnav={bottomNav ? 'true' : undefined}
    >
      <a className="m3-skip" href={`#${skipLinkTarget}`}>
        Skip to content
      </a>
      {collapsed ? (
        <NavigationRail
          items={railItems}
          linkAs={linkAs ?? drawer.linkAs}
          label={drawer.label}
          header={
            <>
              <Brand />
              <button
                type="button"
                className="m3-drawer__collapse"
                aria-label="Expand navigation"
                onClick={() => setCollapsed(false)}
              >
                <Icon name="chevron-right" />
              </button>
            </>
          }
        />
      ) : (
        <NavigationDrawer {...drawer} linkAs={linkAs ?? drawer.linkAs} onCollapse={collapsible ? () => setCollapsed(true) : undefined} />
      )}
      {mobileOpen ? (
        <>
          <div className="m3-scrim" onClick={() => setMobileOpen(false)} />
          <NavigationDrawer
            {...drawer}
            linkAs={linkAs ?? drawer.linkAs}
            modal
            onClose={() => setMobileOpen(false)}
            sections={drawer.sections.map((s) => ({
              ...s,
              items: s.items.map((i) => ({
                ...i,
                onSelect: () => {
                  i.onSelect?.();
                  setMobileOpen(false);
                },
              })),
            }))}
          />
        </>
      ) : null}
      <div className="m3-work">
        <main id={skipLinkTarget} className="m3-main" tabIndex={-1}>
          {children}
        </main>
      </div>
      {bottomNav ? <BottomNav items={bottomNav} linkAs={linkAs ?? drawer.linkAs} /> : null}
      {overlays}
    </div>
  );
}
