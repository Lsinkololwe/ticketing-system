import { z } from 'zod';

const time = z.union([z.literal(''), z.string().regex(/^\d{2}:\d{2}$/, 'Use a time like 22:00.')]);

export const profileSchema = z.object({
  firstName: z.string().trim().min(1, 'Enter your name.').max(100),
  lastName: z.string().trim().max(100),
  whatsappEnabled: z.boolean(),
  smsEnabled: z.boolean(),
  pushEnabled: z.boolean(),
  emailEnabled: z.boolean(),
  inAppEnabled: z.boolean(),
  eventReminders: z.boolean(),
  eventUpdates: z.boolean(),
  teamNotifications: z.boolean(),
  marketingEmails: z.boolean(),
  systemAnnouncements: z.boolean(),
  reminderHoursBefore: z.string(),
  quietHoursStart: time,
  quietHoursEnd: time,
});
export type ProfileValues = z.input<typeof profileSchema>;
