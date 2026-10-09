'use client';

import { useEffect, useMemo } from 'react';
import { TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import { Card, CardHeader, FormCell, FormGrid, TextField } from '@pml.tickets/shared/components/m3';
import type { SettingsMe } from '@/lib/api/settings';
import { myProfileSchema } from './schemas';
import { SettingsForm } from './SettingsForm';

export interface MyProfileSectionProps {
  me: SettingsMe;
  /** Throw to have the server error mapped onto the form. */
  onSave: (firstName: string, lastName: string) => Promise<void>;
}

export function MyProfileSection({ me, onSave }: MyProfileSectionProps) {
  const base = useMemo(() => ({ firstName: me.firstName ?? '', lastName: me.lastName ?? '' }), [me]);
  const form = useZodForm(myProfileSchema, { defaultValues: base });
  useEffect(() => form.reset(base), [base, form]);
  return (
    <div className="m3-stack">
      <SettingsForm form={form} label="My profile" testId="settings-me" onSubmit={(v) => onSave(v.firstName, v.lastName)}>
        <Card>
          <CardHeader title="My profile" />
          <FormGrid>
            <FormCell span={6}><TextFieldRHF name="firstName" label="First name" required /></FormCell>
            <FormCell span={6}><TextFieldRHF name="lastName" label="Last name" /></FormCell>
            <FormCell span={6}>
              <TextField label="Email" value={me.email ?? ''} readOnly helperText="Managed by your sign-in account" />
            </FormCell>
            <FormCell span={6}>
              <TextField label="Mobile number (sign-in)" value={me.phoneNumber ?? ''} readOnly helperText="Changes need a code sent to the new number" />
            </FormCell>
          </FormGrid>
        </Card>
      </SettingsForm>
    </div>
  );
}
