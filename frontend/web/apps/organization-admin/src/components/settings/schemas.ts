import { z } from 'zod';
import { isoTime, nonEmptyTrimmed, slug } from '@pml.tickets/shared';

/** Optional web address: blank is allowed, anything else must be an http(s) URL. */
const optionalUrl = z
  .string()
  .trim()
  .refine(
    (v) => {
      if (!v) return true;
      try {
        const u = new URL(v);
        return u.protocol === 'http:' || u.protocol === 'https:';
      } catch {
        return false;
      }
    },
    { error: 'Enter a valid web address starting with https://' }
  );

/** Optional social profile: a handle (@name), a bare address (facebook.com/name) or a full http(s) URL. */
const optionalSocial = z
  .string()
  .trim()
  .refine((v) => !v || /^@[A-Za-z0-9._]{1,60}$/.test(v) || /^(https?:\/\/)?([a-z0-9-]+\.)+[a-z]{2,}(\/\S*)?$/i.test(v), {
    error: 'Enter a handle such as @name or a web address such as facebook.com/name',
  });

export const orgProfileSchema = z.object({
  name: nonEmptyTrimmed('Organization name', { max: 120 }).min(2, 'Enter the organization name'),
  slug: slug({ min: 3 }),
  description: z.string().max(2000, 'Use at most 2000 characters'),
  logoUrl: optionalUrl,
  bannerUrl: optionalUrl,
  tagline: z.string().trim().max(160, 'Use at most 160 characters'),
  website: optionalUrl,
  facebook: optionalSocial,
  instagram: optionalSocial,
  twitter: optionalSocial,
  linkedin: optionalSocial,
  youtube: optionalSocial,
  tiktok: optionalSocial,
  taxId: z.string().trim().max(40, 'Use at most 40 characters'),
  businessRegistrationNumber: z.string().trim().max(60, 'Use at most 60 characters'),
  businessPhone: z.string().trim().max(24, 'Use at most 24 characters'),
  businessEmail: z
    .string()
    .trim()
    .refine((v) => !v || /^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(v), { error: 'Enter a valid email address' }),
  addressLine1: z.string().trim().max(160, 'Use at most 160 characters'),
  city: z.string().trim().max(80, 'Use at most 80 characters'),
  province: z.string().trim().max(80, 'Use at most 80 characters'),
  country: z.string().trim().max(80, 'Use at most 80 characters'),
});
export type OrgProfileValues = z.input<typeof orgProfileSchema>;

const flag = z.boolean();
export const orgSettingsSchema = z.object({
  requireEventApproval: flag,
  allowMembersToInvite: flag,
  inviteRequiresApproval: flag,
  managersCanViewFinancials: flag,
  adminsCanRequestPayouts: flag,
  notifyOwnerOnMemberJoin: flag,
  notifyOwnerOnEventCreated: flag,
  notifyOwnerOnPayoutRequest: flag,
});

const optionalTime = z.union([z.literal(''), isoTime()]);
export const notificationPrefsSchema = z.object({
  emailEnabled: flag,
  smsEnabled: flag,
  whatsappEnabled: flag,
  pushEnabled: flag,
  inAppEnabled: flag,
  ticketNotifications: flag,
  eventReminders: flag,
  eventUpdates: flag,
  paymentNotifications: flag,
  teamNotifications: flag,
  marketingEmails: flag,
  systemAnnouncements: flag,
  reminderHoursBefore: z.coerce.number({ error: 'Choose a reminder time' }).int().min(1, 'Choose a reminder time'),
  timezone: z.string().min(1, 'Choose a time zone'),
  quietHoursStart: optionalTime,
  quietHoursEnd: optionalTime,
});

export const myProfileSchema = z.object({
  firstName: nonEmptyTrimmed('First name', { max: 60 }).min(2, 'Enter your first name'),
  lastName: z.string().trim().max(60, 'Use at most 60 characters'),
});
export type MyProfileValues = z.input<typeof myProfileSchema>;
