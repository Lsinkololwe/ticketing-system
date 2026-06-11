'use client';

/**
 * Application Status Page
 *
 * Shows the current status of the organization application:
 * - Visual timeline of application progress
 * - Status-specific messaging
 * - Next steps information
 * - Support contact
 *
 * MIGRATION NOTE: This page now uses the Organization model and GraphQL operations.
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
  Edit,
  Eye,
} from 'iconoir-react';
import {
  useMyOrganization,
  canEditApplication,
  isApproved,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
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
  PENDING_REVIEW: {
    label: 'Under Review',
    description: 'Your application is being reviewed by our team. This typically takes 2-3 business days.',
    icon: <Clock style={{ width: 24, height: 24 }} />,
    color: '#3B82F6',
    bgColor: 'rgba(59, 130, 246, 0.1)',
    borderColor: 'rgba(59, 130, 246, 0.3)',
  },
  APPROVED: {
    label: 'Approved',
    description: 'Congratulations! Your application has been approved. You can now access your organization dashboard and publish events.',
    icon: <Check style={{ width: 24, height: 24 }} />,
    color: '#10B981',
    bgColor: 'rgba(16, 185, 129, 0.1)',
    borderColor: 'rgba(16, 185, 129, 0.3)',
  },
  ACTIVE: {
    label: 'Active',
    description: 'Your organization is active. You have full access to all features.',
    icon: <Check style={{ width: 24, height: 24 }} />,
    color: '#10B981',
    bgColor: 'rgba(16, 185, 129, 0.1)',
    borderColor: 'rgba(16, 185, 129, 0.3)',
  },
  REJECTED: {
    label: 'Not Approved',
    description: 'Unfortunately, your application was not approved at this time. Please see the notes below for more information.',
    icon: <Xmark style={{ width: 24, height: 24 }} />,
    color: '#EF4444',
    bgColor: 'rgba(239, 68, 68, 0.1)',
    borderColor: 'rgba(239, 68, 68, 0.3)',
  },
  CHANGES_REQUESTED: {
    label: 'Changes Requested',
    description: 'We need some additional information or corrections. Please review the notes and update your application.',
    icon: <WarningTriangle style={{ width: 24, height: 24 }} />,
    color: '#F59E0B',
    bgColor: 'rgba(245, 158, 11, 0.1)',
    borderColor: 'rgba(245, 158, 11, 0.3)',
  },
  SUSPENDED: {
    label: 'Suspended',
    description: 'Your organization account has been suspended. Please contact support for more information.',
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
  const { organization, hasOrganization, status, loading, error, refetch } = useMyOrganization();
  // Auto-refresh is always enabled for pending review status
  const autoRefreshEnabled = true;

  // Redirect if user doesn't have an organization
  useEffect(() => {
    if (!loading && !hasOrganization) {
      router.replace('/welcome');
      return;
    }

    // Redirect to dashboard if approved
    if (!loading && hasOrganization && isApproved(status)) {
      router.replace('/dashboard');
    }

    // Redirect to edit form if in draft or changes requested
    if (!loading && hasOrganization && canEditApplication(status)) {
      router.replace('/apply/business-info');
    }
  }, [loading, hasOrganization, status, router]);

  // Auto-refresh when pending review (every 30 seconds)
  useEffect(() => {
    if (status !== 'PENDING_REVIEW' || !autoRefreshEnabled) return;

    const interval = setInterval(() => {
      refetch();
    }, 30000); // 30 seconds

    return () => clearInterval(interval);
  }, [status, autoRefreshEnabled, refetch]);

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

  // Handle preview dashboard
  const handlePreviewDashboard = () => {
    // For now, just show a toast or alert
    // In a real app, this would navigate to a preview mode
    alert('Dashboard preview coming soon! You can create draft events while waiting for approval.');
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
          <Button variant="outline" onClick={() => router.push('/welcome')}>
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

  if (!organization || !status) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Text size="2" style={{ color: '#94A3B8' }}>No application found.</Text>
        <Button
          variant="outline"
          size="2"
          mt="4"
          onClick={() => router.push('/welcome')}
          style={{ color: '#10B981', borderColor: 'rgba(16, 185, 129, 0.3)' }}
        >
          Start Application
        </Button>
      </Box>
    );
  }

  const config = statusConfig[status] || statusConfig.DRAFT;
  const isPendingReview = status === 'PENDING_REVIEW';
  const needsChanges = status === 'CHANGES_REQUESTED';
  const isRejected = status === 'REJECTED';
  const approved = isApproved(status);

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
        <Flex justify="center" gap="3" mt="6" wrap="wrap">
          {approved && (
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
              <Edit style={{ width: 18, height: 18, marginRight: 8 }} />
              Edit Application
            </Button>
          )}
          {isPendingReview && (
            <>
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
              <Button
                variant="outline"
                size="3"
                onClick={handlePreviewDashboard}
                style={{
                  borderColor: 'rgba(16, 185, 129, 0.3)',
                  color: '#10B981',
                }}
              >
                <Eye style={{ width: 18, height: 18, marginRight: 8 }} />
                Explore Dashboard
              </Button>
            </>
          )}
          {isRejected && (
            <Button
              variant="outline"
              size="3"
              onClick={() => window.open('mailto:support@myticket.zm', '_blank')}
              style={{
                borderColor: 'rgba(148, 163, 184, 0.3)',
                color: '#94A3B8',
              }}
            >
              <Mail style={{ width: 18, height: 18, marginRight: 8 }} />
              Contact Support
            </Button>
          )}
        </Flex>

        {/* Auto-refresh indicator */}
        {isPendingReview && autoRefreshEnabled && (
          <Text size="1" style={{ color: '#94A3B8', display: 'block', marginTop: 16 }}>
            Status will auto-refresh every 30 seconds
          </Text>
        )}
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
              Organization Name
            </Text>
            <Text size="2" style={{ color: '#F8FAFC' }}>
              {organization.name || '-'}
            </Text>
          </Box>
          <Box>
            <Text size="1" style={{ color: '#94A3B8', display: 'block', marginBottom: '4px' }}>
              Submitted
            </Text>
            <Text size="2" style={{ color: '#F8FAFC' }}>
              {formatDate(organization.submittedAt)}
            </Text>
          </Box>
          <Box>
            <Text size="1" style={{ color: '#94A3B8', display: 'block', marginBottom: '4px' }}>
              Last Updated
            </Text>
            <Text size="2" style={{ color: '#F8FAFC' }}>
              {formatDate(organization.updatedAt)}
            </Text>
          </Box>
        </Box>
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
            status={organization.submittedAt ? 'completed' : 'pending'}
            date={organization.submittedAt ? formatDate(organization.submittedAt) : undefined}
          />
          <TimelineStep
            step={2}
            title="Information Review"
            description="Our team is reviewing your organization information"
            status={
              status === 'PENDING_REVIEW' ? 'current' :
              approved || isRejected ? 'completed' : 'pending'
            }
          />
          <TimelineStep
            step={3}
            title="Approval"
            description="Your organization will be activated"
            status={approved ? 'completed' : 'pending'}
            date={organization.approvedAt ? formatDate(organization.approvedAt) : undefined}
            isLast
          />
        </Box>
      </Card>

      {/* What You Can Do Now (for pending review) */}
      {isPendingReview && (
        <Card
          mb="6"
          style={{
            padding: '24px',
            background: 'rgba(59, 130, 246, 0.1)',
            border: '1px solid rgba(59, 130, 246, 0.2)',
            borderRadius: '12px',
          }}
        >
          <Heading size="4" mb="4" style={{ color: '#F8FAFC' }}>
            While You Wait
          </Heading>
          <Text size="2" mb="3" style={{ color: '#94A3B8', display: 'block' }}>
            You can start preparing for your events while we review your application:
          </Text>
          <ul style={{ margin: 0, paddingLeft: 20 }}>
            <li style={{ color: '#CBD5E1', marginBottom: 8 }}>
              <Text size="2">Create draft events (they can be published once approved)</Text>
            </li>
            <li style={{ color: '#CBD5E1', marginBottom: 8 }}>
              <Text size="2">Explore the dashboard features</Text>
            </li>
            <li style={{ color: '#CBD5E1', marginBottom: 8 }}>
              <Text size="2">Prepare your event content and images</Text>
            </li>
          </ul>
        </Card>
      )}

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
