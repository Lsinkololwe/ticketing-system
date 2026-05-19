'use client';

/**
 * Themed Sidebar Navigation
 *
 * Visual design:
 * - Deep gradient background
 * - Subtle violet accent border
 * - Glass-like internal highlights
 * - Smooth hover/active states
 *
 * Responsive:
 * - Fixed on desktop (1024px+)
 * - Slide-in overlay on mobile/tablet
 */

import { usePathname } from 'next/navigation';
import Link from 'next/link';
import { Box, Flex, Text, ScrollArea, Separator } from '@radix-ui/themes';
import {
  HomeSimple,
  Calendar,
  Group,
  Label,
  CreditCard,
  Settings,
  StatsReport,
  Building,
  UserCircle,
  Lock,
  Xmark,
} from 'iconoir-react';

// =============================================================================
// TYPES
// =============================================================================

interface NavItem {
  label: string;
  href: string;
  icon: React.ComponentType<{ className?: string; style?: React.CSSProperties }>;
  roles?: string[];
}

interface NavSection {
  title: string;
  items: NavItem[];
}

interface SidebarProps {
  isOpen: boolean;
  isMobile: boolean;
  onClose: () => void;
}

// =============================================================================
// NAVIGATION CONFIG
// =============================================================================

const navigation: NavSection[] = [
  {
    title: 'Overview',
    items: [
      { label: 'Dashboard', href: '/dashboard', icon: HomeSimple },
      { label: 'Analytics', href: '/dashboard/analytics', icon: StatsReport },
    ],
  },
  {
    title: 'Event Management',
    items: [
      { label: 'Events', href: '/dashboard/events', icon: Calendar },
      { label: 'Categories', href: '/dashboard/categories', icon: Building },
      { label: 'Locations', href: '/dashboard/locations', icon: Building },
    ],
  },
  {
    title: 'User Management',
    items: [
      { label: 'Organizers', href: '/organizers', icon: Group },
      { label: 'Users', href: '/dashboard/users', icon: UserCircle },
      { label: 'Roles & Permissions', href: '/dashboard/permissions', icon: Lock },
    ],
  },
  {
    title: 'Transactions',
    items: [
      { label: 'Tickets', href: '/dashboard/tickets', icon: Label },
      { label: 'Payments', href: '/dashboard/payments', icon: CreditCard },
      { label: 'Refunds', href: '/dashboard/refunds', icon: CreditCard },
      { label: 'Payouts', href: '/dashboard/payouts', icon: CreditCard },
    ],
  },
  {
    title: 'System',
    items: [
      { label: 'Settings', href: '/dashboard/settings', icon: Settings },
    ],
  },
];

// =============================================================================
// NAV ITEM COMPONENT
// =============================================================================

function NavItemComponent({
  item,
  isActive,
  onClick,
}: {
  item: NavItem;
  isActive: boolean;
  onClick?: () => void;
}) {
  const Icon = item.icon;

  return (
    <Link href={item.href} style={{ textDecoration: 'none' }} onClick={onClick}>
      <Flex
        align="center"
        gap="3"
        px="3"
        py="2"
        className="sidebar-nav-item"
        style={{
          borderRadius: '8px',
          backgroundColor: isActive
            ? 'rgba(139, 92, 246, 0.15)'
            : 'transparent',
          color: isActive ? '#A78BFA' : 'rgba(255, 255, 255, 0.7)',
          cursor: 'pointer',
          transition: 'all 150ms ease',
          border: isActive ? '1px solid rgba(139, 92, 246, 0.3)' : '1px solid transparent',
        }}
      >
        <Box
          style={{
            width: 20,
            height: 20,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            color: isActive ? '#A78BFA' : 'rgba(255, 255, 255, 0.5)',
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
    </Link>
  );
}

// =============================================================================
// SIDEBAR COMPONENT
// =============================================================================

export function Sidebar({ isOpen, isMobile, onClose }: SidebarProps) {
  const pathname = usePathname();

  const shouldShow = !isMobile || isOpen;

  return (
    <>
      <Box
        asChild
        className="sidebar-container"
        style={{
          width: '260px',
          height: '100vh',
          position: 'fixed',
          left: 0,
          top: 0,
          zIndex: 50,
          transform: shouldShow ? 'translateX(0)' : 'translateX(-100%)',
          transition: 'transform 200ms ease',
          display: 'flex',
          flexDirection: 'column',
          // Themed gradient background
          background: 'var(--dashboard-sidebar-bg)',
          // Right border with accent glow
          borderRight: '1px solid var(--dashboard-sidebar-border)',
          // Subtle inner shadow for depth
          boxShadow: 'inset -1px 0 0 rgba(255, 255, 255, 0.03)',
        }}
      >
        <aside>
          {/* Decorative gradient overlay at top */}
          <Box
            style={{
              position: 'absolute',
              top: 0,
              left: 0,
              right: 0,
              height: '200px',
              background: 'radial-gradient(ellipse at top, rgba(139, 92, 246, 0.15) 0%, transparent 70%)',
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
            }}
          >
            <Flex align="center" gap="3">
              <Box
                style={{
                  width: '38px',
                  height: '38px',
                  borderRadius: '10px',
                  background: 'linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 0 20px rgba(139, 92, 246, 0.4), inset 0 1px 0 rgba(255,255,255,0.2)',
                }}
              >
                <Label style={{ width: 18, height: 18, color: 'white' }} />
              </Box>
              <Box>
                <Text
                  size="3"
                  weight="bold"
                  style={{ color: '#F8FAFC', lineHeight: 1.2 }}
                >
                  PML Admin
                </Text>
                <Text size="1" style={{ color: 'rgba(255, 255, 255, 0.5)' }}>
                  Ticketing Portal
                </Text>
              </Box>
            </Flex>

            {/* Close button (mobile only) */}
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
              >
                <Xmark style={{ width: 20, height: 20, color: 'rgba(255, 255, 255, 0.7)' }} />
              </Box>
            )}
          </Flex>

          {/* Navigation */}
          <ScrollArea
            style={{
              flex: 1,
              height: 'calc(100vh - 64px)',
              position: 'relative',
              zIndex: 1,
            }}
          >
            <Flex direction="column" gap="1" p="3">
              {navigation.map((section, sectionIndex) => (
                <Box key={section.title}>
                  {sectionIndex > 0 && (
                    <Separator
                      size="4"
                      my="3"
                      style={{ backgroundColor: 'rgba(255, 255, 255, 0.06)' }}
                    />
                  )}
                  <Text
                    size="1"
                    weight="medium"
                    style={{
                      color: 'rgba(255, 255, 255, 0.4)',
                      textTransform: 'uppercase',
                      letterSpacing: '0.08em',
                      padding: '8px 12px 4px',
                      display: 'block',
                    }}
                  >
                    {section.title}
                  </Text>
                  <Flex direction="column" gap="1" mt="1">
                    {section.items.map((item) => {
                      const isActive =
                        pathname === item.href ||
                        (item.href !== '/dashboard' && pathname.startsWith(item.href));

                      return (
                        <NavItemComponent
                          key={item.href}
                          item={item}
                          isActive={isActive}
                          onClick={isMobile ? onClose : undefined}
                        />
                      );
                    })}
                  </Flex>
                </Box>
              ))}
            </Flex>

            {/* Bottom decoration */}
            <Box
              style={{
                padding: '16px',
                marginTop: '16px',
                borderTop: '1px solid rgba(255, 255, 255, 0.06)',
              }}
            >
              <Box
                style={{
                  padding: '12px',
                  borderRadius: '10px',
                  background: 'rgba(139, 92, 246, 0.1)',
                  border: '1px solid rgba(139, 92, 246, 0.2)',
                }}
              >
                <Text size="1" style={{ color: 'rgba(255, 255, 255, 0.6)' }}>
                  Need help?
                </Text>
                <Text
                  size="1"
                  weight="medium"
                  style={{ color: '#A78BFA', cursor: 'pointer' }}
                >
                  View documentation →
                </Text>
              </Box>
            </Box>
          </ScrollArea>
        </aside>
      </Box>

      {/* Hover styles */}
      <style jsx global>{`
        .sidebar-nav-item:hover {
          background-color: rgba(255, 255, 255, 0.05) !important;
          color: rgba(255, 255, 255, 0.9) !important;
        }
        .sidebar-close-btn:hover {
          background-color: rgba(255, 255, 255, 0.1);
        }
      `}</style>
    </>
  );
}
