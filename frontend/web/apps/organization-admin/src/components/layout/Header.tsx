'use client';

/**
 * Dashboard Header Component
 *
 * Features:
 * - Glassmorphism design with backdrop blur
 * - Mobile menu toggle
 * - Theme toggle (light/dark/system)
 * - User menu with profile and logout
 * - Organization context switcher (future)
 *
 * Accessibility:
 * - Keyboard navigation support
 * - ARIA labels and roles
 */

import { useState, useCallback } from 'react';
import { useTheme } from 'next-themes';
import {
  Box,
  Flex,
  Text,
  IconButton,
  DropdownMenu,
  Avatar,
} from '@radix-ui/themes';
import {
  Menu,
  SunLight,
  HalfMoon,
  LogOut,
  User,
  Building,
  Bell,
} from 'iconoir-react';
import Link from 'next/link';
import { useSession, signOut } from '@/lib/auth/client';
import {
  useMyOrganization,
  canEditOrganization,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';

interface HeaderProps {
  onMenuClick: () => void;
  showMenuButton: boolean;
}

export function Header({ onMenuClick, showMenuButton }: HeaderProps) {
  const { data: session } = useSession();
  const isAuthenticated = !!session?.user;
  const { organization, status } = useMyOrganization({ skip: !isAuthenticated });
  const { theme, setTheme } = useTheme();
  const [isLoggingOut, setIsLoggingOut] = useState(false);

  // Check if user can manage organization settings
  const canManageSettings = canEditOrganization(status);

  const handleLogout = useCallback(async () => {
    try {
      setIsLoggingOut(true);
      await signOut();
    } catch (error) {
      console.error('Logout failed:', error);
      setIsLoggingOut(false);
    }
  }, []);

  const toggleTheme = useCallback(() => {
    if (theme === 'dark') {
      setTheme('light');
    } else if (theme === 'light') {
      setTheme('system');
    } else {
      setTheme('dark');
    }
  }, [theme, setTheme]);

  const getThemeIcon = () => {
    if (theme === 'dark') return <HalfMoon style={{ width: 18, height: 18 }} />;
    return <SunLight style={{ width: 18, height: 18 }} />;
  };

  return (
    <Box
      asChild
      px={{ initial: '4', sm: '6' }}
      style={{
        height: '64px',
        position: 'sticky',
        top: 0,
        zIndex: 30,
        background: 'var(--dashboard-header-bg)',
        backdropFilter: `blur(var(--dashboard-header-blur))`,
        borderBottom: '1px solid var(--dashboard-header-border)',
      }}
    >
      <header>
        <Flex align="center" justify="between" style={{ height: '100%' }}>
          {/* Left Side - Menu Button & Breadcrumbs */}
          <Flex align="center" gap="3">
            {showMenuButton && (
              <IconButton
                variant="ghost"
                size="2"
                onClick={onMenuClick}
                aria-label="Open navigation menu"
                style={{ color: 'var(--content-secondary)' }}
              >
                <Menu style={{ width: 20, height: 20 }} />
              </IconButton>
            )}

            {/* Organization Name */}
            <Flex align="center" gap="2">
              <Box
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: '8px',
                  background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Building style={{ width: 16, height: 16, color: 'white' }} />
              </Box>
              <Box className="hidden-mobile">
                <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
                  {organization?.name || 'My Organization'}
                </Text>
              </Box>
            </Flex>
          </Flex>

          {/* Right Side - Actions */}
          <Flex align="center" gap="2">
            {/* Theme Toggle */}
            <IconButton
              variant="ghost"
              size="2"
              onClick={toggleTheme}
              aria-label={`Switch to ${theme === 'dark' ? 'light' : theme === 'light' ? 'system' : 'dark'} theme`}
              style={{ color: 'var(--content-secondary)' }}
            >
              {getThemeIcon()}
            </IconButton>

            {/* User Menu */}
            <DropdownMenu.Root>
              <DropdownMenu.Trigger>
                <Box
                  style={{
                    cursor: 'pointer',
                    borderRadius: '50%',
                    padding: '2px',
                    border: '2px solid transparent',
                    transition: 'border-color 150ms ease',
                  }}
                  className="user-avatar-trigger"
                >
                  <Avatar
                    size="2"
                    fallback={session?.user?.name?.charAt(0) || 'U'}
                    radius="full"
                    style={{
                      background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                    }}
                  />
                </Box>
              </DropdownMenu.Trigger>

              <DropdownMenu.Content align="end" sideOffset={8}>
                {/* User Info */}
                <Box px="3" py="2" style={{ borderBottom: '1px solid var(--surface-border)' }}>
                  <Text size="2" weight="medium" style={{ display: 'block', color: 'var(--content-primary)' }}>
                    {session?.user?.name || 'User'}
                  </Text>
                  <Text size="1" style={{ color: 'var(--content-muted)' }}>
                    {session?.user?.email || ''}
                  </Text>
                </Box>

                <DropdownMenu.Item asChild>
                  <Link href="/settings/profile" style={{ textDecoration: 'none', color: 'inherit' }}>
                    <User style={{ width: 16, height: 16, marginRight: 8 }} />
                    My Profile
                  </Link>
                </DropdownMenu.Item>

                {canManageSettings && (
                  <DropdownMenu.Item asChild>
                    <Link href="/settings" style={{ textDecoration: 'none', color: 'inherit' }}>
                      <Building style={{ width: 16, height: 16, marginRight: 8 }} />
                      Organization Settings
                    </Link>
                  </DropdownMenu.Item>
                )}

                <DropdownMenu.Item asChild>
                  <Link href="/settings/notifications" style={{ textDecoration: 'none', color: 'inherit' }}>
                    <Bell style={{ width: 16, height: 16, marginRight: 8 }} />
                    Notifications
                  </Link>
                </DropdownMenu.Item>

                <DropdownMenu.Separator />

                <DropdownMenu.Item
                  color="red"
                  onClick={handleLogout}
                  disabled={isLoggingOut}
                >
                  <LogOut style={{ width: 16, height: 16, marginRight: 8 }} />
                  {isLoggingOut ? 'Signing out...' : 'Sign Out'}
                </DropdownMenu.Item>
              </DropdownMenu.Content>
            </DropdownMenu.Root>
          </Flex>
        </Flex>

        <style jsx global>{`
          .user-avatar-trigger:hover {
            border-color: var(--brand-400) !important;
          }
        `}</style>
      </header>
    </Box>
  );
}
