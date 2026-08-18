'use client';

/**
 * Application Status Page
 *
 * Compact view of application status with timeline.
 * Auto-refreshes when pending review.
 */

import { useCallback } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Button, Card, Avatar, Badge, Grid } from '@radix-ui/themes';
import {
  Check,
  Clock,
  WarningTriangle,
  Xmark,
  Mail,
  Phone,
  Building,
  Calendar,
  ShieldCheck,
  Rocket,
  SendDiagonal,
  Refresh,
  Wifi,
  EditPencil,
  ArrowRight,
} from 'iconoir-react';
import {
  useMyOrganization,
  isApproved,
} from '@pml.tickets/shared/api/organization-admin/modules/organization';
import { ROUTES } from '@/lib/onboarding/state';
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
    description: 'Being reviewed by our team. Usually takes 1-3 business days.',
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

/** Format an enum value (e.g. NON_PROFIT) into a readable label (Non-profit). */
function formatEnum(v: string | null | undefined): string {
  if (!v) return '';
  const words = v.toLowerCase().split('_');
  return words.map((w, i) => (i === 0 ? w.charAt(0).toUpperCase() + w.slice(1) : w)).join('-');
}

/** Derive up-to-two-letter initials from an organization name. */
function initials(name: string | null | undefined): string {
  if (!name) return '?';
  return name
    .trim()
    .split(/\s+/)
    .slice(0, 2)
    .map((w) => w.charAt(0).toUpperCase())
    .join('');
}

type BadgeColor = 'gray' | 'blue' | 'green' | 'amber' | 'red' | 'teal';

/** Map an organization status to a Radix Badge color. */
function statusBadgeColor(status: string | null | undefined): BadgeColor {
  switch (status) {
    case 'APPROVED':
    case 'ACTIVE':
      return 'green';
    case 'PENDING_REVIEW':
      return 'blue';
    case 'CHANGES_REQUESTED':
      return 'amber';
    case 'REJECTED':
    case 'SUSPENDED':
      return 'red';
    default:
      return 'gray';
  }
}

/** Map a KYB status to a Radix Badge color. */
function kybBadgeColor(kyb: string | null | undefined): BadgeColor {
  switch (kyb) {
    case 'VERIFIED':
    case 'APPROVED':
      return 'green';
    case 'IN_PROGRESS':
    case 'PENDING':
    case 'SUBMITTED':
      return 'blue';
    case 'REJECTED':
      return 'red';
    default:
      return 'gray';
  }
}

/** A single label / value detail row with an optional leading icon. Hidden when there is no value. */
function DetailRow({
  icon: RowIcon,
  label,
  value,
}: {
  icon?: React.ElementType;
  label: string;
  value?: React.ReactNode;
}) {
  if (value === null || value === undefined || value === '') return null;
  return (
    <Flex justify="between" align="center" gap="3">
      <Flex align="center" gap="2" style={{ flexShrink: 0 }}>
        {RowIcon && <RowIcon style={{ width: 15, height: 15, color: 'var(--content-tertiary)' }} />}
        <Text size="2" style={{ color: 'var(--content-tertiary)' }}>{label}</Text>
      </Flex>
      {typeof value === 'string' ? (
        <Text size="2" align="right" style={{ color: 'var(--content-primary)', wordBreak: 'break-word' }}>
          {value}
        </Text>
      ) : (
        value
      )}
    </Flex>
  );
}

/** A capability / readiness tile: icon chip + label + unlocked/locked state. */
function Capability({
  icon: TileIcon,
  label,
  enabled,
}: {
  icon: React.ElementType;
  label: string;
  enabled: boolean;
}) {
  return (
    <Flex
      direction="column"
      align="center"
      gap="2"
      p="3"
      style={{
        borderRadius: 'var(--radius-3)',
        border: '1px solid var(--surface-border)',
        background: enabled ? 'var(--success-50)' : 'var(--surface-secondary)',
        textAlign: 'center',
      }}
    >
      <Box
        aria-hidden="true"
        style={{
          width: 36,
          height: 36,
          borderRadius: '50%',
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          background: enabled ? 'var(--success-100)' : 'var(--surface-tertiary)',
          color: enabled ? 'var(--success-600)' : 'var(--content-tertiary)',
        }}
      >
        <TileIcon style={{ width: 18, height: 18 }} />
      </Box>
      <Text size="1" weight="medium" style={{ color: 'var(--content-primary)', lineHeight: 1.2 }}>
        {label}
      </Text>
      <Flex align="center" gap="1">
        {enabled ? (
          <Check style={{ width: 12, height: 12, color: 'var(--success-600)' }} />
        ) : (
          <Clock style={{ width: 12, height: 12, color: 'var(--content-tertiary)' }} />
        )}
        <Text size="1" style={{ color: enabled ? 'var(--success-600)' : 'var(--content-tertiary)' }}>
          {enabled ? 'Available' : 'Locked'}
        </Text>
      </Flex>
    </Flex>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function StatusPage() {
  const router = useRouter();
  const { organization, status, loading, error, refetch } = useMyOrganization();

  // NOTE: no client-side redirects here.
  //
  // This page used to `router.replace()` out of itself based on the status the
  // client query returned. That duplicated the server guard and disagreed with
  // it whenever the client query failed — including bouncing an applicant to
  // `/welcome`, the setup form, on a transient error. Routing is now decided
  // once, server-side, in the `(application)` layout.
  //
  // @see lib/onboarding/state.ts

  const goToDashboard = useCallback(() => router.push(ROUTES.dashboard), [router]);
  const goToEdit = useCallback(() => router.push(ROUTES.businessInfo), [router]);
  const goToDocuments = useCallback(() => router.push(ROUTES.documents), [router]);
  const goToNewEvent = useCallback(() => router.push('/events/new'), [router]);

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
        {/* Retry only. The "Home" button here used to route to /welcome — the
            setup form — so a transient network error invited an applicant with
            a live application to start a second one. */}
        <Flex gap="2" justify="center">
          <Button size="2" onClick={() => refetch()} className="btn-primary" data-testid="status-retry">
            <Refresh style={{ width: 14, height: 14 }} aria-hidden="true" /> Retry
          </Button>
          <Button
            variant="soft"
            size="2"
            onClick={() => window.open('mailto:support@myticket.zm', '_blank')}
            data-testid="status-support"
          >
            <Mail style={{ width: 14, height: 14 }} aria-hidden="true" /> Contact support
          </Button>
        </Flex>
      </Box>
    );
  }

  // Organization missing from a successful response. The server guard only routes
  // here when one exists, so this is a race with a just-completed mutation —
  // wait it out rather than offering to start again.
  if (!organization || !status) {
    return (
      <Box py="9" style={{ textAlign: 'center' }} aria-live="polite" data-testid="status-empty">
        <Text size="2" style={{ color: 'var(--content-tertiary)', display: 'block', marginBottom: 12 }}>
          Loading your application…
        </Text>
        <Button variant="soft" size="2" onClick={() => refetch()} data-testid="status-empty-retry">
          <Refresh style={{ width: 14, height: 14 }} aria-hidden="true" /> Refresh
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
  // The backend stores both the rejection reason and the changes-requested
  // reason in `rejectionReason`; only show it for the states it belongs to.
  const reviewReason = (needsChanges || rejected) ? organization.rejectionReason : null;

  return (
    <Box width="100%" maxWidth="680px" mx="auto" data-testid="status-shell">
      {/* Status Banner */}
      <Card size="3" mb="4" style={{ background: config.bg }}>
          <Flex align="start" gap="3">
            <Box
              aria-hidden="true"
              style={{
                width: 40,
                height: 40,
                borderRadius: 'var(--radius-3)',
                background: 'var(--color-surface)',
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

          {/* The reviewer's own words, verbatim.
              "Your tax certificate is illegible" and "we do not believe this
              business exists" are different messages that need different
              responses, so the reason is shown rather than summarised into the
              status label. */}
          {reviewReason && (
            <Box
              mt="3"
              p="3"
              data-testid="review-reason"
              style={{
                borderRadius: 'var(--radius-3)',
                background: rejected ? 'var(--red-a2)' : 'var(--amber-a2)',
                border: `1px solid ${rejected ? 'var(--red-a5)' : 'var(--amber-a5)'}`,
              }}
            >
              <Text
                size="1"
                weight="medium"
                style={{ color: 'var(--content-tertiary)', display: 'block', marginBottom: 4 }}
              >
                {rejected ? 'Why it wasn’t approved' : 'What the reviewer needs'}
              </Text>
              <Text size="2" style={{ color: 'var(--content-primary)' }}>
                {reviewReason}
              </Text>
            </Box>
          )}

          {/* Actions — one primary per state, per the design authority. */}
          <Flex gap="2" mt="4" wrap="wrap">
            {approved && (
              <Button size="2" onClick={goToDashboard} className="btn-primary" data-testid="status-cta-dashboard">
                Go to dashboard <ArrowRight style={{ width: 14, height: 14 }} aria-hidden="true" />
              </Button>
            )}

            {needsChanges && (
              <>
                {/* Straight to the documents step when that is what was
                    queried — the common case, and the one where sending
                    somebody back through the whole form loses them. */}
                <Button size="2" onClick={goToDocuments} className="btn-primary" data-testid="status-cta-resubmit">
                  <EditPencil style={{ width: 14, height: 14 }} aria-hidden="true" /> Edit &amp; resubmit
                </Button>
                <Button variant="outline" size="2" onClick={goToEdit} data-testid="status-cta-edit-details">
                  Edit business details
                </Button>
              </>
            )}

            {rejected && (
              <>
                {/* Spec transition 8: REJECTED → DRAFT. A rejection is a door
                    the applicant may walk back through. */}
                <Button size="2" onClick={goToEdit} className="btn-primary" data-testid="status-cta-reapply">
                  Apply again
                </Button>
                <Button
                  variant="outline"
                  size="2"
                  onClick={() => window.open('mailto:support@myticket.zm', '_blank')}
                  data-testid="status-cta-support"
                >
                  <Mail style={{ width: 14, height: 14 }} aria-hidden="true" /> Contact support
                </Button>
              </>
            )}

            {isPending && (
              <>
                {/* Staged access (spec R7): the wait is spent building
                    something rather than watching a holding page. */}
                <Button size="2" onClick={goToNewEvent} className="btn-primary" data-testid="status-cta-draft-event">
                  Start a draft event <ArrowRight style={{ width: 14, height: 14 }} aria-hidden="true" />
                </Button>
                <Button variant="outline" size="2" onClick={() => refetch()} data-testid="status-cta-refresh">
                  <Refresh style={{ width: 14, height: 14 }} aria-hidden="true" /> Check for updates
                </Button>
              </>
            )}
          </Flex>
      </Card>

      {/* Details + Timeline in Grid */}
      <Flex gap="4" direction={{ initial: 'column', sm: 'row' }}>
        {/* Details */}
        <Card size="3" style={{ flex: 1 }}>
            <Text size="2" weight="medium" mb="3" style={{ color: 'var(--content-primary)', display: 'block' }}>
              Application Details
            </Text>

            {/* Identity header: avatar, name, handle, type */}
            <Flex align="center" gap="3" mb="3">
              <Avatar
                size="4"
                radius="large"
                src={organization.logoUrl ?? undefined}
                fallback={initials(organization.name)}
                style={{
                  flexShrink: 0,
                  background: 'linear-gradient(135deg, var(--accent-9), var(--accent-11))',
                  color: 'var(--content-inverse)',
                }}
              />
              <Box style={{ minWidth: 0, flex: 1 }}>
                <Flex align="center" gap="2" wrap="wrap">
                  <Text size="3" weight="bold" style={{ color: 'var(--content-primary)' }}>
                    {organization.name || '-'}
                  </Text>
                  {organization.type && (
                    <Badge color="teal" variant="soft" size="1" radius="full">
                      {formatEnum(organization.type)}
                    </Badge>
                  )}
                </Flex>
                {organization.slug && (
                  <Text size="1" style={{ color: 'var(--content-tertiary)' }}>@{organization.slug}</Text>
                )}
              </Box>
            </Flex>

            {/* Tagline + description */}
            {organization.tagline && (
              <Text size="2" style={{ color: 'var(--content-secondary)', fontStyle: 'italic', display: 'block', marginBottom: 8 }}>
                {organization.tagline}
              </Text>
            )}
            {organization.description && (
              <Text size="2" style={{ color: 'var(--content-secondary)', lineHeight: 1.5, display: 'block', marginBottom: 12 }}>
                {organization.description}
              </Text>
            )}

            {/* Detail rows */}
            <Flex
              direction="column"
              gap="2"
              pt="3"
              style={{ borderTop: '1px solid var(--surface-border)' }}
            >
              <DetailRow
                icon={Building}
                label="Location"
                value={[organization.businessAddress?.city, organization.businessAddress?.province]
                  .filter(Boolean)
                  .join(', ') || undefined}
              />
              <DetailRow icon={Mail} label="Email" value={organization.businessEmail} />
              <DetailRow icon={Phone} label="Phone" value={organization.businessPhone} />
              <DetailRow
                icon={Clock}
                label="Status"
                value={
                  <Badge color={statusBadgeColor(organization.status)} variant="soft" size="1">
                    {formatEnum(organization.status)}
                  </Badge>
                }
              />
              <DetailRow
                icon={ShieldCheck}
                label="KYB"
                value={
                  <Badge color={kybBadgeColor(organization.kybStatus)} variant="soft" size="1">
                    {formatEnum(organization.kybStatus)}
                  </Badge>
                }
              />
              <DetailRow icon={Calendar} label="Submitted" value={formatDate(organization.submittedAt)} />
              <DetailRow icon={Calendar} label="Updated" value={formatDate(organization.updatedAt)} />
            </Flex>
        </Card>

        {/* Timeline */}
        <Card size="3" style={{ flex: 1 }}>
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
              title="Under review"
              status={isPending ? 'current' : (approved || rejected ? 'done' : 'pending')}
            />
            <Step
              num={3}
              title="Approved"
              status={approved ? 'done' : 'pending'}
              date={formatDate(organization.approvedAt)}
              isLast
            />
        </Card>
      </Flex>

      {/* Readiness / capabilities infographic */}
      <Card size="3" mt="4">
        <Flex justify="between" align="center" mb="3" wrap="wrap" gap="2">
          <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
            What you can do now
          </Text>
          <Text size="1" style={{ color: 'var(--content-tertiary)' }}>
            {[
              organization.canCreateDraftEvents,
              organization.canPublishEvents,
              organization.canReceivePayouts,
              organization.verified,
            ].filter(Boolean).length}{' '}
            of 4 unlocked
          </Text>
        </Flex>
        <Grid columns={{ initial: '2', sm: '4' }} gap="3">
          <Capability icon={EditPencil} label="Create draft events" enabled={!!organization.canCreateDraftEvents} />
          <Capability icon={Rocket} label="Publish events" enabled={!!organization.canPublishEvents} />
          <Capability icon={SendDiagonal} label="Receive payouts" enabled={!!organization.canReceivePayouts} />
          <Capability icon={ShieldCheck} label="Verified organization" enabled={!!organization.verified} />
        </Grid>
      </Card>

      {/* Tips while waiting */}
      {isPending && (
        <Card size="3" mt="4" style={{ background: 'var(--info-50)' }}>
            <Text size="2" weight="medium" mb="2" style={{ color: 'var(--content-primary)', display: 'block' }}>
              While you wait
            </Text>
            <Text size="2" style={{ color: 'var(--content-secondary)', lineHeight: 1.5 }}>
              Create draft events, explore the dashboard, and prepare your event content.
              Drafts can be published once approved.
            </Text>
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
