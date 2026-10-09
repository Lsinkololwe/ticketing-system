'use client';

import { useEffect } from 'react';
import { z } from 'zod';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { email } from '@pml.tickets/shared/forms/schemas/primitives';
import { Button, Checkbox, Dialog, FormGrid, FormCell, Select, TextField } from '@pml.tickets/shared/components/m3';
import { ADMIN_STAFF_ROLES, ADMIN_USER_ROLES, type AdminUserRecord, type AdminUserRole } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { humanize } from '@/lib/format';

export type UserFormMode = 'create' | 'admin' | 'edit';

const nameField = (msg: string) => z.string().trim().min(1, msg);
const PHONE = /^\+\d{9,15}$/;
const createSchema = z.object({
  firstName: nameField('Enter a first name'),
  lastName: nameField('Enter a last name'),
  email: email(),
  phone: z.string().trim().refine((v) => v === '' || PHONE.test(v), 'Use the format +260971234567'),
  role: z.string(),
  displayName: z.string(),
});
const adminSchema = createSchema.extend({ phone: z.string().trim().regex(PHONE, 'Staff need a phone number such as +260971234567') });
const editSchema = z.object({
  firstName: nameField('Enter a first name'),
  lastName: nameField('Enter a last name'),
  email: z.string(),
  phone: z.string(),
  role: z.string(),
  displayName: z.string().trim(),
});
export type UserFormValues = z.output<typeof createSchema>;

export interface UserFormDialogProps {
  open: boolean;
  mode: UserFormMode;
  user?: AdminUserRecord | null;
  loading?: boolean;
  onClose: () => void;
  onSubmit: (v: UserFormValues) => void;
}

const COPY: Record<UserFormMode, { title: string; body: string; label: string }> = {
  create: {
    title: 'New user',
    body: 'Creates a customer or organizer account. The person completes their phone verification at first sign-in.',
    label: 'Create user',
  },
  admin: { title: 'Create admin', body: 'Staff accounts must enrol an authenticator app at first sign-in.', label: 'Create admin' },
  edit: { title: 'Edit user', body: 'Email, phone and verification state are managed in Keycloak and cannot be edited here.', label: 'Save changes' },
};

/** New user / Create admin / Edit: one react-hook-form + zod form, three modes. */
export function UserFormDialog({ open, mode, user, loading, onClose, onSubmit }: UserFormDialogProps) {
  const form = useZodForm(mode === 'edit' ? editSchema : mode === 'admin' ? adminSchema : createSchema, {
    defaultValues: { firstName: '', lastName: '', email: '', phone: '', role: mode === 'admin' ? 'ADMIN' : 'CUSTOMER', displayName: '' },
  });
  const { register, handleSubmit, reset, formState } = form;
  const { errors } = formState;
  useEffect(() => {
    if (!open) return;
    reset({
      firstName: user?.firstName ?? '',
      lastName: user?.lastName ?? '',
      email: user?.email ?? '',
      phone: '',
      role: mode === 'admin' ? 'ADMIN' : 'CUSTOMER',
      displayName: '',
    });
  }, [open, mode, user, reset]);
  const copy = COPY[mode];
  const title = mode === 'edit' && user ? `Edit ${user.fullName}` : copy.title;
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={title}
      actions={
        <>
          <Button variant="text" onClick={onClose}>
            Cancel
          </Button>
          <Button variant="filled" loading={loading} onClick={handleSubmit((v) => onSubmit(v as UserFormValues))}>
            {copy.label}
          </Button>
        </>
      }
    >
      <p className="m3-muted">{copy.body}</p>
      <FormGrid>
        <FormCell span={6}>
          <TextField label="First name" errorText={errors.firstName?.message} {...register('firstName')} />
        </FormCell>
        <FormCell span={6}>
          <TextField label="Last name" errorText={errors.lastName?.message} {...register('lastName')} />
        </FormCell>
        {mode === 'edit' ? (
          <FormCell span={12}>
            <TextField label="Display name (optional)" {...register('displayName')} />
          </FormCell>
        ) : (
          <>
            <FormCell span={12}>
              <TextField label="Email" type="email" errorText={errors.email?.message} {...register('email')} />
            </FormCell>
            <FormCell span={12}>
              <TextField label="Phone number" type="tel" inputMode="tel" placeholder="+260971234567" errorText={errors.phone?.message} {...register('phone')} />
            </FormCell>
            <FormCell span={12}>
              {mode === 'admin' ? (
                <Select label="Staff role" {...register('role')}>
                  {(['ADMIN', 'FINANCE', 'FINANCE_LEAD', 'SUPER_ADMIN'] as const).map((r) => (
                    <option key={r} value={r}>
                      {humanize(r)}
                    </option>
                  ))}
                </Select>
              ) : (
                <Select label="Role" {...register('role')}>
                  <option value="CUSTOMER">Customer</option>
                  <option value="ORGANIZER">Organizer</option>
                </Select>
              )}
            </FormCell>
          </>
        )}
      </FormGrid>
    </Dialog>
  );
}

export interface RolesDialogProps {
  open: boolean;
  user: AdminUserRecord | null;
  /** SUPER_ADMIN only: may grant or remove staff roles. */
  canEditStaffRoles: boolean;
  loading?: boolean;
  onClose: () => void;
  onSubmit: (roles: AdminUserRole[]) => void;
}

const rolesSchema = z.object({ roles: z.array(z.string()).min(1, 'Choose at least one role') });

/** Role checklist. CUSTOMER is the base role the backend requires on every account. */
export function RolesDialog({ open, user, canEditStaffRoles, loading, onClose, onSubmit }: RolesDialogProps) {
  const form = useZodForm(rolesSchema, { defaultValues: { roles: ['CUSTOMER'] } });
  const { register, handleSubmit, reset, formState } = form;
  useEffect(() => {
    if (open && user) reset({ roles: Array.from(new Set(['CUSTOMER', ...user.roles])) });
  }, [open, user, reset]);
  if (!user) return null;
  return (
    <Dialog
      open={open}
      onClose={onClose}
      title={`Roles for ${user.fullName}`}
      actions={
        <>
          <Button variant="text" onClick={onClose}>
            Cancel
          </Button>
          <Button
            variant="filled"
            loading={loading}
            onClick={handleSubmit((v) => {
              const chosen = ([] as string[]).concat(v.roles ?? []);
              // Disabled controls drop out of the form value, so locked staff roles are carried over from the account.
              onSubmit(
                ADMIN_USER_ROLES.filter((r) => {
                  if (r === 'CUSTOMER') return true;
                  if (ADMIN_STAFF_ROLES.includes(r) && !canEditStaffRoles) return user.roles.includes(r);
                  return chosen.includes(r);
                })
              );
            })}
          >
            Save roles
          </Button>
        </>
      }
    >
      <p className="m3-muted">{canEditStaffRoles ? 'Select every role this person holds.' : 'Only a super admin can grant or remove staff roles.'}</p>
      <div className="m3-stack">
        {ADMIN_USER_ROLES.map((r) => (
          <Checkbox
            key={r}
            label={humanize(r)}
            hint={r === 'CUSTOMER' ? 'Base role every account keeps' : undefined}
            value={r}
            disabled={r === 'CUSTOMER' || (ADMIN_STAFF_ROLES.includes(r) && !canEditStaffRoles)}
            {...register('roles')}
          />
        ))}
        {formState.errors.roles?.message ? <span role="alert">{formState.errors.roles.message}</span> : null}
      </div>
    </Dialog>
  );
}
