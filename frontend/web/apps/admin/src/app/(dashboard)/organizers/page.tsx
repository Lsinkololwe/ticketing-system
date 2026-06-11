'use client';

/**
 * Organizations Management Page
 *
 * Admin page for managing organization applications and profiles.
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
  useOrganizationApplications,
  usePendingApplications,
  useApprovedOrganizations,
  useSuspendedOrganizations,
  useSearchOrganizations,
  // Mutation hooks
  useApproveOrganization,
  useRejectOrganization,
  useSuspendOrganization,
  useUnsuspendOrganization,
  // Types and helpers
  type Organization as OrgType,
  getStatusColor,
  getStatusLabel,
} from '@pml.tickets/shared/api/admin/modules/organization';

type Organization = OrgType;
type TabValue = 'all' | 'pending' | 'approved' | 'suspended' | 'rejected' | 'changes_requested';

// Legacy status mappings - now using helpers from API
const STATUS_COLORS: Record<string, 'gray' | 'amber' | 'green' | 'red' | 'blue' | 'orange'> = {
  DRAFT: 'amber',
  PENDING_REVIEW: 'blue',
  APPROVED: 'green',
  ACTIVE: 'green',
  REJECTED: 'red',
  SUSPENDED: 'gray',
  CHANGES_REQUESTED: 'orange',
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

function OrganizationStatusBadge({ status }: { status: string }) {
  return (
    <Badge color={STATUS_COLORS[status] || 'gray'} variant="soft" size="1">
      {STATUS_LABELS[status] || status}
    </Badge>
  );
}

function OrganizationsTable({
  organizations,
  loading,
  onView,
  onApprove,
  onReject,
  onSuspend,
  onReactivate,
}: {
  organizations: Organization[];
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

  if (organizations.length === 0) {
    return <EmptyCard message="No organizations found" />;
  }

  return (
    <Table.Root variant="surface">
      <Table.Header>
        <Table.Row>
          <Table.ColumnHeaderCell>Organization</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell>Contact</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell>Status</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell>Verification</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell>Submitted</Table.ColumnHeaderCell>
          <Table.ColumnHeaderCell align="right">Actions</Table.ColumnHeaderCell>
        </Table.Row>
      </Table.Header>
      <Table.Body>
        {organizations.map((organization) => (
          <Table.Row
            key={organization.id}
            style={{ cursor: 'pointer' }}
            onClick={() => onView(organization.id)}
          >
            <Table.Cell>
              <Flex align="center" gap="3">
                <Avatar
                  size="2"
                  fallback={organization.name?.charAt(0) || 'O'}
                />
                <Box>
                  <Text weight="medium" size="2">{organization.name || 'Unnamed'}</Text>
                  <Text color="gray" size="1">
                    {organization.businessAddress?.city}{organization.businessAddress?.province ? `, ${organization.businessAddress.province}` : ''}
                  </Text>
                </Box>
              </Flex>
            </Table.Cell>
            <Table.Cell>
              <Box>
                <Text size="2">{organization.businessEmail || '-'}</Text>
                <Text color="gray" size="1">{organization.businessPhone || '-'}</Text>
              </Box>
            </Table.Cell>
            <Table.Cell>
              <OrganizationStatusBadge status={organization.status} />
            </Table.Cell>
            <Table.Cell>
              <Flex gap="1">
                <Tooltip content="Verified">
                  <Badge
                    color={organization.verified ? 'green' : 'gray'}
                    variant="soft"
                    size="1"
                  >
                    Verified
                  </Badge>
                </Tooltip>
                <Tooltip content="Documents Verified">
                  <Badge
                    color={organization.documentsVerified ? 'green' : 'gray'}
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
                {organization.submittedAt
                  ? new Date(organization.submittedAt).toLocaleDateString()
                  : '-'}
              </Text>
            </Table.Cell>
            <Table.Cell align="right" onClick={(e) => e.stopPropagation()}>
              <Flex gap="1" justify="end">
                <Tooltip content="View Details">
                  <IconButton
                    size="1"
                    variant="ghost"
                    onClick={() => onView(organization.id)}
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
                    <DropdownMenu.Item onClick={() => onView(organization.id)}>
                      <Eye width={14} height={14} />
                      View Details
                    </DropdownMenu.Item>
                    {organization.status === 'PENDING_REVIEW' && (
                      <>
                        <DropdownMenu.Separator />
                        <DropdownMenu.Item
                          color="green"
                          onClick={() => onApprove(organization.id)}
                        >
                          <Check width={14} height={14} />
                          Approve
                        </DropdownMenu.Item>
                        <DropdownMenu.Item onClick={() => onView(organization.id)}>
                          <EditPencil width={14} height={14} />
                          Request Changes
                        </DropdownMenu.Item>
                        <DropdownMenu.Item
                          color="red"
                          onClick={() => onReject(organization.id)}
                        >
                          <Xmark width={14} height={14} />
                          Reject
                        </DropdownMenu.Item>
                      </>
                    )}
                    {(organization.status === 'APPROVED' || organization.status === 'ACTIVE') && (
                      <>
                        <DropdownMenu.Separator />
                        <DropdownMenu.Item
                          color="red"
                          onClick={() => onSuspend(organization.id)}
                        >
                          <Pause width={14} height={14} />
                          Suspend
                        </DropdownMenu.Item>
                      </>
                    )}
                    {organization.status === 'SUSPENDED' && (
                      <>
                        <DropdownMenu.Separator />
                        <DropdownMenu.Item
                          color="green"
                          onClick={() => onReactivate(organization.id)}
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

  // Hooks for different organization lists based on status
  const allOrganizations = useOrganizationApplications(undefined, { page: currentPage });
  const pendingOrganizations = usePendingApplications({ page: currentPage });
  const approvedOrganizations = useApprovedOrganizations({ page: currentPage });
  const suspendedOrganizations = useSuspendedOrganizations({ page: currentPage });

  // Search hook
  const { search, results: searchResults, loading: searchLoading } = useSearchOrganizations();

  // Mutation hooks
  const { approve, loading: approving } = useApproveOrganization();
  const { reject, loading: rejecting } = useRejectOrganization();
  const { suspend, loading: suspending } = useSuspendOrganization();
  const { unsuspend, loading: reactivating } = useUnsuspendOrganization();

  // Get data based on active tab
  const getActiveData = useCallback(() => {
    if (searchQuery && searchResults.length > 0) {
      return {
        organizations: searchResults,
        loading: searchLoading,
        totalPages: 1,
        totalElements: searchResults.length,
      };
    }

    switch (activeTab) {
      case 'pending':
        return {
          organizations: pendingOrganizations.applications,
          loading: pendingOrganizations.loading,
          totalPages: pendingOrganizations.totalPages,
          totalElements: pendingOrganizations.totalElements,
        };
      case 'approved':
        return {
          organizations: approvedOrganizations.organizations,
          loading: approvedOrganizations.loading,
          totalPages: approvedOrganizations.totalPages,
          totalElements: approvedOrganizations.totalElements,
        };
      case 'suspended':
        return {
          organizations: suspendedOrganizations.organizations,
          loading: suspendedOrganizations.loading,
          totalPages: suspendedOrganizations.totalPages,
          totalElements: suspendedOrganizations.totalElements,
        };
      case 'all':
      default:
        return {
          organizations: allOrganizations.applications,
          loading: allOrganizations.loading,
          totalPages: allOrganizations.totalPages,
          totalElements: allOrganizations.totalElements,
        };
    }
  }, [
    activeTab,
    searchQuery,
    searchResults,
    searchLoading,
    allOrganizations,
    pendingOrganizations,
    approvedOrganizations,
    suspendedOrganizations,
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
        await approve(id);
      } catch (error) {
        console.error('Failed to approve organization:', error);
      }
    },
    [approve]
  );

  const handleReject = useCallback(
    async (id: string) => {
      // TODO: Show rejection reason dialog
      try {
        await reject(id, 'Application does not meet requirements');
      } catch (error) {
        console.error('Failed to reject organization:', error);
      }
    },
    [reject]
  );

  const handleSuspend = useCallback(
    async (id: string) => {
      // TODO: Show suspension reason dialog
      try {
        await suspend(id, 'Account suspended for review');
      } catch (error) {
        console.error('Failed to suspend organization:', error);
      }
    },
    [suspend]
  );

  const handleReactivate = useCallback(
    async (id: string) => {
      try {
        await unsuspend(id);
      } catch (error) {
        console.error('Failed to reactivate organization:', error);
      }
    },
    [unsuspend]
  );

  const handleRefresh = useCallback(() => {
    switch (activeTab) {
      case 'pending':
        pendingOrganizations.refetch();
        break;
      case 'approved':
        approvedOrganizations.refetch();
        break;
      case 'suspended':
        suspendedOrganizations.refetch();
        break;
      default:
        allOrganizations.refetch();
    }
  }, [activeTab, allOrganizations, pendingOrganizations, approvedOrganizations, suspendedOrganizations]);

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
            Organization Management
          </Heading>
          <Text color="gray" size="2">
            Review and manage organization applications
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
              placeholder="Search by organization name..."
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
            {pendingOrganizations.totalElements > 0 && (
              <Badge color="amber" size="1" ml="2">
                {pendingOrganizations.totalElements}
              </Badge>
            )}
          </Tabs.Trigger>
          <Tabs.Trigger value="approved">Approved</Tabs.Trigger>
          <Tabs.Trigger value="suspended">Suspended</Tabs.Trigger>
          <Tabs.Trigger value="all">All</Tabs.Trigger>
        </Tabs.List>

        <Box mt="4">
          <OrganizationsTable
            organizations={activeData.organizations as Organization[]}
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
