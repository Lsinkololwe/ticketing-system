'use client';

import { useEffect } from 'react';
import { z } from 'zod';
import { Button, Card, CardHeader, ErrorState, FormCell, FormGrid, Skeleton, StatusPill, TextField, useSnackbar } from '@pml.tickets/shared/components/m3';
import { Form } from '@pml.tickets/shared/forms/Form';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { useMySecurity, useUpdateMyProfile } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { ROLE_LABELS } from '@/config/navigation';
import { useStaff } from '@/components/console/StaffContext';

export const profileSchema = z.object({
  firstName: z.string().trim().min(1, 'Enter your first name'),
  lastName: z.string().trim().min(1, 'Enter your last name'),
  displayName: z.string().trim(),
});

/** Personal details: name fields are editable (updateMyProfile); email and phone are managed in the identity provider. */
export function PersonalInfo() {
  const staff = useStaff();
  const { me, loading, error, refetch } = useMySecurity();
  const { update, loading: saving } = useUpdateMyProfile();
  const snackbar = useSnackbar();
  const form = useZodForm(profileSchema, { defaultValues: { firstName: '', lastName: '', displayName: '' } });
  const { register, reset, formState: { errors, isDirty } } = form;
  useEffect(() => {
    if (me) reset({ firstName: me.firstName ?? '', lastName: me.lastName ?? '', displayName: me.displayName ?? '' });
  }, [me, reset]);

  if (error && !me) return <ErrorState error={error} onRetry={refetch} />;
  if (loading && !me) return <Skeleton width="100%" />;
  return (
    <div className="adm-stack">
      <Card>
        <CardHeader title="Personal details" subtitle="Shown in the audit log next to your actions." />
        <Form form={form} guardLeave={false} aria-label="Personal details" onSubmit={async (v) => {
          try {
            await update({ firstName: v.firstName, lastName: v.lastName, displayName: v.displayName || undefined });
            snackbar.show('Profile saved');
            reset(v);
          } catch (e) {
            snackbar.show((e as Error).message || 'Could not save your profile');
          }
        }}>
          <FormGrid>
            <FormCell span={6}><TextField label="First name" errorText={errors.firstName?.message} {...register('firstName')} /></FormCell>
            <FormCell span={6}><TextField label="Last name" errorText={errors.lastName?.message} {...register('lastName')} /></FormCell>
            <FormCell span={12}><TextField label="Display name (optional)" {...register('displayName')} /></FormCell>
            <FormCell span={6}><TextField label="Email" type="email" value={me?.email ?? staff.email} readOnly /></FormCell>
            <FormCell span={6}><TextField label="Phone" type="tel" value={me?.phoneNumber ?? ''} readOnly /></FormCell>
          </FormGrid>
          <p className="m3-muted">Email and phone are managed in your sign-in account and cannot be changed here.</p>
          <div>
            <span>Role</span>
            <div className="m3-row">
              {staff.roles.map((r) => (
                <StatusPill key={r}>{ROLE_LABELS[r]}</StatusPill>
              ))}
            </div>
          </div>
          <div className="m3-card__foot">
            <Button type="submit" variant="filled" loading={saving} disabled={!isDirty}>
              Save changes
            </Button>
          </div>
        </Form>
      </Card>
    </div>
  );
}
