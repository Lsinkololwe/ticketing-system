'use client';

import { useEffect, useMemo } from 'react';
import { SelectRHF, SwitchRHF, TimeRHF, useZodForm } from '@pml.tickets/shared';
import { Banner, Card, CardHeader, FormCell, FormGrid, SettingRow, Skeleton } from '@pml.tickets/shared/components/m3';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import type { NotificationPrefs } from '@/lib/api/settings';
import { useDefaultChoice } from '@/lib/team/useDefaultChoice';
import { notificationPrefsSchema } from './schemas';
import { SettingsForm } from './SettingsForm';

const HOURS = [1, 2, 6, 12, 24, 48, 72];

export interface NotificationPrefsSectionProps {
  prefs: NotificationPrefs;
  /** Throw to have the server error mapped onto the form. */
  onSave: (draft: NotificationPrefs) => Promise<void>;
}

const toForm = (p: NotificationPrefs) => ({
  ...p,
  reminderHoursBefore: p.reminderHoursBefore,
  timezone: p.timezone ?? '',
  quietHoursStart: p.quietHoursStart ?? '',
  quietHoursEnd: p.quietHoursEnd ?? '',
});

function FirstZone({ first }: { first: string | undefined }) {
  useDefaultChoice('timezone', first);
  return null;
}

type PrefMeta = { preferenceKey?: string; locked?: boolean };

export function NotificationPrefsSection({ prefs, onSave }: NotificationPrefsSectionProps) {
  const channelList = useReferenceOptions<PrefMeta>('NOTIFICATION_CHANNEL');
  const categoryList = useReferenceOptions<PrefMeta>('NOTIFICATION_CATEGORY');
  const zones = useReferenceOptions('TIMEZONE');
  // A row is shown only when its preferenceKey names a boolean this account's preferences actually carry.
  const known = (key: string | undefined): key is keyof NotificationPrefs => Boolean(key) && typeof (prefs as unknown as Record<string, unknown>)[key as string] === 'boolean';
  const channels = channelList.options.filter((o) => known(o.metadata.preferenceKey));
  const categories = categoryList.options.filter((o) => known(o.metadata.preferenceKey));
  const zoneOptions = zones.options.map((z) => ({ value: z.value, label: z.label }));
  if (prefs.timezone && !zoneOptions.some((z) => z.value === prefs.timezone)) zoneOptions.unshift({ value: prefs.timezone, label: prefs.timezone });
  const base = useMemo(() => toForm(prefs), [prefs]);
  const form = useZodForm(notificationPrefsSchema, { defaultValues: base });
  useEffect(() => form.reset(base), [base, form]);
  return (
    <SettingsForm
      form={form}
      label="Notification preferences"
      testId="settings-notifications"
      onSubmit={(v) => onSave({ ...prefs, ...v, quietHoursStart: v.quietHoursStart || null, quietHoursEnd: v.quietHoursEnd || null } as NotificationPrefs)}
    >
      <Card>
        <CardHeader title="Channels" subtitle="Where we reach you. WhatsApp first, SMS as the fallback." />
        {channelList.loading ? <Skeleton /> : null}
        {!channelList.loading && channels.length === 0 ? <Banner tone="warning">Not available yet: the notification channels could not be loaded.</Banner> : null}
        {channels.map((c) => {
          const k = c.metadata.preferenceKey as keyof NotificationPrefs;
          return <SettingRow key={c.value} label={c.label} description={c.description ?? undefined} control={<SwitchRHF name={k} aria-label={c.label} />} />;
        })}
      </Card>
      <Card>
        <CardHeader title="What to tell me about" />
        {categoryList.loading ? <Skeleton /> : null}
        {!categoryList.loading && categories.length === 0 ? <Banner tone="warning">Not available yet: the notification categories could not be loaded.</Banner> : null}
        {categories.map((c) => {
          const k = c.metadata.preferenceKey as keyof NotificationPrefs;
          return <SettingRow key={c.value} label={c.label} description={c.description ?? undefined} control={<SwitchRHF name={k} aria-label={c.label} disabled={c.metadata.locked === true} />} />;
        })}
      </Card>
      <Card>
        <CardHeader title="Timing" />
        <FormGrid>
          <FormCell span={6}>
            <SelectRHF name="reminderHoursBefore" label="Remind me before events" options={HOURS.map((h) => ({ value: String(h), label: `${h} hours` }))} />
          </FormCell>
          <FormCell span={6}>
            <SelectRHF name="timezone" label="Time zone" disabled={zones.loading} options={zoneOptions} helperText={!zones.loading && zones.empty && !prefs.timezone ? 'Not available yet: the time zones could not be loaded' : undefined} />
            <FirstZone first={zones.options[0]?.value} />
          </FormCell>
          <FormCell span={6}>
            <TimeRHF name="quietHoursStart" label="Quiet hours start" />
          </FormCell>
          <FormCell span={6}>
            <TimeRHF name="quietHoursEnd" label="Quiet hours end" />
          </FormCell>
        </FormGrid>
      </Card>
    </SettingsForm>
  );
}
