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
import {
  WarningTriangle,
  Clock,
  Xmark,
  ArrowRight,
  CheckCircle,
  PageEdit,
} from 'iconoir-react';
import { useSession } from '@/lib/auth/client';
import {
  useMyOrganization,
  isApproved as checkIsApproved,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';

// =============================================================================
// TYPES
// =============================================================================

// Organization status values (using string to handle stale codegen)
// TODO: Remove when codegen is regenerated with running backend services
type OrganizationStatusString =
  | 'DRAFT'
  | 'PENDING_REVIEW'
  | 'APPROVED'
  | 'ACTIVE'
  | 'REJECTED'
  | 'CHANGES_REQUESTED'
  | 'SUSPENDED'
  | 'INACTIVE'
  | 'PENDING_DELETION';

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
  status: OrganizationStatusString;
}

/**
 * Status presentation.
 *
 * `tone` selects a semantic status ramp — never a raw hex. Note that an
 * approved/active organization is `success` (GREEN), not jade: jade is the
 * money role and is reserved for amounts, so an "Approved" chip can never be
 * mistaken for a "Paid" amount.
 *
 * Copy: enums are humanized ("PENDING_REVIEW" reads as "Application under
 * review") and each description says what happens next, in plain third person.
 */
type StatusTone = 'neutral' | 'info' | 'success' | 'warning' | 'danger';

const TONE: Record<StatusTone, { fg: string; surface: string; border: string }> = {
  neutral: { fg: 'var(--gray-11)', surface: 'var(--gray-a3)', border: 'var(--gray-a6)' },
  info: { fg: 'var(--status-info-11)', surface: 'var(--status-info-a3)', border: 'var(--blue-a6)' },
  success: {
    fg: 'var(--status-success-11)',
    surface: 'var(--status-success-a3)',
    border: 'var(--green-a6)',
  },
  warning: {
    fg: 'var(--status-warning-11)',
    surface: 'var(--status-warning-a3)',
    border: 'var(--amber-a6)',
  },
  danger: {
    fg: 'var(--status-danger-11)',
    surface: 'var(--status-danger-a3)',
    border: 'var(--red-a6)',
  },
};

const statusConfig: Record<
  string,
  {
    icon: React.ReactNode;
    title: string;
    description: string;
    actionLabel: string;
    actionHref: string;
    tone: StatusTone;
  }
> = {
  DRAFT: {
    icon: <PageEdit width={32} height={32} />,
    title: 'Application not started',
    description:
      'Complete the organizer application to unlock the dashboard. It takes about ten minutes.',
    actionLabel: 'Start application',
    actionHref: '/apply/business-info',
    tone: 'neutral',
  },
  PENDING_REVIEW: {
    icon: <Clock width={32} height={32} />,
    title: 'Application under review',
    description: 'The application is with the review team. Most decisions land within two business days.',
    actionLabel: 'View status',
    actionHref: '/apply/status',
    tone: 'info',
  },
  APPROVED: {
    icon: <CheckCircle width={32} height={32} />,
    title: 'Application approved',
    description: 'The organization is approved and the dashboard is open.',
    actionLabel: 'Go to dashboard',
    actionHref: '/dashboard',
    tone: 'success',
  },
  ACTIVE: {
    icon: <CheckCircle width={32} height={32} />,
    title: 'Organization active',
    description: 'Everything is live. Events can be published and payouts requested.',
    actionLabel: 'Go to dashboard',
    actionHref: '/dashboard',
    tone: 'success',
  },
  REJECTED: {
    icon: <Xmark width={32} height={32} />,
    title: 'Application rejected',
    description:
      'The application was not approved. The status page lists the reason given by the review team.',
    actionLabel: 'View details',
    actionHref: '/apply/status',
    tone: 'danger',
  },
  CHANGES_REQUESTED: {
    icon: <WarningTriangle width={32} height={32} />,
    title: 'Changes requested',
    description: 'The review team needs corrections before the application can proceed.',
    actionLabel: 'Update application',
    actionHref: '/apply/business-info',
    tone: 'warning',
  },
  SUSPENDED: {
    icon: <Xmark width={32} height={32} />,
    title: 'Organization suspended',
    description: 'Access is paused. Support can explain what triggered the suspension.',
    actionLabel: 'Contact support',
    actionHref: '/apply/status',
    tone: 'danger',
  },
  INACTIVE: {
    icon: <Clock width={32} height={32} />,
    title: 'Organization inactive',
    description: 'This organization is not currently active.',
    actionLabel: 'View status',
    actionHref: '/apply/status',
    tone: 'neutral',
  },
  PENDING_DELETION: {
    icon: <WarningTriangle width={32} height={32} />,
    title: 'Scheduled for deletion',
    description: 'This organization is queued for deletion. Support can stop it before it runs.',
    actionLabel: 'Contact support',
    actionHref: '/apply/status',
    tone: 'warning',
  },
};

function StatusCard({ status }: StatusCardProps) {
  const router = useRouter();

  const config = statusConfig[status];
  const tone = TONE[config.tone];

  return (
    <Box
      style={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        background: 'var(--color-background)',
        padding: 'var(--space-5)',
      }}
    >
      <Card
        style={{
          maxWidth: 480,
          width: '100%',
          padding: 'var(--space-7)',
          textAlign: 'center',
        }}
      >
        <Box
          aria-hidden="true"
          style={{
            width: 72,
            height: 72,
            borderRadius: '50%',
            background: tone.surface,
            border: `1px solid ${tone.border}`,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto var(--space-5)',
            color: tone.fg,
          }}
        >
          {config.icon}
        </Box>

        <Heading size="5" mb="3" style={{ color: 'var(--gray-12)' }}>
          {config.title}
        </Heading>

        <Text
          size="2"
          style={{
            color: 'var(--gray-11)',
            display: 'block',
            marginBottom: 'var(--space-6)',
            lineHeight: 1.6,
          }}
        >
          {config.description}
        </Text>

        <Button
          data-testid="require-approval-action"
          size="3"
          color="teal"
          onClick={() => router.push(config.actionHref)}
          style={{ cursor: 'pointer', width: '100%' }}
        >
          {config.actionLabel}
          <ArrowRight width={18} height={18} style={{ marginLeft: 8 }} />
        </Button>

        {status !== 'DRAFT' && (
          <Text
            size="1"
            style={{ color: 'var(--gray-10)', display: 'block', marginTop: 'var(--space-5)' }}
          >
            Need help?{' '}
            <a
              href="mailto:support@myticket.zm"
              style={{ color: 'var(--accent-11)', textDecoration: 'none', fontWeight: 500 }}
            >
              Contact support
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
        background: 'var(--color-background)',
      }}
    >
      <Flex direction="column" align="center" gap="4">
        <Box
          aria-hidden="true"
          style={{
            width: 40,
            height: 40,
            borderRadius: '50%',
            border: '3px solid var(--accent-a5)',
            borderTopColor: 'var(--accent-9)',
            animation: 'ds-spin 1s linear infinite',
          }}
        />
        <Text size="2" style={{ color: 'var(--gray-10)' }}>
          Loading your organization…
        </Text>
      </Flex>
      <style jsx global>{`
        @keyframes ds-spin {
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
  const { data: session, isPending: isSessionPending } = useSession();
  const isAuthenticated = !!session?.user;

  const { organization, status, loading, error } = useMyOrganization({
    skip: !isAuthenticated,
  });

  const isLoading = isSessionPending || loading;
  const isApproved = checkIsApproved(status);

  // Handle redirects based on status
  useEffect(() => {
    if (isLoading) return;

    // No organization - redirect to welcome
    if (!organization) {
      router.replace('/welcome');
      return;
    }

    // If allowPending is true, don't redirect (for status page)
    if (allowPending) return;

    // Check status and redirect accordingly
    // Cast to string to handle stale codegen types
    const statusStr = status as string;
    switch (statusStr) {
      case 'DRAFT':
      case 'CHANGES_REQUESTED':
        router.replace('/apply/business-info');
        break;
      case 'PENDING_REVIEW':
      case 'REJECTED':
      case 'SUSPENDED':
        router.replace('/apply/status');
        break;
      case 'APPROVED':
      case 'ACTIVE':
        // Allow access
        break;
    }
  }, [organization, status, isLoading, allowPending, router]);

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
          background: 'var(--color-background)',
          padding: 'var(--space-5)',
        }}
      >
        <Card
          className="error-card"
          style={{ maxWidth: 400, padding: 'var(--space-6)', textAlign: 'center' }}
        >
          <WarningTriangle
            aria-hidden="true"
            width={40}
            height={40}
            style={{ color: 'var(--status-danger-9)', margin: '0 auto var(--space-4)' }}
          />
          <Heading size="4" mb="2" style={{ color: 'var(--gray-12)' }}>
            Could not load your organization
          </Heading>
          <Text
            size="2"
            style={{ color: 'var(--gray-11)', display: 'block', marginBottom: 'var(--space-5)' }}
          >
            {error?.message || 'The request did not complete. Reload to try again.'}
          </Text>
          <Button
            data-testid="require-approval-retry"
            variant="outline"
            color="red"
            onClick={() => window.location.reload()}
            style={{ cursor: 'pointer' }}
          >
            Reload
          </Button>
        </Card>
      </Box>
    );
  }

  // No organization - show redirect message
  if (!organization) {
    return <StatusCard status="DRAFT" />;
  }

  // If allowPending, show children regardless of status
  if (allowPending) {
    return <>{children}</>;
  }

  // Not approved - show status card
  if (!isApproved && status) {
    return <StatusCard status={status as OrganizationStatusString} />;
  }

  // Approved - render children
  return <>{children}</>;
}

export default RequireApproval;
