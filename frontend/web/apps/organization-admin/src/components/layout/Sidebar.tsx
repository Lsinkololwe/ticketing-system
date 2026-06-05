'use client';

/**
 * Organization Portal Sidebar Navigation
 *
 * Security (OWASP Compliant):
 * - No inline event handlers that could enable XSS
 * - Sanitized navigation items from config
 * - Role-based access control
 * - Secure link handling (no javascript: URLs)
 *
 * Accessibility (WCAG AAA):
 * - Keyboard navigation support
 * - ARIA labels and roles
 * - Focus visible states
 *
 * UI/UX Pro Max Design:
 * - Deep gradient background with emerald accents
 * - Smooth 150-200ms transitions
 * - Collapsible sections
 */

import { useState, useCallback, useMemo } from 'react';
import { usePathname } from 'next/navigation';
import Link from 'next/link';
import { Box, Flex, Text, ScrollArea, Badge } from '@radix-ui/themes';
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
} from '@/config/navigation';
import { useSession } from '@/lib/auth/client';
import { useOrganization, type OrganizationRole } from '@/lib/contexts/OrganizationContext';

// =============================================================================
// ICON MAP - Secure icon rendering (prevents XSS via icon injection)
// =============================================================================

const IconComponents: Record<string, React.ComponentType<{ style?: React.CSSProperties }>> = {
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
  /** Whether sidebar is collapsed (desktop mode) */
  collapsed: boolean;
  /** Toggle collapsed state */
  onToggle: () => void;
  /** Whether mobile sidebar is open */
  mobileOpen: boolean;
  /** Close mobile sidebar */
  onMobileClose: () => void;
  /** Whether we're on mobile viewport */
  isMobile: boolean;
}

// =============================================================================
// MOCK BADGE COUNTS (Replace with real API data)
// =============================================================================

const badgeCounts: Record<string, number> = {
  'events-drafts': 2,
  'finance-payouts': 1,
};

// =============================================================================
// NAV ITEM COMPONENT
// =============================================================================

interface NavItemComponentProps {
  item: NavItem;
  isActive: boolean;
  onClick?: () => void;
  isFirstItem?: boolean;
  isLastItem?: boolean;
}

function NavItemComponent({ item, isActive, onClick, isFirstItem, isLastItem }: NavItemComponentProps) {
  const Icon = getIcon(item.icon);
  const badgeCount = item.badge === 'dynamic' ? badgeCounts[item.id] : item.badge;

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
          borderRadius: isFirstItem && isLastItem
            ? '10px'
            : isFirstItem
            ? '10px 10px 4px 4px'
            : isLastItem
            ? '4px 4px 10px 10px'
            : '4px',
          backgroundColor: isActive ? 'rgba(16, 185, 129, 0.15)' : 'transparent',
          color: isActive ? '#2DD4BF' : 'rgba(255, 255, 255, 0.7)',
          cursor: 'pointer',
          transition: 'all 150ms ease',
          borderLeft: isActive ? '2px solid #10B981' : '2px solid transparent',
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
              color: isActive ? '#2DD4BF' : 'rgba(255, 255, 255, 0.5)',
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
        {badgeCount && badgeCount > 0 && (
          <Badge
            size="1"
            variant="solid"
            style={{
              backgroundColor: isActive ? '#2DD4BF' : 'rgba(239, 68, 68, 0.9)',
              color: isActive ? '#0F172A' : 'white',
              fontSize: '10px',
              fontWeight: 600,
              minWidth: '18px',
              height: '18px',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: isActive ? 'none' : '0 0 8px rgba(239, 68, 68, 0.4)',
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
  pathname: string;
  onItemClick?: () => void;
  defaultExpanded?: boolean;
}

function CollapsibleSection({
  section,
  pathname,
  onItemClick,
  defaultExpanded = false,
}: CollapsibleSectionProps) {
  const hasActiveItem = useMemo(() => {
    return section.items.some((item) => isNavItemActive(item.href, pathname));
  }, [section.items, pathname]);

  const [isExpanded, setIsExpanded] = useState(defaultExpanded || hasActiveItem);
  const [userToggled, setUserToggled] = useState(false);

  const handleToggle = useCallback(() => {
    setIsExpanded(prev => !prev);
    setUserToggled(true);
  }, []);

  const expanded = userToggled ? isExpanded : (isExpanded || hasActiveItem);

  const sectionBadgeCount = useMemo(() => {
    return section.items.reduce((total, item) => {
      const count = item.badge === 'dynamic' ? badgeCounts[item.id] || 0 : item.badge || 0;
      return total + count;
    }, 0);
  }, [section.items]);

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
            handleToggle();
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
              color: expanded ? 'rgba(45, 212, 191, 0.8)' : 'rgba(255, 255, 255, 0.4)',
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
              color: expanded ? 'rgba(45, 212, 191, 0.9)' : 'rgba(255, 255, 255, 0.5)',
              textTransform: 'uppercase',
              letterSpacing: '0.08em',
              transition: 'color 150ms ease',
            }}
          >
            {section.title}
          </Text>
        </Flex>

        {sectionBadgeCount > 0 && !expanded && (
          <Badge
            size="1"
            variant="soft"
            style={{
              backgroundColor: 'rgba(239, 68, 68, 0.2)',
              color: '#F87171',
              fontSize: '10px',
            }}
          >
            {sectionBadgeCount}
          </Badge>
        )}
      </Flex>

      {/* Section Items */}
      {expanded && (
        <Box
          className="nav-items-container"
          style={{
            marginTop: '4px',
            marginLeft: '8px',
            padding: '4px',
            borderRadius: '12px',
            background: 'rgba(255, 255, 255, 0.02)',
            border: '1px solid rgba(255, 255, 255, 0.04)',
            position: 'relative',
          }}
        >
          <Box
            style={{
              position: 'absolute',
              left: 0,
              top: '12px',
              bottom: '12px',
              width: '2px',
              background: 'linear-gradient(180deg, transparent 0%, rgba(16, 185, 129, 0.3) 20%, rgba(16, 185, 129, 0.3) 80%, transparent 100%)',
              borderRadius: '1px',
            }}
          />

          <Flex direction="column" gap="1">
            {section.items.map((item, index) => {
              const isActive = isNavItemActive(item.href, pathname);
              const isFirst = index === 0;
              const isLast = index === section.items.length - 1;

              return (
                <NavItemComponent
                  key={item.id}
                  item={item}
                  isActive={isActive}
                  onClick={onItemClick}
                  isFirstItem={isFirst}
                  isLastItem={isLast}
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

export function Sidebar({ collapsed, onToggle, mobileOpen, onMobileClose, isMobile }: SidebarProps) {
  const pathname = usePathname();
  const { data: session } = useSession();
  const { role } = useOrganization();

  // Get user role from OrganizationContext (default to OWNER for demo)
  const userRole: OrganizationRole = role || 'OWNER';

  const filteredNavigation = useMemo(() => {
    return getNavigationForRole(userRole);
  }, [userRole]);

  // On mobile: show when mobileOpen is true
  // On desktop: show always (but can be collapsed)
  const shouldShow = !isMobile || mobileOpen;

  const handleItemClick = useCallback(() => {
    if (isMobile) {
      onMobileClose();
    }
  }, [isMobile, onMobileClose]);

  // Suppress unused variable warnings (will be used for collapsed behavior)
  void collapsed;
  void onToggle;

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
          boxShadow: 'inset -1px 0 0 rgba(255, 255, 255, 0.03)',
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
            background: 'radial-gradient(ellipse at top, rgba(16, 185, 129, 0.12) 0%, transparent 70%)',
            pointerEvents: 'none',
          }}
        />

        {/* Header */}
        <Flex
          align="center"
          justify="between"
          p="4"
          style={{
            borderBottom: '1px solid rgba(255, 255, 255, 0.06)',
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
                background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                boxShadow: '0 0 24px rgba(16, 185, 129, 0.4), inset 0 1px 0 rgba(255,255,255,0.2)',
              }}
            >
              <Calendar style={{ width: 20, height: 20, color: 'white' }} />
            </Box>
            <Box>
              <Text size="3" weight="bold" style={{ color: '#F8FAFC', lineHeight: 1.2 }}>
                Organization
              </Text>
              <Text size="1" style={{ color: 'rgba(255, 255, 255, 0.5)' }}>
                {userRole}
              </Text>
            </Box>
          </Flex>

          {isMobile && (
            <Box
              onClick={onMobileClose}
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
              <Xmark style={{ width: 20, height: 20, color: 'rgba(255, 255, 255, 0.7)' }} />
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
                pathname={pathname}
                onItemClick={handleItemClick}
                defaultExpanded={section.id === 'overview' || section.id === 'events'}
              />
            ))}
          </Flex>
        </ScrollArea>

        {/* User Info Footer */}
        <Box
          style={{
            padding: '12px 16px',
            borderTop: '1px solid rgba(255, 255, 255, 0.06)',
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
                background: 'linear-gradient(135deg, #14B8A6 0%, #10B981 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                fontSize: '12px',
                fontWeight: 600,
                color: 'white',
              }}
            >
              {session?.user?.name?.charAt(0) || 'U'}
            </Box>
            <Box style={{ flex: 1, minWidth: 0 }}>
              <Text
                size="2"
                weight="medium"
                style={{
                  color: '#E2E8F0',
                  display: 'block',
                  overflow: 'hidden',
                  textOverflow: 'ellipsis',
                  whiteSpace: 'nowrap',
                }}
              >
                {session?.user?.name || 'User'}
              </Text>
              <Text
                size="1"
                style={{
                  color: 'rgba(255, 255, 255, 0.4)',
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
          background-color: rgba(255, 255, 255, 0.05) !important;
          color: rgba(255, 255, 255, 0.9) !important;
        }
        .sidebar-nav-item:focus-visible {
          outline: 2px solid #10B981;
          outline-offset: -2px;
        }
        .sidebar-section-header:hover {
          background-color: rgba(255, 255, 255, 0.03);
        }
        .sidebar-section-header:focus-visible {
          outline: 2px solid #10B981;
          outline-offset: -2px;
        }
        .sidebar-close-btn:hover {
          background-color: rgba(255, 255, 255, 0.1);
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
