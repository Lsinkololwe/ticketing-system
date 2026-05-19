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
  useOrganizerProfile,
  useApproveOrganizer,
  useRejectOrganizer,
  useRequestOrganizerChanges,
  useSuspendOrganizer,
  useReactivateOrganizer,
  useVerifyOrganizerBusiness,
  useVerifyOrganizerDocuments,
  useVerifyOrganizerBankAccount,
  useUpdateOrganizerAdminNotes,
} from '@pml.tickets/shared/api/graphql/admin/organizers';
import type { OrganizerStatus, DocumentStatus } from '@pml.tickets/shared/types/graphql';

// ==================== Status Helpers ====================

const STATUS_COLORS: Record<OrganizerStatus, 'gray' | 'amber' | 'green' | 'red' | 'blue' | 'orange'> = {
  DRAFT: 'gray',
  PENDING_DOCUMENTS: 'blue',
  PENDING_REVIEW: 'amber',
  APPROVED: 'green',
  REJECTED: 'red',
  SUSPENDED: 'red',
  CHANGES_REQUESTED: 'orange',
};

const STATUS_LABELS: Record<OrganizerStatus, string> = {
  DRAFT: 'Draft',
  PENDING_DOCUMENTS: 'Pending Documents',
  PENDING_REVIEW: 'Pending Review',
  APPROVED: 'Approved',
  REJECTED: 'Rejected',
  SUSPENDED: 'Suspended',
  CHANGES_REQUESTED: 'Changes Requested',
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

  // Fetch organizer profile
  const { profile, loading, error, refetch } = useOrganizerProfile(organizerId);

  // Mutation hooks
  const { approveOrganizer, loading: approving } = useApproveOrganizer();
  const { rejectOrganizer, loading: rejecting } = useRejectOrganizer();
  const { requestChanges, loading: requesting } = useRequestOrganizerChanges();
  const { suspendOrganizer, loading: suspending } = useSuspendOrganizer();
  const { reactivateOrganizer, loading: reactivating } = useReactivateOrganizer();
  const { verifyBusiness, loading: verifyingBusiness } = useVerifyOrganizerBusiness();
  const { verifyDocuments, loading: verifyingDocs } = useVerifyOrganizerDocuments();
  const { verifyBankAccount, loading: verifyingBank } = useVerifyOrganizerBankAccount();
  const { updateAdminNotes: _updateAdminNotes, loading: _updatingNotes } = useUpdateOrganizerAdminNotes();

  // Dialog states
  const [approveDialogOpen, setApproveDialogOpen] = useState(false);
  const [rejectDialogOpen, setRejectDialogOpen] = useState(false);
  const [changesDialogOpen, setChangesDialogOpen] = useState(false);
  const [suspendDialogOpen, setSuspendDialogOpen] = useState(false);

  // Handlers
  const handleApprove = useCallback(async () => {
    await approveOrganizer(organizerId);
    setApproveDialogOpen(false);
    refetch();
  }, [approveOrganizer, organizerId, refetch]);

  const handleReject = useCallback(async (reason?: string) => {
    if (reason) {
      await rejectOrganizer(organizerId, reason);
      setRejectDialogOpen(false);
      refetch();
    }
  }, [rejectOrganizer, organizerId, refetch]);

  const handleRequestChanges = useCallback(async (reason?: string) => {
    if (reason) {
      await requestChanges(organizerId, reason);
      setChangesDialogOpen(false);
      refetch();
    }
  }, [requestChanges, organizerId, refetch]);

  const handleSuspend = useCallback(async (reason?: string) => {
    if (reason) {
      await suspendOrganizer(organizerId, reason);
      setSuspendDialogOpen(false);
      refetch();
    }
  }, [suspendOrganizer, organizerId, refetch]);

  const handleReactivate = useCallback(async () => {
    await reactivateOrganizer(organizerId);
    refetch();
  }, [reactivateOrganizer, organizerId, refetch]);

  const handleVerifyBusiness = useCallback(async () => {
    await verifyBusiness(organizerId);
    refetch();
  }, [verifyBusiness, organizerId, refetch]);

  const handleVerifyDocuments = useCallback(async () => {
    await verifyDocuments(organizerId);
    refetch();
  }, [verifyDocuments, organizerId, refetch]);

  const handleVerifyBankAccount = useCallback(async () => {
    await verifyBankAccount(organizerId);
    refetch();
  }, [verifyBankAccount, organizerId, refetch]);

  // Loading state
  if (loading) {
    return (
      <Flex align="center" justify="center" style={{ minHeight: '50vh' }}>
        <Spinner size="3" />
      </Flex>
    );
  }

  // Error state
  if (error || !profile) {
    return (
      <Callout.Root color="red">
        <Callout.Icon>
          <WarningTriangle />
        </Callout.Icon>
        <Callout.Text>
          {error?.message || 'Organizer not found'}
        </Callout.Text>
      </Callout.Root>
    );
  }

  const isActionLoading = approving || rejecting || requesting || suspending || reactivating;
  const isPendingReview = profile.status === 'PENDING_REVIEW';
  const isApproved = profile.status === 'APPROVED';
  const isSuspended = profile.status === 'SUSPENDED';

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
              fallback={profile.companyName?.charAt(0) || 'O'}
            />
            <Box>
              <Heading size="5">{profile.companyName || 'Unnamed Organizer'}</Heading>
              <Flex align="center" gap="2" mt="1">
                <Badge color={STATUS_COLORS[profile.status]} variant="soft">
                  {STATUS_LABELS[profile.status]}
                </Badge>
                {profile.verified && (
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
      {profile.statusReason && (
        <Callout.Root color="amber" mb="4">
          <Callout.Icon>
            <WarningTriangle />
          </Callout.Icon>
          <Callout.Text>
            <strong>Status Reason:</strong> {profile.statusReason}
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
              <InfoRow icon={Building} label="Company Name" value={profile.companyName} />
              <InfoRow label="Tagline" value={profile.tagline} />
              <InfoRow label="Business Type" value={profile.businessType} />
              <InfoRow label="Year Established" value={profile.yearEstablished} />
              <Separator my="2" />
              <InfoRow label="Registration Number" value={profile.businessRegistrationNumber} />
              <InfoRow label="Tax ID (TPIN)" value={profile.taxId} />
            </InfoCard>

            {/* Contact Information */}
            <InfoCard title="Contact Information">
              <InfoRow icon={Mail} label="Email" value={profile.businessEmail} />
              <InfoRow icon={Phone} label="Phone" value={profile.businessPhone} />
              <InfoRow icon={Globe} label="Website" value={profile.website} />
              <Separator my="2" />
              <InfoRow icon={MapPin} label="Address" value={profile.businessAddress} />
              <InfoRow label="City" value={profile.city} />
              <InfoRow label="Province" value={profile.province} />
              <InfoRow label="Country" value={profile.country || 'Zambia'} />
              <InfoRow label="Postal Code" value={profile.postalCode} />
            </InfoCard>

            {/* Platform Settings */}
            <InfoCard title="Platform Settings">
              <InfoRow label="Commission Rate" value={profile.commissionRate ? `${profile.commissionRate}%` : null} />
              <InfoRow label="Payout Schedule" value={profile.payoutSchedule} />
            </InfoCard>

            {/* Statistics */}
            <InfoCard title="Statistics">
              <InfoRow label="Total Events" value={profile.totalEvents} />
              <InfoRow label="Tickets Sold" value={profile.totalTicketsSold} />
              <InfoRow label="Total Revenue" value={profile.totalRevenue ? `K${profile.totalRevenue}` : null} />
              <InfoRow label="Average Rating" value={profile.averageRating ? `${profile.averageRating}/5` : null} />
            </InfoCard>

            {/* Description */}
            {profile.companyDescription && (
              <StyledCard style={{ gridColumn: '1 / -1' }}>
                <Heading size="3" mb="3">Description</Heading>
                <Text size="2" color="gray">
                  {profile.companyDescription}
                </Text>
              </StyledCard>
            )}

            {/* Review Notes */}
            {profile.reviewNotes && (
              <StyledCard style={{ gridColumn: '1 / -1' }}>
                <Heading size="3" mb="3">Admin Review Notes</Heading>
                <Text size="2" color="gray">
                  {profile.reviewNotes}
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
                  color={profile.verified ? 'green' : 'gray'}
                  variant="soft"
                  style={{ padding: '12px 24px', borderRadius: '50%' }}
                >
                  <Building width={24} height={24} />
                </Badge>
                <Text weight="medium">Business Verified</Text>
                <Text size="1" color="gray">
                  {profile.verified ? 'Verified' : 'Not Verified'}
                </Text>
                {!profile.verified && (
                  <Button
                    size="1"
                    variant="soft"
                    onClick={handleVerifyBusiness}
                    disabled={verifyingBusiness}
                  >
                    {verifyingBusiness ? <Spinner size="1" /> : 'Verify'}
                  </Button>
                )}
              </Flex>
            </StyledCard>

            <StyledCard>
              <Flex direction="column" align="center" gap="3" py="4">
                <Badge
                  size="2"
                  color={profile.documentsVerified ? 'green' : 'gray'}
                  variant="soft"
                  style={{ padding: '12px 24px', borderRadius: '50%' }}
                >
                  <ShieldCheck width={24} height={24} />
                </Badge>
                <Text weight="medium">Documents Verified</Text>
                <Text size="1" color="gray">
                  {profile.documentsVerified ? 'Verified' : 'Not Verified'}
                </Text>
                {!profile.documentsVerified && (
                  <Button
                    size="1"
                    variant="soft"
                    onClick={handleVerifyDocuments}
                    disabled={verifyingDocs}
                  >
                    {verifyingDocs ? <Spinner size="1" /> : 'Verify'}
                  </Button>
                )}
              </Flex>
            </StyledCard>

            <StyledCard>
              <Flex direction="column" align="center" gap="3" py="4">
                <Badge
                  size="2"
                  color={profile.bankVerified ? 'green' : 'gray'}
                  variant="soft"
                  style={{ padding: '12px 24px', borderRadius: '50%' }}
                >
                  <Building width={24} height={24} />
                </Badge>
                <Text weight="medium">Bank Account Verified</Text>
                <Text size="1" color="gray">
                  {profile.bankVerified ? 'Verified' : 'Not Verified'}
                </Text>
                {!profile.bankVerified && (
                  <Button
                    size="1"
                    variant="soft"
                    onClick={handleVerifyBankAccount}
                    disabled={verifyingBank}
                  >
                    {verifyingBank ? <Spinner size="1" /> : 'Verify'}
                  </Button>
                )}
              </Flex>
            </StyledCard>
          </Grid>
        </Tabs.Content>

        {/* Documents Tab */}
        <Tabs.Content value="documents">
          <Box mt="4">
            {profile.verificationDocuments && profile.verificationDocuments.length > 0 ? (
              <Grid columns={{ initial: '1', md: '2' }} gap="4">
                {profile.verificationDocuments.map((doc) => (
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
              <InfoRow icon={Calendar} label="Created" value={profile.createdAt ? new Date(profile.createdAt).toLocaleDateString() : null} />
              <InfoRow label="Submitted" value={profile.submittedAt ? new Date(profile.submittedAt).toLocaleDateString() : null} />
              <InfoRow label="Reviewed" value={profile.reviewedAt ? new Date(profile.reviewedAt).toLocaleDateString() : null} />
              <InfoRow label="Approved" value={profile.approvedAt ? new Date(profile.approvedAt).toLocaleDateString() : null} />
              <InfoRow label="Last Updated" value={profile.updatedAt ? new Date(profile.updatedAt).toLocaleDateString() : null} />
            </InfoCard>

            <InfoCard title="Review Information">
              <InfoRow icon={UserIcon} label="Reviewed By" value={profile.reviewedBy?.fullName} />
              {profile.reviewNotes && (
                <Box mt="2">
                  <Text size="2" color="gray">Notes:</Text>
                  <Text size="2">{profile.reviewNotes}</Text>
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
