'use client';

/**
 * Organization Settings Page
 *
 * Settings for the organization (OWNER/ADMIN only):
 * - Organization profile (synced with OrganizerProfile via GraphQL)
 * - Branding & logo
 * - Contact information
 * - Business address
 *
 * Uses GraphQL mutations to persist changes to the backend.
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import { Box, Flex, Text, TextField, TextArea, Button, Card, Avatar, Select, Callout } from '@radix-ui/themes';
import { Building, Upload, FloppyDisk, Camera, Check, WarningTriangle, Wifi } from 'iconoir-react';
import { PageHeader } from '@/components/ui';
import { useOrganization } from '@/lib/contexts/OrganizationContext';
import {
  useMyOrganizerProfile,
  useUpdateOrganizerProfile,
  canEditProfile,
} from '@pml.tickets/shared';
import { isNetworkError } from '@pml.tickets/shared';
import type { UpdateOrganizerProfileInput } from '@pml.tickets/shared/types/graphql';

// =============================================================================
// TYPES
// =============================================================================

interface OrganizationFormData {
  companyName: string;
  tagline: string;
  companyDescription: string;
  businessEmail: string;
  businessPhone: string;
  website: string;
  businessAddress: string;
  city: string;
  province: string;
  country: string;
  postalCode: string;
  businessType: string;
  businessRegistrationNumber: string;
  taxId: string;
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
// OPTIONS
// =============================================================================

const provinces = [
  { value: 'Lusaka', label: 'Lusaka Province' },
  { value: 'Copperbelt', label: 'Copperbelt Province' },
  { value: 'Central', label: 'Central Province' },
  { value: 'Southern', label: 'Southern Province' },
  { value: 'Eastern', label: 'Eastern Province' },
  { value: 'Northern', label: 'Northern Province' },
  { value: 'Luapula', label: 'Luapula Province' },
  { value: 'North-Western', label: 'North-Western Province' },
  { value: 'Western', label: 'Western Province' },
  { value: 'Muchinga', label: 'Muchinga Province' },
];

const businessTypes = [
  { value: 'SOLE_PROPRIETORSHIP', label: 'Sole Proprietorship' },
  { value: 'PARTNERSHIP', label: 'Partnership' },
  { value: 'LIMITED_COMPANY', label: 'Limited Company' },
  { value: 'NGO', label: 'Non-Governmental Organization' },
  { value: 'GOVERNMENT', label: 'Government Agency' },
  { value: 'INDIVIDUAL', label: 'Individual' },
];

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function OrganizationSettingsPage() {
  const { can } = useOrganization();
  const { profile, loading: profileLoading, error: profileError, refetch } = useMyOrganizerProfile();
  const { updateProfile, loading: updating, error: updateError } = useUpdateOrganizerProfile();

  const [saveMessage, setSaveMessage] = useState<{ type: 'success' | 'error'; text: string } | null>(null);
  const formInitialized = useRef(false);

  const [formData, setFormData] = useState<OrganizationFormData>({
    companyName: '',
    tagline: '',
    companyDescription: '',
    businessEmail: '',
    businessPhone: '',
    website: '',
    businessAddress: '',
    city: '',
    province: '',
    country: 'Zambia',
    postalCode: '',
    businessType: '',
    businessRegistrationNumber: '',
    taxId: '',
  });

  // Initialize form with profile data
  useEffect(() => {
    if (profile && !formInitialized.current) {
      setFormData({
        companyName: profile.companyName || '',
        tagline: profile.tagline || '',
        companyDescription: profile.companyDescription || '',
        businessEmail: profile.businessEmail || '',
        businessPhone: profile.businessPhone || '',
        website: profile.website || '',
        businessAddress: profile.businessAddress || '',
        city: profile.city || '',
        province: profile.province || '',
        country: profile.country || 'Zambia',
        postalCode: profile.postalCode || '',
        businessType: profile.businessType || '',
        businessRegistrationNumber: profile.businessRegistrationNumber || '',
        taxId: profile.taxId || '',
      });
      formInitialized.current = true;
    }
  }, [profile]);

  const handleChange = useCallback((field: keyof OrganizationFormData, value: string | number | null) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    // Clear save message when user starts editing
    if (saveMessage) setSaveMessage(null);
  }, [saveMessage]);

  const handleSave = useCallback(async () => {
    setSaveMessage(null);

    try {
      // Build the GraphQL input - only include changed fields
      const input: UpdateOrganizerProfileInput = {
        companyName: formData.companyName || null,
        tagline: formData.tagline || null,
        companyDescription: formData.companyDescription || null,
        businessEmail: formData.businessEmail || null,
        businessPhone: formData.businessPhone || null,
        website: formData.website || null,
        businessAddress: formData.businessAddress || null,
        city: formData.city || null,
        province: formData.province || null,
        country: formData.country || null,
        postalCode: formData.postalCode || null,
        businessType: formData.businessType || null,
        businessRegistrationNumber: formData.businessRegistrationNumber || null,
        taxId: formData.taxId || null,
        // Additional fields (not editable in this form yet)
        logoUrl: null,
        bannerUrl: null,
      };

      const result = await updateProfile(input);

      if (result?.success) {
        setSaveMessage({ type: 'success', text: result.message || 'Settings saved successfully!' });
        // Refetch to ensure we have latest data
        refetch();
      } else {
        setSaveMessage({ type: 'error', text: result?.message || 'Failed to save settings. Please try again.' });
      }
    } catch (error) {
      console.error('Failed to save:', error);
      setSaveMessage({ type: 'error', text: 'An error occurred while saving. Please try again.' });
    }
  }, [formData, updateProfile, refetch]);

  const canEdit = can('manageSettings') && canEditProfile(profile?.status || null);

  // Handle network error state
  if (profileError && isNetworkError(profileError)) {
    return (
      <Box>
        <PageHeader
          title="Organization Settings"
          description="Manage your organization profile and preferences"
          breadcrumbs={[
            { label: 'Settings', href: '/settings' },
            { label: 'Organization' },
          ]}
        />
        <Card
          style={{
            padding: '48px 24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '16px',
            textAlign: 'center',
          }}
        >
          <Flex direction="column" align="center" gap="4">
            <Box
              style={{
                width: 64,
                height: 64,
                borderRadius: '50%',
                background: 'rgba(239, 68, 68, 0.1)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
              }}
            >
              <Wifi style={{ width: 32, height: 32, color: 'var(--error-500)' }} />
            </Box>
            <Text size="4" weight="medium" style={{ color: 'var(--content-primary)' }}>
              Unable to connect to server
            </Text>
            <Text size="2" style={{ color: 'var(--content-muted)', maxWidth: 400 }}>
              We couldn&apos;t load your organization settings. Please check your connection and try again.
            </Text>
            <Button
              variant="outline"
              onClick={() => refetch()}
              style={{ marginTop: '8px' }}
            >
              Try Again
            </Button>
          </Flex>
        </Card>
      </Box>
    );
  }

  // Loading state
  if (profileLoading && !profile) {
    return (
      <Box>
        <PageHeader
          title="Organization Settings"
          description="Manage your organization profile and preferences"
          breadcrumbs={[
            { label: 'Settings', href: '/settings' },
            { label: 'Organization' },
          ]}
        />
        <Card
          style={{
            padding: '48px 24px',
            background: 'var(--surface-elevated)',
            border: '1px solid var(--surface-border)',
            borderRadius: '16px',
            textAlign: 'center',
          }}
        >
          <Text size="2" style={{ color: 'var(--content-muted)' }}>
            Loading organization settings...
          </Text>
        </Card>
      </Box>
    );
  }

  return (
    <Box>
      <PageHeader
        title="Organization Settings"
        description="Manage your organization profile and preferences"
        breadcrumbs={[
          { label: 'Settings', href: '/settings' },
          { label: 'Organization' },
        ]}
        actions={canEdit ? [
          {
            label: updating ? 'Saving...' : 'Save Changes',
            icon: <FloppyDisk style={{ width: 18, height: 18, marginRight: 8 }} />,
            onClick: handleSave,
            disabled: updating,
          },
        ] : undefined}
      />

      {/* Save Status Messages */}
      {(saveMessage || updateError) && (
        <Callout.Root
          mb="4"
          color={saveMessage?.type === 'success' ? 'green' : 'red'}
          variant="soft"
        >
          <Callout.Icon>
            {saveMessage?.type === 'success' ? (
              <Check style={{ width: 16, height: 16 }} />
            ) : (
              <WarningTriangle style={{ width: 16, height: 16 }} />
            )}
          </Callout.Icon>
          <Callout.Text>
            {saveMessage?.text || updateError?.message || 'An error occurred'}
          </Callout.Text>
        </Callout.Root>
      )}

      {/* Logo Section */}
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
          Organization Logo
        </Text>
        <Flex align="center" gap="5">
          <Box style={{ position: 'relative' }}>
            <Avatar
              size="7"
              fallback={formData.companyName?.charAt(0) || 'O'}
              radius="large"
              style={{
                background: 'linear-gradient(135deg, var(--brand-500) 0%, var(--brand-600) 100%)',
              }}
            />
            {canEdit && (
              <Box
                style={{
                  position: 'absolute',
                  bottom: 0,
                  right: 0,
                  width: 28,
                  height: 28,
                  borderRadius: '50%',
                  background: 'var(--surface-elevated)',
                  border: '2px solid var(--surface-border)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  cursor: 'pointer',
                }}
              >
                <Camera style={{ width: 14, height: 14, color: 'var(--content-secondary)' }} />
              </Box>
            )}
          </Box>
          <Box>
            <Text size="2" style={{ color: 'var(--content-primary)', display: 'block', marginBottom: '4px' }}>
              Upload a new logo
            </Text>
            <Text size="1" style={{ color: 'var(--content-muted)', display: 'block', marginBottom: '12px' }}>
              Recommended: 400x400px, PNG or JPG format
            </Text>
            {canEdit && (
              <Button
                variant="outline"
                size="1"
                style={{
                  borderColor: 'rgba(16, 185, 129, 0.3)',
                  color: 'var(--brand-500)',
                }}
              >
                <Upload style={{ width: 14, height: 14, marginRight: 6 }} />
                Upload Image
              </Button>
            )}
          </Box>
        </Flex>
      </Card>

      {/* Basic Information */}
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
          <Building style={{ width: 20, height: 20, color: 'var(--brand-500)' }} />
          <Text size="3" weight="medium" style={{ color: 'var(--content-primary)' }}>
            Basic Information
          </Text>
        </Flex>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: '16px',
          }}
        >
          <FormField label="Company Name" required>
            <TextField.Root
              size="3"
              value={formData.companyName}
              onChange={(e) => handleChange('companyName', e.target.value)}
              disabled={!canEdit}
              placeholder="Your company name"
            />
          </FormField>

          <FormField label="Tagline" helper="A short slogan or motto">
            <TextField.Root
              size="3"
              value={formData.tagline}
              onChange={(e) => handleChange('tagline', e.target.value)}
              disabled={!canEdit}
              placeholder="Creating memorable experiences"
            />
          </FormField>

          <Box style={{ gridColumn: '1 / -1' }}>
            <FormField label="Description">
              <TextArea
                size="3"
                rows={3}
                value={formData.companyDescription}
                onChange={(e) => handleChange('companyDescription', e.target.value)}
                disabled={!canEdit}
                placeholder="Brief description of your organization"
              />
            </FormField>
          </Box>

          <FormField label="Business Type">
            <Select.Root
              value={formData.businessType}
              onValueChange={(value) => handleChange('businessType', value)}
              disabled={!canEdit}
            >
              <Select.Trigger placeholder="Select business type" style={{ width: '100%' }} />
              <Select.Content>
                {businessTypes.map((type) => (
                  <Select.Item key={type.value} value={type.value}>
                    {type.label}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
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
          <FormField label="Business Email" required>
            <TextField.Root
              size="3"
              type="email"
              value={formData.businessEmail}
              onChange={(e) => handleChange('businessEmail', e.target.value)}
              disabled={!canEdit}
              placeholder="contact@organization.com"
            />
          </FormField>

          <FormField label="Business Phone">
            <TextField.Root
              size="3"
              type="tel"
              value={formData.businessPhone}
              onChange={(e) => handleChange('businessPhone', e.target.value)}
              disabled={!canEdit}
              placeholder="+260 97X XXX XXX"
            />
          </FormField>

          <FormField label="Website">
            <TextField.Root
              size="3"
              type="url"
              value={formData.website}
              onChange={(e) => handleChange('website', e.target.value)}
              disabled={!canEdit}
              placeholder="https://yourwebsite.com"
            />
          </FormField>
        </Box>
      </Card>

      {/* Business Registration */}
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
          Business Registration
        </Text>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: '16px',
          }}
        >
          <FormField label="Business Registration Number" helper="PACRA registration number">
            <TextField.Root
              size="3"
              value={formData.businessRegistrationNumber}
              onChange={(e) => handleChange('businessRegistrationNumber', e.target.value)}
              disabled={!canEdit}
              placeholder="123456789"
            />
          </FormField>

          <FormField label="Tax ID (TPIN)" helper="Taxpayer Identification Number">
            <TextField.Root
              size="3"
              value={formData.taxId}
              onChange={(e) => handleChange('taxId', e.target.value)}
              disabled={!canEdit}
              placeholder="1234567890"
            />
          </FormField>
        </Box>
      </Card>

      {/* Business Address */}
      <Card
        style={{
          padding: '24px',
          background: 'var(--surface-elevated)',
          border: '1px solid var(--surface-border)',
          borderRadius: '16px',
        }}
      >
        <Text size="3" weight="medium" mb="4" style={{ color: 'var(--content-primary)', display: 'block' }}>
          Business Address
        </Text>

        <Box
          style={{
            display: 'grid',
            gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
            gap: '16px',
          }}
        >
          <Box style={{ gridColumn: '1 / -1' }}>
            <FormField label="Street Address">
              <TextField.Root
                size="3"
                value={formData.businessAddress}
                onChange={(e) => handleChange('businessAddress', e.target.value)}
                disabled={!canEdit}
                placeholder="123 Main Street, Building A"
              />
            </FormField>
          </Box>

          <FormField label="City">
            <TextField.Root
              size="3"
              value={formData.city}
              onChange={(e) => handleChange('city', e.target.value)}
              disabled={!canEdit}
              placeholder="Lusaka"
            />
          </FormField>

          <FormField label="Province">
            <Select.Root
              value={formData.province}
              onValueChange={(value) => handleChange('province', value)}
              disabled={!canEdit}
            >
              <Select.Trigger placeholder="Select province" style={{ width: '100%' }} />
              <Select.Content>
                {provinces.map((prov) => (
                  <Select.Item key={prov.value} value={prov.value}>
                    {prov.label}
                  </Select.Item>
                ))}
              </Select.Content>
            </Select.Root>
          </FormField>

          <FormField label="Postal Code">
            <TextField.Root
              size="3"
              value={formData.postalCode}
              onChange={(e) => handleChange('postalCode', e.target.value)}
              disabled={!canEdit}
              placeholder="10101"
            />
          </FormField>

          <FormField label="Country">
            <TextField.Root
              size="3"
              value={formData.country}
              onChange={(e) => handleChange('country', e.target.value)}
              disabled={!canEdit}
              placeholder="Zambia"
            />
          </FormField>
        </Box>
      </Card>
    </Box>
  );
}
