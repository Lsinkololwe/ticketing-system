'use client';

/**
 * Team Members Page
 *
 * Displays organization team members with:
 * - Member list with roles
 * - Role management
 * - Remove/invite actions
 * - Search and filter
 */

import { useState, useMemo, useCallback } from 'react';
import {
  Box,
  Flex,
  Text,
  TextField,
  Button,
  Card,
  Badge,
  Avatar,
  DropdownMenu,
  Dialog,
} from '@radix-ui/themes';
import {
  Plus,
  Search,
  MoreHoriz,
  Edit,
  Trash,
  Mail,
  Shield,
  Crown,
  User,
} from 'iconoir-react';
import { PageHeader, NoTeamMembersEmptyState } from '@/components/ui';
import { useOrganization, OrganizationRole } from '@/lib/contexts/OrganizationContext';

// =============================================================================
// TYPES
// =============================================================================

interface TeamMember {
  id: string;
  name: string;
  email: string;
  avatarUrl?: string;
  role: OrganizationRole;
  joinedAt: string;
  lastActive?: string;
  status: 'active' | 'pending' | 'inactive';
}

// =============================================================================
// MOCK DATA
// =============================================================================

const mockMembers: TeamMember[] = [
  {
    id: '1',
    name: 'John Mwanza',
    email: 'john@organization.com',
    role: 'OWNER',
    joinedAt: '2024-01-15',
    lastActive: '2025-05-19',
    status: 'active',
  },
  {
    id: '2',
    name: 'Mary Banda',
    email: 'mary@organization.com',
    role: 'ADMIN',
    joinedAt: '2024-03-20',
    lastActive: '2025-05-18',
    status: 'active',
  },
  {
    id: '3',
    name: 'Peter Tembo',
    email: 'peter@organization.com',
    role: 'MANAGER',
    joinedAt: '2024-06-10',
    lastActive: '2025-05-17',
    status: 'active',
  },
  {
    id: '4',
    name: 'Grace Phiri',
    email: 'grace@organization.com',
    role: 'MARKETER',
    joinedAt: '2024-09-05',
    lastActive: '2025-05-15',
    status: 'active',
  },
  {
    id: '5',
    name: 'David Lungu',
    email: 'david@organization.com',
    role: 'CONTRIBUTOR',
    joinedAt: '2025-01-20',
    status: 'pending',
  },
];

// =============================================================================
// ROLE CONFIG
// =============================================================================

const roleConfig: Record<OrganizationRole, { label: string; color: string; icon: React.ReactNode; description: string }> = {
  OWNER: {
    label: 'Owner',
    color: 'amber',
    icon: <Crown style={{ width: 14, height: 14 }} />,
    description: 'Full access to all features',
  },
  ADMIN: {
    label: 'Admin',
    color: 'blue',
    icon: <Shield style={{ width: 14, height: 14 }} />,
    description: 'Can manage team and settings',
  },
  MANAGER: {
    label: 'Manager',
    color: 'green',
    icon: <User style={{ width: 14, height: 14 }} />,
    description: 'Can create and manage events',
  },
  MARKETER: {
    label: 'Marketer',
    color: 'purple',
    icon: <User style={{ width: 14, height: 14 }} />,
    description: 'Can view analytics',
  },
  CONTRIBUTOR: {
    label: 'Contributor',
    color: 'gray',
    icon: <User style={{ width: 14, height: 14 }} />,
    description: 'Can check-in attendees',
  },
};

// =============================================================================
// MEMBER CARD COMPONENT
// =============================================================================

interface MemberCardProps {
  member: TeamMember;
  canManage: boolean;
  isCurrentUser: boolean;
  onRoleChange: (memberId: string, role: OrganizationRole) => void;
  onRemove: (memberId: string) => void;
}

function MemberCard({ member, canManage, isCurrentUser, onRoleChange, onRemove }: MemberCardProps) {
  const role = roleConfig[member.role];

  return (
    <Card
      style={{
        padding: '20px',
        background: 'var(--surface-elevated)',
        border: '1px solid var(--surface-border)',
        borderRadius: '12px',
      }}
    >
      <Flex justify="between" align="start">
        <Flex gap="3">
          <Avatar
            size="4"
            fallback={member.name.charAt(0)}
            radius="full"
            src={member.avatarUrl}
            style={{
              background: member.avatarUrl ? undefined : 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
            }}
          />
          <Box>
            <Flex align="center" gap="2" mb="1">
              <Text size="2" weight="medium" style={{ color: 'var(--content-primary)' }}>
                {member.name}
              </Text>
              {isCurrentUser && (
                <Badge size="1" variant="soft" color="gray">You</Badge>
              )}
              {member.status === 'pending' && (
                <Badge size="1" variant="soft" color="orange">Pending</Badge>
              )}
            </Flex>
            <Text size="1" style={{ color: 'var(--content-muted)', display: 'block', marginBottom: '8px' }}>
              {member.email}
            </Text>
            <Flex align="center" gap="2">
              <Badge color={role.color as any} variant="soft">
                <Flex align="center" gap="1">
                  {role.icon}
                  {role.label}
                </Flex>
              </Badge>
              <Text size="1" style={{ color: 'var(--content-muted)' }}>
                Joined {new Date(member.joinedAt).toLocaleDateString('en-US', { month: 'short', year: 'numeric' })}
              </Text>
            </Flex>
          </Box>
        </Flex>

        {canManage && !isCurrentUser && member.role !== 'OWNER' && (
          <DropdownMenu.Root>
            <DropdownMenu.Trigger>
              <Button variant="ghost" size="1" style={{ color: 'var(--content-muted)' }}>
                <MoreHoriz style={{ width: 18, height: 18 }} />
              </Button>
            </DropdownMenu.Trigger>
            <DropdownMenu.Content>
              <DropdownMenu.Sub>
                <DropdownMenu.SubTrigger>
                  <Edit style={{ width: 16, height: 16, marginRight: 8 }} />
                  Change Role
                </DropdownMenu.SubTrigger>
                <DropdownMenu.SubContent>
                  {(Object.keys(roleConfig) as OrganizationRole[])
                    .filter((r) => r !== 'OWNER')
                    .map((r) => (
                      <DropdownMenu.Item
                        key={r}
                        onClick={() => onRoleChange(member.id, r)}
                        disabled={r === member.role}
                      >
                        {roleConfig[r].label}
                      </DropdownMenu.Item>
                    ))}
                </DropdownMenu.SubContent>
              </DropdownMenu.Sub>
              <DropdownMenu.Item>
                <Mail style={{ width: 16, height: 16, marginRight: 8 }} />
                Resend Invite
              </DropdownMenu.Item>
              <DropdownMenu.Separator />
              <DropdownMenu.Item color="red" onClick={() => onRemove(member.id)}>
                <Trash style={{ width: 16, height: 16, marginRight: 8 }} />
                Remove Member
              </DropdownMenu.Item>
            </DropdownMenu.Content>
          </DropdownMenu.Root>
        )}
      </Flex>
    </Card>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function TeamPage() {
  const { can } = useOrganization();
  const [searchQuery, setSearchQuery] = useState('');
  const [members, setMembers] = useState<TeamMember[]>(mockMembers);
  const [removeDialogOpen, setRemoveDialogOpen] = useState(false);
  const [memberToRemove, setMemberToRemove] = useState<TeamMember | null>(null);

  const canManageTeam = can('manageTeam');

  // Filter members
  const filteredMembers = useMemo(() => {
    if (!searchQuery) return members;
    const query = searchQuery.toLowerCase();
    return members.filter(
      (m) =>
        m.name.toLowerCase().includes(query) ||
        m.email.toLowerCase().includes(query) ||
        roleConfig[m.role].label.toLowerCase().includes(query)
    );
  }, [members, searchQuery]);

  // Group by role
  const membersByRole = useMemo(() => {
    const groups: Record<string, TeamMember[]> = {
      'Owners & Admins': [],
      'Managers': [],
      'Other Members': [],
    };

    filteredMembers.forEach((member) => {
      if (member.role === 'OWNER' || member.role === 'ADMIN') {
        groups['Owners & Admins'].push(member);
      } else if (member.role === 'MANAGER') {
        groups['Managers'].push(member);
      } else {
        groups['Other Members'].push(member);
      }
    });

    return groups;
  }, [filteredMembers]);

  const handleRoleChange = useCallback((memberId: string, newRole: OrganizationRole) => {
    setMembers((prev) =>
      prev.map((m) => (m.id === memberId ? { ...m, role: newRole } : m))
    );
  }, []);

  const handleRemoveClick = useCallback((memberId: string) => {
    const member = members.find((m) => m.id === memberId);
    if (member) {
      setMemberToRemove(member);
      setRemoveDialogOpen(true);
    }
  }, [members]);

  const handleRemoveConfirm = useCallback(() => {
    if (memberToRemove) {
      setMembers((prev) => prev.filter((m) => m.id !== memberToRemove.id));
      setRemoveDialogOpen(false);
      setMemberToRemove(null);
    }
  }, [memberToRemove]);

  return (
    <Box>
      <PageHeader
        title="Team Members"
        description={`${members.length} members in your organization`}
        actions={canManageTeam ? [
          {
            label: 'Invite Member',
            icon: <Plus style={{ width: 18, height: 18, marginRight: 8 }} />,
            href: '/team/invite',
          },
        ] : undefined}
      />

      {/* Search */}
      <Box mb="6">
        <TextField.Root
          size="3"
          placeholder="Search by name, email, or role..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
          style={{ maxWidth: 400 }}
        >
          <TextField.Slot>
            <Search style={{ width: 18, height: 18, color: 'var(--content-muted)' }} />
          </TextField.Slot>
        </TextField.Root>
      </Box>

      {/* Role Permission Info */}
      <Card
        mb="6"
        style={{
          padding: '16px 20px',
          background: 'rgba(59, 130, 246, 0.1)',
          border: '1px solid rgba(59, 130, 246, 0.2)',
          borderRadius: '12px',
        }}
      >
        <Flex align="center" gap="3" wrap="wrap">
          <Shield style={{ width: 20, height: 20, color: '#3B82F6' }} />
          <Text size="2" style={{ color: 'var(--content-secondary)' }}>
            <strong>Role Permissions:</strong>{' '}
            <Text style={{ color: 'var(--content-muted)' }}>
              Owner/Admin can manage team • Manager can create events • Marketer can view analytics • Contributor can check-in
            </Text>
          </Text>
        </Flex>
      </Card>

      {/* Members List */}
      {filteredMembers.length === 0 ? (
        <Card
          style={{
            padding: '60px 24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '16px',
          }}
        >
          {searchQuery ? (
            <NoTeamMembersEmptyState
              action={{ label: 'Clear Search', onClick: () => setSearchQuery('') }}
            />
          ) : (
            <NoTeamMembersEmptyState />
          )}
        </Card>
      ) : (
        <Flex direction="column" gap="6">
          {Object.entries(membersByRole).map(([groupName, groupMembers]) =>
            groupMembers.length > 0 ? (
              <Box key={groupName}>
                <Text
                  size="2"
                  weight="medium"
                  mb="3"
                  style={{ color: 'var(--content-muted)', display: 'block' }}
                >
                  {groupName} ({groupMembers.length})
                </Text>
                <Flex direction="column" gap="3">
                  {groupMembers.map((member) => (
                    <MemberCard
                      key={member.id}
                      member={member}
                      canManage={canManageTeam}
                      isCurrentUser={member.id === '1'} // In real app, check against current user ID
                      onRoleChange={handleRoleChange}
                      onRemove={handleRemoveClick}
                    />
                  ))}
                </Flex>
              </Box>
            ) : null
          )}
        </Flex>
      )}

      {/* Remove Member Dialog */}
      <Dialog.Root open={removeDialogOpen} onOpenChange={setRemoveDialogOpen}>
        <Dialog.Content style={{ maxWidth: 450 }}>
          <Dialog.Title>Remove Team Member</Dialog.Title>
          <Dialog.Description size="2" mb="4">
            Are you sure you want to remove <strong>{memberToRemove?.name}</strong> from your organization?
            They will lose access to all events and data.
          </Dialog.Description>
          <Flex gap="3" justify="end">
            <Dialog.Close>
              <Button variant="outline" style={{ borderColor: 'var(--surface-border)' }}>
                Cancel
              </Button>
            </Dialog.Close>
            <Button
              color="red"
              onClick={handleRemoveConfirm}
              style={{ background: 'linear-gradient(135deg, #EF4444 0%, #DC2626 100%)' }}
            >
              Remove Member
            </Button>
          </Flex>
        </Dialog.Content>
      </Dialog.Root>
    </Box>
  );
}
