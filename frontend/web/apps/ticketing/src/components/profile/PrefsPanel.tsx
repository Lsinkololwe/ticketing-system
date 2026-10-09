'use client';

import { Banner, Icon, TextField } from '@pml.tickets/shared/components/m3';
import { SelectRHF, SwitchRHF, TimeRHF } from '@pml.tickets/shared/forms';
import { useFormContext } from 'react-hook-form';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';

const HOURS = [1, 2, 6, 12, 24, 48];

/** Channel, category and timing switches bound to the profile form. Ticket and payment messages are essential and always on. */
export function PrefsPanel({ timezone }: { timezone: string }) {
  const channelList = useReferenceOptions<{ preferenceKey?: string }>('NOTIFICATION_CHANNEL');
  const categoryList = useReferenceOptions<{ preferenceKey?: string; locked?: boolean }>('NOTIFICATION_CATEGORY');
  const { getValues } = useFormContext();
  // A row is a switch only when the profile form holds that preference; the platform may list more than this form edits.
  const editable = (key: string | undefined): key is string => Boolean(key) && key! in getValues();
  const channels = channelList.options.filter((o) => editable(o.metadata.preferenceKey));
  const categories = categoryList.options.filter((o) => o.metadata.locked !== true && editable(o.metadata.preferenceKey));
  const essential = categoryList.options.filter((o) => o.metadata.locked === true);
  return (
    <section className="m3-panel m3-stack" aria-labelledby="prefs-title">
      <h3 className="m3-card__title" id="prefs-title">Notification preferences</h3>
      <p className="m3-muted">Choose how Showstop contacts you. Ticket and payment messages are essential, so they are always on.</p>
      <h4 className="m3-eyebrow">Channels</h4>
      <div aria-busy={channelList.loading}>
        {channelList.loading ? <p className="m3-muted">Loading channels</p> : null}
        {!channelList.loading && channels.length === 0 ? (
          <Banner tone="warning" title="Channels are not available right now.">Try again in a moment.</Banner>
        ) : null}
        {channels.map((o) => (
          <SwitchRHF key={o.value} name={o.metadata.preferenceKey!} label={o.label} hint={o.description ?? undefined} />
        ))}
      </div>
      <h4 className="m3-eyebrow">Categories</h4>
      <div>
      {essential.map((o) => (
        <label key={o.value} className="m3-switch-row">
          <span className="m3-switch-row__text">
            <span className="m3-switch-row__label">
              {o.label}
              <span className="buyer-pill">
                <Icon name="lock" /> Always on
              </span>
            </span>
            <span className="m3-switch-row__hint">{o.description}</span>
          </span>
          <span className="m3-switch">
            <input type="checkbox" role="switch" className="m3-switch__input" checked disabled readOnly aria-label={o.label} />
            <span className="m3-switch__track" />
          </span>
        </label>
      ))}
      {categories.map((o) => (
        <SwitchRHF key={o.value} name={o.metadata.preferenceKey!} label={o.label} hint={o.description ?? undefined} />
      ))}
      </div>
      <h4 className="m3-eyebrow">Timing</h4>
      <div className="buyer-g2">
        <SelectRHF name="reminderHoursBefore" label="Event reminder" options={HOURS.map((h) => ({ value: String(h), label: `${h} hour${h > 1 ? 's' : ''} before` }))} />
        <TextField label="Time zone" readOnly value={timezone} />
        <TimeRHF name="quietHoursStart" label="Quiet hours start" />
        <TimeRHF name="quietHoursEnd" label="Quiet hours end" />
      </div>
      <p className="m3-muted">During quiet hours we hold non-essential messages until they end.</p>
    </section>
  );
}
