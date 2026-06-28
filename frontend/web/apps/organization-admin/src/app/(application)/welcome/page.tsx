'use client';

/**
 * Welcome Page - Accessible Onboarding Entry Point
 *
 * Built with Radix UI Themes Best Practices:
 * - Uses configured theme: accentColor="teal", grayColor="slate"
 * - Uses Radix Theme tokens (--accent-*, --gray-*)
 * - Uses ONLY Radix spacing props (p, m, gap, mb, mt, px, py)
 * - Uses Radix Card for feature items
 * - Uses Radix Button with built-in states
 * - Uses Radix VisuallyHidden for accessibility
 *
 * Radix Spacing Scale:
 * - 1 = 4px, 2 = 8px, 3 = 12px, 4 = 16px, 5 = 24px, 6 = 32px, 7 = 40px, 8 = 48px, 9 = 64px
 *
 * WCAG 2.1 AA Compliant:
 * - Color contrast: 4.5:1 minimum
 * - Focus states: Built into Radix components
 * - Keyboard navigation: Native button behavior
 * - Screen reader: Semantic HTML + ARIA
 * - Reduced motion: Handled by Radix
 */

import { useCallback, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Card,
  Text,
  Heading,
  Button,
  Flex,
  VisuallyHidden,
} from '@radix-ui/themes';
import { Building, ArrowRight, Clock, ShieldCheck, Rocket } from 'iconoir-react';
import { useSession } from '@/lib/auth/client';
import {
  useMyOrganization,
  isApproved,
  canEditApplication,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';

// =============================================================================
// TYPES
// =============================================================================

interface FeatureItemProps {
  icon: React.ReactNode;
  title: string;
  description: string;
}

// =============================================================================
// SUB-COMPONENTS
// =============================================================================

/**
 * Compact feature item using Radix Card with proper spacing
 */
function FeatureItem({ icon, title, description }: FeatureItemProps) {
  return (
    <Card
      variant="surface"
      size="1"
      data-testid="feature-item"
      className="feature-card"
    >
      <Flex align="center" gap="3" p="1">
        {/* Icon Container */}
        <Flex
          align="center"
          justify="center"
          flexShrink="0"
          width="32px"
          height="32px"
          className="feature-icon"
          aria-hidden="true"
        >
          {icon}
        </Flex>

        {/* Text Content */}
        <Box>
          <Text as="span" size="2" weight="medium" highContrast>
            {title}
          </Text>
          <Text as="span" size="1" color="gray" ml="1">
            — {description}
          </Text>
        </Box>
      </Flex>
    </Card>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function WelcomePage() {
  const router = useRouter();
  const { data: session, isPending } = useSession();
  const ctaButtonRef = useRef<HTMLButtonElement>(null);

  // Organization status drives where the user belongs. Welcome is only the right
  // place when there is no application yet, or it is still editable (not submitted).
  const { hasOrganization, status, loading: orgLoading } = useMyOrganization({
    fetchPolicy: 'cache-first',
  });

  // Extract first name from session
  const firstName = session?.user?.name?.split(' ')[0] || 'there';

  // Route the user to the page that matches their application status:
  //   - APPROVED / ACTIVE        → dashboard
  //   - PENDING_REVIEW / REJECTED / SUSPENDED (submitted) → status page
  //   - DRAFT / CHANGES_REQUESTED (editable) or none      → stay on welcome
  useEffect(() => {
    if (orgLoading || !hasOrganization || !status) return;
    if (isApproved(status)) {
      router.replace('/dashboard');
    } else if (!canEditApplication(status)) {
      router.replace('/apply/status');
    }
  }, [orgLoading, hasOrganization, status, router]);

  // Handle CTA click
  const handleGetStarted = useCallback(() => {
    router.push('/apply/business-info');
  }, [router]);

  // Auto-focus CTA button for keyboard users (after loading)
  useEffect(() => {
    if (!isPending && ctaButtonRef.current) {
      const timer = setTimeout(() => {
        ctaButtonRef.current?.focus();
      }, 100);
      return () => clearTimeout(timer);
    }
    return undefined;
  }, [isPending]);

  return (
    <>
      {/* Skip Link */}
      <a href="#get-started-button" className="skip-link">
        <VisuallyHidden>Skip to main action</VisuallyHidden>
      </a>

      {/* Main Content - Centered vertically */}
      <Flex
        role="main"
        aria-labelledby="welcome-heading"
        data-testid="welcome-page"
        direction="column"
        align="center"
        justify="center"
        px={{ initial: '4', sm: '6' }}
        py={{ initial: '6', sm: '8' }}
        minHeight="calc(100vh - 132px)"
      >
        {/* Content Container - max-width 480px for readability */}
        <Flex
          direction="column"
          align="center"
          width="100%"
          maxWidth="480px"
        >
          {/* Hero Icon - mb="5" = 24px */}
          <Flex
            align="center"
            justify="center"
            data-testid="welcome-icon"
            aria-hidden="true"
            mb="5"
            className="hero-icon"
          >
            <Building width={40} height={40} color="white" strokeWidth={1.5} />
          </Flex>

          {/* Welcome Heading - mb="2" = 8px */}
          <Heading
            id="welcome-heading"
            as="h1"
            size={{ initial: '6', sm: '7' }}
            weight="bold"
            highContrast
            mb="2"
            align="center"
            data-testid="welcome-heading"
          >
            Welcome, {firstName}
          </Heading>

          {/* Subheading - mb="6" = 32px */}
          <Text
            as="p"
            size={{ initial: '2', sm: '3' }}
            color="gray"
            align="center"
            data-testid="welcome-subheading"
            mb="6"
          >
            Let&apos;s set up your organization so you can start creating and
            managing events on MyTicket Zambia.
          </Text>

          {/* Feature Highlights - gap="2" = 8px between items, mb="6" = 32px */}
          <Flex
            direction="column"
            gap="2"
            mb="6"
            width="100%"
            asChild
          >
            <ul
              role="list"
              aria-label="Benefits of becoming an organizer"
              data-testid="feature-list"
              className="feature-list"
            >
              <li>
                <FeatureItem
                  icon={<Clock width={16} height={16} strokeWidth={1.5} />}
                  title="Quick Setup"
                  description="5 minutes"
                />
              </li>
              <li>
                <FeatureItem
                  icon={<ShieldCheck width={16} height={16} strokeWidth={1.5} />}
                  title="Verified Status"
                  description="Build trust"
                />
              </li>
              <li>
                <FeatureItem
                  icon={<Rocket width={16} height={16} strokeWidth={1.5} />}
                  title="Start Selling"
                  description="Immediately"
                />
              </li>
            </ul>
          </Flex>

          {/* Primary CTA Button - mt="6" = 32px spacing from features */}
          <Flex direction="column" align="center" gap="3" mt="6" width="100%">
            <Button
              ref={ctaButtonRef}
              id="get-started-button"
              size="4"
              variant="solid"
              color="teal"
              radius="large"
              onClick={handleGetStarted}
              data-testid="get-started-button"
              aria-describedby="cta-helper-text"
            >
              Start Setup
              <ArrowRight width={18} height={18} strokeWidth={2} aria-hidden="true" />
            </Button>

            {/* Helper Text */}
            <Text
              as="p"
              id="cta-helper-text"
              size="1"
              color="gray"
              data-testid="helper-text"
            >
              Quick 5-minute setup
            </Text>
          </Flex>
        </Flex>
      </Flex>

      {/* Minimal CSS - only for visual styling Radix doesn't provide */}
      <style jsx global>{`
        /* Feature list reset */
        .feature-list {
          list-style: none;
          padding: 0;
          margin: 0;
        }

        /* Hero Icon - Visual gradient */
        .hero-icon {
          width: 80px;
          height: 80px;
          border-radius: var(--radius-4);
          background: linear-gradient(135deg, var(--teal-9) 0%, var(--teal-10) 100%);
          box-shadow: 0 4px 20px var(--teal-a5);
        }

        /* Feature Card - Tinted surface */
        .feature-card {
          background-color: var(--teal-a2) !important;
          border-color: var(--teal-a4) !important;
        }

        .feature-card:hover {
          border-color: var(--teal-a6) !important;
        }

        /* Feature Icon */
        .feature-icon {
          border-radius: var(--radius-2);
          background-color: var(--teal-a3);
          color: var(--teal-11);
        }

        /* CTA Button - removed custom width, using Radix defaults */

        /* Skip Link */
        .skip-link {
          position: absolute;
          top: -100%;
          left: var(--space-4);
          z-index: 100;
          padding: var(--space-2) var(--space-4);
          background-color: var(--teal-9);
          color: white;
          border-radius: var(--radius-2);
          text-decoration: none;
          font-size: var(--font-size-2);
        }

        .skip-link:focus {
          top: var(--space-4);
        }

        /* Reduced Motion */
        @media (prefers-reduced-motion: reduce) {
          .feature-card,
          .cta-button {
            transition: none !important;
          }
        }
      `}</style>
    </>
  );
}
