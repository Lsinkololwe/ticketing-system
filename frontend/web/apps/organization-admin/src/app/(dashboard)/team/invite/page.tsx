'use client';

/**
 * Invite Team Member Page
 *
 * Invite new members to the organization:
 * - Email invitation
 * - Role selection
 * - Bulk invite option
 */

import { useState, useCallback } from 'react';
import { useRouter } from 'next/navigation';
import {
  Box,
  Flex,
  Text,
  TextField,
  Button,
  Card,
  Select,
  TextArea,
} from '@radix-ui/themes';
import {
  Mail,
  Plus,
  Trash,
  Send,
  Shield,
  User,
  Check,
} from 'iconoir-react';
import { PageHeader } from '@/components/ui';
import type { OrganizationRole } from '@/config/navigation';

// =============================================================================
// TYPES
// =============================================================================

interface InviteEntry {
  id: string;
  email: string;
  role: OrganizationRole;
}

// =============================================================================
// ROLE CONFIG
// =============================================================================

const roleOptions: { value: OrganizationRole; label: string; description: string; icon: React.ReactNode }[] = [
  {
    value: 'ADMIN',
    label: 'Admin',
    description: 'Can manage team, events, and settings',
    icon: <Shield style={{ width: 16, height: 16 }} />,
  },
  {
    value: 'MANAGER',
    label: 'Manager',
    description: 'Can create and manage events, view financials',
    icon: <User style={{ width: 16, height: 16 }} />,
  },
  {
    value: 'MARKETER',
    label: 'Marketer',
    description: 'Can view analytics and check-in attendees',
    icon: <User style={{ width: 16, height: 16 }} />,
  },
  {
    value: 'CONTRIBUTOR',
    label: 'Contributor',
    description: 'Can only check-in attendees',
    icon: <User style={{ width: 16, height: 16 }} />,
  },
];

// =============================================================================
// INVITE ROW COMPONENT
// =============================================================================

interface InviteRowProps {
  invite: InviteEntry;
  onChange: (id: string, field: 'email' | 'role', value: string) => void;
  onRemove: (id: string) => void;
  canRemove: boolean;
  error?: string;
}

function InviteRow({ invite, onChange, onRemove, canRemove, error }: InviteRowProps) {
  return (
    <Flex gap="3" align="start">
      <Box style={{ flex: 1 }}>
        <TextField.Root
          size="3"
          type="email"
          value={invite.email}
          onChange={(e) => onChange(invite.id, 'email', e.target.value)}
          placeholder="email@example.com"
          style={{
            border: error ? '1px solid var(--error-500)' : undefined,
          }}
        >
          <TextField.Slot>
            <Mail style={{ width: 16, height: 16, color: 'var(--content-muted)' }} />
          </TextField.Slot>
        </TextField.Root>
        {error && (
          <Text size="1" style={{ color: 'var(--error-500)', display: 'block', marginTop: '4px' }}>
            {error}
          </Text>
        )}
      </Box>

      <Select.Root
        value={invite.role}
        onValueChange={(value) => onChange(invite.id, 'role', value)}
      >
        <Select.Trigger style={{ width: 160 }} />
        <Select.Content>
          {roleOptions.map((role) => (
            <Select.Item key={role.value} value={role.value}>
              <Flex align="center" gap="2">
                {role.icon}
                {role.label}
              </Flex>
            </Select.Item>
          ))}
        </Select.Content>
      </Select.Root>

      {canRemove && (
        <Button
          variant="ghost"
          size="3"
          onClick={() => onRemove(invite.id)}
          style={{ color: 'var(--content-muted)' }}
        >
          <Trash style={{ width: 18, height: 18 }} />
        </Button>
      )}
    </Flex>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function InviteMemberPage() {
  const router = useRouter();
  const [invites, setInvites] = useState<InviteEntry[]>([
    { id: '1', email: '', role: 'CONTRIBUTOR' },
  ]);
  const [errors, setErrors] = useState<Record<string, string>>({});
  const [isSending, setIsSending] = useState(false);
  const [message, setMessage] = useState('');
  const [showSuccess, setShowSuccess] = useState(false);

  const handleChange = useCallback((id: string, field: 'email' | 'role', value: string) => {
    setInvites((prev) =>
      prev.map((inv) => (inv.id === id ? { ...inv, [field]: value } : inv))
    );
    // Clear error when user types
    if (errors[id]) {
      setErrors((prev) => ({ ...prev, [id]: '' }));
    }
  }, [errors]);

  const handleAddRow = useCallback(() => {
    setInvites((prev) => [
      ...prev,
      { id: String(Date.now()), email: '', role: 'CONTRIBUTOR' },
    ]);
  }, []);

  const handleRemoveRow = useCallback((id: string) => {
    setInvites((prev) => prev.filter((inv) => inv.id !== id));
    setErrors((prev) => {
      const newErrors = { ...prev };
      delete newErrors[id];
      return newErrors;
    });
  }, []);

  const validateInvites = useCallback((): boolean => {
    const newErrors: Record<string, string> = {};
    let valid = true;

    invites.forEach((invite) => {
      if (!invite.email.trim()) {
        newErrors[invite.id] = 'Email is required';
        valid = false;
      } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(invite.email)) {
        newErrors[invite.id] = 'Please enter a valid email';
        valid = false;
      }
    });

    // Check for duplicates
    const emails = invites.map((i) => i.email.toLowerCase()).filter(Boolean);
    const duplicates = emails.filter((e, i) => emails.indexOf(e) !== i);
    invites.forEach((invite) => {
      if (duplicates.includes(invite.email.toLowerCase())) {
        newErrors[invite.id] = 'Duplicate email address';
        valid = false;
      }
    });

    setErrors(newErrors);
    return valid;
  }, [invites]);

  const handleSend = useCallback(async () => {
    if (!validateInvites()) return;

    setIsSending(true);
    try {
      // TODO: Send invites via GraphQL API
      await new Promise((resolve) => setTimeout(resolve, 1500));
      console.log('Sending invites:', invites, 'Message:', message);
      setShowSuccess(true);
      setTimeout(() => {
        router.push('/team');
      }, 2000);
    } catch (error) {
      console.error('Failed to send invites:', error);
    } finally {
      setIsSending(false);
    }
  }, [invites, message, validateInvites, router]);

  if (showSuccess) {
    return (
      <Box
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'center',
          minHeight: '60vh',
        }}
      >
        <Card
          style={{
            padding: '48px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '20px',
            textAlign: 'center',
            maxWidth: 400,
          }}
        >
          <Box
            style={{
              width: 64,
              height: 64,
              borderRadius: '50%',
              background: 'rgba(16, 185, 129, 0.1)',
              border: '2px solid rgba(16, 185, 129, 0.3)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              margin: '0 auto 20px',
            }}
          >
            <Check style={{ width: 32, height: 32, color: 'var(--brand-500)' }} />
          </Box>
          <Text size="5" weight="bold" style={{ color: 'var(--content-primary)', display: 'block', marginBottom: '8px' }}>
            Invitations Sent!
          </Text>
          <Text size="2" style={{ color: 'var(--content-muted)' }}>
            {invites.length} invitation{invites.length > 1 ? 's' : ''} sent successfully.
            Redirecting to team page...
          </Text>
        </Card>
      </Box>
    );
  }

  return (
    <Box>
      <PageHeader
        title="Invite Team Members"
        description="Send invitations to add new members to your organization"
        breadcrumbs={[
          { label: 'Team', href: '/team' },
          { label: 'Invite Member' },
        ]}
      />

      {/* Role Permissions Info */}
      <Card
        mb="6"
        style={{
          padding: '24px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
        }}
      >
        <Text size="3" weight="medium" mb="4" style={{ color: 'var(--content-primary)', display: 'block' }}>
          Role Permissions
        </Text>
        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(220px, 1fr))',
            gap: '16px',
          }}
        >
          {roleOptions.map((role) => (
            <Flex
              key={role.value}
              align="start"
              gap="3"
              p="3"
              style={{
                background: 'var(--surface-subtle)',
                borderRadius: '10px',
              }}
            >
              <Box
                style={{
                  width: 32,
                  height: 32,
                  borderRadius: '8px',
                  background: 'rgba(16, 185, 129, 0.1)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'var(--brand-500)',
                  flexShrink: 0,
                }}
              >
                {role.icon}
              </Box>
              <Box>
                <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                  {role.label}
                </Text>
                <Text size="1" style={{ color: 'var(--content-muted)' }}>
                  {role.description}
                </Text>
              </Box>
            </Flex>
          ))}
        </Box>
      </Card>

      {/* Invite Form */}
      <Card
        mb="6"
        style={{
          padding: '24px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
        }}
      >
        <Flex justify="between" align="center" mb="4">
          <Text size="3" weight="medium" style={{ color: 'var(--content-primary)' }}>
            Email Addresses
          </Text>
          <Button
            variant="ghost"
            size="2"
            onClick={handleAddRow}
            style={{ color: 'var(--brand-500)' }}
          >
            <Plus style={{ width: 16, height: 16, marginRight: 6 }} />
            Add Another
          </Button>
        </Flex>

        <Flex direction="column" gap="3" mb="5">
          {invites.map((invite) => (
            <InviteRow
              key={invite.id}
              invite={invite}
              onChange={handleChange}
              onRemove={handleRemoveRow}
              canRemove={invites.length > 1}
              error={errors[invite.id]}
            />
          ))}
        </Flex>

        {/* Optional Message */}
        <Box>
          <Text
            size="2"
            weight="medium"
            mb="2"
            style={{ color: 'var(--content-secondary)', display: 'block' }}
          >
            Personal Message (Optional)
          </Text>
          <TextArea
            size="3"
            rows={3}
            value={message}
            onChange={(e) => setMessage(e.target.value)}
            placeholder="Add a personal message to your invitation..."
          />
          <Text size="1" style={{ color: 'var(--content-muted)', display: 'block', marginTop: '4px' }}>
            This message will be included in the invitation email
          </Text>
        </Box>
      </Card>

      {/* Actions */}
      <Flex justify="between">
        <Button
          variant="outline"
          size="3"
          onClick={() => router.push('/team')}
          style={{ borderColor: 'var(--surface-border)', color: 'var(--content-secondary)' }}
        >
          Cancel
        </Button>
        <Button
          size="3"
          onClick={handleSend}
          disabled={isSending || invites.every((i) => !i.email.trim())}
          style={{
            background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
            cursor: isSending ? 'not-allowed' : 'pointer',
          }}
        >
          {isSending ? (
            <>
              <Box
                style={{
                  width: 16,
                  height: 16,
                  borderRadius: '50%',
                  border: '2px solid rgba(255,255,255,0.3)',
                  borderTopColor: 'white',
                  animation: 'spin 1s linear infinite',
                  marginRight: 8,
                }}
              />
              Sending...
            </>
          ) : (
            <>
              <Send style={{ width: 18, height: 18, marginRight: 8 }} />
              Send {invites.filter((i) => i.email.trim()).length || ''} Invitation{invites.filter((i) => i.email.trim()).length !== 1 ? 's' : ''}
            </>
          )}
        </Button>
      </Flex>

      <style jsx global>{`
        @keyframes spin {
          to { transform: rotate(360deg); }
        }
      `}</style>
    </Box>
  );
}
