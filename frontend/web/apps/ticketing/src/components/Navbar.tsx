'use client';

import React, { useState } from 'react';
import Link from 'next/link';
import { useRouter, usePathname } from 'next/navigation';
import {
  Box,
  Flex,
  Text,
  Button,
  IconButton,
  Avatar,
  Badge,
  DropdownMenu,
  Separator,
} from '@radix-ui/themes';
import {
  Menu as MenuIcon,
  Xmark,
  Calendar,
  ShoppingBag,
  Bell,
  Plus,
  Home,
  Search,
  Bookmark,
} from 'iconoir-react';
import { useAuth } from '@pml.tickets/shared';
import { useUserRole } from '@/hooks/useMocks';

const NavbarComponent: React.FC = () => {
  const [isNavOpen, setIsNavOpen] = useState(false);

  const { user, authenticated: isAuthenticated } = useAuth();
  const { isOrganizer } = useUserRole();
  const router = useRouter();
  const pathname = usePathname();

  const navLinks = [
    { href: '/', label: 'Home', icon: Home, isActive: pathname === '/' },
    {
      href: '/events',
      label: 'Events',
      icon: Calendar,
      isActive: pathname.startsWith('/events'),
    },
  ];

  const organizerLinks = isOrganizer
    ? [
        {
          href: '/organizer/dashboard',
          label: 'Create Event',
          icon: Plus,
          isActive: pathname.startsWith('/organizer'),
        },
      ]
    : [];

  const allNavLinks = [...navLinks, ...organizerLinks];

  return (
    <Box
      style={{
        borderBottom: '1px solid var(--gray-4)',
        backgroundColor: 'var(--color-background)',
      }}
      py="3"
      px="4"
    >
      <Flex
        justify="between"
        align="center"
        style={{ maxWidth: '1280px', margin: '0 auto' }}
      >
        {/* Logo */}
        <Link href="/" style={{ textDecoration: 'none' }}>
          <Flex align="center" gap="2">
            <Box
              style={{
                width: '2rem',
                height: '2rem',
                background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
                borderRadius: 'var(--radius-2)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Bookmark style={{ width: '1.25rem', height: '1.25rem', color: 'white' }} />
            </Box>
            <Text size="5" weight="bold" color="iris">
              Event Ticketing
            </Text>
          </Flex>
        </Link>

        {/* Desktop Navigation */}
        <Flex gap="6" align="center" className="hidden lg:flex">
          {allNavLinks.map((link) => (
            <Link
              key={link.href}
              href={link.href}
              style={{
                textDecoration: 'none',
                color: link.isActive ? 'var(--accent-11)' : 'var(--gray-11)',
                fontWeight: link.isActive ? 600 : 400,
              }}
            >
              <Flex align="center" gap="2">
                <link.icon style={{ width: '1rem', height: '1rem' }} />
                <Text size="2">{link.label}</Text>
              </Flex>
            </Link>
          ))}
        </Flex>

        {/* Right Side Actions */}
        <Flex align="center" gap="3">
          {/* Search Button (Mobile) */}
          <IconButton variant="ghost" size="2" className="lg:hidden">
            <Search style={{ width: '1.25rem', height: '1.25rem' }} />
          </IconButton>

          {/* Notifications */}
          {isAuthenticated && (
            <Box style={{ position: 'relative' }}>
              <IconButton variant="ghost" size="2">
                <Bell style={{ width: '1.25rem', height: '1.25rem' }} />
              </IconButton>
              <Badge
                color="red"
                style={{
                  position: 'absolute',
                  top: '-4px',
                  right: '-4px',
                  fontSize: '10px',
                  minWidth: '18px',
                  height: '18px',
                }}
              >
                3
              </Badge>
            </Box>
          )}

          {/* Cart */}
          {isAuthenticated && (
            <Box style={{ position: 'relative' }}>
              <IconButton variant="ghost" size="2">
                <ShoppingBag style={{ width: '1.25rem', height: '1.25rem' }} />
              </IconButton>
              <Badge
                color="red"
                style={{
                  position: 'absolute',
                  top: '-4px',
                  right: '-4px',
                  fontSize: '10px',
                  minWidth: '18px',
                  height: '18px',
                }}
              >
                2
              </Badge>
            </Box>
          )}

          {/* User Menu or Auth Buttons */}
          {isAuthenticated ? (
            <DropdownMenu.Root>
              <DropdownMenu.Trigger>
                <Button variant="ghost" style={{ padding: '0.25rem' }}>
                  <Flex align="center" gap="2">
                    <Avatar
                      radius="full"
                      size="2"
                      fallback={(user?.givenName?.charAt(0) || 'U') + (user?.familyName?.charAt(0) || '')}
                      src={`https://ui-avatars.com/api/?name=${encodeURIComponent(user?.givenName || 'U')}+${encodeURIComponent(user?.familyName || '')}&background=8B5CF6&color=fff`}
                    />
                    <Text size="2" weight="medium" className="hidden lg:block">
                      {user?.givenName} {user?.familyName}
                    </Text>
                  </Flex>
                </Button>
              </DropdownMenu.Trigger>
              <DropdownMenu.Content>
                {/* User Info Header */}
                <Box px="3" py="2">
                  <Flex align="center" gap="3">
                    <Avatar
                      radius="full"
                      size="2"
                      fallback={(user?.givenName?.charAt(0) || 'U') + (user?.familyName?.charAt(0) || '')}
                      src={`https://ui-avatars.com/api/?name=${encodeURIComponent(user?.givenName || 'U')}+${encodeURIComponent(user?.familyName || '')}&background=8B5CF6&color=fff`}
                    />
                    <Box>
                      <Text size="2" weight="medium" style={{ display: 'block' }}>
                        {user?.givenName} {user?.familyName}
                      </Text>
                      <Text size="1" color="gray">
                        {user?.email}
                      </Text>
                    </Box>
                  </Flex>
                </Box>
                <Separator size="4" />
                <DropdownMenu.Item onSelect={() => router.push('/profile')}>
                  My Profile
                </DropdownMenu.Item>
                <DropdownMenu.Item onSelect={() => router.push('/settings')}>
                  Settings
                </DropdownMenu.Item>
                <DropdownMenu.Item onSelect={() => router.push('/my-tickets')}>
                  My Tickets
                </DropdownMenu.Item>
                <Separator size="4" />
                <DropdownMenu.Item
                  color="red"
                  onSelect={() => {
                    console.log('Logout clicked');
                  }}
                >
                  Logout
                </DropdownMenu.Item>
              </DropdownMenu.Content>
            </DropdownMenu.Root>
          ) : (
            <Flex align="center" gap="2">
              <Button variant="ghost" size="2" onClick={() => router.push('/auth')}>
                Sign In
              </Button>
              <Button size="2" onClick={() => router.push('/auth')}>
                Sign Up
              </Button>
            </Flex>
          )}

          {/* Mobile Menu Button */}
          <IconButton
            variant="ghost"
            size="2"
            className="lg:hidden"
            onClick={() => setIsNavOpen(!isNavOpen)}
          >
            {isNavOpen ? (
              <Xmark style={{ width: '1.5rem', height: '1.5rem' }} />
            ) : (
              <MenuIcon style={{ width: '1.5rem', height: '1.5rem' }} />
            )}
          </IconButton>
        </Flex>
      </Flex>

      {/* Mobile Navigation */}
      {isNavOpen && (
        <Box className="lg:hidden" mt="4">
          <Flex direction="column" gap="2">
            {allNavLinks.map((link) => (
              <Link
                key={link.href}
                href={link.href}
                style={{
                  textDecoration: 'none',
                  color: link.isActive ? 'var(--accent-11)' : 'var(--gray-11)',
                  padding: '0.5rem',
                  borderRadius: 'var(--radius-2)',
                  backgroundColor: link.isActive ? 'var(--accent-3)' : 'transparent',
                }}
                onClick={() => setIsNavOpen(false)}
              >
                <Flex align="center" gap="2">
                  <link.icon style={{ width: '1rem', height: '1rem' }} />
                  <Text size="2" weight={link.isActive ? 'bold' : 'regular'}>
                    {link.label}
                  </Text>
                </Flex>
              </Link>
            ))}

            {/* Mobile Auth Buttons */}
            {!isAuthenticated && (
              <Flex direction="column" gap="2" mt="3">
                <Button
                  variant="outline"
                  onClick={() => {
                    router.push('/auth');
                    setIsNavOpen(false);
                  }}
                >
                  Sign In
                </Button>
                <Button
                  onClick={() => {
                    router.push('/auth');
                    setIsNavOpen(false);
                  }}
                >
                  Sign Up
                </Button>
              </Flex>
            )}
          </Flex>
        </Box>
      )}
    </Box>
  );
};

export default NavbarComponent;
