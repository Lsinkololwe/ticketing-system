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
  DropdownMenu,
  Separator,
} from '@radix-ui/themes';
import {
  Menu as MenuIcon,
  Xmark,
  Calendar,
  ShoppingBag,
  Plus,
  Home,
  Search,
} from 'iconoir-react';
import { useAuth } from '@pml.tickets/shared';
import { BrandMark } from '@/components/ui';

const NavbarComponent: React.FC = () => {
  const [isNavOpen, setIsNavOpen] = useState(false);

  const { user, authenticated: isAuthenticated, hasRole, logout } = useAuth();
  const isOrganizer = hasRole('ORGANIZER');
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
          label: 'Create event',
          icon: Plus,
          isActive: pathname.startsWith('/organizer'),
        },
      ]
    : [];

  const allNavLinks = [...navLinks, ...organizerLinks];

  return (
    /* Simple, non-fixed top nav (spec §4 — the fixed/frosted header chrome
       belongs to the two admin apps, not the customer app). */
    <Box
      style={{
        background: 'var(--color-panel-solid)',
        borderBottom: 'var(--hairline)',
      }}
      py="3"
      px="4"
    >
      <Flex
        justify="between"
        align="center"
        style={{ maxWidth: '1280px', margin: '0 auto' }}
      >
        {/* Wordmark — plain type, no invented logo (DS rule) */}
        <Link href="/" style={{ textDecoration: 'none' }} aria-label="MyTicketZM home">
          <BrandMark size="5" />
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

          {/* My tickets */}
          {isAuthenticated && (
            <IconButton
              variant="ghost"
              size="2"
              aria-label="My tickets"
              onClick={() => router.push('/my-tickets')}
            >
              <ShoppingBag style={{ width: '1.25rem', height: '1.25rem' }} />
            </IconButton>
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
                      color="iris"
                      fallback={(user?.givenName?.charAt(0) || 'U') + (user?.familyName?.charAt(0) || '')}
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
                      color="iris"
                      fallback={(user?.givenName?.charAt(0) || 'U') + (user?.familyName?.charAt(0) || '')}
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
                  My profile
                </DropdownMenu.Item>
                <DropdownMenu.Item onSelect={() => router.push('/settings')}>
                  Settings
                </DropdownMenu.Item>
                <DropdownMenu.Item onSelect={() => router.push('/my-tickets')}>
                  My tickets
                </DropdownMenu.Item>
                <Separator size="4" />
                <DropdownMenu.Item color="red" onSelect={() => logout()}>
                  Sign out
                </DropdownMenu.Item>
              </DropdownMenu.Content>
            </DropdownMenu.Root>
          ) : (
            <Flex align="center" gap="2">
              <Button variant="ghost" size="2" onClick={() => router.push('/auth')}>
                Sign in
              </Button>
              <Button size="2" onClick={() => router.push('/auth')}>
                Sign up
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
                  Sign in
                </Button>
                <Button
                  onClick={() => {
                    router.push('/auth');
                    setIsNavOpen(false);
                  }}
                >
                  Sign up
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
