import { otp6 } from '@pml.tickets/shared/forms';
import { z } from 'zod';
import { isValidEmail, isValidWhatsApp } from '@/lib/identity/contact';
import { describeError } from '@/lib/identity/messages';

/** One contact form for both ways in: WhatsApp number or email. The server stays authoritative. */
export const contactSchema = z
  .object({
    mode: z.enum(['WHATSAPP', 'EMAIL']),
    phone: z.string(),
    email: z.string(),
  })
  .superRefine((v, ctx) => {
    const ok = v.mode === 'EMAIL' ? isValidEmail(v.email) : isValidWhatsApp(v.phone || undefined);
    if (!ok) {
      ctx.addIssue({ code: 'custom', path: [v.mode === 'EMAIL' ? 'email' : 'phone'], message: describeError('CONTACT_INVALID').message });
    }
  });
export type ContactValues = z.input<typeof contactSchema>;

export const codeSchema = z.object({ code: otp6() });
export type CodeValues = z.input<typeof codeSchema>;
