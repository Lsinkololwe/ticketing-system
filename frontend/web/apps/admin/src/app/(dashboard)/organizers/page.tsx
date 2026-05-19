'use client';

/**
 * Organizers Management Page
 *
 * Admin page for managing organizer applications and profiles.
 * Uses shared API hooks from @pml.tickets/shared.
 */

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Flex,
  Heading,
  Text,
  Tabs,
  Table,
  Badge,
  Button,
  TextField,
  IconButton,
  DropdownMenu,
  Spinner,
  Avatar,
  Tooltip,
} from '@radix-ui/themes';
import { StyledCard, EmptyCard } from '@/components/ui/StyledCard';
import {
  Search,
  MoreVert,
  Eye,
  Check,
  Xmark,
  EditPencil,
  Pause,
  PlaySolid,
  RefreshDouble,
} from 'iconoir-react';
import {
  // Query hooks
  useOrganizerApplications,
  usePendingApplications,
  useApprovedOrganizers,
  useSuspendedOrganizers,
  useSearchOrganizers,
  // Mutation hooks
  useApproveOrganizer,
  useRejectOrganizer,
  useSuspendOrganizer,
  useReactivateOrganizer,
} from '@pml.tickets/shared/api/graphql/admin/organizers';
import type { OrganizerProfile, OrganizerStatus } from '@pml.tickets/shared/types/graphql';

type TabValue = 'all' | 'pending' | 'approved' | 'suspended' | 'rejected' | 'changes_requested';

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

function OrganizerStatusBadge({ status }: { status: OrganizerStatus }) {
  return (
    <Badge color={STATUS_COLORS[status]} variant="soft" size="1">
      {STATUS_LABELS[status]}
    </Badge>
  );
}

function OrganizersTable({
  organizers,
  loading,
  onView,
  onApprove,
  onReject,
  onSuspend,
  onReactivate,
}: {
  organizers: OrganizerProfile[];
  loading: boolean;
  onView: (id: string) => void;
  onApprove: (id: string) => void;
  onReject: (id: string) => void;
  onSuspend: (id: string) => void;
  onReactivate: (id: string) => void;
}) {
  if (loading) {
    return (
      <Flex align="center" justify="center" py="9">
        <Spinner size="3" />
      </Flex>
    );
  }

  if (organizers.length === 0) {
    return <EmptyCard message="No organizers found" />;
  }

  return (
    <Table.Root variant="surface">
      <Table.Header>
        <Table.Row>
          <Table.ColumnHeaderCell>Company</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell>Contact</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell>Verification</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell>Submitted</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell align="right">Actions</Table.ColumnHeaderCell>
        </Table.Row>
      </Table.Header>
      <Table.Body>
        {organizers.map((organizer) => (
          <Table.Row
            key={organizer.id}
            style={{ cursor: 'pointer' }}
            onClick={() => onView(organizer.id)}
          >
            <Table.Cell>
              <Flex align="center" gap="3">
                <Avatar
                  size="2"
                  fallback={organizer.companyName?.charAt(0) || 'O'}
                />
                <Box>
                  <Text weight="medium" size="2">{organizer.companyName}</Text>
                  <Text color="gray" size="1">{organizer.province}, {organizer.city}</Text>
                </Box>
              </Flex>
            </Table.Cell>
            <Table.Cell>
              <Box>
                <Text size="2">{organizer.businessEmail}</Text>
                <Text color="gray" size="1">{organizer.businessPhone}</Text>
              </Box>
            </Table.Cell>
            <Table.Cell>
              <OrganizerStatusBadge status={organizer.status} />
            </Table.Cell>
            <Table.Cell>
              <Flex gap="1">
                <Tooltip content="Verified">
                  <Badge
                    color={organizer.verified ? 'green' : 'gray'}
                    variant="soft"
                    size="1"
                  >
                    Verified
                  </Badge>
                </Tooltip>
                <Tooltip content="Documents Verified">
                  <Badge
                    color={organizer.documentsVerified ? 'green' : 'gray'}
                    variant="soft"
                    size="1"
                  >
                    Docs
                  </Badge>
                </Tooltip>
              </Flex>
            </Table.Cell>
            <Table.Cell>
              <Text size="2" color="gray">
                {organizer.submittedAt
                  ? new Date(organizer.submittedAt).toLocaleDateString()
                  : '-'}
              </Text>
            </Table.Cell>
            <Table.Cell align="right" onClick={(e) => e.stopPropagation()}>
              <Flex gap="1" justify="end">
                <Tooltip content="View Details">
                  <IconButton
                    size="1"
                    variant="ghost"
                    onClick={() => onView(organizer.id)}
                  >
                    <Eye width={16} height={16} />
                  </IconButton>
                </Tooltip>
                <DropdownMenu.Root>
                  <DropdownMenu.Trigger>
                    <IconButton size="1" variant="ghost">
                      <MoreVert width={16} height={16} />
                    </IconButton>
                  </DropdownMenu.Trigger>
                  <DropdownMenu.Content align="end">
                    <DropdownMenu.Item onClick={() => onView(organizer.id)}>
                      <Eye width={14} height={14} />
                      View Details
                    </DropdownMenu.Item>
                    {organizer.status === 'PENDING_REVIEW' && (
                      <>
                        <DropdownMenu.Separator />
                        <DropdownMenu.Item
                          color="green"
                          onClick={() => onApprove(organizer.id)}
                        >
                          <Check width={14} height={14} />
                          Approve
                        </DropdownMenu.Item>
                        <DropdownMenu.Item onClick={() => onView(organizer.id)}>
                          <EditPencil width={14} height={14} />
                          Request Changes
                        </DropdownMenu.Item>
                        <DropdownMenu.Item
                          color="red"
                          onClick={() => onReject(organizer.id)}
                        >
                          <Xmark width={14} height={14} />
                          Reject
                        </DropdownMenu.Item>
                      </>
                    )}
                    {organizer.status === 'APPROVED' && (
                      <>
                        <DropdownMenu.Separator />
                        <DropdownMenu.Item
                          color="red"
                          onClick={() => onSuspend(organizer.id)}
                        >
                          <Pause width={14} height={14} />
                          Suspend
                        </DropdownMenu.Item>
                      </>
                    )}
                    {organizer.status === 'SUSPENDED' && (
                      <>
                        <DropdownMenu.Separator />
                        <DropdownMenu.Item
                          color="green"
                          onClick={() => onReactivate(organizer.id)}
                        >
                          <PlaySolid width={14} height={14} />
                          Reactivate
                        </DropdownMenu.Item>
                      </>
                    )}
                  </DropdownMenu.Content>
                </DropdownMenu.Root>
              </Flex>
            </Table.Cell>
          </Table.Row>
        ))}
      </Table.Body>
    </Table.Root>
  );
}

function PaginationControls({
  currentPage,
  totalPages,
  totalElements,
  onPageChange,
}: {
  currentPage: number;
  totalPages: number;
  totalElements: number;
  onPageChange: (page: number) => void;
}) {
  if (totalPages <= 1) return null;

  return (
    <Flex align="center" justify="between" mt="4">
      <Text size="2" color="gray">
        Showing page {currentPage + 1} of {totalPages} ({totalElements} total)
      </Text>
      <Flex gap="2">
        <Button
          variant="soft"
          size="1"
          disabled={currentPage === 0}
          onClick={() => onPageChange(currentPage - 1)}
        >
          Previous
        </Button>
        <Button
          variant="soft"
          size="1"
          disabled={currentPage >= totalPages - 1}
          onClick={() => onPageChange(currentPage + 1)}
        >
          Next
        </Button>
      </Flex>
    </Flex>
  );
}

export default function OrganizersPage() {
  const router = useRouter();
  const [activeTab, setActiveTab] = useState<TabValue>('pending');
  const [searchQuery, setSearchQuery] = useState('');
  const [currentPage, setCurrentPage] = useState(0);

  // Hooks for different organizer lists based on status
  const allOrganizers = useOrganizerApplications(undefined, { page: currentPage });
  const pendingOrganizers = usePendingApplications({ page: currentPage });
  const approvedOrganizers = useApprovedOrganizers({ page: currentPage });
  const suspendedOrganizers = useSuspendedOrganizers({ page: currentPage });

  // Search hook
  const { search, results: searchResults, loading: searchLoading } = useSearchOrganizers();

  // Mutation hooks
  const { approveOrganizer, loading: approving } = useApproveOrganizer();
  const { rejectOrganizer, loading: rejecting } = useRejectOrganizer();
  const { suspendOrganizer, loading: suspending } = useSuspendOrganizer();
  const { reactivateOrganizer, loading: reactivating } = useReactivateOrganizer();

  // Get data based on active tab
  const getActiveData = useCallback(() => {
    if (searchQuery) {
      return {
        organizers: searchResults,
        loading: searchLoading,
        totalPages: 1,
        totalElements: searchResults.length,
      };
    }

    switch (activeTab) {
      case 'pending':
        return {
          organizers: pendingOrganizers.applications,
          loading: pendingOrganizers.loading,
          totalPages: pendingOrganizers.totalPages,
          totalElements: pendingOrganizers.totalElements,
        };
      case 'approved':
        return {
          organizers: approvedOrganizers.organizers,
          loading: approvedOrganizers.loading,
          totalPages: approvedOrganizers.totalPages,
          totalElements: approvedOrganizers.totalElements,
        };
      case 'suspended':
        return {
          organizers: suspendedOrganizers.organizers,
          loading: suspendedOrganizers.loading,
          totalPages: suspendedOrganizers.totalPages,
          totalElements: suspendedOrganizers.totalElements,
        };
      case 'all':
      default:
        return {
          organizers: allOrganizers.applications,
          loading: allOrganizers.loading,
          totalPages: allOrganizers.totalPages,
          totalElements: allOrganizers.totalElements,
        };
    }
  }, [
    activeTab,
    searchQuery,
    searchResults,
    searchLoading,
    allOrganizers,
    pendingOrganizers,
    approvedOrganizers,
    suspendedOrganizers,
  ]);

  const activeData = getActiveData();

  // Handlers
  const handleSearch = useCallback(
    (e: React.FormEvent) => {
      e.preventDefault();
      if (searchQuery.trim()) {
        search(searchQuery.trim());
      }
    },
    [searchQuery, search]
  );

  const handleClearSearch = useCallback(() => {
    setSearchQuery('');
  }, []);

  const handleView = useCallback((id: string) => {
    router.push(`/organizers/${id}`);
  }, [router]);

  const handleApprove = useCallback(
    async (id: string) => {
      try {
        await approveOrganizer(id);
      } catch (error) {
        console.error('Failed to approve organizer:', error);
      }
    },
    [approveOrganizer]
  );

  const handleReject = useCallback(
    async (id: string) => {
      // TODO: Show rejection reason dialog
      try {
        await rejectOrganizer(id, 'Application does not meet requirements');
      } catch (error) {
        console.error('Failed to reject organizer:', error);
      }
    },
    [rejectOrganizer]
  );

  const handleSuspend = useCallback(
    async (id: string) => {
      // TODO: Show suspension reason dialog
      try {
        await suspendOrganizer(id, 'Account suspended for review');
      } catch (error) {
        console.error('Failed to suspend organizer:', error);
      }
    },
    [suspendOrganizer]
  );

  const handleReactivate = useCallback(
    async (id: string) => {
      try {
        await reactivateOrganizer(id);
      } catch (error) {
        console.error('Failed to reactivate organizer:', error);
      }
    },
    [reactivateOrganizer]
  );

  const handleRefresh = useCallback(() => {
    switch (activeTab) {
      case 'pending':
        pendingOrganizers.refetch();
        break;
      case 'approved':
        approvedOrganizers.refetch();
        break;
      case 'suspended':
        suspendedOrganizers.refetch();
        break;
      default:
        allOrganizers.refetch();
    }
  }, [activeTab, allOrganizers, pendingOrganizers, approvedOrganizers, suspendedOrganizers]);

  const handleTabChange = useCallback((value: string) => {
    setActiveTab(value as TabValue);
    setCurrentPage(0);
    setSearchQuery('');
  }, []);

  const isActionLoading = approving || rejecting || suspending || reactivating;

  return (
    <Box>
      {/* Page Header */}
      <Flex justify="between" align="center" mb="5">
        <Box>
          <Heading size="6" weight="bold">
            Organizer Management
          </Heading>
          <Text color="gray" size="2">
            Review and manage organizer applications
          </Text>
        </Box>
        <Flex gap="2">
          <Tooltip content="Refresh">
            <IconButton variant="soft" onClick={handleRefresh}>
              <RefreshDouble width={18} height={18} />
            </IconButton>
          </Tooltip>
        </Flex>
      </Flex>

      {/* Search Bar */}
      <StyledCard style={{ marginBottom: 'var(--space-4)' }}>
        <form onSubmit={handleSearch}>
          <Flex gap="2" align="center">
            <TextField.Root
              size="2"
              placeholder="Search by company name..."
              value={searchQuery}
              onChange={(e) => setSearchQuery(e.target.value)}
              style={{ flex: 1 }}
            >
              <TextField.Slot>
                <Search width={16} height={16} />
              </TextField.Slot>
            </TextField.Root>
            <Button type="submit" variant="soft" disabled={searchLoading}>
              Search
            </Button>
            {searchQuery && (
              <Button type="button" variant="ghost" onClick={handleClearSearch}>
                Clear
              </Button>
            )}
          </Flex>
        </form>
      </StyledCard>

      {/* Tabs */}
      <Tabs.Root value={activeTab} onValueChange={handleTabChange}>
        <Tabs.List>
          <Tabs.Trigger value="pending">
            Pending Review
            {pendingOrganizers.totalElements > 0 && (
              <Badge color="amber" size="1" ml="2">
                {pendingOrganizers.totalElements}
              </Badge>
            )}
          </Tabs.Trigger>
          <Tabs.Trigger value="approved">Approved</Tabs.Trigger>
          <Tabs.Trigger value="suspended">Suspended</Tabs.Trigger>
          <Tabs.Trigger value="all">All</Tabs.Trigger>
        </Tabs.List>

        <Box mt="4">
          <OrganizersTable
            organizers={activeData.organizers as OrganizerProfile[]}
            loading={activeData.loading || isActionLoading}
            onView={handleView}
            onApprove={handleApprove}
            onReject={handleReject}
            onSuspend={handleSuspend}
            onReactivate={handleReactivate}
          />

          <PaginationControls
            currentPage={currentPage}
            totalPages={activeData.totalPages}
            totalElements={activeData.totalElements}
            onPageChange={setCurrentPage}
          />
        </Box>
      </Tabs.Root>
    </Box>
  );
}
