/**
 * Application Flow Constants
 *
 * Centralized constants for the organization application wizard.
 * Includes provinces, organization types, and step configuration.
 */

// =============================================================================
// STEP CONFIGURATION
// =============================================================================

export interface ApplicationStep {
  id: string;
  title: string;
  description?: string;
}

/**
 * Steps in the organization application wizard
 */
export const APPLICATION_STEPS: ApplicationStep[] = [
  { id: 'business-info', title: 'Organization Info' },
  { id: 'review', title: 'Review & Submit' },
];

// =============================================================================
// PROVINCE OPTIONS
// =============================================================================

export interface SelectOption<T extends string = string> {
  value: T;
  label: string;
}

/**
 * Zambian provinces with display labels
 * Values match the backend enum and Zod schema
 */
export const PROVINCE_OPTIONS: SelectOption[] = [
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

/**
 * Province value to label mapping for display
 */
export const PROVINCE_LABELS: Record<string, string> = Object.fromEntries(
  PROVINCE_OPTIONS.map(({ value, label }) => [value, label])
);

// =============================================================================
// ORGANIZATION TYPE OPTIONS
// =============================================================================

/**
 * Organization types with display labels
 * Values match the backend enum and Zod schema
 */
export const ORGANIZATION_TYPE_OPTIONS: SelectOption<
  'INDIVIDUAL' | 'BUSINESS' | 'NON_PROFIT' | 'GOVERNMENT' | 'EDUCATIONAL' | 'COMMUNITY' | 'RELIGIOUS'
>[] = [
  { value: 'INDIVIDUAL', label: 'Individual / Personal' },
  { value: 'BUSINESS', label: 'Business / Company' },
  { value: 'NON_PROFIT', label: 'Non-Profit / NGO' },
  { value: 'GOVERNMENT', label: 'Government / Public Sector' },
  { value: 'EDUCATIONAL', label: 'Educational Institution' },
  { value: 'COMMUNITY', label: 'Community Group / Club' },
  { value: 'RELIGIOUS', label: 'Religious Organization' },
];

/**
 * Organization type value to label mapping for display
 */
export const ORGANIZATION_TYPE_LABELS: Record<string, string> = Object.fromEntries(
  ORGANIZATION_TYPE_OPTIONS.map(({ value, label }) => [value, label])
);

// =============================================================================
// BUSINESS TYPE OPTIONS (for settings)
// =============================================================================

/**
 * Business types for organization settings
 */
export const BUSINESS_TYPE_OPTIONS: SelectOption[] = [
  { value: 'SOLE_PROPRIETORSHIP', label: 'Sole Proprietorship' },
  { value: 'PARTNERSHIP', label: 'Partnership' },
  { value: 'LIMITED_COMPANY', label: 'Limited Company' },
  { value: 'NGO', label: 'Non-Governmental Organization (NGO)' },
  { value: 'GOVERNMENT', label: 'Government Entity' },
  { value: 'INDIVIDUAL', label: 'Individual' },
];

/**
 * Business type value to label mapping
 */
export const BUSINESS_TYPE_LABELS: Record<string, string> = Object.fromEntries(
  BUSINESS_TYPE_OPTIONS.map(({ value, label }) => [value, label])
);
