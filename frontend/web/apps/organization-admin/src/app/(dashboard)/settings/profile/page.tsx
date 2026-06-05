'use client';

/**
 * Profile Settings Page
 *
 * User profile settings:
 * - Personal information
 * - Avatar upload
 * - Password change (via Keycloak)
 * - Two-factor authentication
 */

import { useState, useCallback } from 'react';
import { Box, Flex, Text, TextField, Button, Card, Avatar, Badge } from '@radix-ui/themes';
import { User, FloppyDisk, Camera, Key, Shield, Mail, Phone } from 'iconoir-react';
import { PageHeader } from '@/components/ui';
import { useSession } from '@/lib/auth/client';
import { useOrganization } from '@/lib/contexts/OrganizationContext';

// =============================================================================
// TYPES
// =============================================================================

interface ProfileFormData {
  firstName: string;
  lastName: string;
  email: string;
  phone: string;
  jobTitle: string;
}

// =============================================================================
// FORM FIELD COMPONENT
// =============================================================================

interface FormFieldProps {
  label: string;
  required?: boolean;
  helper?: string;
  children: React.ReactNode;
}

function FormField({ label, required, helper, children }: FormFieldProps) {
  return (
    <Box mb="4">
      <Text
        as="label"
        size="2"
        weight="medium"
        style={{ color: 'var(--content-secondary)', display: 'block', marginBottom: '8px' }}
      >
        {label}
        {required && <span style={{ color: 'var(--error-500)', marginLeft: 4 }}>*</span>}
      </Text>
      {children}
      {helper && (
        <Text size="1" style={{ color: 'var(--content-muted)', display: 'block', marginTop: '4px' }}>
          {helper}
        </Text>
      )}
    </Box>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function ProfileSettingsPage() {
  const { data: session } = useSession();
  const { role } = useOrganization();
  const [isSaving, setIsSaving] = useState(false);

  const [formData, setFormData] = useState<ProfileFormData>({
    firstName: session?.user?.name?.split(' ')[0] || '',
    lastName: session?.user?.name?.split(' ').slice(1).join(' ') || '',
    email: session?.user?.email || '',
    phone: '',
    jobTitle: '',
  });

  const handleChange = useCallback((field: keyof ProfileFormData, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
  }, []);

  const handleSave = useCallback(async () => {
    setIsSaving(true);
    try {
      // TODO: Save to GraphQL API
      await new Promise((resolve) => setTimeout(resolve, 1000));
      console.log('Saving profile:', formData);
    } catch (error) {
      console.error('Failed to save:', error);
    } finally {
      setIsSaving(false);
    }
  }, [formData]);

  const handleChangePassword = useCallback(() => {
    // Redirect to Keycloak account management
    const keycloakUrl = process.env.NEXT_PUBLIC_KEYCLOAK_URL || 'http://localhost:8084';
    const realm = process.env.NEXT_PUBLIC_KEYCLOAK_REALM || 'event-ticketing';
    window.open(`${keycloakUrl}/realms/${realm}/account/#/security/signingin`, '_blank');
  }, []);

  const getRoleBadgeColor = (role: string | null) => {
    switch (role) {
      case 'OWNER':
        return 'amber';
      case 'ADMIN':
        return 'blue';
      case 'MANAGER':
        return 'green';
      case 'MARKETER':
        return 'purple';
      default:
        return 'gray';
    }
  };

  return (
    <Box>
      <PageHeader
        title="My Profile"
        description="Manage your personal information and account settings"
        breadcrumbs={[
          { label: 'Settings', href: '/settings' },
          { label: 'Profile' },
        ]}
        actions={[
          {
            label: isSaving ? 'Saving...' : 'Save Changes',
            icon: <FloppyDisk style={{ width: 18, height: 18, marginRight: 8 }} />,
            onClick: handleSave,
            disabled: isSaving,
          },
        ]}
      />

      {/* Profile Header Card */}
      <Card
        mb="6"
        style={{
          padding: '32px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
        }}
      >
        <Flex align="center" gap="5" direction={{ initial: 'column', sm: 'row' }}>
          <Box style={{ position: 'relative' }}>
            <Avatar
              size="8"
              fallback={session?.user?.name?.charAt(0) || 'U'}
              radius="full"
              style={{
                background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
              }}
            />
            <Box
              style={{
                position: 'absolute',
                bottom: 4,
                right: 4,
                width: 32,
                height: 32,
                borderRadius: '50%',
                background: 'var(--surface-elevated)',
                border: '2px solid var(--surface-border)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                cursor: 'pointer',
              }}
            >
              <Camera style={{ width: 16, height: 16, color: 'var(--content-secondary)' }} />
            </Box>
          </Box>

          <Box style={{ flex: 1, textAlign: 'center' }} className="sm-text-left">
            <Flex align="center" gap="2" justify={{ initial: 'center', sm: 'start' }} mb="1">
              <Text size="5" weight="bold" style={{ color: 'var(--content-primary)' }}>
                {session?.user?.name || 'User'}
              </Text>
              {role && (
                <Badge color={getRoleBadgeColor(role)} variant="soft">
                  {role}
                </Badge>
              )}
            </Flex>
            <Text size="2" style={{ color: 'var(--content-muted)', display: 'block' }}>
              {session?.user?.email}
            </Text>
          </Box>
        </Flex>
      </Card>

      {/* Personal Information */}
      <Card
        mb="6"
        style={{
          padding: '24px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
        }}
      >
        <Flex align="center" gap="2" mb="4">
          <User style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />
          <Text size="3" weight="medium" style={{ color: 'var(--content-primary)' }}>
            Personal Information
          </Text>
        </Flex>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: '16px',
          }}
        >
          <FormField label="First Name" required>
            <TextField.Root
              size="3"
              value={formData.firstName}
              onChange={(e) => handleChange('firstName', e.target.value)}
              placeholder="First name"
            />
          </FormField>

          <FormField label="Last Name" required>
            <TextField.Root
              size="3"
              value={formData.lastName}
              onChange={(e) => handleChange('lastName', e.target.value)}
              placeholder="Last name"
            />
          </FormField>

          <FormField label="Job Title">
            <TextField.Root
              size="3"
              value={formData.jobTitle}
              onChange={(e) => handleChange('jobTitle', e.target.value)}
              placeholder="e.g., Event Manager"
            />
          </FormField>
        </Box>
      </Card>

      {/* Contact Information */}
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
          Contact Information
        </Text>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: '16px',
          }}
        >
          <FormField label="Email Address" helper="Managed through your SSO provider">
            <Flex align="center" gap="2">
              <Mail style={{ width: 18, height: 18, color: 'var(--content-muted)' }} />
              <TextField.Root
                size="3"
                type="email"
                value={formData.email}
                disabled
                style={{ flex: 1, opacity: 0.7 }}
              />
            </Flex>
          </FormField>

          <FormField label="Phone Number">
            <Flex align="center" gap="2">
              <Phone style={{ width: 18, height: 18, color: 'var(--content-muted)' }} />
              <TextField.Root
                size="3"
                type="tel"
                value={formData.phone}
                onChange={(e) => handleChange('phone', e.target.value)}
                placeholder="+260 97X XXX XXX"
                style={{ flex: 1 }}
              />
            </Flex>
          </FormField>
        </Box>
      </Card>

      {/* Security */}
      <Card
        style={{
          padding: '24px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
        }}
      >
        <Flex align="center" gap="2" mb="4">
          <Shield style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />
          <Text size="3" weight="medium" style={{ color: 'var(--content-primary)' }}>
            Security
          </Text>
        </Flex>

        <Flex direction="column" gap="4">
          {/* Password */}
          <Flex
            justify="between"
            align="center"
            p="4"
            style={{
              background: 'var(--surface-subtle)',
              borderRadius: '12px',
              border: '1px solid var(--surface-border)',
            }}
          >
            <Flex align="center" gap="3">
              <Box
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: '10px',
                  background: 'rgba(16, 185, 129, 0.1)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Key style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />
              </Box>
              <Box>
                <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                  Password
                </Text>
                <Text size="1" style={{ color: 'var(--content-muted)' }}>
                  Change your password via SSO provider
                </Text>
              </Box>
            </Flex>
            <Button
              variant="outline"
              size="2"
              onClick={handleChangePassword}
              style={{
                borderColor: 'rgba(16, 185, 129, 0.3)',
                color: 'var(--brand-500)',
              }}
            >
              Change Password
            </Button>
          </Flex>

          {/* Two-Factor Authentication */}
          <Flex
            justify="between"
            align="center"
            p="4"
            style={{
              background: 'var(--surface-subtle)',
              borderRadius: '12px',
              border: '1px solid var(--surface-border)',
            }}
          >
            <Flex align="center" gap="3">
              <Box
                style={{
                  width: 40,
                  height: 40,
                  borderRadius: '10px',
                  background: 'rgba(16, 185, 129, 0.1)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                }}
              >
                <Shield style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />
              </Box>
              <Box>
                <Text size="2" weight="medium" style={{ color: 'var(--content-primary)', display: 'block' }}>
                  Two-Factor Authentication
                </Text>
                <Text size="1" style={{ color: 'var(--content-muted)' }}>
                  Add an extra layer of security to your account
                </Text>
              </Box>
            </Flex>
            <Badge color="gray" variant="soft">
              Managed by SSO
            </Badge>
          </Flex>
        </Flex>
      </Card>

      <style jsx global>{`
        @media (min-width: 640px) {
          .sm-text-left {
            text-align: left !important;
          }
        }
      `}</style>
    </Box>
  );
}
