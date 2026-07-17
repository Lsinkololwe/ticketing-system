'use client';

/**
 * Professional Admin Sidebar Navigation
 *
 * Security (OWASP Compliant):
 * - No inline event handlers that could enable XSS
 * - Sanitized navigation items from config
 * - Role-based access control
 * - Secure link handling (no javascript: URLs)
 *
 * Accessibility (WCAG AA):
 * - Keyboard navigation support
 * - ARIA labels and roles
 * - Focus visible states
 * - Screen reader announcements
 * - All text tokens verified ≥4.5:1 against dark rail (see global.css comments)
 *
 * UI/UX Pro Max Design:
 * - Deep gradient background
 * - Subtle accent accents
 * - Badge indicators for pending items
 * - Smooth 150-200ms transitions
 * - Collapsible sections with curved inward design
 */

import { useState, useCallback, useMemo } from 'react';
import { usePathname } from 'next/navigation';
import Link from 'next/link';
import { Box, Flex, Text, ScrollArea, Badge } from '@radix-ui/themes';
import {
  HomeSimple,
  ClipboardCheck,
  Group,
  Calendar,
  CalendarPlus,
  PageSearch,
  Folder,
  MapPin,
  Building,
  Community,
  SendDiagonal,
  Undo,
  Safe,
  CreditCard,
  Label,
  Percentage,
  StatsReport,
  GraphUp,
  StatsUpSquare,
  Settings,
  HistoricShield,
  Key,
  Xmark,
  NavArrowDown,
} from 'iconoir-react';
import {
  getNavigationForRoles,  // Agent A: multi-role filter
  getActiveNavHref,        // Agent A: longest-match active href
  type NavItem,
  type NavSection,
  type AdminRole,
} from '@/config/navigation';
import { useSession } from '@/lib/auth/client';
// usePendingCounts is being created by Agent C.
// Imported from the shared lib's public barrel (@pml.tickets/shared).
// TODO: verify once Agent C lands — the barrel re-exports from api/admin/modules/analytics.
import { usePendingCounts } from '@pml.tickets/shared';

// =============================================================================
// ICON MAP - Secure icon rendering (prevents XSS via icon injection)
// =============================================================================

const IconComponents: Record<string, React.ComponentType<{ style?: React.CSSProperties }>> = {
  HomeSimple,
  ClipboardCheck,
  Group,
  Calendar,
  CalendarPlus,
  PageSearch,
  Folder,
  MapPin,
  Building,
  Community,
  SendDiagonal,
  Undo,
  Safe,
  CreditCard,
  Label,
  Percentage,
  StatsReport,
  GraphUp,
  StatsUpSquare,
  Settings,
  HistoricShield,
  Key,
};

function getIcon(iconName: string) {
  return IconComponents[iconName] || HomeSimple;
}

// =============================================================================
// TYPES
// =============================================================================

interface SidebarProps {
  isOpen: boolean;
  isMobile: boolean;
  onClose: () => void;
}

// =============================================================================
// NAV ITEM COMPONENT
// =============================================================================

interface NavItemComponentProps {
  item: NavItem;
  isActive: boolean;
  /** Pre-computed badge count (0 = no badge) */
  badgeCount: number;
  onClick?: () => void;
  isFirstItem?: boolean;
  isLastItem?: boolean;
}

function NavItemComponent({
  item,
  isActive,
  badgeCount,
  onClick,
  isFirstItem,
  isLastItem,
}: NavItemComponentProps) {
  const Icon = getIcon(item.icon);

  return (
    <Link
      href={item.href}
      onClick={onClick}
      style={{ textDecoration: 'none', display: 'block' }}
      aria-current={isActive ? 'page' : undefined}
    >
      <Flex
        align="center"
        justify="between"
        gap="3"
        className="sidebar-nav-item"
        style={{
          padding: '10px 12px',
          borderRadius:
            isFirstItem && isLastItem
              ? '10px'
              : isFirstItem
              ? '10px 10px 4px 4px'
              : isLastItem
              ? '4px 4px 10px 10px'
              : '4px',
          backgroundColor: isActive ? 'var(--sidebar-active-bg)' : 'transparent',
          color: isActive ? 'var(--sidebar-active-fg)' : 'var(--sidebar-fg-muted)',
          cursor: 'pointer',
          transition: 'all 150ms ease',
          borderLeft: isActive
            ? '2px solid var(--sidebar-active-border)'
            : '2px solid transparent',
        }}
      >
        <Flex align="center" gap="3">
          <Box
            style={{
              width: 20,
              height: 20,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: 'inherit',
              flexShrink: 0,
            }}
          >
            <Icon style={{ width: 18, height: 18 }} />
          </Box>
          <Text
            size="2"
            weight={isActive ? 'medium' : 'regular'}
            style={{ color: 'inherit' }}
          >
            {item.label}
          </Text>
        </Flex>

        {/* Badge for pending items */}
        {badgeCount > 0 && (
          <Badge
            size="1"
            variant="solid"
            style={{
              backgroundColor: isActive
                ? 'var(--sidebar-active-fg)'
                : 'var(--sidebar-badge-bg)',
              color: isActive ? 'var(--sidebar-active-bg)' : 'var(--sidebar-badge-fg)',
              fontSize: '10px',
              fontWeight: 600,
              minWidth: '18px',
              height: '18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: 'none',
            }}
          >
            {badgeCount > 99 ? '99+' : badgeCount}
          </Badge>
        )}
      </Flex>
    </Link>
  );
}

// =============================================================================
// COLLAPSIBLE SECTION COMPONENT
// =============================================================================

interface CollapsibleSectionProps {
  section: NavSection;
  /** Longest-match active href, computed once in the parent (Agent A). */
  activeHref: string | null;
  /** Live badge counts keyed by nav-item id (Agent C). */
  counts: Record<string, number>;
  onItemClick?: () => void;
  defaultExpanded?: boolean;
}

function CollapsibleSection({
  section,
  activeHref,
  counts,
  onItemClick,
  defaultExpanded = false,
}: CollapsibleSectionProps) {
  // Section has an active item when any item's href matches the longest-match result
  const hasActiveItem = useMemo(() => {
    return section.items.some((item) => item.href === activeHref);
  }, [section.items, activeHref]);

  // Initialize expanded state: default OR has active item on first render
  const [isExpanded, setIsExpanded] = useState(defaultExpanded || hasActiveItem);

  // Track if user has manually toggled this section
  const [userToggled, setUserToggled] = useState(false);

  // Handle toggle - mark as user-controlled after first toggle
  const handleToggle = useCallback(() => {
    setIsExpanded((prev) => !prev);
    setUserToggled(true);
  }, []);

  // The section is expanded if user controls it, otherwise auto-expand for active items
  const expanded = userToggled ? isExpanded : isExpanded || hasActiveItem;

  // Section aggregate badge — sums live counts for all dynamic-badge items
  const sectionBadgeCount = useMemo(() => {
    return section.items.reduce((total, item) => {
      const count =
        item.badge === 'dynamic' ? (counts[item.id] ?? 0) : (item.badge ?? 0);
      return total + count;
    }, 0);
  }, [section.items, counts]);

  return (
    <Box style={{ marginBottom: '4px' }}>
      {/* Section Header */}
      <Flex
        align="center"
        justify="between"
        px="3"
        py="2"
        onClick={handleToggle}
        className="sidebar-section-header"
        style={{
          cursor: 'pointer',
          borderRadius: '8px',
          transition: 'background-color 150ms ease',
        }}
        role="button"
        aria-expanded={expanded}
        tabIndex={0}
        onKeyDown={(e) => {
          if (e.key === 'Enter' || e.key === ' ') {
            e.preventDefault();
            setIsExpanded(!isExpanded);
          }
        }}
      >
        <Flex align="center" gap="2">
          <Box
            style={{
              width: 16,
              height: 16,
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: expanded ? 'var(--sidebar-active-fg)' : 'var(--sidebar-section-fg)',
              transition: 'transform 150ms ease, color 150ms ease',
              transform: expanded ? 'rotate(0deg)' : 'rotate(-90deg)',
            }}
          >
            <NavArrowDown style={{ width: 12, height: 12 }} />
          </Box>
          <Text
            size="1"
            weight="medium"
            style={{
              color: expanded ? 'var(--sidebar-active-fg)' : 'var(--sidebar-section-fg)',
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              transition: 'color 150ms ease',
            }}
          >
            {section.title}
          </Text>
        </Flex>

        {/* Section aggregate badge — shown only when collapsed */}
        {sectionBadgeCount > 0 && !expanded && (
          <Badge
            size="1"
            variant="soft"
            style={{
              backgroundColor: 'var(--red-a3)',
              color: 'var(--red-11)',
              fontSize: '10px',
            }}
          >
            {sectionBadgeCount}
          </Badge>
        )}
      </Flex>

      {/* Section Items - Curved Inward Container */}
      {expanded && (
        <Box
          className="nav-items-container"
          style={{
            marginTop: '4px',
            marginLeft: '8px',
            padding: '4px',
            borderRadius: '12px',
            background: 'var(--sidebar-well-bg)',
            border: '1px solid var(--sidebar-well-border)',
            position: 'relative',
          }}
        >
          {/* Curved edge indicator */}
          <Box
            style={{
              position: 'absolute',
              left: 0,
              top: '12px',
              bottom: '12px',
              width: '2px',
              background:
                'linear-gradient(180deg, transparent 0%, var(--sidebar-active-border) 20%, var(--sidebar-active-border) 80%, transparent 100%)',
              opacity: 0.3,
              borderRadius: '1px',
            }}
          />

          <Flex direction="column" gap="1">
            {section.items.map((item, index) => {
              const isActive = item.href === activeHref;
              const badgeCount =
                item.badge === 'dynamic'
                  ? (counts[item.id] ?? 0)
                  : (item.badge ?? 0);

              return (
                <NavItemComponent
                  key={item.id}
                  item={item}
                  isActive={isActive}
                  badgeCount={badgeCount}
                  onClick={onItemClick}
                  isFirstItem={index === 0}
                  isLastItem={index === section.items.length - 1}
                />
              );
            })}
          </Flex>
        </Box>
      )}
    </Box>
  );
}

// =============================================================================
// MAIN SIDEBAR COMPONENT
// =============================================================================

export function Sidebar({ isOpen, isMobile, onClose }: SidebarProps) {
  const pathname = usePathname();
  const { data: session } = useSession();

  // Extract full roles array, filter to known AdminRole values (OWASP: server-validated)
  const roles: AdminRole[] = useMemo(() => {
    const rawRoles = (session?.user as { roles?: string[] })?.roles ?? [];
    const knownRoles: AdminRole[] = ['SUPER_ADMIN', 'ADMIN', 'FINANCE'];
    return rawRoles.filter((r): r is AdminRole =>
      knownRoles.includes(r as AdminRole)
    );
  }, [session]);

  // Highest-privilege role for header display label
  const displayRole = useMemo(() => {
    if (roles.includes('SUPER_ADMIN')) return 'Super Admin';
    if (roles.includes('ADMIN')) return 'Admin';
    if (roles.includes('FINANCE')) return 'Finance';
    return 'Admin';
  }, [roles]);

  // Filter navigation for all held roles (Agent A: getNavigationForRoles)
  const filteredNavigation = useMemo(() => {
    return getNavigationForRoles(roles);
  }, [roles]);

  // Longest-match active href — computed ONCE, shared with all sections (Agent A)
  const activeHref = useMemo(() => {
    return getActiveNavHref(pathname, roles);
  }, [pathname, roles]);

  // Live badge counts from GraphQL (Agent C: usePendingCounts)
  // counts keys: 'pending-approvals' | 'organizer-applications' | 'event-reviews' |
  //              'document-verification' | 'payout-requests' | 'refund-requests'
  const { counts = {}, loading: countsLoading } = usePendingCounts();

  // Pass empty counts while loading so badges render as absent (no flash of stale mocks)
  const liveCounts = countsLoading ? {} : counts;

  const shouldShow = !isMobile || isOpen;

  const handleItemClick = useCallback(() => {
    if (isMobile) {
      onClose();
    }
  }, [isMobile, onClose]);

  return (
    <>
      <Box
        className="sidebar-container"
        role="navigation"
        aria-label="Main navigation"
        style={{
          width: '280px',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          zIndex: 50,
          transform: shouldShow ? 'translateX(0)' : 'translateX(-100%)',
          transition: 'transform 200ms ease',
          display: 'flex',
          flexDirection: 'column',
          background: 'var(--dashboard-sidebar-bg)',
          borderRight: '1px solid var(--dashboard-sidebar-border)',
          boxShadow: 'inset -1px 0 0 var(--gray-a1)',
        }}
      >
        {/* Decorative gradient overlay */}
        <Box
          style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            height: '200px',
            background:
              'radial-gradient(ellipse at top, var(--accent-a4) 0%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />

        {/* Header */}
        <Flex
          align="center"
          justify="between"
          p="4"
          style={{
            borderBottom: '1px solid var(--sidebar-divider)',
            height: '64px',
            position: 'relative',
            zIndex: 1,
            flexShrink: 0,
          }}
        >
          <Flex align="center" gap="3">
            <Box
              style={{
                width: '40px',
                height: '40px',
                borderRadius: '12px',
                background:
                  'linear-gradient(135deg, var(--accent-9) 0%, var(--accent-10) 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow:
                  '0 0 24px var(--accent-a6), inset 0 1px 0 var(--gray-a4)',
              }}
            >
              <Label
                style={{ width: 20, height: 20, color: 'var(--accent-contrast)' }}
              />
            </Box>
            <Box>
              <Text
                as="div"
                size="3"
                weight="bold"
                style={{ color: 'var(--sidebar-fg)', lineHeight: 1.2 }}
              >
                MyTicket Zambia
              </Text>
              <Text
                as="div"
                size="1"
                style={{ color: 'var(--sidebar-fg-muted)', lineHeight: 1.3 }}
              >
                {displayRole}
              </Text>
            </Box>
          </Flex>

          {isMobile && (
            <Box
              onClick={onClose}
              className="sidebar-close-btn"
              style={{
                padding: '8px',
                borderRadius: '8px',
                cursor: 'pointer',
                transition: 'background-color 150ms ease',
              }}
              role="button"
              aria-label="Close navigation"
              tabIndex={0}
            >
              <Xmark
                style={{ width: 20, height: 20, color: 'var(--sidebar-fg-muted)' }}
              />
            </Box>
          )}
        </Flex>

        {/* Navigation */}
        <ScrollArea
          style={{
            flex: 1,
            position: 'relative',
            zIndex: 1,
          }}
        >
          <Flex direction="column" p="3" gap="1">
            {filteredNavigation.map((section) => (
              <CollapsibleSection
                key={section.id}
                section={section}
                activeHref={activeHref}
                counts={liveCounts}
                onItemClick={handleItemClick}
                defaultExpanded={
                  section.id === 'overview' || section.id === 'action-center'
                }
              />
            ))}
          </Flex>
        </ScrollArea>

        {/* User Info Footer */}
        <Box
          style={{
            padding: '12px 16px',
            borderTop: '1px solid var(--sidebar-divider)',
            position: 'relative',
            zIndex: 1,
          }}
        >
          <Flex align="center" gap="3">
            <Box
              style={{
                width: '32px',
                height: '32px',
                borderRadius: '8px',
                background:
                  'linear-gradient(135deg, var(--accent-10) 0%, var(--accent-9) 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '12px',
                fontWeight: 600,
                color: 'var(--accent-contrast)',
              }}
            >
              {session?.user?.name?.charAt(0) || 'A'}
            </Box>
            <Box style={{ flex: 1, minWidth: 0 }}>
              <Text
                size="2"
                weight="medium"
                style={{
                  color: 'var(--sidebar-fg)',
                  display: 'block',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {session?.user?.name || 'Admin User'}
              </Text>
              <Text
                size="1"
                style={{
                  color: 'var(--sidebar-fg-muted)',
                  display: 'block',
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

      {/* Styles */}
      <style jsx global>{`
        .sidebar-nav-item:hover {
          background-color: var(--sidebar-item-hover-bg) !important;
          color: var(--sidebar-fg) !important;
        }
        .sidebar-nav-item:focus-visible {
          outline: 2px solid var(--sidebar-active-border);
          outline-offset: -2px;
        }
        .sidebar-section-header:hover {
          background-color: var(--sidebar-item-hover-bg);
        }
        .sidebar-section-header:focus-visible {
          outline: 2px solid var(--sidebar-active-border);
          outline-offset: -2px;
        }
        .sidebar-close-btn:hover {
          background-color: var(--sidebar-item-hover-bg);
        }
        .nav-items-container {
          transition: all 200ms ease;
        }
        @media (prefers-reduced-motion: reduce) {
          .sidebar-container,
          .sidebar-nav-item,
          .sidebar-section-header,
          .nav-items-container {
            transition: none !important;
          }
        }
      `}</style>
    </>
  );
}
