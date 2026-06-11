'use client';

/**
 * Organizer Detail Page
 *
 * Shows full organizer profile with review actions.
 * Uses shared API hooks from @pml.tickets/shared.
 */

import { useState, useCallback } from 'react';
import { useParams, useRouter } from 'next/navigation';
import {
  Box,
  Flex,
  Heading,
  Text,
  Badge,
  Button,
  IconButton,
  Separator,
  Tabs,
  Avatar,
  Dialog,
  TextArea,
  Spinner,
  Grid,
  Callout,
} from '@radix-ui/themes';
import { StyledCard, InfoCard } from '@/components/ui/StyledCard';
import {
  ArrowLeft,
  Check,
  Xmark,
  EditPencil,
  Pause,
  PlaySolid,
  Mail,
  Phone,
  Globe,
  MapPin,
  Building,
  Calendar,
  User as UserIcon,
  WarningTriangle,
  ShieldCheck,
} from 'iconoir-react';
import {
  useOrganization,
  useApproveOrganization,
  useRejectOrganization,
  useRequestOrganizationChanges,
  useSuspendOrganization,
  useUnsuspendOrganization,
  type Organization,
  getStatusColor,
  getStatusLabel,
} from '@pml.tickets/shared/api/admin/modules/organization';
import type { DocumentStatus } from '@pml.tickets/shared/types/graphql';

// Extended organization type that includes fields from the admin query
// that may not be in the base generated types
interface OrganizationAdmin {
  id: string;
  ownerId: string;
  name: string | null;
  slug: string | null;
  tagline?: string | null;
  description?: string | null;
  logoUrl?: string | null;
  bannerUrl?: string | null;
  website?: string | null;
  type?: string | null;
  status: string;
  kybStatus?: string | null;
  businessType?: string | null;
  businessRegistrationNumber?: string | null;
  taxId?: string | null;
  businessPhone?: string | null;
  businessEmail?: string | null;
  businessAddress?: {
    street?: string | null;
    city?: string | null;
    province?: string | null;
    country?: string | null;
    postalCode?: string | null;
  } | null;
  socialLinks?: {
    facebook?: string | null;
    twitter?: string | null;
    instagram?: string | null;
    linkedin?: string | null;
    youtube?: string | null;
    tiktok?: string | null;
  } | null;
  verified: boolean;
  documentsVerified?: boolean | null;
  payoutAccountVerified?: boolean | null;
  verifiedAt?: string | null;
  submittedAt?: string | null;
  approvedAt?: string | null;
  rejectionReason?: string | null;
  reviewedAt?: string | null;
  reviewedBy?: {
    id: string;
    firstName?: string | null;
    lastName?: string | null;
    fullName?: string | null;
  } | null;
  verificationDocuments?: Array<{
    id: string;
    documentType: string;
    documentUrl?: string | null;
    fileName?: string | null;
    fileSize?: number | null;
    mimeType?: string | null;
    status: string;
    uploadedAt?: string | null;
  }> | null;
  totalEvents?: number | null;
  totalRevenue?: string | null;
  totalTicketsSold?: number | null;
  averageRating?: number | null;
  createdAt: string;
  updatedAt?: string | null;
}

// ==================== Status Helpers ====================

// Status color mapping - uses string keys to handle approval workflow statuses
// that may not be in the generated types yet
const STATUS_COLORS: Record<string, 'gray' | 'amber' | 'green' | 'red' | 'blue' | 'orange'> = {
  DRAFT: 'gray',
  PENDING_REVIEW: 'amber',
  APPROVED: 'green',
  ACTIVE: 'green',
  REJECTED: 'red',
  SUSPENDED: 'red',
  CHANGES_REQUESTED: 'orange',
  INACTIVE: 'gray',
  PENDING_DELETION: 'red',
};

const STATUS_LABELS: Record<string, string> = {
  DRAFT: 'Draft',
  PENDING_REVIEW: 'Pending Review',
  APPROVED: 'Approved',
  ACTIVE: 'Active',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
  CHANGES_REQUESTED: 'Changes Requested',
  INACTIVE: 'Inactive',
  PENDING_DELETION: 'Pending Deletion',
};

// ==================== Info Card Component ====================
// Using InfoCard from @/components/ui/StyledCard

function InfoRow({
  label,
  value,
  icon: Icon,
}: {
  label: string;
  value: React.ReactNode;
  icon?: React.ComponentType;
}) {
  return (
    <Flex justify="between" align="center" py="2">
      <Flex align="center" gap="2">
        {Icon && (
          <span style={{ color: 'var(--gray-9)' }}>
            <Icon />
          </span>
        )}
        <Text size="2" color="gray">{label}</Text>
      </Flex>
      <Text size="2" weight="medium">{value || '-'}</Text>
    </Flex>
  );
}

// ==================== Review Dialog Component ====================

function ReviewDialog({
  open,
  onOpenChange,
  title,
  description,
  confirmLabel,
  confirmColor,
  requireReason,
  onConfirm,
  loading,
}: {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  title: string;
  description: string;
  confirmLabel: string;
  confirmColor: 'green' | 'red' | 'orange';
  requireReason?: boolean;
  onConfirm: (reason?: string) => void;
  loading: boolean;
}) {
  const [reason, setReason] = useState('');

  const handleConfirm = () => {
    onConfirm(reason || undefined);
  };

  return (
    <Dialog.Root open={open} onOpenChange={onOpenChange}>
      <Dialog.Content maxWidth="450px">
        <Dialog.Title>{title}</Dialog.Title>
        <Dialog.Description size="2" mb="4">
          {description}
        </Dialog.Description>

        {requireReason && (
          <TextArea
            placeholder="Enter reason..."
            value={reason}
            onChange={(e) => setReason(e.target.value)}
            style={{ minHeight: 100 }}
          />
        )}

        <Flex gap="3" mt="4" justify="end">
          <Dialog.Close>
            <Button variant="soft" color="gray">
              Cancel
            </Button>
          </Dialog.Close>
          <Button
            color={confirmColor}
            onClick={handleConfirm}
            disabled={loading || (requireReason && !reason.trim())}
          >
            {loading ? <Spinner size="1" /> : confirmLabel}
          </Button>
        </Flex>
      </Dialog.Content>
    </Dialog.Root>
  );
}

// ==================== Main Page Component ====================

export default function OrganizerDetailPage() {
  const params = useParams();
  const router = useRouter();
  const organizerId = params.id as string;

  // Fetch organization (cast to extended type that includes admin fields)
  const { organization: rawOrganization, loading, error, refetch } = useOrganization(organizerId);
  const organization = rawOrganization as OrganizationAdmin | null;

  // Mutation hooks
  const { approve, loading: approving } = useApproveOrganization();
  const { reject, loading: rejecting } = useRejectOrganization();
  const { requestChanges, loading: requesting } = useRequestOrganizationChanges();
  const { suspend, loading: suspending } = useSuspendOrganization();
  const { unsuspend, loading: reactivating } = useUnsuspendOrganization();

  // Dialog states
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [changesDialogOpen, setChangesDialogOpen] = useState(false);
  const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);

  // Handlers
  const handleApprove = useCallback(async () => {
    await approve(organizerId);
    setApproveDialogOpen(false);
    refetch();
  }, [approve, organizerId, refetch]);

  const handleReject = useCallback(async (reason?: string) => {
    if (reason) {
      await reject(organizerId, reason);
      setRejectDialogOpen(false);
      refetch();
    }
  }, [reject, organizerId, refetch]);

  const handleRequestChanges = useCallback(async (reason?: string) => {
    if (reason) {
      await requestChanges(organizerId, reason);
      setChangesDialogOpen(false);
      refetch();
    }
  }, [requestChanges, organizerId, refetch]);

  const handleSuspend = useCallback(async (reason?: string) => {
    if (reason) {
      await suspend(organizerId, reason);
      setSuspendDialogOpen(false);
      refetch();
    }
  }, [suspend, organizerId, refetch]);

  const handleReactivate = useCallback(async () => {
    await unsuspend(organizerId);
    refetch();
  }, [unsuspend, organizerId, refetch]);

  // Loading state
  if (loading) {
    return (
      <Flex align="center" justify="center" style={{ minHeight: '50vh' }}>
        <Spinner size="3" />
      </Flex>
    );
  }

  // Error state
  if (error || !organization) {
    return (
      <Callout.Root color="red">
        <Callout.Icon>
          <WarningTriangle />
        </Callout.Icon>
        <Callout.Text>
          {error?.message || 'Organization not found'}
        </Callout.Text>
      </Callout.Root>
    );
  }

  const isActionLoading = approving || rejecting || requesting || suspending || reactivating;
  const isPendingReview = organization.status === 'PENDING_REVIEW';
  const isApproved = organization.status === 'APPROVED' || organization.status === 'ACTIVE';
  const isSuspended = organization.status === 'SUSPENDED';

  return (
    <Box>
      {/* Header */}
      <Flex align="center" gap="4" mb="5">
        <IconButton variant="ghost" onClick={() => router.back()}>
          <ArrowLeft />
        </IconButton>
        <Box style={{ flex: 1 }}>
          <Flex align="center" gap="3">
            <Avatar
              size="4"
              fallback={organization.name?.charAt(0) || 'O'}
            />
            <Box>
              <Heading size="5">{organization.name || 'Unnamed Organization'}</Heading>
              <Flex align="center" gap="2" mt="1">
                <Badge color={STATUS_COLORS[organization.status]} variant="soft">
                  {STATUS_LABELS[organization.status]}
                </Badge>
                {organization.verified && (
                  <Badge color="green" variant="soft">
                    <ShieldCheck width={12} height={12} /> Verified
                  </Badge>
                )}
              </Flex>
            </Box>
          </Flex>
        </Box>

        {/* Action Buttons */}
        <Flex gap="2">
          {isPendingReview && (
            <>
              <Button
                color="green"
                onClick={() => setApproveDialogOpen(true)}
                disabled={isActionLoading}
              >
                <Check width={16} height={16} /> Approve
              </Button>
              <Button
                color="orange"
                variant="soft"
                onClick={() => setChangesDialogOpen(true)}
                disabled={isActionLoading}
              >
                <EditPencil width={16} height={16} /> Request Changes
              </Button>
              <Button
                color="red"
                variant="soft"
                onClick={() => setRejectDialogOpen(true)}
                disabled={isActionLoading}
              >
                <Xmark width={16} height={16} /> Reject
              </Button>
            </>
          )}
          {isApproved && (
            <Button
              color="red"
              variant="soft"
              onClick={() => setSuspendDialogOpen(true)}
              disabled={isActionLoading}
            >
              <Pause width={16} height={16} /> Suspend
            </Button>
          )}
          {isSuspended && (
            <Button
              color="green"
              onClick={handleReactivate}
              disabled={isActionLoading}
            >
              <PlaySolid width={16} height={16} /> Reactivate
            </Button>
          )}
        </Flex>
      </Flex>

      {/* Status Reason Callout */}
      {organization.rejectionReason && (
        <Callout.Root color="amber" mb="4">
          <Callout.Icon>
            <WarningTriangle />
          </Callout.Icon>
          <Callout.Text>
            <strong>Status Reason:</strong> {organization.rejectionReason}
          </Callout.Text>
        </Callout.Root>
      )}

      {/* Tabs */}
      <Tabs.Root defaultValue="details">
        <Tabs.List>
          <Tabs.Trigger value="details">Details</Tabs.Trigger>
          <Tabs.Trigger value="verification">Verification</Tabs.Trigger>
          <Tabs.Trigger value="documents">Documents</Tabs.Trigger>
          <Tabs.Trigger value="activity">Activity</Tabs.Trigger>
        </Tabs.List>

        {/* Details Tab */}
        <Tabs.Content value="details">
          <Grid columns={{ initial: '1', md: '2' }} gap="4" mt="4">
            {/* Business Information */}
            <InfoCard title="Business Information">
              <InfoRow icon={Building} label="Organization Name" value={organization.name} />
              <InfoRow label="Tagline" value={organization.tagline} />
              <InfoRow label="Organization Type" value={organization.type} />
              <InfoRow label="Business Type" value={organization.businessType} />
              <Separator my="2" />
              <InfoRow label="Registration Number" value={organization.businessRegistrationNumber} />
              <InfoRow label="Tax ID (TPIN)" value={organization.taxId} />
            </InfoCard>

            {/* Contact Information */}
            <InfoCard title="Contact Information">
              <InfoRow icon={Mail} label="Email" value={organization.businessEmail} />
              <InfoRow icon={Phone} label="Phone" value={organization.businessPhone} />
              <InfoRow icon={Globe} label="Website" value={organization.website} />
              <Separator my="2" />
              <InfoRow icon={MapPin} label="Address" value={organization.businessAddress?.street} />
              <InfoRow label="City" value={organization.businessAddress?.city} />
              <InfoRow label="Province" value={organization.businessAddress?.province} />
              <InfoRow label="Country" value={organization.businessAddress?.country || 'Zambia'} />
              <InfoRow label="Postal Code" value={organization.businessAddress?.postalCode} />
            </InfoCard>

            {/* Statistics */}
            <InfoCard title="Statistics">
              <InfoRow label="Total Events" value={organization.totalEvents} />
              <InfoRow label="Tickets Sold" value={organization.totalTicketsSold} />
              <InfoRow label="Total Revenue" value={organization.totalRevenue ? `K${organization.totalRevenue}` : null} />
              <InfoRow label="Average Rating" value={organization.averageRating ? `${organization.averageRating}/5` : null} />
            </InfoCard>

            {/* Description */}
            {organization.description && (
              <StyledCard style={{ gridColumn: '1 / -1' }}>
                <Heading size="3" mb="3">Description</Heading>
                <Text size="2" color="gray">
                  {organization.description}
                </Text>
              </StyledCard>
            )}
          </Grid>
        </Tabs.Content>

        {/* Verification Tab */}
        <Tabs.Content value="verification">
          <Grid columns={{ initial: '1', md: '3' }} gap="4" mt="4">
            <StyledCard>
              <Flex direction="column" align="center" gap="3" py="4">
                <Badge
                  size="2"
                  color={organization.verified ? 'green' : 'gray'}
                  variant="soft"
                  style={{ padding: '12px 24px', borderRadius: '50%' }}
                >
                  <Building width={24} height={24} />
                </Badge>
                <Text weight="medium">Business Verified</Text>
                <Text size="1" color="gray">
                  {organization.verified ? 'Verified' : 'Not Verified'}
                </Text>
                {organization.verifiedAt && (
                  <Text size="1" color="gray">
                    {new Date(organization.verifiedAt).toLocaleDateString()}
                  </Text>
                )}
              </Flex>
            </StyledCard>

            <StyledCard>
              <Flex direction="column" align="center" gap="3" py="4">
                <Badge
                  size="2"
                  color={organization.documentsVerified ? 'green' : 'gray'}
                  variant="soft"
                  style={{ padding: '12px 24px', borderRadius: '50%' }}
                >
                  <ShieldCheck width={24} height={24} />
                </Badge>
                <Text weight="medium">Documents Verified</Text>
                <Text size="1" color="gray">
                  {organization.documentsVerified ? 'Verified' : 'Not Verified'}
                </Text>
              </Flex>
            </StyledCard>

            <StyledCard>
              <Flex direction="column" align="center" gap="3" py="4">
                <Badge
                  size="2"
                  color={organization.payoutAccountVerified ? 'green' : 'gray'}
                  variant="soft"
                  style={{ padding: '12px 24px', borderRadius: '50%' }}
                >
                  <Building width={24} height={24} />
                </Badge>
                <Text weight="medium">Payout Account Verified</Text>
                <Text size="1" color="gray">
                  {organization.payoutAccountVerified ? 'Verified' : 'Not Verified'}
                </Text>
              </Flex>
            </StyledCard>
          </Grid>
        </Tabs.Content>

        {/* Documents Tab */}
        <Tabs.Content value="documents">
          <Box mt="4">
            {organization.verificationDocuments && organization.verificationDocuments.length > 0 ? (
              <Grid columns={{ initial: '1', md: '2' }} gap="4">
                {organization.verificationDocuments.map((doc) => (
                  <StyledCard key={doc.id}>
                    <Flex justify="between" align="center">
                      <Box>
                        <Text weight="medium" size="2">{doc.documentType}</Text>
                        <Text size="1" color="gray">{doc.fileName}</Text>
                      </Box>
                      <Badge
                        color={doc.status === ('APPROVED' as DocumentStatus) ? 'green' : doc.status === ('REJECTED' as DocumentStatus) ? 'red' : 'amber'}
                        variant="soft"
                      >
                        {doc.status}
                      </Badge>
                    </Flex>
                  </StyledCard>
                ))}
              </Grid>
            ) : (
              <StyledCard>
                <Flex align="center" justify="center" direction="column" gap="2" py="9">
                  <Text color="gray" size="2">No documents uploaded</Text>
                </Flex>
              </StyledCard>
            )}
          </Box>
        </Tabs.Content>

        {/* Activity Tab */}
        <Tabs.Content value="activity">
          <Grid columns={{ initial: '1', md: '2' }} gap="4" mt="4">
            <InfoCard title="Timeline">
              <InfoRow icon={Calendar} label="Created" value={organization.createdAt ? new Date(organization.createdAt).toLocaleDateString() : null} />
              <InfoRow label="Submitted" value={organization.submittedAt ? new Date(organization.submittedAt).toLocaleDateString() : null} />
              <InfoRow label="Reviewed" value={organization.reviewedAt ? new Date(organization.reviewedAt).toLocaleDateString() : null} />
              <InfoRow label="Approved" value={organization.approvedAt ? new Date(organization.approvedAt).toLocaleDateString() : null} />
              <InfoRow label="Last Updated" value={organization.updatedAt ? new Date(organization.updatedAt).toLocaleDateString() : null} />
            </InfoCard>

            <InfoCard title="Review Information">
              <InfoRow icon={UserIcon} label="Reviewed By" value={organization.reviewedBy?.fullName} />
              {organization.rejectionReason && (
                <Box mt="2">
                  <Text size="2" color="gray">Reason:</Text>
                  <Text size="2">{organization.rejectionReason}</Text>
                </Box>
              )}
            </InfoCard>
          </Grid>
        </Tabs.Content>
      </Tabs.Root>

      {/* Dialogs */}
      <ReviewDialog
        open={approveDialogOpen}
        onOpenChange={setApproveDialogOpen}
        title="Approve Organizer"
        description="Are you sure you want to approve this organizer application? They will be able to create events and sell tickets."
        confirmLabel="Approve"
        confirmColor="green"
        onConfirm={handleApprove}
        loading={approving}
      />

      <ReviewDialog
        open={rejectDialogOpen}
        onOpenChange={setRejectDialogOpen}
        title="Reject Organizer"
        description="Please provide a reason for rejecting this application. The organizer will be notified."
        confirmLabel="Reject"
        confirmColor="red"
        requireReason
        onConfirm={handleReject}
        loading={rejecting}
      />

      <ReviewDialog
        open={changesDialogOpen}
        onOpenChange={setChangesDialogOpen}
        title="Request Changes"
        description="Please describe what changes are needed. The organizer will be notified and can resubmit."
        confirmLabel="Request Changes"
        confirmColor="orange"
        requireReason
        onConfirm={handleRequestChanges}
        loading={requesting}
      />

      <ReviewDialog
        open={suspendDialogOpen}
        onOpenChange={setSuspendDialogOpen}
        title="Suspend Organizer"
        description="Please provide a reason for suspending this organizer. They will not be able to create new events."
        confirmLabel="Suspend"
        confirmColor="red"
        requireReason
        onConfirm={handleSuspend}
        loading={suspending}
      />
    </Box>
  );
}
