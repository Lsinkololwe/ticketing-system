'use client';

/**
 * Application Status Page
 *
 * Shows the current status of the organizer application:
 * - Visual timeline of application progress
 * - Status-specific messaging
 * - Next steps information
 * - Support contact
 *
 * OWASP Compliance:
 * - Uses authenticated GraphQL queries
 * - No sensitive data stored in client state
 * - Proper loading states
 */

import { useEffect } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Button, Card, Spinner } from '@radix-ui/themes';
import {
  Check,
  Clock,
  WarningTriangle,
  Xmark,
  Calendar,
  Mail,
  Phone,
  Refresh,
  Wifi,
} from 'iconoir-react';
import {
  useMyOrganizerProfile,
  getRouteForStatus,
} from '@pml.tickets/shared/api/graphql/organizer';
import { isServerUnavailable } from '@pml.tickets/shared';

// =============================================================================
// STATUS CONFIGURATION
// =============================================================================

const statusConfig: Record<
  string,
  {
    label: string;
    description: string;
    icon: React.ReactNode;
    color: string;
    bgColor: string;
    borderColor: string;
  }
> = {
  DRAFT: {
    label: 'Draft',
    description: 'Your application has been started but not yet submitted.',
    icon: <Clock style={{ width: 24, height: 24 }} />,
    color: '#94A3B8',
    bgColor: 'rgba(148, 163, 184, 0.1)',
    borderColor: 'rgba(148, 163, 184, 0.3)',
  },
  PENDING_DOCUMENTS: {
    label: 'Awaiting Information',
    description: 'Please complete all required information to continue.',
    icon: <Clock style={{ width: 24, height: 24 }} />,
    color: '#F59E0B',
    bgColor: 'rgba(245, 158, 11, 0.1)',
    borderColor: 'rgba(245, 158, 11, 0.3)',
  },
  PENDING_REVIEW: {
    label: 'Under Review',
    description: 'Your application is being reviewed by our team. This typically takes 1-2 business days.',
    icon: <Clock style={{ width: 24, height: 24 }} />,
    color: '#3B82F6',
    bgColor: 'rgba(59, 130, 246, 0.1)',
    borderColor: 'rgba(59, 130, 246, 0.3)',
  },
  APPROVED: {
    label: 'Approved',
    description: 'Congratulations! Your application has been approved. You can now access your organization dashboard.',
    icon: <Check style={{ width: 24, height: 24 }} />,
    color: '#10B981',
    bgColor: 'rgba(16, 185, 129, 0.1)',
    borderColor: 'rgba(16, 185, 129, 0.3)',
  },
  REJECTED: {
    label: 'Rejected',
    description: 'Unfortunately, your application was not approved. Please see the notes below for more information.',
    icon: <Xmark style={{ width: 24, height: 24 }} />,
    color: '#EF4444',
    bgColor: 'rgba(239, 68, 68, 0.1)',
    borderColor: 'rgba(239, 68, 68, 0.3)',
  },
  CHANGES_REQUESTED: {
    label: 'Changes Requested',
    description: 'We need some additional information or corrections. Please review the notes below and update your application.',
    icon: <WarningTriangle style={{ width: 24, height: 24 }} />,
    color: '#F59E0B',
    bgColor: 'rgba(245, 158, 11, 0.1)',
    borderColor: 'rgba(245, 158, 11, 0.3)',
  },
  SUSPENDED: {
    label: 'Suspended',
    description: 'Your organizer account has been suspended. Please contact support for more information.',
    icon: <Xmark style={{ width: 24, height: 24 }} />,
    color: '#EF4444',
    bgColor: 'rgba(239, 68, 68, 0.1)',
    borderColor: 'rgba(239, 68, 68, 0.3)',
  },
};

// =============================================================================
// TIMELINE STEP COMPONENT
// =============================================================================

interface TimelineStepProps {
  step: number;
  title: string;
  description: string;
  status: 'completed' | 'current' | 'pending';
  date?: string;
  isLast?: boolean;
}

function TimelineStep({ step, title, description, status, date, isLast }: TimelineStepProps) {
  return (
    <Flex gap="4" style={{ position: 'relative' }}>
      {/* Timeline Line */}
      {!isLast && (
        <Box
          style={{
            position: 'absolute',
            left: '19px',
            top: '40px',
            width: '2px',
            height: 'calc(100% - 40px)',
            background:
              status === 'completed'
                ? 'linear-gradient(180deg, #10B981 0%, #10B981 100%)'
                : 'rgba(148, 163, 184, 0.2)',
          }}
        />
      )}

      {/* Step Circle */}
      <Box
        style={{
          width: 40,
          height: 40,
          borderRadius: '50%',
          background:
            status === 'completed'
              ? '#10B981'
              : status === 'current'
                ? 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)'
                : 'rgba(30, 41, 59, 0.8)',
          border:
            status === 'pending'
              ? '2px solid rgba(148, 163, 184, 0.3)'
              : 'none',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
          boxShadow: status === 'current' ? '0 0 20px rgba(16, 185, 129, 0.4)' : 'none',
          zIndex: 1,
        }}
      >
        {status === 'completed' ? (
          <Check style={{ width: 18, height: 18, color: 'white' }} />
        ) : (
          <Text
            size="2"
            weight="bold"
            style={{ color: status === 'current' ? 'white' : '#94A3B8' }}
          >
            {step}
          </Text>
        )}
      </Box>

      {/* Step Content */}
      <Box pb="6" style={{ flex: 1 }}>
        <Flex justify="between" align="start">
          <Box>
            <Text
              size="2"
              weight="medium"
              style={{
                color: status === 'pending' ? '#94A3B8' : '#F8FAFC',
                display: 'block',
              }}
            >
              {title}
            </Text>
            <Text size="1" style={{ color: '#94A3B8' }}>
              {description}
            </Text>
          </Box>
          {date && (
            <Text size="1" style={{ color: '#94A3B8', whiteSpace: 'nowrap' }}>
              {date}
            </Text>
          )}
        </Flex>
      </Box>
    </Flex>
  );
}

// =============================================================================
// HELPER FUNCTIONS
// =============================================================================

function formatDate(dateString: string | null | undefined): string {
  if (!dateString) return '-';
  try {
    return new Date(dateString).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'long',
      day: 'numeric',
    });
  } catch {
    return '-';
  }
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function StatusPage() {
  const router = useRouter();
  const { profile, hasProfile, status, loading, error, refetch } = useMyOrganizerProfile();

  // Redirect if user doesn't have a profile
  useEffect(() => {
    if (!loading && !hasProfile) {
      router.replace('/apply');
      return;
    }

    // Redirect to dashboard if approved
    if (!loading && hasProfile && status === 'APPROVED') {
      router.replace('/dashboard');
    }

    // Redirect to edit form if in draft or changes requested
    if (!loading && hasProfile && (status === 'DRAFT' || status === 'PENDING_DOCUMENTS' || status === 'CHANGES_REQUESTED')) {
      const route = getRouteForStatus(status, hasProfile);
      if (route !== '/status') {
        router.replace(route);
      }
    }
  }, [loading, hasProfile, status, router]);

  // Handle refresh
  const handleRefresh = () => {
    refetch();
  };

  // Handle go to dashboard (only if approved)
  const handleGoToDashboard = () => {
    router.push('/dashboard');
  };

  // Handle edit application (only if changes requested)
  const handleEditApplication = () => {
    router.push('/apply/business-info');
  };

  // Show loading state
  if (loading) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spinner size="3" />
        <Text size="2" style={{ color: '#94A3B8', display: 'block', marginTop: 16 }}>
          Loading application status...
        </Text>
      </Box>
    );
  }

  // Show error state with network-aware messaging
  const isBackendUnavailable = error && isServerUnavailable(error);

  if (error) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Box
          style={{
            width: 64,
            height: 64,
            borderRadius: '16px',
            background: isBackendUnavailable ? 'rgba(251, 191, 36, 0.1)' : 'rgba(239, 68, 68, 0.1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px',
          }}
        >
          {isBackendUnavailable ? (
            <Wifi style={{ width: 32, height: 32, color: '#FBBF24' }} />
          ) : (
            <WarningTriangle style={{ width: 32, height: 32, color: '#EF4444' }} />
          )}
        </Box>
        <Text size="3" weight="medium" style={{ color: '#F8FAFC', display: 'block', marginBottom: 8 }}>
          {isBackendUnavailable ? 'Unable to connect to server' : 'Failed to load status'}
        </Text>
        <Text size="2" style={{ color: '#94A3B8', display: 'block', marginBottom: 16, maxWidth: 400, margin: '0 auto 16px' }}>
          {isBackendUnavailable
            ? 'The server is currently unavailable. Please check your internet connection or try again later.'
            : error.message || 'An error occurred while loading your application status.'}
        </Text>
        <Flex gap="3" justify="center">
          <Button variant="outline" onClick={() => router.push('/')}>
            Go Home
          </Button>
          <Button
            onClick={() => refetch()}
            style={{
              background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
            }}
          >
            Try Again
          </Button>
        </Flex>
      </Box>
    );
  }

  if (!profile || !status) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Text size="2" style={{ color: '#94A3B8' }}>No application found.</Text>
        <Button
          variant="outline"
          size="2"
          mt="4"
          onClick={() => router.push('/apply')}
          style={{ color: '#10B981', borderColor: 'rgba(16, 185, 129, 0.3)' }}
        >
          Start Application
        </Button>
      </Box>
    );
  }

  const config = statusConfig[status] || statusConfig.DRAFT;
  const isApproved = status === 'APPROVED';
  const needsChanges = status === 'CHANGES_REQUESTED';
  const isPendingReview = status === 'PENDING_REVIEW';

  return (
    <Box>
      {/* Status Header */}
      <Card
        mb="6"
        style={{
          padding: '32px',
          background: config.bgColor,
          border: `1px solid ${config.borderColor}`,
          borderRadius: '16px',
          textAlign: 'center',
        }}
      >
        <Box
          style={{
            width: 64,
            height: 64,
            borderRadius: '50%',
            background: config.bgColor,
            border: `2px solid ${config.borderColor}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px',
            color: config.color,
          }}
        >
          {config.icon}
        </Box>
        <Heading size="5" mb="2" style={{ color: config.color }}>
          {config.label}
        </Heading>
        <Text size="2" style={{ color: '#CBD5E1', maxWidth: '500px', margin: '0 auto' }}>
          {config.description}
        </Text>

        {/* CTA Buttons */}
        <Flex justify="center" gap="3" mt="6">
          {isApproved && (
            <Button
              size="3"
              onClick={handleGoToDashboard}
              style={{
                background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
                cursor: 'pointer',
              }}
            >
              Go to Dashboard
            </Button>
          )}
          {needsChanges && (
            <Button
              size="3"
              onClick={handleEditApplication}
              style={{
                background: 'linear-gradient(135deg, #F59E0B 0%, #D97706 100%)',
                cursor: 'pointer',
              }}
            >
              Edit Application
            </Button>
          )}
          {isPendingReview && (
            <Button
              variant="outline"
              size="3"
              onClick={handleRefresh}
              style={{
                borderColor: 'rgba(148, 163, 184, 0.3)',
                color: '#94A3B8',
              }}
            >
              <Refresh style={{ width: 18, height: 18, marginRight: 8 }} />
              Refresh Status
            </Button>
          )}
        </Flex>
      </Card>

      {/* Application Info */}
      <Card
        mb="6"
        style={{
          padding: '24px',
          background: 'rgba(30, 41, 59, 0.5)',
          border: '1px solid rgba(148, 163, 184, 0.1)',
          borderRadius: '12px',
        }}
      >
        <Heading size="4" mb="4" style={{ color: '#F8FAFC' }}>
          Application Details
        </Heading>
        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(200px, 1fr))',
            gap: '24px',
          }}
        >
          <Box>
            <Text size="1" style={{ color: '#94A3B8', display: 'block', marginBottom: '4px' }}>
              Company Name
            </Text>
            <Text size="2" style={{ color: '#F8FAFC' }}>
              {profile.companyName || '-'}
            </Text>
          </Box>
          <Box>
            <Text size="1" style={{ color: '#94A3B8', display: 'block', marginBottom: '4px' }}>
              Submitted
            </Text>
            <Text size="2" style={{ color: '#F8FAFC' }}>
              {formatDate(profile.submittedAt)}
            </Text>
          </Box>
          <Box>
            <Text size="1" style={{ color: '#94A3B8', display: 'block', marginBottom: '4px' }}>
              Last Updated
            </Text>
            <Text size="2" style={{ color: '#F8FAFC' }}>
              {formatDate(profile.updatedAt)}
            </Text>
          </Box>
        </Box>

        {/* Review Notes (if any) */}
        {profile.statusReason && (
          <Box mt="4" pt="4" style={{ borderTop: '1px solid rgba(148, 163, 184, 0.1)' }}>
            <Text size="2" weight="medium" mb="2" style={{ color: '#F8FAFC', display: 'block' }}>
              Reviewer Notes
            </Text>
            <Box
              p="3"
              style={{
                background: 'rgba(15, 23, 42, 0.5)',
                borderRadius: '8px',
                border: '1px solid rgba(148, 163, 184, 0.1)',
              }}
            >
              <Text size="2" style={{ color: '#CBD5E1' }}>
                {profile.statusReason}
              </Text>
            </Box>
          </Box>
        )}
      </Card>

      {/* Timeline */}
      <Card
        mb="6"
        style={{
          padding: '24px',
          background: 'rgba(30, 41, 59, 0.5)',
          border: '1px solid rgba(148, 163, 184, 0.1)',
          borderRadius: '12px',
        }}
      >
        <Heading size="4" mb="4" style={{ color: '#F8FAFC' }}>
          Application Timeline
        </Heading>
        <Box>
          <TimelineStep
            step={1}
            title="Application Submitted"
            description="Your application has been received"
            status={profile.submittedAt ? 'completed' : 'pending'}
            date={profile.submittedAt ? formatDate(profile.submittedAt) : undefined}
          />
          <TimelineStep
            step={2}
            title="Information Review"
            description="Our team is reviewing your business information"
            status={
              status === 'PENDING_REVIEW' ? 'current' :
              status === 'APPROVED' ? 'completed' : 'pending'
            }
          />
          <TimelineStep
            step={3}
            title="Approval"
            description="Your organization will be activated"
            status={status === 'APPROVED' ? 'completed' : 'pending'}
            date={profile.approvedAt ? formatDate(profile.approvedAt) : undefined}
            isLast
          />
        </Box>
      </Card>

      {/* Support Section */}
      <Card
        style={{
          padding: '24px',
          background: 'rgba(30, 41, 59, 0.5)',
          border: '1px solid rgba(148, 163, 184, 0.1)',
          borderRadius: '12px',
        }}
      >
        <Heading size="4" mb="4" style={{ color: '#F8FAFC' }}>
          Need Help?
        </Heading>
        <Text size="2" mb="4" style={{ color: '#94A3B8', display: 'block' }}>
          If you have questions about your application or need assistance, our support team is here to help.
        </Text>
        <Flex gap="4" wrap="wrap">
          <Flex align="center" gap="2">
            <Mail style={{ width: 16, height: 16, color: '#10B981' }} />
            <a href="mailto:support@myticket.zm" style={{ color: '#10B981', textDecoration: 'none', fontSize: '14px' }}>
              support@myticket.zm
            </a>
          </Flex>
          <Flex align="center" gap="2">
            <Phone style={{ width: 16, height: 16, color: '#10B981' }} />
            <a href="tel:+260211123456" style={{ color: '#10B981', textDecoration: 'none', fontSize: '14px' }}>
              +260 211 123 456
            </a>
          </Flex>
          <Flex align="center" gap="2">
            <Calendar style={{ width: 16, height: 16, color: '#10B981' }} />
            <Text size="2" style={{ color: '#94A3B8' }}>
              Mon - Fri, 8am - 5pm CAT
            </Text>
          </Flex>
        </Flex>
      </Card>
    </Box>
  );
}
