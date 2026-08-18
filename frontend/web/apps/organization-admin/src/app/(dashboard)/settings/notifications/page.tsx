'use client';

/**
 * Notification Settings Page
 *
 * Two independent axes, because that is what the backend actually stores:
 *
 *   CHANNELS    — how you are reached (email, SMS, WhatsApp, push, in-app)
 *   CATEGORIES  — what you are told about (ticket sales, payouts, team, …)
 *
 * <h2>Why this is not a category x channel matrix</h2>
 *
 * It used to be one: eight categories, each with its own email/push/SMS
 * toggle, twenty-four switches in all. None of them could be saved.
 * `UpdateNotificationPreferencesInput` has five channel booleans and seven
 * category booleans — flat, global, not a grid. There is no field in which
 * "email me about payouts but not about marketing" could be stored, so every
 * one of those twenty-four switches discarded its value on save.
 *
 * The screen now mirrors the storage model. Restoring the matrix means adding
 * per-category channel columns to the backend first.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { useState, useCallback, useEffect } from 'react';
import { Box, Flex, Text, Card, Switch, Skeleton } from '@radix-ui/themes';
import { FloppyDisk } from 'iconoir-react';
import { PageHeader } from '@/components/ui';
import {
  useMyNotificationPreferences,
  useUpdateNotificationPreferences,
  type NotificationPreferencesPatch,
} from '@pml.tickets/shared/api/organization-admin/modules/settings';

// =============================================================================
// FIELD DEFINITIONS
//
// Keys are the literal backend field names, so a rename shows up as a compile
// error rather than a toggle that quietly stops saving.
// =============================================================================

type PreferenceKey = keyof NotificationPreferencesPatch;

interface PreferenceField {
  key: PreferenceKey;
  title: string;
  description: string;
}

const CHANNELS: PreferenceField[] = [
  { key: 'emailEnabled', title: 'Email', description: 'Sent to your account email address.' },
  { key: 'smsEnabled', title: 'SMS', description: 'Text messages to your registered number.' },
  {
    key: 'whatsappEnabled',
    title: 'WhatsApp',
    description: 'The primary channel for most organizers in Zambia.',
  },
  { key: 'pushEnabled', title: 'Push', description: 'Alerts on the mobile app.' },
  { key: 'inAppEnabled', title: 'In-app', description: 'Shown in your notification centre here.' },
];

const CATEGORIES: PreferenceField[] = [
  {
    key: 'ticketNotifications',
    title: 'Ticket sales',
    description: 'Someone buys, transfers or refunds a ticket.',
  },
  {
    key: 'eventReminders',
    title: 'Event reminders',
    description: 'Ahead of an event you are running.',
  },
  {
    key: 'eventUpdates',
    title: 'Event updates',
    description: 'Approval decisions and changes to your events.',
  },
  {
    key: 'paymentNotifications',
    title: 'Payments and payouts',
    description: 'Payout approvals, transfers and failures.',
  },
  {
    key: 'teamNotifications',
    title: 'Team activity',
    description: 'Invitations accepted, roles changed, members removed.',
  },
  {
    key: 'systemAnnouncements',
    title: 'Service announcements',
    description: 'Planned maintenance and platform changes.',
  },
  {
    key: 'marketingEmails',
    title: 'Tips and marketing',
    description: 'Advice on growing your events. Off by default.',
  },
];

// =============================================================================
// TOGGLE ROW
// =============================================================================

function PreferenceRow({
  field,
  checked,
  onChange,
}: {
  field: PreferenceField;
  checked: boolean;
  onChange: (key: PreferenceKey, value: boolean) => void;
}) {
  return (
    <Flex
      justify="between"
      align="center"
      gap="4"
      py="3"
      style={{ borderBottom: '1px solid var(--gray-a4)' }}
    >
      <Box style={{ flex: 1, minWidth: 0 }}>
        <Text as="p" size="2" weight="medium" style={{ color: 'var(--gray-12)' }}>
          {field.title}
        </Text>
        <Text as="p" size="1" style={{ color: 'var(--gray-11)' }}>
          {field.description}
        </Text>
      </Box>
      <Switch
        size="2"
        checked={checked}
        onCheckedChange={(value) => onChange(field.key, value)}
        data-testid={`pref-${field.key}`}
      />
    </Flex>
  );
}

// =============================================================================
// MAIN COMPONENT
// =============================================================================

export default function NotificationSettingsPage() {
  const { preferences, loading } = useMyNotificationPreferences();
  const { updatePreferences, loading: isSaving } = useUpdateNotificationPreferences();

  // Only what the user has actually changed. Sending the whole object back
  // would reset any preference this build does not know about — every input
  // field is optional server-side precisely so partial updates are possible.
  const [patch, setPatch] = useState<NotificationPreferencesPatch>({});
  const [saveError, setSaveError] = useState<string | null>(null);
  const [savedAt, setSavedAt] = useState<string | null>(null);

  // Clear pending edits whenever the server sends a newer set, so the switches
  // never show a stale local value on top of fresh data.
  useEffect(() => {
    setPatch({});
  }, [preferences]);

  const valueOf = useCallback(
    (key: PreferenceKey): boolean => {
      if (key in patch) return patch[key] as boolean;
      const stored = preferences as Record<string, unknown> | null;
      return Boolean(stored?.[key]);
    },
    [patch, preferences]
  );

  const handleChange = useCallback((key: PreferenceKey, value: boolean) => {
    setSavedAt(null);
    setPatch((prev) => ({ ...prev, [key]: value }));
  }, []);

  const handleSave = useCallback(async () => {
    setSaveError(null);

    if (Object.keys(patch).length === 0) {
      setSavedAt(null);
      return;
    }

    const result = await updatePreferences(patch);

    if (!result.success) {
      setSaveError(result.error ?? 'Your preferences could not be saved.');
      return;
    }

    setPatch({});
    setSavedAt(new Date().toLocaleTimeString());
  }, [patch, updatePreferences]);

  const hasChanges = Object.keys(patch).length > 0;

  return (
    <Box>
      <PageHeader
        title="Notifications"
        description="Choose which updates reach you, and where."
        breadcrumbs={[{ label: 'Settings', href: '/settings' }, { label: 'Notifications' }]}
        actions={[
          {
            label: isSaving ? 'Saving…' : 'Save changes',
            icon: <FloppyDisk style={{ width: 18, height: 18, marginRight: 8 }} />,
            onClick: handleSave,
            // Nothing to save is not an error, but the button should not
            // pretend work happened either.
            disabled: isSaving || !hasChanges,
          },
        ]}
      />

      {saveError && (
        <Box
          className="error-card"
          mb="4"
          style={{
            padding: 'var(--space-3) var(--space-4)',
            borderRadius: 'var(--card-radius)',
            border: '1px solid var(--red-a6)',
          }}
          role="alert"
          data-testid="notifications-save-error"
        >
          <Text as="p" size="2" style={{ color: 'var(--status-danger-11)' }}>
            {saveError}
          </Text>
        </Box>
      )}

      {savedAt && !saveError && (
        <Text
          as="p"
          size="1"
          mb="4"
          style={{ color: 'var(--status-success-11)' }}
          data-testid="notifications-saved"
        >
          Saved at {savedAt}.
        </Text>
      )}

      {/* Preferences are never rendered from hardcoded defaults while loading.
          Showing "marketing: off" before the real value arrives invites the
          user to save a setting they never chose. */}
      {loading && !preferences ? (
        <Flex direction="column" gap="4" data-testid="notifications-loading">
          <Skeleton style={{ height: 280, borderRadius: 'var(--card-radius-bento)' }} />
          <Skeleton style={{ height: 340, borderRadius: 'var(--card-radius-bento)' }} />
        </Flex>
      ) : (
        <Flex direction="column" gap="5">
          <Card className="ds-card-bento" style={{ padding: 'var(--space-5)' }}>
            <Text as="p" className="viz-title" mb="1">
              How we reach you
            </Text>
            <Text as="p" size="1" mb="3" style={{ color: 'var(--gray-11)' }}>
              Turning a channel off silences it for every category below.
            </Text>
            {CHANNELS.map((field) => (
              <PreferenceRow
                key={field.key}
                field={field}
                checked={valueOf(field.key)}
                onChange={handleChange}
              />
            ))}
          </Card>

          <Card className="ds-card-bento" style={{ padding: 'var(--space-5)' }}>
            <Text as="p" className="viz-title" mb="1">
              What we tell you about
            </Text>
            <Text as="p" size="1" mb="3" style={{ color: 'var(--gray-11)' }}>
              These apply across every channel you have switched on.
            </Text>
            {CATEGORIES.map((field) => (
              <PreferenceRow
                key={field.key}
                field={field}
                checked={valueOf(field.key)}
                onChange={handleChange}
              />
            ))}
          </Card>
        </Flex>
      )}
    </Box>
  );
}
