/**
 * Zod Validation Schemas for Organizations
 *
 * These schemas provide client-side validation for organization forms
 * and ensure data integrity before GraphQL mutations.
 *
 * OWASP A03:2021 - Injection Prevention:
 * All text fields that accept user input include sanitization transforms
 * to strip potentially dangerous HTML content and prevent XSS attacks.
 *
 * @see organization.types.ts for type definitions
 */

import { z } from 'zod';

// ==========================================
// Sanitization Utilities (OWASP A03:2021)
// ==========================================

/**
 * Strip HTML tags from a string to prevent XSS.
 */
function stripHtmlTags(str: string): string {
  return str.replace(/<[^>]*>/g, '');
}

/**
 * Sanitize user input by stripping HTML and trimming whitespace.
 */
function sanitizeText(str: string): string {
  return stripHtmlTags(str).trim();
}

/**
 * Sanitize optional text field.
 * Returns undefined for empty strings after sanitization.
 */
function sanitizeOptionalText(str: string | undefined): string | undefined {
  if (!str) return undefined;
  const sanitized = sanitizeText(str);
  return sanitized.length > 0 ? sanitized : undefined;
}

// ==========================================
// Constants
// ==========================================

/**
 * Zambian phone number regex
 * Accepts: +260971234567, 0971234567, 260971234567
 */
const ZAMBIAN_PHONE_REGEX = /^(\+?260|0)?[79]\d{8}$/;

/**
 * Zambian provinces
 */
export const ZAMBIAN_PROVINCES = [
  'CENTRAL',
  'COPPERBELT',
  'EASTERN',
  'LUAPULA',
  'LUSAKA',
  'MUCHINGA',
  'NORTHERN',
  'NORTH_WESTERN',
  'SOUTHERN',
  'WESTERN',
] as const;

/**
 * Organization types
 */
export const ORGANIZATION_TYPES = ['INDIVIDUAL', 'BUSINESS'] as const;

/**
 * Business types for settings
 */
export const BUSINESS_TYPES = [
  'SOLE_PROPRIETORSHIP',
  'PARTNERSHIP',
  'LIMITED_COMPANY',
  'NGO',
  'GOVERNMENT',
  'INDIVIDUAL',
] as const;

// ==========================================
// Field-Level Schemas
// ==========================================

/**
 * Email field schema
 */
export const emailFieldSchema = z
  .string()
  .min(1, 'Email is required')
  .email('Please enter a valid email address')
  .max(255, 'Email must be less than 255 characters')
  .transform(sanitizeText);

/**
 * Zambian phone number schema with normalization
 */
export const zambianPhoneFieldSchema = z
  .string()
  .min(1, 'Phone number is required')
  .regex(ZAMBIAN_PHONE_REGEX, {
    message:
      'Please enter a valid Zambian phone number (e.g., +260971234567 or 0971234567)',
  })
  .transform((val) => {
    // Normalize to E.164 format (+260...)
    if (val.startsWith('+260')) return val;
    if (val.startsWith('260')) return `+${val}`;
    if (val.startsWith('0')) return `+260${val.slice(1)}`;
    return `+260${val}`;
  });

/**
 * Optional URL schema
 */
export const optionalUrlFieldSchema = z
  .union([
    z.string().length(0),
    z
      .string()
      .url('Please enter a valid URL starting with http:// or https://'),
  ])
  .optional()
  .transform((val) => (val === '' ? undefined : val));

/**
 * Social URL schema (allows URL or @username)
 */
export const socialUrlFieldSchema = z
  .string()
  .refine(
    (val) => {
      if (!val || val.trim() === '') return true;
      const URL_REGEX = /^https?:\/\/.+$/;
      const SOCIAL_HANDLE_REGEX = /^@?[\w.]+$/;
      return URL_REGEX.test(val) || SOCIAL_HANDLE_REGEX.test(val);
    },
    {
      message: 'Please enter a valid URL (https://...) or username (@username)',
    }
  )
  .optional()
  .transform((val) => (val === '' ? undefined : val));

/**
 * Required text field schema factory
 */
export const requiredTextField = (fieldName: string, maxLength = 255) =>
  z
    .string()
    .min(1, `${fieldName} is required`)
    .max(maxLength, `${fieldName} must be less than ${maxLength} characters`)
    .transform(sanitizeText);

/**
 * Optional text field schema factory
 */
export const optionalTextField = (maxLength = 1000) =>
  z
    .string()
    .max(maxLength, `Text must be less than ${maxLength} characters`)
    .transform(sanitizeOptionalText)
    .optional();

// ==========================================
// Business Info Form Schema (Application Step 1)
// ==========================================

/**
 * Business information form validation schema
 * Used in organization application flow
 */
export const businessInfoFormSchema = z.object({
  // Basic information
  name: requiredTextField('Organization name', 100),
  type: z.enum(ORGANIZATION_TYPES, {
    message: 'Organization type is required',
  }),
  tagline: optionalTextField(150),
  description: optionalTextField(2000),

  // Contact information
  businessEmail: emailFieldSchema,
  businessPhone: zambianPhoneFieldSchema,
  website: optionalUrlFieldSchema,

  // Location
  city: requiredTextField('City', 100),
  province: z.enum(ZAMBIAN_PROVINCES, {
    message: 'Province is required',
  }),
  country: z.string().default('Zambia'),

  // Social links
  facebook: socialUrlFieldSchema,
  instagram: socialUrlFieldSchema,
  twitter: socialUrlFieldSchema,
});

export type BusinessInfoFormData = z.infer<typeof businessInfoFormSchema>;
export type BusinessInfoFormInput = z.input<typeof businessInfoFormSchema>;

// ==========================================
// Organization Settings Form Schema
// ==========================================

/**
 * Organization settings form validation schema
 * Used in dashboard settings page
 */
export const organizationSettingsFormSchema = z.object({
  // Basic Information
  companyName: requiredTextField('Company name', 100),
  tagline: optionalTextField(150),
  companyDescription: optionalTextField(2000),
  businessType: z.enum(BUSINESS_TYPES).optional(),

  // Contact Information
  businessEmail: emailFieldSchema,
  businessPhone: z
    .string()
    .regex(ZAMBIAN_PHONE_REGEX, {
      message: 'Please enter a valid Zambian phone number',
    })
    .optional()
    .or(z.literal('')),
  website: optionalUrlFieldSchema,

  // Business Registration
  businessRegistrationNumber: optionalTextField(50),
  taxId: optionalTextField(20),

  // Address
  businessAddress: optionalTextField(200),
  city: optionalTextField(100),
  province: z.string().optional(),
  country: z.string().default('Zambia'),
  postalCode: optionalTextField(20),
});

export type OrganizationSettingsFormData = z.infer<
  typeof organizationSettingsFormSchema
>;

// ==========================================
// Social Links Schema
// ==========================================

/**
 * Social links validation schema
 */
export const socialLinksSchema = z.object({
  facebook: socialUrlFieldSchema,
  instagram: socialUrlFieldSchema,
  twitter: socialUrlFieldSchema,
  linkedin: socialUrlFieldSchema,
  youtube: socialUrlFieldSchema,
  tiktok: socialUrlFieldSchema,
});

export type SocialLinksFormData = z.infer<typeof socialLinksSchema>;

// ==========================================
// Application Review Schema
// ==========================================

/**
 * Application review and submission validation schema
 * Used in the final review step before submitting the application
 */
export const applicationReviewSchema = z.object({
  agreedToTerms: z.boolean().refine((val) => val === true, {
    message: 'You must agree to the Terms of Service to continue',
  }),
  agreedToPrivacy: z.boolean().refine((val) => val === true, {
    message: 'You must agree to the Privacy Policy to continue',
  }),
});

export type ApplicationReviewFormData = z.infer<typeof applicationReviewSchema>;

// ==========================================
// Validation Helpers
// ==========================================

/**
 * Validate that all required fields are filled for submission
 */
export function validateForSubmission(data: BusinessInfoFormData): boolean {
  return !!(
    data.name &&
    data.type &&
    data.businessEmail &&
    data.businessPhone &&
    data.city &&
    data.province
  );
}

// ==========================================
// Re-export Constants
// ==========================================

export { ZAMBIAN_PHONE_REGEX };
