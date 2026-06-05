'use client';

/**
 * Business Info Page - Step 1 of KYB Application
 *
 * Collects business information:
 * - Company name and type
 * - Registration details
 * - Contact information
 * - Business address
 *
 * OWASP Compliance:
 * - Uses authenticated GraphQL mutations
 * - Validates all input before submission
 * - No sensitive data stored in client state beyond what's needed
 * - Proper loading states to prevent race conditions
 */

import { useState, useCallback, useEffect, useRef } from 'react';
import { useRouter } from 'next/navigation';
import { Box, Flex, Text, Heading, Button, TextField, Select, TextArea, Card, Spinner } from '@radix-ui/themes';
import { Building, ArrowRight, ArrowLeft, InfoCircle, WarningTriangle, Wifi } from 'iconoir-react';
import { StepIndicator, Step } from '@/components/application/StepIndicator';
import {
  useMyOrganizerProfile,
  useUpdateOrganizerProfile,
  useCreateOrganizerProfile,
  getRouteForStatus,
  canEditProfile,
} from '@pml.tickets/shared/api/graphql/organizer';
import { isNetworkError, isServerUnavailable } from '@pml.tickets/shared';

// =============================================================================
// TYPES
// =============================================================================

interface FormData {
  companyName: string;
  businessType: string;
  registrationNumber: string;
  taxId: string;
  email: string;
  phone: string;
  website: string;
  addressLine1: string;
  addressLine2: string;
  city: string;
  province: string;
  postalCode: string;
  country: string;
  description: string;
}

interface FormErrors {
  [key: string]: string;
}

// =============================================================================
// CONSTANTS
// =============================================================================

const steps: Step[] = [
  { id: 'business-info', title: 'Business Info' },
  { id: 'review', title: 'Review' },
];

const businessTypes = [
  { value: 'SOLE_PROPRIETORSHIP', label: 'Sole Proprietorship' },
  { value: 'PARTNERSHIP', label: 'Partnership' },
  { value: 'LIMITED_COMPANY', label: 'Limited Company' },
  { value: 'NGO', label: 'Non-Profit / NGO' },
  { value: 'GOVERNMENT', label: 'Government Entity' },
  { value: 'OTHER', label: 'Other' },
];

const provinces = [
  { value: 'CENTRAL', label: 'Central Province' },
  { value: 'COPPERBELT', label: 'Copperbelt Province' },
  { value: 'EASTERN', label: 'Eastern Province' },
  { value: 'LUAPULA', label: 'Luapula Province' },
  { value: 'LUSAKA', label: 'Lusaka Province' },
  { value: 'MUCHINGA', label: 'Muchinga Province' },
  { value: 'NORTHERN', label: 'Northern Province' },
  { value: 'NORTH_WESTERN', label: 'North-Western Province' },
  { value: 'SOUTHERN', label: 'Southern Province' },
  { value: 'WESTERN', label: 'Western Province' },
];

// =============================================================================
// FORM FIELD COMPONENTS
// =============================================================================

interface FormFieldProps {
  label: string;
  required?: boolean;
  error?: string;
  helper?: string;
  children: React.ReactNode;
}

function FormField({ label, required, error, helper, children }: FormFieldProps) {
  return (
    <Box mb="4">
      <Text
        as="label"
        size="2"
        weight="medium"
        style={{ color: '#CBD5E1', display: 'block', marginBottom: '8px' }}
      >
        {label}
        {required && <span style={{ color: '#F87171', marginLeft: 4 }}>*</span>}
      </Text>
      {children}
      {helper && !error && (
        <Text size="1" style={{ color: '#94A3B8', display: 'block', marginTop: '4px' }}>
          {helper}
        </Text>
      )}
      {error && (
        <Text size="1" style={{ color: '#F87171', display: 'block', marginTop: '4px' }}>
          {error}
        </Text>
      )}
    </Box>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function BusinessInfoPage() {
  const router = useRouter();
  const { profile, hasProfile, status, loading: profileLoading, error: profileError, refetch } = useMyOrganizerProfile();
  const { updateProfile, loading: updating, error: updateError } = useUpdateOrganizerProfile();
  const { createProfile, loading: creating, error: createError } = useCreateOrganizerProfile();
  const [isInitializing, setIsInitializing] = useState(false);

  // Track initialization attempts to prevent infinite loops
  const initAttemptedRef = useRef(false);

  const [formData, setFormData] = useState<FormData>({
    companyName: '',
    businessType: '',
    registrationNumber: '',
    taxId: '',
    email: '',
    phone: '',
    website: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    province: '',
    postalCode: '',
    country: 'Zambia',
    description: '',
  });
  const [errors, setErrors] = useState<FormErrors>({});
  const [formInitialized, setFormInitialized] = useState(false);

  // Create profile if user doesn't have one, or redirect if can't edit
  // Only attempt once to prevent infinite loops when backend is down
  useEffect(() => {
    // Skip if still loading, already initializing, or if there's a network error
    if (profileLoading || isInitializing) return;

    // If there's a network error, don't try to create profile
    if (profileError && isNetworkError(profileError)) {
      return;
    }

    // Only attempt initialization once per mount
    if (initAttemptedRef.current) return;

    const initializeProfile = async () => {
      if (!hasProfile && !profileError) {
        // No profile exists and no error - create one
        initAttemptedRef.current = true;
        setIsInitializing(true);
        try {
          await createProfile({
            companyName: null,
            businessEmail: null,
            businessPhone: null,
            businessAddress: null,
            city: null,
            province: null,
            website: null,
            companyDescription: null,
          });
          // Refetch to get the new profile
          await refetch();
        } catch (err) {
          console.error('Failed to create organizer profile:', err);
          // Don't retry on error - let the error state show
        } finally {
          setIsInitializing(false);
        }
        return;
      }

      if (hasProfile && status && !canEditProfile(status)) {
        // Has profile but can't edit (e.g., PENDING_REVIEW, REJECTED)
        const route = getRouteForStatus(status, hasProfile);
        router.replace(route);
      }
    };

    initializeProfile();
  }, [profileLoading, hasProfile, status, isInitializing, profileError, createProfile, refetch, router]);

  // Pre-populate form with existing profile data
  useEffect(() => {
    if (profile && !formInitialized) {
      setFormData({
        companyName: profile.companyName || '',
        businessType: profile.businessType || '',
        registrationNumber: profile.businessRegistrationNumber || '',
        taxId: profile.taxId || '',
        email: profile.businessEmail || '',
        phone: profile.businessPhone || '',
        website: profile.website || '',
        addressLine1: profile.businessAddress || '',
        addressLine2: '',
        city: profile.city || '',
        province: profile.province || '',
        postalCode: profile.postalCode || '',
        country: profile.country || 'Zambia',
        description: profile.companyDescription || '',
      });
      setFormInitialized(true);
    }
  }, [profile, formInitialized]);

  // Handle form field changes
  const handleChange = useCallback((field: keyof FormData, value: string) => {
    setFormData((prev) => ({ ...prev, [field]: value }));
    // Clear error when user types
    if (errors[field]) {
      setErrors((prev) => ({ ...prev, [field]: '' }));
    }
  }, [errors]);

  // Validate form
  const validateForm = useCallback((): boolean => {
    const newErrors: FormErrors = {};

    if (!formData.companyName.trim()) {
      newErrors.companyName = 'Company name is required';
    }
    if (!formData.businessType) {
      newErrors.businessType = 'Business type is required';
    }
    if (!formData.registrationNumber.trim()) {
      newErrors.registrationNumber = 'Registration number is required';
    }
    if (!formData.taxId.trim()) {
      newErrors.taxId = 'Tax ID (TPIN) is required';
    }
    if (!formData.email.trim()) {
      newErrors.email = 'Email is required';
    } else if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(formData.email)) {
      newErrors.email = 'Please enter a valid email address';
    }
    if (!formData.phone.trim()) {
      newErrors.phone = 'Phone number is required';
    }
    if (!formData.addressLine1.trim()) {
      newErrors.addressLine1 = 'Address is required';
    }
    if (!formData.city.trim()) {
      newErrors.city = 'City is required';
    }
    if (!formData.province) {
      newErrors.province = 'Province is required';
    }

    setErrors(newErrors);
    return Object.keys(newErrors).length === 0;
  }, [formData]);

  // Handle form submission
  const handleContinue = useCallback(async () => {
    if (!validateForm()) return;

    try {
      const result = await updateProfile({
        companyName: formData.companyName.trim(),
        businessType: formData.businessType,
        businessRegistrationNumber: formData.registrationNumber.trim(),
        taxId: formData.taxId.trim(),
        businessEmail: formData.email.trim(),
        businessPhone: formData.phone.trim(),
        website: formData.website.trim() || null,
        businessAddress: formData.addressLine1.trim() + (formData.addressLine2 ? ', ' + formData.addressLine2.trim() : ''),
        city: formData.city.trim(),
        province: formData.province,
        postalCode: formData.postalCode.trim() || null,
        country: formData.country,
        companyDescription: formData.description.trim() || null,
        // Optional branding fields - set to null for now (can be added in profile settings later)
        bannerUrl: null,
        logoUrl: null,
        tagline: null,
      });

      if (result?.success) {
        // Navigate to review page (skip documents step)
        router.push('/apply/review');
      }
    } catch (error) {
      console.error('Failed to save business info:', error);
    }
  }, [formData, validateForm, updateProfile, router]);

  // Handle back navigation - go to landing page
  const handleBack = useCallback(() => {
    router.push('/');
  }, [router]);

  // Show loading state while fetching or creating profile
  if (profileLoading || isInitializing || creating) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Spinner size="3" />
        <Text size="2" style={{ color: '#94A3B8', display: 'block', marginTop: 16 }}>
          {isInitializing || creating ? 'Starting your application...' : 'Loading your application...'}
        </Text>
      </Box>
    );
  }

  // Show error state - differentiate between network errors and other errors
  const currentError = profileError || createError;
  const isBackendUnavailable = currentError && isServerUnavailable(currentError);

  if (currentError) {
    return (
      <Box style={{ textAlign: 'center', padding: '60px 0' }}>
        <Box
          style={{
            width: 64,
            height: 64,
            borderRadius: '16px',
            background: isBackendUnavailable ? 'rgba(251, 191, 36, 0.1)' : 'rgba(239, 68, 68, 0.1)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            margin: '0 auto 16px',
          }}
        >
          {isBackendUnavailable ? (
            <Wifi style={{ width: 32, height: 32, color: '#FBBF24' }} />
          ) : (
            <WarningTriangle style={{ width: 32, height: 32, color: '#EF4444' }} />
          )}
        </Box>
        <Text size="3" weight="medium" style={{ color: '#F8FAFC', display: 'block', marginBottom: 8 }}>
          {isBackendUnavailable ? 'Unable to connect to server' : 'Failed to load application'}
        </Text>
        <Text size="2" style={{ color: '#94A3B8', display: 'block', marginBottom: 16, maxWidth: 400, margin: '0 auto 16px' }}>
          {isBackendUnavailable
            ? 'The server is currently unavailable. Please check your internet connection or try again later.'
            : currentError?.message || 'An error occurred while loading your application.'}
        </Text>
        <Flex gap="3" justify="center">
          <Button variant="outline" onClick={() => router.push('/')}>
            Go Home
          </Button>
          <Button
            onClick={() => {
              initAttemptedRef.current = false;
              refetch();
            }}
            style={{
              background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
            }}
          >
            Try Again
          </Button>
        </Flex>
      </Box>
    );
  }

  return (
    <Box>
      {/* Step Indicator */}
      <StepIndicator steps={steps} currentStep={0} allowNavigation={false} />

      {/* Page Header */}
      <Box mb="6">
        <Heading size="5" mb="2" style={{ color: '#F8FAFC' }}>
          Business Information
        </Heading>
        <Text size="2" style={{ color: '#94A3B8' }}>
          Tell us about your organization. This information will be used for verification.
        </Text>
      </Box>

      {/* Form */}
      <Box>
        {/* Company Details Card */}
        <Card
          mb="6"
          className="application-card"
          style={{
            background: 'rgba(30, 41, 59, 0.5)',
            border: '1px solid rgba(148, 163, 184, 0.1)',
            borderRadius: '16px',
            padding: '32px',
          }}
        >
          <Flex align="center" gap="2" mb="4">
            <Building style={{ width: 20, height: 20, color: '#10B981' }} />
            <Text size="3" weight="medium" style={{ color: '#F8FAFC' }}>
              Company Details
            </Text>
          </Flex>

          <Box
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: '16px',
            }}
          >
            <FormField label="Company Name" required error={errors.companyName}>
              <TextField.Root
                size="3"
                placeholder="Enter your company name"
                value={formData.companyName}
                onChange={(e) => handleChange('companyName', e.target.value)}
                className="application-input"
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: errors.companyName ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Business Type" required error={errors.businessType}>
              <Select.Root
                value={formData.businessType}
                onValueChange={(value) => handleChange('businessType', value)}
              >
                <Select.Trigger
                  placeholder="Select business type"
                  style={{
                    width: '100%',
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: errors.businessType ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                  }}
                />
                <Select.Content>
                  {businessTypes.map((type) => (
                    <Select.Item key={type.value} value={type.value}>
                      {type.label}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </FormField>

            <FormField
              label="Business Registration Number"
              required
              error={errors.registrationNumber}
              helper="As shown on your registration certificate"
            >
              <TextField.Root
                size="3"
                placeholder="e.g., 123456789"
                value={formData.registrationNumber}
                onChange={(e) => handleChange('registrationNumber', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: errors.registrationNumber ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField
              label="Tax ID (TPIN)"
              required
              error={errors.taxId}
              helper="Your Taxpayer Identification Number"
            >
              <TextField.Root
                size="3"
                placeholder="e.g., 1234567890"
                value={formData.taxId}
                onChange={(e) => handleChange('taxId', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: errors.taxId ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>
          </Box>
        </Card>

        {/* Contact Information Card */}
        <Card
          mb="6"
          style={{
            background: 'rgba(30, 41, 59, 0.5)',
            border: '1px solid rgba(148, 163, 184, 0.1)',
            borderRadius: '16px',
            padding: '32px',
          }}
        >
          <Flex align="center" gap="2" mb="4">
            <InfoCircle style={{ width: 20, height: 20, color: '#10B981' }} />
            <Text size="3" weight="medium" style={{ color: '#F8FAFC' }}>
              Contact Information
            </Text>
          </Flex>

          <Box
            style={{
              display: 'grid',
              gridTemplateColumns: 'repeat(auto-fit, minmax(280px, 1fr))',
              gap: '16px',
            }}
          >
            <FormField label="Business Email" required error={errors.email}>
              <TextField.Root
                size="3"
                type="email"
                placeholder="contact@yourcompany.com"
                value={formData.email}
                onChange={(e) => handleChange('email', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: errors.email ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Phone Number" required error={errors.phone}>
              <TextField.Root
                size="3"
                type="tel"
                placeholder="+260 97X XXX XXX"
                value={formData.phone}
                onChange={(e) => handleChange('phone', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: errors.phone ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Website" helper="Optional">
              <TextField.Root
                size="3"
                type="url"
                placeholder="https://yourcompany.com"
                value={formData.website}
                onChange={(e) => handleChange('website', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>
          </Box>
        </Card>

        {/* Address Card */}
        <Card
          mb="6"
          style={{
            background: 'rgba(30, 41, 59, 0.5)',
            border: '1px solid rgba(148, 163, 184, 0.1)',
            borderRadius: '16px',
            padding: '32px',
          }}
        >
          <Text size="3" weight="medium" mb="4" style={{ color: '#F8FAFC', display: 'block' }}>
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
              <FormField label="Address Line 1" required error={errors.addressLine1}>
                <TextField.Root
                  size="3"
                  placeholder="Street address"
                  value={formData.addressLine1}
                  onChange={(e) => handleChange('addressLine1', e.target.value)}
                  style={{
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: errors.addressLine1 ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                  }}
                />
              </FormField>
            </Box>

            <Box style={{ gridColumn: '1 / -1' }}>
              <FormField label="Address Line 2" helper="Optional">
                <TextField.Root
                  size="3"
                  placeholder="Suite, unit, building, floor, etc."
                  value={formData.addressLine2}
                  onChange={(e) => handleChange('addressLine2', e.target.value)}
                  style={{
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: '1px solid rgba(148, 163, 184, 0.2)',
                  }}
                />
              </FormField>
            </Box>

            <FormField label="City" required error={errors.city}>
              <TextField.Root
                size="3"
                placeholder="e.g., Lusaka"
                value={formData.city}
                onChange={(e) => handleChange('city', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: errors.city ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Province" required error={errors.province}>
              <Select.Root
                value={formData.province}
                onValueChange={(value) => handleChange('province', value)}
              >
                <Select.Trigger
                  placeholder="Select province"
                  style={{
                    width: '100%',
                    background: 'rgba(15, 23, 42, 0.6)',
                    border: errors.province ? '1px solid #F87171' : '1px solid rgba(148, 163, 184, 0.2)',
                  }}
                />
                <Select.Content>
                  {provinces.map((prov) => (
                    <Select.Item key={prov.value} value={prov.value}>
                      {prov.label}
                    </Select.Item>
                  ))}
                </Select.Content>
              </Select.Root>
            </FormField>

            <FormField label="Postal Code" helper="Optional">
              <TextField.Root
                size="3"
                placeholder="e.g., 10101"
                value={formData.postalCode}
                onChange={(e) => handleChange('postalCode', e.target.value)}
                style={{
                  background: 'rgba(15, 23, 42, 0.6)',
                  border: '1px solid rgba(148, 163, 184, 0.2)',
                }}
              />
            </FormField>

            <FormField label="Country">
              <TextField.Root
                size="3"
                value={formData.country}
                disabled
                style={{
                  background: 'rgba(15, 23, 42, 0.4)',
                  border: '1px solid rgba(148, 163, 184, 0.1)',
                  color: '#94A3B8',
                }}
              />
            </FormField>
          </Box>
        </Card>

        {/* Description Card */}
        <Card
          mb="6"
          style={{
            background: 'rgba(30, 41, 59, 0.5)',
            border: '1px solid rgba(148, 163, 184, 0.1)',
            borderRadius: '16px',
            padding: '32px',
          }}
        >
          <FormField
            label="About Your Organization"
            helper="Brief description of what your organization does and the types of events you plan to host"
          >
            <TextArea
              size="3"
              rows={4}
              placeholder="Tell us about your organization and the events you plan to organize..."
              value={formData.description}
              onChange={(e) => handleChange('description', e.target.value)}
              style={{
                background: 'rgba(15, 23, 42, 0.6)',
                border: '1px solid rgba(148, 163, 184, 0.2)',
                resize: 'vertical',
              }}
            />
          </FormField>
        </Card>

        {/* Update Error Display */}
        {updateError && (
          <Card
            mb="4"
            style={{
              padding: '16px',
              background: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              borderRadius: '12px',
            }}
          >
            <Flex align="center" gap="3">
              <WarningTriangle style={{ width: 20, height: 20, color: '#EF4444' }} />
              <Text size="2" style={{ color: '#FCA5A5' }}>
                {updateError.message || 'Failed to save your information. Please try again.'}
              </Text>
            </Flex>
          </Card>
        )}

        {/* Navigation Buttons */}
        <Flex justify="between" mt="6">
          <Button
            variant="outline"
            size="3"
            onClick={handleBack}
            disabled={updating}
            style={{
              borderColor: 'rgba(148, 163, 184, 0.3)',
              color: '#94A3B8',
            }}
          >
            <ArrowLeft style={{ width: 18, height: 18, marginRight: 8 }} />
            Back
          </Button>
          <Button
            size="3"
            onClick={handleContinue}
            disabled={updating}
            style={{
              background: 'linear-gradient(135deg, #10B981 0%, #14B8A6 100%)',
              cursor: updating ? 'not-allowed' : 'pointer',
              opacity: updating ? 0.7 : 1,
            }}
          >
            {updating ? (
              <>
                <Spinner size="1" />
                <span style={{ marginLeft: 8 }}>Saving...</span>
              </>
            ) : (
              <>
                Continue
                <ArrowRight style={{ width: 18, height: 18, marginLeft: 8 }} />
              </>
            )}
          </Button>
        </Flex>
      </Box>
    </Box>
  );
}
