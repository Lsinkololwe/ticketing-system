'use client';

/**
 * Welcome Page - Entry Point for New Organizers
 *
 * Landing page for authenticated users who don't have an organization yet.
 * Features:
 * - Hero section with "Become an Event Organizer" message
 * - Benefits list highlighting key features
 * - CTA button to start the application
 * - FAQ accordion for common questions
 *
 * User Journey:
 * 1. User logs in for the first time
 * 2. Router detects no organization
 * 3. Redirects to this welcome page
 * 4. User clicks "Start Application" -> /apply/business-info
 */

import { useCallback, useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Button, Card, Spinner } from '@radix-ui/themes';
import {
  Calendar,
  DollarCircle,
  GraphUp,
  Group,
  ShieldCheck,
  Headset,
  ArrowRight,
  Check,
  NavArrowDown,
  NavArrowUp,
} from 'iconoir-react';
import { useState } from 'react';
import {
  useMyOrganization,
  getRouteForStatus,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';

// =============================================================================
// TYPES
// =============================================================================

interface BenefitCardProps {
  icon: React.ReactNode;
  title: string;
  description: string;
}

interface FAQItemProps {
  question: string;
  answer: string;
  isOpen: boolean;
  onToggle: () => void;
}

// =============================================================================
// BENEFIT CARD COMPONENT
// =============================================================================

function BenefitCard({ icon, title, description }: BenefitCardProps) {
  return (
    <Card
      style={{
        padding: '24px',
        background: 'rgba(30, 41, 59, 0.5)',
        border: '1px solid rgba(148, 163, 184, 0.1)',
        borderRadius: '16px',
        transition: 'all 0.2s ease',
      }}
    >
      <Box
        style={{
          width: 48,
          height: 48,
          borderRadius: '12px',
          background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(20, 184, 166, 0.1) 100%)',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          marginBottom: 16,
        }}
      >
        {icon}
      </Box>
      <Text size="3" weight="medium" style={{ color: '#F8FAFC', display: 'block', marginBottom: 8 }}>
        {title}
      </Text>
      <Text size="2" style={{ color: '#94A3B8', lineHeight: 1.6 }}>
        {description}
      </Text>
    </Card>
  );
}

// =============================================================================
// FAQ ITEM COMPONENT
// =============================================================================

function FAQItem({ question, answer, isOpen, onToggle }: FAQItemProps) {
  return (
    <Box
      style={{
        borderBottom: '1px solid rgba(148, 163, 184, 0.1)',
        padding: '20px 0',
      }}
    >
      <Flex
        align="center"
        justify="between"
        style={{ cursor: 'pointer' }}
        onClick={onToggle}
      >
        <Text size="2" weight="medium" style={{ color: '#F8FAFC' }}>
          {question}
        </Text>
        {isOpen ? (
          <NavArrowUp style={{ width: 20, height: 20, color: '#10B981' }} />
        ) : (
          <NavArrowDown style={{ width: 20, height: 20, color: '#94A3B8' }} />
        )}
      </Flex>
      {isOpen && (
        <Text
          size="2"
          style={{
            color: '#94A3B8',
            display: 'block',
            marginTop: 12,
            lineHeight: 1.7,
            paddingRight: 32,
          }}
        >
          {answer}
        </Text>
      )}
    </Box>
  );
}

// =============================================================================
// CONSTANTS
// =============================================================================

const benefits: BenefitCardProps[] = [
  {
    icon: <Calendar style={{ width: 24, height: 24, color: '#10B981' }} />,
    title: 'Easy Event Creation',
    description: 'Create professional event pages in minutes with our intuitive builder. Add ticket types, pricing, and customize your event details.',
  },
  {
    icon: <DollarCircle style={{ width: 24, height: 24, color: '#10B981' }} />,
    title: 'Mobile Money Payments',
    description: 'Accept payments via MTN, Airtel, and Zamtel Mobile Money. Instant confirmations and automatic ticket delivery.',
  },
  {
    icon: <GraphUp style={{ width: 24, height: 24, color: '#10B981' }} />,
    title: 'Real-time Analytics',
    description: 'Track ticket sales, revenue, and attendee demographics with powerful analytics dashboards.',
  },
  {
    icon: <Group style={{ width: 24, height: 24, color: '#10B981' }} />,
    title: 'Team Collaboration',
    description: 'Invite team members with different roles. Managers, marketers, and check-in staff all in one place.',
  },
  {
    icon: <ShieldCheck style={{ width: 24, height: 24, color: '#10B981' }} />,
    title: 'Secure & Reliable',
    description: 'QR code tickets with fraud protection. Easy check-in process with our mobile app.',
  },
  {
    icon: <Headset style={{ width: 24, height: 24, color: '#10B981' }} />,
    title: 'Dedicated Support',
    description: 'Our team is here to help you succeed. Get support via WhatsApp, email, or phone.',
  },
];

const faqs = [
  {
    question: 'What do I need to apply?',
    answer: 'You need your organization name, business contact information (email and phone), and your location details. No business registration is required to start - you can add that later as part of verification for payouts.',
  },
  {
    question: 'How long does approval take?',
    answer: 'Most applications are reviewed within 2-3 business days. You will receive an email notification when your application status changes.',
  },
  {
    question: 'Can I create events before approval?',
    answer: 'Yes! You can create draft events while your application is being reviewed. Once approved, you can publish them immediately.',
  },
  {
    question: 'What are the fees?',
    answer: 'We charge a small service fee per ticket sold (typically 3-5%). There are no upfront costs or monthly fees. You only pay when you make sales.',
  },
  {
    question: 'How do I receive my earnings?',
    answer: 'Earnings are paid out to your Mobile Money account or bank account after each event. Payouts are processed within 3-5 business days after your event.',
  },
];

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function WelcomePage() {
  const router = useRouter();
  const { hasOrganization, status, loading } = useMyOrganization();
  const [openFAQ, setOpenFAQ] = useState<number | null>(null);

  // Redirect if user already has an organization
  useEffect(() => {
    if (!loading && hasOrganization) {
      const route = getRouteForStatus(status);
      router.replace(route);
    }
  }, [loading, hasOrganization, status, router]);

  // Handle start application
  const handleStartApplication = useCallback(() => {
    router.push('/apply/business-info');
  }, [router]);

  // Toggle FAQ
  const toggleFAQ = useCallback((index: number) => {
    setOpenFAQ(openFAQ === index ? null : index);
  }, [openFAQ]);

  // Show loading state
  if (loading) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spinner size="3" />
        <Text size="2" style={{ color: '#94A3B8', display: 'block', marginTop: 16 }}>
          Loading...
        </Text>
      </Box>
    );
  }

  // Don't render if redirecting
  if (hasOrganization) {
    return null;
  }

  return (
    <Box>
      {/* Hero Section */}
      <Box style={{ textAlign: 'center', marginBottom: 48 }}>
        {/* Badge */}
        <Flex justify="center" mb="4">
          <Box
            style={{
              padding: '8px 16px',
              background: 'rgba(16, 185, 129, 0.1)',
              border: '1px solid rgba(16, 185, 129, 0.2)',
              borderRadius: '24px',
            }}
          >
            <Text size="1" weight="medium" style={{ color: '#10B981' }}>
              Join thousands of event organizers in Zambia
            </Text>
          </Box>
        </Flex>

        {/* Heading */}
        <Heading
          size="8"
          mb="4"
          style={{
            color: '#F8FAFC',
            background: 'linear-gradient(135deg, #F8FAFC 0%, #CBD5E1 100%)',
            WebkitBackgroundClip: 'text',
            WebkitTextFillColor: 'transparent',
          }}
        >
          Become an Event Organizer
        </Heading>

        {/* Subheading */}
        <Text
          size="4"
          style={{
            color: '#94A3B8',
            display: 'block',
            maxWidth: 600,
            margin: '0 auto 32px',
            lineHeight: 1.6,
          }}
        >
          Create, promote, and sell tickets for your events. Accept mobile money payments
          and reach thousands of potential attendees across Zambia.
        </Text>

        {/* CTA Button */}
        <Button
          size="4"
          onClick={handleStartApplication}
          style={{
            background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
            cursor: 'pointer',
            padding: '0 32px',
            height: 52,
            fontSize: 16,
            fontWeight: 600,
            boxShadow: '0 4px 20px rgba(16, 185, 129, 0.3)',
          }}
        >
          Start Your Application
          <ArrowRight style={{ width: 20, height: 20, marginLeft: 8 }} />
        </Button>

        {/* Trust Indicators */}
        <Flex justify="center" gap="6" mt="6">
          <Flex align="center" gap="2">
            <Check style={{ width: 16, height: 16, color: '#10B981' }} />
            <Text size="2" style={{ color: '#94A3B8' }}>Free to join</Text>
          </Flex>
          <Flex align="center" gap="2">
            <Check style={{ width: 16, height: 16, color: '#10B981' }} />
            <Text size="2" style={{ color: '#94A3B8' }}>No monthly fees</Text>
          </Flex>
          <Flex align="center" gap="2">
            <Check style={{ width: 16, height: 16, color: '#10B981' }} />
            <Text size="2" style={{ color: '#94A3B8' }}>Fast approval</Text>
          </Flex>
        </Flex>
      </Box>

      {/* Benefits Section */}
      <Box mb="8">
        <Heading size="5" mb="2" style={{ color: '#F8FAFC', textAlign: 'center' }}>
          Everything You Need to Succeed
        </Heading>
        <Text
          size="2"
          style={{
            color: '#94A3B8',
            display: 'block',
            textAlign: 'center',
            marginBottom: 32,
          }}
        >
          Powerful tools to create, manage, and grow your events
        </Text>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: 20,
          }}
        >
          {benefits.map((benefit, index) => (
            <BenefitCard key={index} {...benefit} />
          ))}
        </Box>
      </Box>

      {/* FAQ Section */}
      <Card
        mb="8"
        style={{
          padding: '32px',
          background: 'rgba(30, 41, 59, 0.5)',
          border: '1px solid rgba(148, 163, 184, 0.1)',
          borderRadius: '16px',
        }}
      >
        <Heading size="5" mb="4" style={{ color: '#F8FAFC' }}>
          Frequently Asked Questions
        </Heading>

        {faqs.map((faq, index) => (
          <FAQItem
            key={index}
            question={faq.question}
            answer={faq.answer}
            isOpen={openFAQ === index}
            onToggle={() => toggleFAQ(index)}
          />
        ))}
      </Card>

      {/* Bottom CTA */}
      <Card
        style={{
          padding: '32px',
          background: 'linear-gradient(135deg, rgba(16, 185, 129, 0.1) 0%, rgba(20, 184, 166, 0.1) 100%)',
          border: '1px solid rgba(16, 185, 129, 0.2)',
          borderRadius: '16px',
          textAlign: 'center',
        }}
      >
        <Heading size="5" mb="2" style={{ color: '#F8FAFC' }}>
          Ready to Get Started?
        </Heading>
        <Text
          size="2"
          style={{ color: '#94A3B8', display: 'block', marginBottom: 24 }}
        >
          It only takes a few minutes to complete your application.
        </Text>
        <Button
          size="3"
          onClick={handleStartApplication}
          style={{
            background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
            cursor: 'pointer',
            padding: '0 32px',
          }}
        >
          Start Your Application
          <ArrowRight style={{ width: 18, height: 18, marginLeft: 8 }} />
        </Button>
      </Card>

      {/* Support Info */}
      <Box mt="6" style={{ textAlign: 'center' }}>
        <Text size="2" style={{ color: '#94A3B8' }}>
          Have questions? Contact us at{' '}
          <a
            href="mailto:support@myticket.zm"
            style={{ color: '#10B981', textDecoration: 'none' }}
          >
            support@myticket.zm
          </a>
          {' '}or WhatsApp{' '}
          <a
            href="https://wa.me/260211123456"
            target="_blank"
            rel="noopener noreferrer"
            style={{ color: '#10B981', textDecoration: 'none' }}
          >
            +260 211 123 456
          </a>
        </Text>
      </Box>
    </Box>
  );
}
