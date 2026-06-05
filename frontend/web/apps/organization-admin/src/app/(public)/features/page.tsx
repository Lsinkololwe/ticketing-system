'use client';

/**
 * Features Page
 *
 * Detailed overview of platform features:
 * - Ticketing & Sales
 * - Payments
 * - Check-in & Validation
 * - Analytics & Reporting
 * - Team Management
 */

import Link from 'next/link';
import {
  Box,
  Flex,
  Text,
  Heading,
  Button,
  Card,
} from '@radix-ui/themes';
import {
  Label,
  QrCode,
  StatsReport,
  Wallet,
  User,
  Shield,
  SmartphoneDevice,
  Mail,
  Calendar,
  Clock,
  NavArrowRight,
  Globe,
  Bell,
  Edit,
  Settings,
} from 'iconoir-react';

// =============================================================================
// FEATURE SECTION COMPONENT
// =============================================================================

interface FeatureItem {
  icon: React.ReactNode;
  title: string;
  description: string;
}

interface FeatureSectionProps {
  title: string;
  subtitle: string;
  features: FeatureItem[];
  reversed?: boolean;
}

function FeatureSection({ title, subtitle, features, reversed }: FeatureSectionProps) {
  return (
    <Box
      style={{
        maxWidth: 1200,
        margin: '0 auto',
        padding: '80px 24px',
      }}
    >
      <Flex
        gap="8"
        direction={{ initial: 'column', md: reversed ? 'row-reverse' : 'row' }}
        align="center"
      >
        {/* Text Content */}
        <Box style={{ flex: 1 }}>
          <Heading
            as="h2"
            size="7"
            style={{
              color: 'var(--content-primary)',
              marginBottom: 16,
            }}
          >
            {title}
          </Heading>
          <Text
            size="3"
            style={{
              color: 'var(--content-muted)',
              marginBottom: 32,
              display: 'block',
              lineHeight: 1.6,
            }}
          >
            {subtitle}
          </Text>

          <Flex direction="column" gap="4">
            {features.map((feature) => (
              <Flex key={feature.title} gap="3" align="start">
                <Box
                  style={{
                    width: 40,
                    height: 40,
                    borderRadius: '10px',
                    background: 'rgba(16, 185, 129, 0.1)',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: 'var(--brand-500)',
                    flexShrink: 0,
                  }}
                >
                  {feature.icon}
                </Box>
                <Box>
                  <Text
                    size="2"
                    weight="medium"
                    style={{ color: 'var(--content-primary)', display: 'block', marginBottom: 4 }}
                  >
                    {feature.title}
                  </Text>
                  <Text size="2" style={{ color: 'var(--content-muted)', lineHeight: 1.5 }}>
                    {feature.description}
                  </Text>
                </Box>
              </Flex>
            ))}
          </Flex>
        </Box>

        {/* Visual */}
        <Box style={{ flex: 1 }}>
          <Card
            style={{
              padding: '40px',
              background: 'linear-gradient(135deg, var(--surface-elevated) 0%, rgba(16, 185, 129, 0.05) 100%)',
              border: '1px solid var(--surface-border)',
              borderRadius: '24px',
              aspectRatio: '4/3',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
            }}
          >
            <Box
              style={{
                width: 120,
                height: 120,
                borderRadius: '24px',
                background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                color: 'white',
              }}
            >
              {features[0]?.icon}
            </Box>
          </Card>
        </Box>
      </Flex>
    </Box>
  );
}

// =============================================================================
// MAIN PAGE
// =============================================================================

export default function FeaturesPage() {
  return (
    <Box>
      {/* Hero */}
      <Box
        style={{
          background: 'linear-gradient(180deg, rgba(16, 185, 129, 0.05) 0%, transparent 100%)',
          padding: '80px 24px',
          textAlign: 'center',
        }}
      >
        <Box style={{ maxWidth: 800, margin: '0 auto' }}>
          <Heading
            as="h1"
            size="8"
            style={{
              color: 'var(--content-primary)',
              marginBottom: 16,
            }}
          >
            Powerful Features for Modern Events
          </Heading>
          <Text
            size="4"
            style={{
              color: 'var(--content-muted)',
              lineHeight: 1.6,
            }}
          >
            Everything you need to create, promote, and manage successful events.
            Built specifically for organizers in Zambia.
          </Text>
        </Box>
      </Box>

      {/* Ticketing */}
      <FeatureSection
        title="Flexible Ticketing"
        subtitle="Create the perfect ticket setup for any event type, from small meetups to large festivals."
        features={[
          {
            icon: <Label style={{ width: 20, height: 20 }} />,
            title: 'Multiple Ticket Types',
            description: 'Create VIP, early bird, regular, and custom ticket tiers with different pricing.',
          },
          {
            icon: <Calendar style={{ width: 20, height: 20 }} />,
            title: 'Scheduled Sales',
            description: 'Set tickets to go on sale at specific times. Perfect for early bird releases.',
          },
          {
            icon: <Edit style={{ width: 20, height: 20 }} />,
            title: 'Promo Codes',
            description: 'Create discount codes for partners, sponsors, or special promotions.',
          },
        ]}
      />

      {/* Payments */}
      <Box style={{ background: 'var(--surface-subtle)' }}>
        <FeatureSection
          title="Local Payments Made Easy"
          subtitle="Accept payments the way your customers prefer. Mobile money is built-in from day one."
          features={[
            {
              icon: <SmartphoneDevice style={{ width: 20, height: 20 }} />,
              title: 'Mobile Money',
              description: 'Accept MTN, Airtel, and Zamtel mobile money. Instant payment confirmation.',
            },
            {
              icon: <Shield style={{ width: 20, height: 20 }} />,
              title: 'Secure Transactions',
              description: 'All payments are encrypted and processed through secure payment gateways.',
            },
            {
              icon: <Wallet style={{ width: 20, height: 20 }} />,
              title: 'Fast Payouts',
              description: 'Get your earnings within 1-3 business days. No waiting for weeks.',
            },
          ]}
          reversed
        />
      </Box>

      {/* Check-in */}
      <FeatureSection
        title="Seamless Check-In"
        subtitle="Fast, reliable, and professional entry management for any event size."
        features={[
          {
            icon: <QrCode style={{ width: 20, height: 20 }} />,
            title: 'QR Code Scanning',
            description: 'Scan tickets with any smartphone camera. Works even without internet.',
          },
          {
            icon: <Clock style={{ width: 20, height: 20 }} />,
            title: 'Real-Time Tracking',
            description: 'See who has arrived and who is still pending in real-time.',
          },
          {
            icon: <User style={{ width: 20, height: 20 }} />,
            title: 'Guest Lists',
            description: 'Easily manage VIP lists and complementary tickets.',
          },
        ]}
      />

      {/* Analytics */}
      <Box style={{ background: 'var(--surface-subtle)' }}>
        <FeatureSection
          title="Data-Driven Decisions"
          subtitle="Understand your audience and optimize your events with powerful analytics."
          features={[
            {
              icon: <StatsReport style={{ width: 20, height: 20 }} />,
              title: 'Sales Analytics',
              description: 'Track ticket sales, revenue, and trends over time.',
            },
            {
              icon: <Globe style={{ width: 20, height: 20 }} />,
              title: 'Audience Insights',
              description: 'Learn where your attendees come from and what they prefer.',
            },
            {
              icon: <Bell style={{ width: 20, height: 20 }} />,
              title: 'Real-Time Updates',
              description: 'Get notified of important events like sales milestones.',
            },
          ]}
          reversed
        />
      </Box>

      {/* Team */}
      <FeatureSection
        title="Team Collaboration"
        subtitle="Work together with your team, each with the right level of access."
        features={[
          {
            icon: <User style={{ width: 20, height: 20 }} />,
            title: 'Role-Based Access',
            description: 'Assign Admin, Manager, Marketer, or Contributor roles to team members.',
          },
          {
            icon: <Mail style={{ width: 20, height: 20 }} />,
            title: 'Easy Invitations',
            description: 'Invite team members via email with just a few clicks.',
          },
          {
            icon: <Settings style={{ width: 20, height: 20 }} />,
            title: 'Permissions Control',
            description: 'Control who can create events, view finances, or check-in attendees.',
          },
        ]}
      />

      {/* CTA */}
      <Box
        style={{
          background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
          padding: '80px 24px',
          textAlign: 'center',
        }}
      >
        <Box style={{ maxWidth: 600, margin: '0 auto' }}>
          <Heading
            as="h2"
            size="7"
            style={{
              color: 'white',
              marginBottom: 16,
            }}
          >
            Ready to Get Started?
          </Heading>
          <Text
            size="3"
            style={{
              color: 'rgba(255, 255, 255, 0.8)',
              marginBottom: 32,
              display: 'block',
            }}
          >
            Apply now and start selling tickets for your next event.
            No setup fees, no monthly charges.
          </Text>
          <Link href="/apply" style={{ textDecoration: 'none' }}>
            <Button
              size="4"
              style={{
                background: 'white',
                color: 'var(--brand-600)',
                padding: '12px 32px',
                fontSize: 16,
              }}
            >
              Apply Now
              <NavArrowRight style={{ width: 20, height: 20, marginLeft: 8 }} />
            </Button>
          </Link>
        </Box>
      </Box>
    </Box>
  );
}
