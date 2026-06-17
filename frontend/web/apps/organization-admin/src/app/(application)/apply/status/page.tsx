'use client';

/**
 * Application Status Page
 *
 * Compact view of application status with timeline.
 * Auto-refreshes when pending review.
 */

import { useEffect, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Button, Card } from '@radix-ui/themes';
import { Check, Clock, WarningTriangle, Xmark, Mail, Refresh, Wifi, EditPencil, ArrowRight } from 'iconoir-react';
import {
  useMyOrganization,
  canEditApplication,
  isApproved,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import { isServerUnavailable } from '@pml.tickets/shared';
import { StatusSkeleton } from '@/components/application';

// =============================================================================
// TYPES & CONFIG
// =============================================================================

type StatusKey = 'DRAFT' | 'PENDING_REVIEW' | 'APPROVED' | 'ACTIVE' | 'REJECTED' | 'CHANGES_REQUESTED' | 'SUSPENDED';

const STATUS_CONFIG: Record<StatusKey, {
  label: string;
  description: string;
  icon: React.ElementType;
  color: string;
  bg: string;
  border: string;
}> = {
  DRAFT: {
    label: 'Draft',
    description: 'Application started but not yet submitted.',
    icon: Clock,
    color: 'var(--content-tertiary)',
    bg: 'var(--surface-tertiary)',
    border: 'var(--surface-border)',
  },
  PENDING_REVIEW: {
    label: 'Under Review',
    description: 'Being reviewed by our team. Usually takes 2-3 business days.',
    icon: Clock,
    color: 'var(--info-500)',
    bg: 'var(--info-50)',
    border: 'var(--info-200)',
  },
  APPROVED: {
    label: 'Approved',
    description: 'Your organization is approved! You can now publish events.',
    icon: Check,
    color: 'var(--success-500)',
    bg: 'var(--success-50)',
    border: 'var(--success-200)',
  },
  ACTIVE: {
    label: 'Active',
    description: 'Your organization is active with full access.',
    icon: Check,
    color: 'var(--success-500)',
    bg: 'var(--success-50)',
    border: 'var(--success-200)',
  },
  REJECTED: {
    label: 'Not Approved',
    description: 'Your application was not approved. See notes below.',
    icon: Xmark,
    color: 'var(--danger-500)',
    bg: 'var(--danger-50)',
    border: 'var(--danger-200)',
  },
  CHANGES_REQUESTED: {
    label: 'Changes Needed',
    description: 'Please update your application with the requested changes.',
    icon: WarningTriangle,
    color: 'var(--warning-500)',
    bg: 'var(--warning-50)',
    border: 'var(--warning-200)',
  },
  SUSPENDED: {
    label: 'Suspended',
    description: 'Account suspended. Contact support for details.',
    icon: Xmark,
    color: 'var(--danger-500)',
    bg: 'var(--danger-50)',
    border: 'var(--danger-200)',
  },
};

// =============================================================================
// INLINE COMPONENTS
// =============================================================================

/** Compact timeline step */
function Step({
  num,
  title,
  status,
  date,
  isLast
}: {
  num: number;
  title: string;
  status: 'done' | 'current' | 'pending';
  date?: string;
  isLast?: boolean;
}) {
  const isDone = status === 'done';
  const isCurrent = status === 'current';

  return (
    <Flex align="start" gap="3" style={{ position: 'relative' }}>
      {/* Line */}
      {!isLast && (
        <Box
          style={{
            position: 'absolute',
            left: 11,
            top: 24,
            width: 2,
            height: 'calc(100% - 8px)',
            background: isDone ? 'var(--success-500)' : 'var(--surface-border)',
          }}
        />
      )}
      {/* Circle */}
      <Box
        style={{
          width: 24,
          height: 24,
          borderRadius: '50%',
          background: isDone ? 'var(--success-500)' : isCurrent ? 'var(--brand-500)' : 'var(--surface-tertiary)',
          border: status === 'pending' ? '2px solid var(--surface-border)' : 'none',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          flexShrink: 0,
          zIndex: 1,
        }}
      >
        {isDone ? (
          <Check style={{ width: 14, height: 14, color: 'var(--content-inverse)' }} />
        ) : (
          <Text size="1" weight="bold" style={{ color: isCurrent ? 'white' : 'var(--content-tertiary)' }}>
            {num}
          </Text>
        )}
      </Box>
      {/* Content */}
      <Flex justify="between" align="center" gap="2" pb="4" style={{ flex: 1, minWidth: 0 }}>
        <Text size="2" style={{ color: status === 'pending' ? 'var(--content-tertiary)' : 'var(--content-primary)' }}>
          {title}
        </Text>
        {date && <Text size="1" style={{ color: 'var(--content-tertiary)', flexShrink: 0 }}>{date}</Text>}
      </Flex>
    </Flex>
  );
}

function formatDate(d: string | null | undefined): string {
  if (!d) return '';
  try {
    return new Date(d).toLocaleDateString('en-US', { month: 'short', day: 'numeric', year: 'numeric' });
  } catch {
    return '';
  }
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function StatusPage() {
  const router = useRouter();
  const { organization, hasOrganization, status, loading, error, refetch } = useMyOrganization();

  // Redirects
  useEffect(() => {
    if (loading) return;
    if (!hasOrganization) {
      router.replace('/welcome');
    } else if (isApproved(status)) {
      router.replace('/dashboard');
    } else if (canEditApplication(status)) {
      router.replace('/apply/business-info');
    }
  }, [loading, hasOrganization, status, router]);

  // Auto-refresh when pending
  useEffect(() => {
    if (status !== 'PENDING_REVIEW') return;
    const id = setInterval(refetch, 30000);
    return () => clearInterval(id);
  }, [status, refetch]);

  const goToDashboard = useCallback(() => router.push('/dashboard'), [router]);
  const goToEdit = useCallback(() => router.push('/apply/business-info'), [router]);

  // Loading
  if (loading) {
    return <StatusSkeleton />;
  }

  // Error
  const isOffline = error && isServerUnavailable(error);
  if (error) {
    return (
      <Box role="alert" aria-live="assertive" py="9" style={{ textAlign: 'center' }}>
        <Box
          aria-hidden="true"
          style={{
            width: 48,
            height: 48,
            borderRadius: 'var(--radius-lg)',
            background: isOffline ? 'var(--warning-50)' : 'var(--danger-50)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 12px',
          }}
        >
          {isOffline ? (
            <Wifi style={{ width: 24, height: 24, color: 'var(--warning-500)' }} />
          ) : (
            <WarningTriangle style={{ width: 24, height: 24, color: 'var(--danger-500)' }} />
          )}
        </Box>
        <Text size="3" weight="medium" style={{ color: 'var(--content-primary)', display: 'block', marginBottom: 4 }}>
          {isOffline ? 'Server unavailable' : 'Failed to load'}
        </Text>
        <Text size="2" style={{ color: 'var(--content-tertiary)', display: 'block', marginBottom: 16 }}>
          {isOffline ? 'Check your connection and try again.' : (error.message || 'An error occurred.')}
        </Text>
        <Flex gap="2" justify="center">
          <Button variant="soft" size="2" onClick={() => router.push('/welcome')}>Home</Button>
          <Button size="2" onClick={() => refetch()} className="btn-primary">Retry</Button>
        </Flex>
      </Box>
    );
  }

  // No organization
  if (!organization || !status) {
    return (
      <Box py="9" style={{ textAlign: 'center' }}>
        <Text size="2" style={{ color: 'var(--content-tertiary)' }}>No application found.</Text>
        <Button variant="soft" size="2" mt="3" onClick={() => router.push('/welcome')}>
          Start Application
        </Button>
      </Box>
    );
  }

  const config = STATUS_CONFIG[status as StatusKey] || STATUS_CONFIG.DRAFT;
  const Icon = config.icon;
  const isPending = status === 'PENDING_REVIEW';
  const needsChanges = status === 'CHANGES_REQUESTED';
  const rejected = status === 'REJECTED';
  const approved = isApproved(status);

  return (
    <Box>
      {/* Status Banner */}
      <Card
        mb="4"
        style={{
          background: config.bg,
          border: `1px solid ${config.border}`,
          borderRadius: 'var(--radius-lg)',
        }}
      >
        <Box p="4">
          <Flex align="start" gap="3">
            <Box
              aria-hidden="true"
              style={{
                width: 40,
                height: 40,
                borderRadius: 'var(--radius-md)',
                background: 'var(--surface-primary)',
                border: `1px solid ${config.border}`,
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                flexShrink: 0,
              }}
            >
              <Icon style={{ width: 20, height: 20, color: config.color }} />
            </Box>
            <Box style={{ flex: 1, minWidth: 0 }}>
              <Heading size="4" mb="1" style={{ color: config.color }}>
                {config.label}
              </Heading>
              <Text size="2" style={{ color: 'var(--content-secondary)' }}>
                {config.description}
              </Text>
            </Box>
          </Flex>

          {/* Actions */}
          <Flex gap="2" mt="4" wrap="wrap">
            {approved && (
              <Button size="2" onClick={goToDashboard} className="btn-primary">
                Go to Dashboard <ArrowRight style={{ width: 14, height: 14 }} />
              </Button>
            )}
            {needsChanges && (
              <Button size="2" onClick={goToEdit} style={{ background: 'var(--warning-500)', color: 'var(--content-inverse)' }}>
                <EditPencil style={{ width: 14, height: 14 }} /> Edit Application
              </Button>
            )}
            {isPending && (
              <>
                <Button variant="outline" size="2" onClick={() => refetch()}>
                  <Refresh style={{ width: 14, height: 14 }} /> Refresh
                </Button>
                <Button variant="outline" size="2" onClick={goToDashboard} style={{ color: 'var(--brand-500)', borderColor: 'var(--brand-500)' }}>
                  Explore Dashboard
                </Button>
              </>
            )}
            {rejected && (
              <Button variant="outline" size="2" onClick={() => window.open('mailto:support@myticket.zm', '_blank')}>
                <Mail style={{ width: 14, height: 14 }} /> Contact Support
              </Button>
            )}
          </Flex>

          {isPending && (
            <Text size="1" mt="3" style={{ color: 'var(--content-tertiary)', display: 'block' }}>
              Auto-refreshes every 30 seconds
            </Text>
          )}
        </Box>
      </Card>

      {/* Details + Timeline in Grid */}
      <Flex gap="4" direction={{ initial: 'column', sm: 'row' }}>
        {/* Details */}
        <Card className="card" style={{ flex: 1 }}>
          <Box p="4">
            <Text size="2" weight="medium" mb="3" style={{ color: 'var(--content-primary)', display: 'block' }}>
              Application Details
            </Text>
            <Flex direction="column" gap="2">
              <Flex justify="between">
                <Text size="2" style={{ color: 'var(--content-tertiary)' }}>Organization</Text>
                <Text size="2" style={{ color: 'var(--content-primary)' }}>{organization.name || '-'}</Text>
              </Flex>
              <Flex justify="between">
                <Text size="2" style={{ color: 'var(--content-tertiary)' }}>Submitted</Text>
                <Text size="2" style={{ color: 'var(--content-primary)' }}>{formatDate(organization.submittedAt) || '-'}</Text>
              </Flex>
              <Flex justify="between">
                <Text size="2" style={{ color: 'var(--content-tertiary)' }}>Updated</Text>
                <Text size="2" style={{ color: 'var(--content-primary)' }}>{formatDate(organization.updatedAt) || '-'}</Text>
              </Flex>
            </Flex>
          </Box>
        </Card>

        {/* Timeline */}
        <Card className="card" style={{ flex: 1 }}>
          <Box p="4">
            <Text size="2" weight="medium" mb="3" style={{ color: 'var(--content-primary)', display: 'block' }}>
              Timeline
            </Text>
            <Step
              num={1}
              title="Submitted"
              status={organization.submittedAt ? 'done' : 'pending'}
              date={formatDate(organization.submittedAt)}
            />
            <Step
              num={2}
              title="Under Review"
              status={isPending ? 'current' : (approved || rejected ? 'done' : 'pending')}
            />
            <Step
              num={3}
              title="Approved"
              status={approved ? 'done' : 'pending'}
              date={formatDate(organization.approvedAt)}
              isLast
            />
          </Box>
        </Card>
      </Flex>

      {/* Tips while waiting */}
      {isPending && (
        <Card
          mt="4"
          style={{
            background: 'var(--info-50)',
            border: '1px solid var(--info-200)',
            borderRadius: 'var(--radius-md)',
          }}
        >
          <Box p="3">
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-primary)', display: 'block' }}>
              While you wait
            </Text>
            <Text size="2" style={{ color: 'var(--content-secondary)', lineHeight: 1.5 }}>
              Create draft events, explore the dashboard, and prepare your event content.
              Drafts can be published once approved.
            </Text>
          </Box>
        </Card>
      )}

      {/* Compact Support */}
      <Flex
        align="center"
        justify="center"
        gap="4"
        mt="5"
        py="3"
        style={{ borderTop: '1px solid var(--surface-border)' }}
      >
        <Text size="1" style={{ color: 'var(--content-tertiary)' }}>Need help?</Text>
        <a href="mailto:support@myticket.zm" style={{ color: 'var(--brand-500)', fontSize: 13, textDecoration: 'none' }}>
          support@myticket.zm
        </a>
      </Flex>
    </Box>
  );
}
