'use client';

/**
 * Application Flow Layout Content (Client Component)
 *
 * Handles the UI for the KYB (Know Your Business) application process.
 *
 * RADIX UI THEMES COMPLIANT:
 * - Uses Radix Theme tokens (--accent-*, --gray-*)
 * - Uses Radix DropdownMenu for user actions
 * - Uses Radix component props (color, highContrast)
 * - Minimal inline styles - only for layout
 *
 * Features:
 * - Clean, focused design for form completion
 * - User dropdown menu for actions
 * - Minimal distractions
 */

import { ReactNode } from 'react';
import { Box, Flex, Text, Avatar, Link as RadixLink, DropdownMenu } from '@radix-ui/themes';
import { Calendar, LogOut, HelpCircle, User, NavArrowDown } from 'iconoir-react';
import Link from 'next/link';
import { useSession, signOutComplete } from '@/lib/auth/client';

// =============================================================================
// TYPES
// =============================================================================

interface ApplicationLayoutContentProps {
  children: ReactNode;
}

// =============================================================================
// HEADER COMPONENT
// =============================================================================

function ApplicationHeader() {
  const { data: session } = useSession();

  const handleLogout = async () => {
    try {
      await signOutComplete();
    } catch (error) {
      console.error('Logout failed:', error);
    }
  };

  const userName = session?.user?.name || 'User';
  const userEmail = session?.user?.email || '';
  const userInitial = userName.charAt(0).toUpperCase();

  return (
    <Box
      asChild
      px={{ initial: '4', sm: '6', lg: '8' }}
      className="application-header"
    >
      <header>
        <Flex
          align="center"
          justify="between"
          height="72px"
          maxWidth="1200px"
          mx="auto"
        >
          {/* Logo */}
          <Link href="/" style={{ textDecoration: 'none' }}>
            <Flex align="center" gap="3">
              <Flex
                align="center"
                justify="center"
                className="logo-icon"
              >
                <Calendar width={20} height={20} color="white" />
              </Flex>
              <Box>
                <Text size="3" weight="bold" highContrast>
                  MyTicket Zambia
                </Text>
                <Text as="p" size="1" color="gray">
                  Organizer Application
                </Text>
              </Box>
            </Flex>
          </Link>

          {/* User Dropdown Menu */}
          <DropdownMenu.Root>
            <DropdownMenu.Trigger>
              <Flex
                align="center"
                gap="2"
                px="2"
                py="1"
                className="user-menu-trigger"
                asChild
              >
                <button type="button" aria-label="User menu">
                  <Avatar
                    size="2"
                    fallback={userInitial}
                    radius="full"
                    color="teal"
                  />
                  <Box className="hidden-mobile">
                    <Text size="2" weight="medium" highContrast>
                      {userName}
                    </Text>
                  </Box>
                  <NavArrowDown width={16} height={16} className="dropdown-icon" />
                </button>
              </Flex>
            </DropdownMenu.Trigger>

            <DropdownMenu.Content align="end" sideOffset={8}>
              {/* User Info Section */}
              <Box px="3" py="2">
                <Text size="2" weight="medium" highContrast>
                  {userName}
                </Text>
                <Text as="p" size="1" color="gray">
                  {userEmail}
                </Text>
              </Box>

              <DropdownMenu.Separator />

              {/* Profile */}
              <DropdownMenu.Item>
                <User width={16} height={16} />
                Profile
              </DropdownMenu.Item>

              {/* Help */}
              <DropdownMenu.Item
                onSelect={() => window.open('mailto:support@myticket.zm', '_blank')}
              >
                <HelpCircle width={16} height={16} />
                Help & Support
              </DropdownMenu.Item>

              <DropdownMenu.Separator />

              {/* Logout */}
              <DropdownMenu.Item color="red" onSelect={handleLogout}>
                <LogOut width={16} height={16} />
                Log out
              </DropdownMenu.Item>
            </DropdownMenu.Content>
          </DropdownMenu.Root>
        </Flex>
      </header>
    </Box>
  );
}

// =============================================================================
// FOOTER COMPONENT
// =============================================================================

function ApplicationFooter() {
  return (
    <Box
      asChild
      py="4"
      px={{ initial: '4', sm: '6' }}
      className="application-footer"
    >
      <footer>
        <Flex
          justify="between"
          align="center"
          maxWidth="1200px"
          mx="auto"
          direction={{ initial: 'column', sm: 'row' }}
          gap="3"
        >
          <Text size="1" color="gray">
            &copy; {new Date().getFullYear()} MyTicket Zambia. All rights reserved.
          </Text>
          <Flex gap="4">
            <RadixLink
              href="mailto:support@myticket.zm"
              size="1"
              color="gray"
            >
              Contact Support
            </RadixLink>
            <RadixLink
              href="/privacy"
              size="1"
              color="gray"
            >
              Privacy Policy
            </RadixLink>
            <RadixLink
              href="/terms"
              size="1"
              color="gray"
            >
              Terms of Service
            </RadixLink>
          </Flex>
        </Flex>
      </footer>
    </Box>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export function ApplicationLayoutContent({ children }: ApplicationLayoutContentProps) {
  return (
    <Flex
      direction="column"
      minHeight="100vh"
      className="application-layout"
    >
      <ApplicationHeader />

      {/* Main Content */}
      <Box
        asChild
        px={{ initial: '4', sm: '6', lg: '8' }}
        py={{ initial: '6', sm: '8' }}
        flexGrow="1"
        maxWidth="900px"
        width="100%"
        mx="auto"
      >
        <main>{children}</main>
      </Box>

      <ApplicationFooter />

      {/*
        CSS for layout-specific styles only.
        All colors use Radix theme tokens.
      */}
      <style jsx global>{`
        /* Application Layout - Dark theme background */
        .application-layout {
          background-color: var(--gray-1);
        }

        /* Header - Gradient using gray scale */
        .application-header {
          background: linear-gradient(180deg, var(--gray-1) 0%, var(--gray-2) 100%);
          backdrop-filter: blur(12px);
          border-bottom: 1px solid var(--gray-a4);
          position: sticky;
          top: 0;
          z-index: 50;
        }

        /* Logo Icon - Brand gradient */
        .logo-icon {
          width: 40px;
          height: 40px;
          border-radius: var(--radius-3);
          background: linear-gradient(135deg, var(--accent-9) 0%, var(--accent-10) 100%);
          box-shadow: 0 0 20px var(--accent-a4);
        }

        /* User Menu Trigger */
        .user-menu-trigger {
          background: transparent;
          border: 1px solid transparent;
          border-radius: var(--radius-3);
          cursor: pointer;
          transition: background-color 150ms ease, border-color 150ms ease;
        }

        .user-menu-trigger:hover {
          background-color: var(--gray-a3);
          border-color: var(--gray-a5);
        }

        .user-menu-trigger:focus-visible {
          outline: none;
          box-shadow: 0 0 0 2px var(--accent-8);
        }

        .dropdown-icon {
          color: var(--gray-10);
          transition: transform 150ms ease;
        }

        [data-state="open"] .dropdown-icon {
          transform: rotate(180deg);
        }

        /* Footer - Subtle background */
        .application-footer {
          border-top: 1px solid var(--gray-a4);
          background-color: var(--gray-a2);
        }

        /* Hide on mobile */
        @media (max-width: 640px) {
          .hidden-mobile {
            display: none !important;
          }
        }

        /* Application form styles using Radix tokens */
        .application-card {
          background: var(--gray-a3);
          border: 1px solid var(--gray-a5);
          border-radius: var(--radius-4);
          padding: var(--space-6);
          backdrop-filter: blur(10px);
        }

        .application-card:hover {
          border-color: var(--accent-a5);
        }

        /* Form input styles */
        .application-input {
          background: var(--gray-a2) !important;
          border: 1px solid var(--gray-a5) !important;
          color: var(--gray-12) !important;
        }

        .application-input:focus {
          border-color: var(--accent-8) !important;
          box-shadow: 0 0 0 3px var(--accent-a4) !important;
        }

        .application-input::placeholder {
          color: var(--gray-9) !important;
        }

        /* Form label styles */
        .application-label {
          color: var(--gray-11);
          font-weight: 500;
          margin-bottom: var(--space-2);
          display: block;
        }

        /* Form helper text */
        .application-helper {
          color: var(--gray-10);
          font-size: var(--font-size-1);
          margin-top: var(--space-1);
        }

        /* Form error text */
        .application-error {
          color: var(--red-9);
          font-size: var(--font-size-1);
          margin-top: var(--space-1);
        }
      `}</style>
    </Flex>
  );
}
