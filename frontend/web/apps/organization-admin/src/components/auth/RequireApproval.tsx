'use client';

/**
 * RequireApproval Component
 *
 * Wrapper that ensures the user has an approved organizer profile
 * before allowing access to dashboard content.
 *
 * Redirects:
 * - No profile → /apply
 * - Pending/Draft → /status
 * - Rejected/Changes Requested → /status
 * - Approved → renders children
 *
 * OWASP Security:
 * - Defense-in-depth with server-side checks in middleware
 * - Client-side UX enhancement only
 */

import { useEffect, ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Button, Heading, Card } from '@radix-ui/themes';
import { WarningTriangle, Clock, Xmark, ArrowRight } from 'iconoir-react';
import { useOrganization, OrganizerProfileStatus } from '@/lib/contexts/OrganizationContext';

// =============================================================================
// TYPES
// =============================================================================

interface RequireApprovalProps {
  children: ReactNode;
  /** Custom loading component */
  loadingComponent?: ReactNode;
  /** Allow access even if not approved (for status page) */
  allowPending?: boolean;
}

// =============================================================================
// STATUS CARDS
// =============================================================================

interface StatusCardProps {
  status: OrganizerProfileStatus;
}

function StatusCard({ status }: StatusCardProps) {
  const router = useRouter();

  const statusConfig: Record<
    OrganizerProfileStatus,
    {
      icon: React.ReactNode;
      title: string;
      description: string;
      actionLabel: string;
      actionHref: string;
      color: string;
    }
  > = {
    DRAFT: {
      icon: <Clock style={{ width: 32, height: 32 }} />,
      title: 'Application Not Started',
      description: 'You need to complete your organizer application to access the dashboard.',
      actionLabel: 'Start Application',
      actionHref: '/apply',
      color: '#94A3B8',
    },
    PENDING_DOCUMENTS: {
      icon: <Clock style={{ width: 32, height: 32 }} />,
      title: 'Documents Required',
      description: 'Please upload the required documents to continue with your application.',
      actionLabel: 'Upload Documents',
      actionHref: '/apply/documents',
      color: '#F59E0B',
    },
    PENDING_REVIEW: {
      icon: <Clock style={{ width: 32, height: 32 }} />,
      title: 'Application Under Review',
      description: 'Your application is being reviewed. This typically takes 1-2 business days.',
      actionLabel: 'View Status',
      actionHref: '/status',
      color: '#3B82F6',
    },
    APPROVED: {
      icon: <Clock style={{ width: 32, height: 32 }} />,
      title: 'Approved',
      description: 'Your application has been approved.',
      actionLabel: 'Go to Dashboard',
      actionHref: '/dashboard',
      color: '#10B981',
    },
    REJECTED: {
      icon: <Xmark style={{ width: 32, height: 32 }} />,
      title: 'Application Rejected',
      description: 'Unfortunately, your application was not approved. Please contact support for more information.',
      actionLabel: 'View Details',
      actionHref: '/status',
      color: '#EF4444',
    },
    CHANGES_REQUESTED: {
      icon: <WarningTriangle style={{ width: 32, height: 32 }} />,
      title: 'Changes Requested',
      description: 'We need additional information or corrections to your application.',
      actionLabel: 'Update Application',
      actionHref: '/apply/business-info',
      color: '#F59E0B',
    },
  };

  const config = statusConfig[status];

  return (
    <Box
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#0F172A',
        padding: '24px',
      }}
    >
      <Card
        style={{
          maxWidth: '480px',
          width: '100%',
          padding: '40px',
          background: 'rgba(30, 41, 59, 0.5)',
          border: '1px solid rgba(148, 163, 184, 0.1)',
          borderRadius: '20px',
          textAlign: 'center',
        }}
      >
        <Box
          style={{
            width: 72,
            height: 72,
            borderRadius: '50%',
            background: `${config.color}15`,
            border: `2px solid ${config.color}40`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 24px',
            color: config.color,
          }}
        >
          {config.icon}
        </Box>

        <Heading size="5" mb="3" style={{ color: '#F8FAFC' }}>
          {config.title}
        </Heading>

        <Text
          size="2"
          style={{
            color: '#94A3B8',
            display: 'block',
            marginBottom: '32px',
            lineHeight: 1.6,
          }}
        >
          {config.description}
        </Text>

        <Button
          size="3"
          onClick={() => router.push(config.actionHref)}
          style={{
            background: `linear-gradient(135deg, ${config.color} 0%, ${config.color}CC 100%)`,
            cursor: 'pointer',
            width: '100%',
          }}
        >
          {config.actionLabel}
          <ArrowRight style={{ width: 18, height: 18, marginLeft: 8 }} />
        </Button>

        {status !== 'DRAFT' && (
          <Text
            size="1"
            style={{
              color: '#94A3B8',
              display: 'block',
              marginTop: '24px',
            }}
          >
            Need help?{' '}
            <a
              href="mailto:support@myticket.zm"
              style={{ color: '#10B981', textDecoration: 'none' }}
            >
              Contact Support
            </a>
          </Text>
        )}
      </Card>
    </Box>
  );
}

// =============================================================================
// LOADING COMPONENT
// =============================================================================

function DefaultLoadingComponent() {
  return (
    <Box
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#0F172A',
      }}
    >
      <Flex direction="column" align="center" gap="4">
        <Box
          style={{
            width: 48,
            height: 48,
            borderRadius: '50%',
            border: '3px solid rgba(16, 185, 129, 0.2)',
            borderTopColor: '#10B981',
            animation: 'spin 1s linear infinite',
          }}
        />
        <Text style={{ color: '#94A3B8' }}>Loading organization...</Text>
      </Flex>
      <style jsx global>{`
        @keyframes spin {
          to {
            transform: rotate(360deg);
          }
        }
      `}</style>
    </Box>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export function RequireApproval({
  children,
  loadingComponent,
  allowPending = false,
}: RequireApprovalProps) {
  const router = useRouter();
  const { organizerProfile, isApproved, isLoading, error } = useOrganization();

  // Handle redirects based on status
  useEffect(() => {
    if (isLoading) return;

    // No profile - redirect to apply
    if (!organizerProfile) {
      router.replace('/apply');
      return;
    }

    // If allowPending is true, don't redirect (for status page)
    if (allowPending) return;

    // Check status and redirect accordingly
    const { status } = organizerProfile;

    switch (status) {
      case 'DRAFT':
        router.replace('/apply');
        break;
      case 'PENDING_DOCUMENTS':
        router.replace('/apply/documents');
        break;
      case 'PENDING_REVIEW':
      case 'REJECTED':
      case 'CHANGES_REQUESTED':
        router.replace('/status');
        break;
      case 'APPROVED':
        // Allow access
        break;
    }
  }, [organizerProfile, isLoading, allowPending, router]);

  // Show loading state
  if (isLoading) {
    return loadingComponent || <DefaultLoadingComponent />;
  }

  // Show error state
  if (error) {
    return (
      <Box
        style={{
          minHeight: '100vh',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          backgroundColor: '#0F172A',
          padding: '24px',
        }}
      >
        <Card
          style={{
            maxWidth: '400px',
            padding: '32px',
            background: 'rgba(239, 68, 68, 0.1)',
            border: '1px solid rgba(239, 68, 68, 0.2)',
            borderRadius: '16px',
            textAlign: 'center',
          }}
        >
          <WarningTriangle
            style={{ width: 48, height: 48, color: '#EF4444', margin: '0 auto 16px' }}
          />
          <Heading size="4" mb="2" style={{ color: '#F8FAFC' }}>
            Something went wrong
          </Heading>
          <Text size="2" style={{ color: '#94A3B8', display: 'block', marginBottom: '24px' }}>
            {error}
          </Text>
          <Button
            variant="outline"
            onClick={() => window.location.reload()}
            style={{ borderColor: 'rgba(239, 68, 68, 0.3)', color: '#EF4444' }}
          >
            Try Again
          </Button>
        </Card>
      </Box>
    );
  }

  // No profile - show redirect message
  if (!organizerProfile) {
    return <StatusCard status="DRAFT" />;
  }

  // If allowPending, show children regardless of status
  if (allowPending) {
    return <>{children}</>;
  }

  // Not approved - show status card
  if (!isApproved) {
    return <StatusCard status={organizerProfile.status} />;
  }

  // Approved - render children
  return <>{children}</>;
}

export default RequireApproval;
