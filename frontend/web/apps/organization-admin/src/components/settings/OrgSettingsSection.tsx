'use client';

import { useEffect } from 'react';
import { SwitchRHF, useZodForm } from '@pml.tickets/shared';
import { Banner, Card, CardHeader, SettingRow } from '@pml.tickets/shared/components/m3';
import type { OrgSettingsFlags } from '@/lib/api/settings';
import { orgSettingsSchema } from './schemas';
import { SettingsForm } from './SettingsForm';

const GROUPS: Array<{ title: string; items: Array<{ key: keyof OrgSettingsFlags; label: string; hint?: string }> }> = [
  { title: 'Event approval', items: [{ key: 'requireEventApproval', label: 'Require event approval', hint: 'Events must be approved by the platform before they can be published.' }] },
  {
    title: 'Team',
    items: [
      { key: 'allowMembersToInvite', label: 'Allow members to invite', hint: 'Managers can invite people, not only owner and admins.' },
      { key: 'inviteRequiresApproval', label: 'Invitations need owner approval', hint: 'Invites sent by members wait for the owner.' },
    ],
  },
  {
    title: 'Money',
    items: [
      { key: 'managersCanViewFinancials', label: 'Managers can view finances', hint: 'Lets MANAGER see the Finance section and revenue.' },
      { key: 'adminsCanRequestPayouts', label: 'Admins can request payouts', hint: 'Otherwise only the owner can.' },
    ],
  },
  {
    title: 'Notify the owner when',
    items: [
      { key: 'notifyOwnerOnMemberJoin', label: 'A member joins' },
      { key: 'notifyOwnerOnEventCreated', label: 'An event is created' },
      { key: 'notifyOwnerOnPayoutRequest', label: 'A payout is requested' },
    ],
  },
];

export interface OrgSettingsSectionProps {
  settings: OrgSettingsFlags;
  isOwner: boolean;
  /** Throw to have the server error mapped onto the form. */
  onSave: (flags: OrgSettingsFlags) => Promise<void>;
}

export function OrgSettingsSection({ settings, isOwner, onSave }: OrgSettingsSectionProps) {
  const form = useZodForm(orgSettingsSchema, { defaultValues: settings });
  useEffect(() => form.reset(settings), [settings, form]);
  return (
    <div className="m3-stack">
      {!isOwner ? <Banner tone="info">Only the owner can change these settings.</Banner> : null}
      <SettingsForm form={form} label="Organization settings" testId="settings-org" readOnly={!isOwner} onSubmit={(v) => onSave(v)}>
        {GROUPS.map((g) => (
          <Card key={g.title}>
            <CardHeader title={g.title} />
            {g.items.map((i) => (
              <SettingRow key={i.key} label={i.label} description={i.hint} control={<SwitchRHF name={i.key} aria-label={i.label} />} />
            ))}
          </Card>
        ))}
      </SettingsForm>
    </div>
  );
}
