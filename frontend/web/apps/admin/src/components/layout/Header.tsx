'use client';

/**
 * Glassmorphic Header Component
 *
 * Visual design:
 * - Frosted glass effect with backdrop blur
 * - Subtle border with accent color
 * - Professional user menu
 *
 * Features:
 * - Mobile menu toggle
 * - Theme switcher
 * - Notifications
 * - User dropdown with logout
 */

import { Box, Flex, Text, Button, DropdownMenu, Avatar, IconButton } from '@radix-ui/themes';
import { Bell, LogOut, Settings, UserCircle, Menu, NavArrowDown } from 'iconoir-react';
import { useSession, signOut } from '@/lib/auth/client';
import { ThemeToggleDropdown } from '@/components/ui/ThemeToggle';

// =============================================================================
// TYPES
// =============================================================================

interface HeaderProps {
  onMenuClick?: () => void;
  showMenuButton?: boolean;
}

// =============================================================================
// HEADER COMPONENT
// =============================================================================

export function Header({ onMenuClick, showMenuButton = false }: HeaderProps) {
  const { data: session } = useSession();

  const handleLogout = async () => {
    try {
      await signOut();
    } catch (error) {
      console.error('Logout failed:', error);
    }
  };

  const userName = session?.user?.name || 'Admin User';
  const userEmail = session?.user?.email || '';

  const userInitials = userName
    .split(' ')
    .map((n: string) => n[0])
    .join('')
    .toUpperCase()
    .slice(0, 2);

  return (
    <>
      {/* 64px fixed top bar. `.ds-glass-header` supplies the frosted
          background — header bars are one of only two surfaces in the system
          allowed to use blur. */}
      <Box
        asChild
        className="dashboard-header ds-glass-header"
        style={{
          height: '64px',
          position: 'sticky',
          top: 0,
          zIndex: 30,
          borderBottom: '1px solid var(--dashboard-header-border)',
        }}
      >
        <header>
          <Flex
            align="center"
            justify="between"
            px="4"
            style={{ height: '100%' }}
          >
            {/* Left: Menu Button (mobile) */}
            <Flex align="center" gap="3">
              {showMenuButton && (
                <IconButton
                  variant="ghost"
                  size="2"
                  onClick={onMenuClick}
                  style={{ flexShrink: 0 }}
                >
                  <Menu style={{ width: 20, height: 20 }} />
                </IconButton>
              )}
            </Flex>

            {/* Right: Actions */}
            <Flex align="center" gap="2">
              {/* Theme Toggle */}
              <ThemeToggleDropdown />

              {/* Notifications */}
              <IconButton
                variant="ghost"
                size="2"
                className="header-icon-btn"
                style={{ position: 'relative' }}
              >
                <Bell style={{ width: 18, height: 18 }} />
                {/* Unread dot — the danger status role, not a raw red. */}
                <Box
                  style={{
                    position: 'absolute',
                    top: '6px',
                    right: '6px',
                    width: '8px',
                    height: '8px',
                    backgroundColor: 'var(--status-danger-9)',
                    borderRadius: '9999px',
                    border: '2px solid var(--color-panel-solid)',
                  }}
                />
              </IconButton>

              {/* Divider */}
              <Box
                style={{
                  width: '1px',
                  height: '24px',
                  backgroundColor: 'var(--gray-a4)',
                  margin: '0 4px',
                }}
              />

              {/* User Menu */}
              <DropdownMenu.Root>
                <DropdownMenu.Trigger>
                  <Button
                    variant="ghost"
                    className="user-menu-trigger"
                    style={{
                      padding: '6px 10px',
                      height: 'auto',
                      borderRadius: '10px',
                    }}
                  >
                    <Flex align="center" gap="3">
                      {/* Avatar fallback — .ds-accent-chip is one of only two
                          sanctioned gradients. The previous emerald gradient
                          used the MONEY colour as brand identity. */}
                      <Avatar
                        size="2"
                        radius="full"
                        fallback={userInitials}
                        className="ds-accent-chip"
                      />
                      <Flex
                        direction="column"
                        align="start"
                        gap="0"
                        className="user-info"
                      >
                        <Text
                          size="2"
                          weight="medium"
                          style={{ lineHeight: 1.2, color: 'var(--gray-12)' }}
                        >
                          {userName}
                        </Text>
                        <Text
                          size="1"
                          style={{ lineHeight: 1.2, color: 'var(--gray-10)' }}
                        >
                          Administrator
                        </Text>
                      </Flex>
                      {/* Chevron affordance — signals this opens a dropdown */}
                      <NavArrowDown
                        className="user-menu-chevron"
                        style={{
                          width: 16,
                          height: 16,
                          color: 'var(--gray-9)',
                          flexShrink: 0,
                        }}
                      />
                    </Flex>
                  </Button>
                </DropdownMenu.Trigger>

                <DropdownMenu.Content align="end" sideOffset={8}>
                  <Box px="3" py="2" style={{ borderBottom: '1px solid var(--gray-a4)' }}>
                    <Text size="2" style={{ display: 'block', color: 'var(--gray-12)' }}>
                      {userEmail}
                    </Text>
                  </Box>
                  <DropdownMenu.Item>
                    <UserCircle style={{ width: 16, height: 16 }} />
                    <Text>Profile</Text>
                  </DropdownMenu.Item>
                  <DropdownMenu.Item>
                    <Settings style={{ width: 16, height: 16 }} />
                    <Text>Settings</Text>
                  </DropdownMenu.Item>
                  <DropdownMenu.Separator />
                  <DropdownMenu.Item color="red" onClick={handleLogout}>
                    <LogOut style={{ width: 16, height: 16 }} />
                    <Text>Sign out</Text>
                  </DropdownMenu.Item>
                </DropdownMenu.Content>
              </DropdownMenu.Root>
            </Flex>
          </Flex>
        </header>
      </Box>

      {/* Hover styles */}
      <style jsx global>{`
        .user-menu-trigger:hover {
          background-color: var(--gray-a3) !important;
        }
        /* Chevron rotates when the menu is open — clear open/closed affordance */
        .user-menu-chevron {
          transition: transform 150ms ease;
        }
        .user-menu-trigger[data-state='open'] .user-menu-chevron {
          transform: rotate(180deg);
        }
        @media (prefers-reduced-motion: reduce) {
          .user-menu-chevron {
            transition: none;
          }
        }
        @media (max-width: 640px) {
          .user-info {
            display: none !important;
          }
        }
      `}</style>
    </>
  );
}
