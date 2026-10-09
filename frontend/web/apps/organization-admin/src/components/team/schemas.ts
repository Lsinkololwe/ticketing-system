import { z } from 'zod';
import { isRealIsoDate, nonEmptyTrimmed, normalisePhone } from '@pml.tickets/shared';
import { referenceCode } from '@pml.tickets/shared/api/graphql/shared/reference';

/** The role codes the platform lists (ORGANIZATION_ROLE invitable, EVENT_ROLE grantable); empty while unavailable. */
export interface RoleCodes {
  org: readonly string[];
  event: readonly string[];
}
const orgRoleCode = (roles: RoleCodes) => referenceCode(roles.org, 'Choose one of the listed roles', 'Choose a role');
const eventRoleCode = (roles: RoleCodes) => referenceCode(roles.event, 'Choose one of the listed event roles', 'Choose an event role');

export interface InvitePerson {
  /** At least one of email and phoneNumber; a phone-only invitation is sent by WhatsApp. */
  email: string | null;
  inviteeName: string | null;
  phoneNumber: string | null;
}

/** Parse "name, email or phone" lines. Returns people and the lines that could not be read. */
export function parseBulkInvites(text: string): { people: InvitePerson[]; bad: string[] } {
  const isMail = (v: string) => /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v.trim());
  const people: InvitePerson[] = [];
  const bad: string[] = [];
  text
    .split('\n')
    .map((l) => l.trim())
    .filter(Boolean)
    .forEach((line) => {
      const parts = line.split(',').map((s) => s.trim());
      const mail = parts.find(isMail);
      const phoneRaw = parts.find((s) => !isMail(s) && /^[+\d][\d\s()-]{6,}$/.test(s));
      const phone = phoneRaw ? normalisePhone(phoneRaw) : undefined;
      if (!mail && !phone) return void bad.push(line);
      const name = parts.find((s) => s && s !== mail && s !== phoneRaw);
      people.push({ email: mail ? mail.toLowerCase() : null, inviteeName: name ?? null, phoneNumber: phone ?? null });
    });
  return { people, bad };
}

const inviteShared = (roles: RoleCodes) => ({
  role: orgRoleCode(roles),
  message: z.string().trim(),
  /** One boolean per event id: ticked events receive `grantRole`. */
  picked: z.record(z.string(), z.boolean()),
  grantRole: eventRoleCode(roles),
});

function finish<T extends { role: string; message: string; picked: Record<string, boolean>; grantRole: string }>(v: T, people: InvitePerson[]) {
  return {
    people,
    role: v.role,
    message: v.message || null,
    eventAccessGrants: Object.entries(v.picked)
      .filter(([, on]) => on)
      .map(([eventId]) => ({ eventId, role: v.grantRole })),
  };
}

/** Invite one person: a name plus an email, a WhatsApp number, or both. */
export const inviteOneSchema = (roles: RoleCodes) =>
  z
  .object({
    name: nonEmptyTrimmed().min(2, 'Enter their name'),
    email: z.string().trim().refine((v) => v === '' || /^[^\s@]+@[^\s@]+\.[^\s@]{2,}$/.test(v), { error: 'Enter a valid email address' }),
    phone: z.string().trim().refine((v) => v === '' || normalisePhone(v) !== undefined, { error: 'Enter a valid phone number' }),
    ...inviteShared(roles),
  })
  .superRefine((v, ctx) => {
    if (!v.email && !v.phone) ctx.addIssue({ code: 'custom', path: ['email'], message: 'Enter an email address or a WhatsApp number' });
  })
  .transform((v) => finish(v, [{ email: v.email ? v.email.toLowerCase() : null, inviteeName: v.name, phoneNumber: v.phone ? normalisePhone(v.phone) ?? null : null }]));

/** Invite several: one "name, email" per line. */
export const inviteBulkSchema = (roles: RoleCodes) =>
  z
  .object({ list: z.string(), ...inviteShared(roles) })
  .superRefine((v, ctx) => {
    const parsed = parseBulkInvites(v.list);
    if (!parsed.people.length && !parsed.bad.length) ctx.addIssue({ code: 'custom', path: ['list'], message: 'Add at least one person' });
    else if (parsed.bad.length)
      ctx.addIssue({ code: 'custom', path: ['list'], message: `These lines need a valid email: ${parsed.bad.slice(0, 3).join(' | ')}` });
  })
  .transform((v) => finish(v, parseBulkInvites(v.list).people));

export type InviteRequest = z.output<ReturnType<typeof inviteOneSchema>>;

const today = () => new Date().toISOString().slice(0, 10);

/** Grant (or edit) event access. Reason is required for new grants only; members already granted are refused. */
export function grantSchema(opts: { eventRoles: readonly string[]; editing: boolean; takenUserIds: string[]; /** Organization-wide table: the event is chosen in the dialog. */ withEvent?: boolean; takenPairs?: string[] }) {
  return z
    .object({
      eventId: opts.withEvent && !opts.editing ? z.string().min(1, 'Choose an event') : z.string(),
      userId: z
        .string()
        .min(1, 'Choose a team member')
        .refine((id) => opts.editing || opts.withEvent || !opts.takenUserIds.includes(id), 'This member already has access to the event'),
      role: referenceCode(opts.eventRoles, 'Choose one of the listed event roles', 'Choose an event role'),
      expires: z.string().refine((v) => v === '' || (isRealIsoDate(v) && v >= nextDay(today())), 'Pick a future expiry date'),
      reason: opts.editing ? z.string() : nonEmptyTrimmed().min(3, 'Enter a reason'),
    })
    .superRefine((v, ctx) => {
      if (opts.withEvent && !opts.editing && v.eventId && v.userId && (opts.takenPairs ?? []).includes(`${v.eventId}:${v.userId}`)) {
        ctx.addIssue({ code: 'custom', path: ['userId'], message: 'This member already has access to that event' });
      }
    });
}
export type GrantFormValues = z.output<ReturnType<typeof grantSchema>>;

function nextDay(iso: string): string {
  const d = new Date(`${iso}T00:00:00Z`);
  d.setUTCDate(d.getUTCDate() + 1);
  return d.toISOString().slice(0, 10);
}

/** Change a member's role. */
export const roleSchema = (orgRoles: readonly string[]) =>
  z.object({ role: referenceCode(orgRoles, 'Choose one of the listed roles', 'Choose a role') });

/** Per-permission override, in CAPABILITIES order: role default, allow, deny. */
export const permissionsSchema = z.object({ perms: z.array(z.enum(['', 'allow', 'deny'])) });
export type PermSetting = '' | 'allow' | 'deny';

/** Nominate an admin as the new owner. */
export const transferSchema = z.object({ newOwnerId: z.string().min(1, 'Choose an admin to take over') });
