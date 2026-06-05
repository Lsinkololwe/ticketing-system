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
import { Bell, LogOut, Settings, UserCircle, Menu } from 'iconoir-react';
import { useSession, signOutComplete } from '@/lib/auth/client';
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
      await signOutComplete();
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
      <Box
        asChild
        className="dashboard-header"
        style={{
          height: '64px',
          position: 'sticky',
          top: 0,
          zIndex: 30,
          // Glassmorphism effect
          backgroundColor: 'var(--dashboard-header-bg)',
          backdropFilter: `blur(var(--dashboard-header-blur))`,
          WebkitBackdropFilter: `blur(var(--dashboard-header-blur))`,
          borderBottom: '1px solid var(--dashboard-header-border)',
          // Subtle top highlight for glass effect
          boxShadow: 'inset 0 1px 0 rgba(255, 255, 255, 0.05)',
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
                {/* Notification dot */}
                <Box
                  style={{
                    position: 'absolute',
                    top: '6px',
                    right: '6px',
                    width: '8px',
                    height: '8px',
                    backgroundColor: '#EF4444',
                    borderRadius: '50%',
                    border: '2px solid var(--gray-1)',
                    boxShadow: '0 0 8px rgba(239, 68, 68, 0.5)',
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
                      <Avatar
                        size="2"
                        radius="full"
                        fallback={userInitials}
                        style={{
                          background: 'linear-gradient(135deg, #8B5CF6 0%, #6366F1 100%)',
                          boxShadow: '0 0 12px rgba(139, 92, 246, 0.3)',
                        }}
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
                    </Flex>
                  </Button>
                </DropdownMenu.Trigger>

                <DropdownMenu.Content align="end" sideOffset={8}>
                  <Box px="3" py="2" style={{ borderBottom: '1px solid var(--gray-a4)' }}>
                    <Text size="2" weight="medium" style={{ display: 'block' }}>
                      {userName}
                    </Text>
                    <Text size="1" color="gray">
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
        @media (max-width: 640px) {
          .user-info {
            display: none !important;
          }
        }
      `}</style>
    </>
  );
}
