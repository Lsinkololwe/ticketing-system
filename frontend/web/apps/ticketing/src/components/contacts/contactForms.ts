import { z } from 'zod';
import { isValidEmail, isValidWhatsApp } from '@/lib/identity/contact';
import { describeContactError } from '@/lib/contacts/messages';

/** The new email or WhatsApp number typed into the add/change dialogs. */
export const valueSchema = z
  .object({ type: z.enum(['EMAIL', 'WHATSAPP']), email: z.string(), phone: z.string() })
  .superRefine((v, ctx) => {
    const ok = v.type === 'EMAIL' ? isValidEmail(v.email) : isValidWhatsApp(v.phone || undefined);
    if (!ok) ctx.addIssue({ code: 'custom', path: [v.type === 'EMAIL' ? 'email' : 'phone'], message: describeContactError('CONTACT_INVALID').message });
  });

/** The 6-digit code(s) that confirm a contact operation (a second code when a primary contact must also confirm). */
export const codesSchema = z
  .object({ code: z.string(), primaryCode: z.string(), needPrimary: z.boolean() })
  .superRefine((v, ctx) => {
    const msg = describeContactError('OTP_INVALID').message;
    if (!/^\d{6}$/.test(v.code)) ctx.addIssue({ code: 'custom', path: ['code'], message: msg });
    if (v.needPrimary && !/^\d{6}$/.test(v.primaryCode)) ctx.addIssue({ code: 'custom', path: ['primaryCode'], message: msg });
  });
