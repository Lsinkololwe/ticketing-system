import { z } from 'zod';
import { email, files, nonEmptyTrimmed, phoneE164, url } from '@pml.tickets/shared';
import { referenceCode } from '@pml.tickets/shared/api/graphql/shared/reference';
import { ACCEPTED_MIME_TYPES, MAX_DOCUMENT_BYTES } from '@/lib/onboarding/documents';

const optionalText = (label: string, max: number) =>
  z.string().trim().max(max, `${label} must be at most ${max} characters`);

const optionalUrl = z.union([z.literal(''), url()]);

const SOCIAL_URL = /^https?:\/\/.+$/;
const SOCIAL_HANDLE = /^@?[\w.]+$/;
const social = z
  .string()
  .trim()
  .refine((v) => v === '' || SOCIAL_URL.test(v) || SOCIAL_HANDLE.test(v), {
    message: 'Enter a link (https://...) or a username (@username)',
  });

/** The codes the platform lists for the three reference-backed choices of step 1. */
export interface BusinessInfoCodes {
  types: readonly string[];
  businessTypes: readonly string[];
  provinces: readonly string[];
}

/**
 * Step 1: organization type, business details and address.
 *
 * The three choices are checked against the lists the form loaded (ORGANIZER_TYPE, BUSINESS_TYPE, PROVINCE);
 * while a list is unavailable the client lets the code through and the server, which validates against the
 * same rows, decides.
 */
export const businessInfoSchemaFor = (codes: BusinessInfoCodes) =>
  z.object({
    name: nonEmptyTrimmed('Organization name', { max: 100 }),
    type: referenceCode(codes.types, 'Choose one of the listed organizer types', 'Choose the type of organizer you are'),
    businessType: referenceCode(
      codes.businessTypes,
      'Choose one of the listed business types',
      'Choose a business type. It decides which documents we ask for',
    ),
    tagline: optionalText('Tagline', 50),
    description: optionalText('Description', 2000),
    businessEmail: email(),
    businessPhone: phoneE164({ requiredMessage: 'Enter a business phone number' }),
    website: optionalUrl,
    businessRegistrationNumber: optionalText('Registration number', 50),
    taxId: optionalText('TPIN', 20),
    city: nonEmptyTrimmed('City', { max: 50 }),
    province: referenceCode(codes.provinces, 'Choose one of the listed provinces', 'Choose a province'),
    country: optionalText('Country', 50),
    facebook: social,
    instagram: social,
    twitter: social,
  });
export const businessInfoSchema = businessInfoSchemaFor({ types: [], businessTypes: [], provinces: [] });
export type BusinessInfoInput = z.input<typeof businessInfoSchema>;
export type BusinessInfoOutput = z.output<typeof businessInfoSchema>;

/** One KYB document slot: a single PDF/JPEG/PNG/WEBP up to 10 MB. */
export const documentFileSchema = z.object({
  file: files({ max: 1, maxBytes: MAX_DOCUMENT_BYTES, types: [...ACCEPTED_MIME_TYPES] }),
});
export type DocumentFileInput = z.input<typeof documentFileSchema>;

/** Step 3: confirmation before submit. */
export const reviewSchema = z.object({
  confirm: z.boolean().refine((v) => v, 'Confirm the details are accurate to submit'),
});
export type ReviewInput = z.input<typeof reviewSchema>;
