'use client';

/**
 * Organization Admin sidebar navigation.
 *
 * Layout (spec §4): 280px fixed left, collapsing to an overlay drawer under
 * 1024px, 200ms transform.
 *
 * Surface: the sidebar is a FLAT panel that follows the appearance — not the
 * old dark emerald gradient with a radial glow. Gradients are sanctioned in
 * exactly two places in this system (small accent chips, and the marketing
 * hero) and a 280px navigation column is neither.
 *
 * Active items carry a persistent 2px left accent edge plus a tinted
 * background. Both rules live in `.ds-nav-item[data-active='true']` in
 * global.css rather than as an inline style.
 *
 * Section headers use the micro uppercase label treatment (`.ds-label`) — the
 * one deliberate ALL-CAPS exception in the type system.
 *
 * Security: icons are resolved through a fixed map so a navigation config can
 * never inject an arbitrary component; links come from the same config and are
 * never built from user input.
 */

import { useState, useCallback, useMemo } from 'react';
import { usePathname } from 'next/navigation';
import Link from 'next/link';
import { Box, Flex, Text, ScrollArea } from '@radix-ui/themes';
import {
  HomeSimple,
  Calendar,
  CalendarPlus,
  PageEdit,
  ScanQrCode,
  Group,
  StatsReport,
  GraphUp,
  StatsUpSquare,
  Safe,
  SendDiagonal,
  CreditCard,
  List,
  Community,
  UserPlus,
  Key,
  Building,
  User,
  Bell,
  Xmark,
  NavArrowDown,
} from 'iconoir-react';
import {
  getNavigationForRole,
  isNavItemActive,
  type NavItem,
  type NavSection,
  type OrganizationRole,
} from '@/config/navigation';
import { useSession } from '@/lib/auth/client';
import { useMyOrganization } from '@pml.tickets/shared/api/organization-admin/modules/organization';

// =============================================================================
// ICON MAP — fixed set, no dynamic component resolution.
// =============================================================================

const IconComponents: Record<string, React.ComponentType<{ width?: number; height?: number }>> = {
  HomeSimple,
  Calendar,
  CalendarPlus,
  PageEdit,
  ScanQrCode,
  Group,
  StatsReport,
  GraphUp,
  StatsUpSquare,
  Safe,
  SendDiagonal,
  CreditCard,
  List,
  Community,
  UserPlus,
  Key,
  Building,
  User,
  Bell,
};

function getIcon(iconName: string) {
  return IconComponents[iconName] || HomeSimple;
}

// =============================================================================
// TYPES
// =============================================================================

interface SidebarProps {
  /** Whether the sidebar is collapsed (desktop). */
  collapsed: boolean;
  /** Toggle the collapsed state. */
  onToggle: () => void;
  /** Whether the mobile drawer is open. */
  mobileOpen: boolean;
  /** Close the mobile drawer. */
  onMobileClose: () => void;
  /** Whether we are on a mobile viewport. */
  isMobile: boolean;
}

/** TODO: replace with real counts once the notifications API lands. */
const badgeCounts: Record<string, number> = {
  'events-drafts': 2,
  'finance-payouts': 1,
};

// =============================================================================
// NAV ITEM
// =============================================================================

function NavItemLink({
  item,
  isActive,
  onClick,
}: {
  item: NavItem;
  isActive: boolean;
  onClick?: () => void;
}) {
  const Icon = getIcon(item.icon);
  const badgeCount = item.badge === 'dynamic' ? badgeCounts[item.id] : item.badge;

  return (
    <Link
      href={item.href}
      onClick={onClick}
      className="ds-nav-item"
      data-active={isActive}
      aria-current={isActive ? 'page' : undefined}
      style={{ fontSize: 'var(--text-2-size)' }}
    >
      <span style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)', minWidth: 0 }}>
        <span
          aria-hidden="true"
          style={{
            width: 18,
            height: 18,
            flexShrink: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
          }}
        >
          <Icon width={18} height={18} />
        </span>
        <span style={{ overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
          {item.label}
        </span>
      </span>

      {badgeCount !== undefined && badgeCount > 0 && (
        <span
          className="ds-amount"
          style={{
            minWidth: 18,
            height: 18,
            padding: '0 5px',
            display: 'inline-flex',
            alignItems: 'center',
            justifyContent: 'center',
            borderRadius: 999,
            background: isActive ? 'var(--accent-a5)' : 'var(--gray-a4)',
            color: isActive ? 'var(--accent-11)' : 'var(--gray-11)',
            fontSize: 'var(--label-size)',
            fontWeight: 'var(--weight-semibold)',
          }}
        >
          {badgeCount > 99 ? '99+' : badgeCount}
        </span>
      )}
    </Link>
  );
}

// =============================================================================
// COLLAPSIBLE SECTION
// =============================================================================

function CollapsibleSection({
  section,
  pathname,
  onItemClick,
  defaultExpanded = false,
}: {
  section: NavSection;
  pathname: string;
  onItemClick?: () => void;
  defaultExpanded?: boolean;
}) {
  const hasActiveItem = useMemo(
    () => section.items.some((item) => isNavItemActive(item.href, pathname)),
    [section.items, pathname]
  );

  const [isExpanded, setIsExpanded] = useState(defaultExpanded || hasActiveItem);
  const [userToggled, setUserToggled] = useState(false);

  const handleToggle = useCallback(() => {
    setIsExpanded((prev) => !prev);
    setUserToggled(true);
  }, []);

  const expanded = userToggled ? isExpanded : isExpanded || hasActiveItem;

  const sectionBadgeCount = useMemo(
    () =>
      section.items.reduce((total, item) => {
        const count = item.badge === 'dynamic' ? badgeCounts[item.id] || 0 : item.badge || 0;
        return total + count;
      }, 0),
    [section.items]
  );

  return (
    <Box mb="1">
      <button
        type="button"
        data-testid={`sidebar-section-${section.id}`}
        className="ds-nav-section-header"
        onClick={handleToggle}
        aria-expanded={expanded}
      >
        <span style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-2)' }}>
          <span
            aria-hidden="true"
            style={{
              width: 14,
              height: 14,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'var(--gray-9)',
              transform: expanded ? 'rotate(0deg)' : 'rotate(-90deg)',
              transition: 'transform var(--transition-fast) var(--ease-standard)',
            }}
          >
            <NavArrowDown width={12} height={12} />
          </span>
          <span className="ds-label">{section.title}</span>
        </span>

        {sectionBadgeCount > 0 && !expanded && (
          <span
            className="ds-amount"
            style={{
              minWidth: 18,
              height: 18,
              padding: '0 5px',
              display: 'inline-flex',
              alignItems: 'center',
              justifyContent: 'center',
              borderRadius: 999,
              background: 'var(--gray-a4)',
              color: 'var(--gray-11)',
              fontSize: 'var(--label-size)',
              fontWeight: 'var(--weight-semibold)',
            }}
          >
            {sectionBadgeCount}
          </span>
        )}
      </button>

      {expanded && (
        <Flex direction="column" gap="1" mt="1" ml="2">
          {section.items.map((item) => (
            <NavItemLink
              key={item.id}
              item={item}
              isActive={isNavItemActive(item.href, pathname)}
              onClick={onItemClick}
            />
          ))}
        </Flex>
      )}
    </Box>
  );
}

// =============================================================================
// SIDEBAR
// =============================================================================

/**
 * Organization status → the word and colour shown under the org name.
 *
 * Enums are humanised, never shown raw. Colour is paired with the word so the
 * state is still readable in grayscale and to a colour-blind reader.
 */
const ORG_STATUS: Record<string, { label: string; color: string }> = {
  APPROVED: { label: 'Active', color: 'var(--status-success-11)' },
  PENDING_REVIEW: { label: 'In review', color: 'var(--status-warning-11)' },
  CHANGES_REQUESTED: { label: 'Changes requested', color: 'var(--status-warning-11)' },
  DRAFT: { label: 'Draft', color: 'var(--gray-11)' },
  REJECTED: { label: 'Rejected', color: 'var(--status-danger-11)' },
  SUSPENDED: { label: 'Suspended', color: 'var(--status-danger-11)' },
};

const UNKNOWN_ORG_STATUS = { label: 'Setting up', color: 'var(--gray-11)' };

export function Sidebar({
  collapsed,
  onToggle,
  mobileOpen,
  onMobileClose,
  isMobile,
}: SidebarProps) {
  const pathname = usePathname();
  const { data: session } = useSession();
  const { organization, status: organizationStatus } = useMyOrganization({
    skip: !session?.user,
  });

  const orgName = organization?.name || 'Your organization';
  const orgInitial = orgName.charAt(0).toUpperCase();
  const orgStatus = ORG_STATUS[organizationStatus ?? ''] ?? UNKNOWN_ORG_STATUS;

  // TODO: read the real role from organization membership once it is exposed.
  const userRole: OrganizationRole = 'OWNER';

  const filteredNavigation = useMemo(() => getNavigationForRole(userRole), [userRole]);

  // Always visible on desktop; drawer-controlled on mobile.
  const shouldShow = !isMobile || mobileOpen;

  const handleItemClick = useCallback(() => {
    if (isMobile) onMobileClose();
  }, [isMobile, onMobileClose]);

  // Reserved for the collapsed-rail variant.
  void collapsed;
  void onToggle;

  return (
    <Box
      className="ds-sidebar"
      data-open={shouldShow}
      role="navigation"
      aria-label="Main navigation"
    >
      {/* Organization identity, per the design's sidebar header: a small accent
          chip carrying the org initial, the org name, and its live status.
          The chip is one of the two gradients this system sanctions — it is
          40px, carries no data, and stands in for a logo file that does not
          exist. Never invent a mark. */}
      <Flex
        align="center"
        justify="between"
        gap="3"
        px="4"
        style={{
          height: 'var(--header-height)',
          flexShrink: 0,
          borderBottom: '1px solid var(--gray-a5)',
        }}
      >
        <Flex align="center" gap="3" style={{ minWidth: 0 }}>
          <Box
            aria-hidden="true"
            className="ds-accent-chip"
            style={{
              width: 28,
              height: 28,
              flexShrink: 0,
              borderRadius: 'var(--radius-3)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 'var(--text-2-size)',
              fontWeight: 'var(--weight-bold)',
            }}
          >
            {orgInitial}
          </Box>

          <Box style={{ minWidth: 0 }}>
            <Text
              as="p"
              size="2"
              weight="bold"
              style={{
                color: 'var(--gray-12)',
                lineHeight: 1.2,
                letterSpacing: '-0.01em',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {orgName}
            </Text>
            {/* Status dot + word. The dot is decorative; the word carries the
                meaning, so the state survives grayscale. */}
            <Text
              as="p"
              className="ds-label"
              style={{ color: orgStatus.color, display: 'flex', alignItems: 'center', gap: 4 }}
              data-testid="sidebar-org-status"
            >
              <span
                aria-hidden="true"
                style={{
                  width: 6,
                  height: 6,
                  borderRadius: 'var(--radius-full)',
                  background: 'currentColor',
                  flexShrink: 0,
                }}
              />
              {orgStatus.label}
            </Text>
          </Box>
        </Flex>

        {isMobile && (
          <button
            type="button"
            data-testid="sidebar-close"
            className="ds-nav-section-header"
            onClick={onMobileClose}
            aria-label="Close navigation"
            style={{ width: 'auto', padding: 8 }}
          >
            <Xmark width={20} height={20} />
          </button>
        )}
      </Flex>

      <ScrollArea style={{ flex: 1 }}>
        <Flex direction="column" p="3" gap="1">
          {filteredNavigation.map((section) => (
            <CollapsibleSection
              key={section.id}
              section={section}
              pathname={pathname}
              onItemClick={handleItemClick}
              defaultExpanded={section.id === 'overview' || section.id === 'events'}
            />
          ))}
        </Flex>
      </ScrollArea>

      {/* Signed-in user */}
      <Box p="3" style={{ borderTop: '1px solid var(--gray-a5)' }}>
        <Flex align="center" gap="3">
          <Box
            aria-hidden="true"
            className="ds-accent-chip"
            style={{
              width: 32,
              height: 32,
              flexShrink: 0,
              borderRadius: 'var(--radius-3)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              fontSize: 'var(--text-1-size)',
              fontWeight: 'var(--weight-semibold)',
            }}
          >
            {session?.user?.name?.charAt(0)?.toUpperCase() || 'U'}
          </Box>
          <Box style={{ flex: 1, minWidth: 0 }}>
            <Text
              as="p"
              size="2"
              weight="medium"
              style={{
                color: 'var(--gray-12)',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {session?.user?.name || 'Signed in'}
            </Text>
            <Text
              as="p"
              size="1"
              style={{
                color: 'var(--gray-10)',
                overflow: 'hidden',
                textOverflow: 'ellipsis',
                whiteSpace: 'nowrap',
              }}
            >
              {session?.user?.email || ''}
            </Text>
          </Box>
        </Flex>
      </Box>
    </Box>
  );
}
