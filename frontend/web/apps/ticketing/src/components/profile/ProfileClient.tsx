'use client';

import { useEffect, useState } from 'react';
import { Form, TextFieldRHF, useZodForm } from '@pml.tickets/shared/forms';
import { profileSchema, type ProfileValues } from './profileForm';
import { Avatar, Button, ConfirmDialog, ErrorState, SaveBar, SectionHeader, Skeleton, StatusPill, useSnackbar } from '@pml.tickets/shared/components/m3';
import type { GraphQLLikeError } from '@pml.tickets/shared';
import { useBuyerAuth } from '@/lib/auth/session-context';
import { useMe } from '@pml.tickets/shared';
import { useNotificationPrefs, type NotificationPrefs } from '@pml.tickets/shared';
import type { MeRow } from '@pml.tickets/shared';
import { SignInContacts } from '@/components/contacts/SignInContacts';
import { NotAvailable } from '@/components/NotAvailable';
import { fullDate } from '@/lib/format';
import { SiteShell } from '@/components/shell/SiteShell';
import { PrefsPanel } from './PrefsPanel';

function toValues(me: MeRow | null, p: NotificationPrefs | null): ProfileValues {
  return {
    firstName: me?.firstName ?? '',
    lastName: me?.lastName ?? '',
    whatsappEnabled: p?.whatsappEnabled ?? true,
    smsEnabled: p?.smsEnabled ?? true,
    pushEnabled: p?.pushEnabled ?? false,
    emailEnabled: p?.emailEnabled ?? false,
    inAppEnabled: p?.inAppEnabled ?? true,
    eventReminders: p?.eventReminders ?? true,
    eventUpdates: p?.eventUpdates ?? true,
    teamNotifications: p?.teamNotifications ?? false,
    marketingEmails: p?.marketingEmails ?? false,
    systemAnnouncements: p?.systemAnnouncements ?? true,
    reminderHoursBefore: String(p?.reminderHoursBefore ?? 24),
    quietHoursStart: p?.quietHoursStart ?? '',
    quietHoursEnd: p?.quietHoursEnd ?? '',
  };
}

const ORGANIZER_URL = process.env.NEXT_PUBLIC_ORGANIZER_URL ?? '';

/** Profile and settings: details, sign-in contacts, notification preferences, organizer link and account actions. */
export function ProfileClient() {
  const snack = useSnackbar();
  const auth = useBuyerAuth();
  const meQ = useMe();
  const prefQ = useNotificationPrefs();
  const [signOutOpen, setSignOutOpen] = useState(false);
  const [deleteOpen, setDeleteOpen] = useState(false);
  const askDeletion = async (on: boolean) => {
    setDeleteOpen(false);
    try {
      if (on) await meQ.requestDeletion();
      else await meQ.cancelDeletion();
      snack.show(on ? 'Deletion requested. You can change your mind until the date shown.' : 'Deletion request cancelled');
    } catch {
      snack.show({ message: 'We could not update your deletion request. Try again.', tone: 'error' });
    }
  };
  const form = useZodForm(profileSchema, { defaultValues: toValues(null, null) });
  const { isDirty, dirtyFields } = form.formState;

  // Load the saved values into the form once they arrive (and again after a save).
  useEffect(() => {
    if (meQ.me || prefQ.prefs) form.reset(toValues(meQ.me, prefQ.prefs));
    // reset when the server copies change, not on every form identity change
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [meQ.me, prefQ.prefs]);

  const save = async (v: ProfileValues) => {
    try {
      const d = dirtyFields;
      if (d.firstName || d.lastName) await meQ.save({ firstName: v.firstName, lastName: v.lastName });
      if (prefQ.prefs && Object.keys(d).some((k) => k !== 'firstName' && k !== 'lastName')) {
        // Ticket and payment messages are essential and always stay on.
        await prefQ.save({
          whatsappEnabled: v.whatsappEnabled, smsEnabled: v.smsEnabled, pushEnabled: v.pushEnabled, emailEnabled: v.emailEnabled, inAppEnabled: v.inAppEnabled,
          eventReminders: v.eventReminders, eventUpdates: v.eventUpdates, teamNotifications: v.teamNotifications, marketingEmails: v.marketingEmails,
          systemAnnouncements: v.systemAnnouncements, reminderHoursBefore: Number(v.reminderHoursBefore),
          quietHoursStart: v.quietHoursStart || null, quietHoursEnd: v.quietHoursEnd || null,
          ticketNotifications: true, paymentNotifications: true,
        });
      }
      snack.show('Your changes are saved');
    } catch {
      snack.show({ message: 'We could not save your changes. Try again.', tone: 'error' });
    }
  };
  const discard = () => {
    form.reset(toValues(meQ.me, prefQ.prefs));
    snack.show('Changes discarded');
  };

  const name = meQ.me?.displayName || meQ.me?.fullName || (auth.user ? `${auth.user.givenName} ${auth.user.familyName}`.trim() : '') || 'Your account';
  const scheduled = meQ.me?.deletionScheduledFor ?? null;
  const prefs = prefQ.prefs;

  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-narrow buyer-page">
        <SectionHeader level={1} eyebrow="Your account" title="Profile and settings" />
        <Form form={form} onSubmit={save}>
          <section className="m3-panel buyer-phead">
            <Avatar name={name} size="lg" />
            <div>
              <h3 className="m3-card__title">{name}</h3>
              <div className="m3-muted">
                Signed in with a verified contact <StatusPill tone="success">Verified</StatusPill>
              </div>
            </div>
          </section>

          <section className="m3-panel m3-stack" aria-labelledby="pd-title">
            <h3 className="m3-card__title" id="pd-title">Personal details</h3>
            {meQ.loading && !meQ.me ? (
              <div aria-busy="true" aria-label="Loading your details"><Skeleton width="60%" /><Skeleton width="80%" /></div>
            ) : meQ.error && !meQ.me ? (
              <ErrorState error={meQ.error as unknown as GraphQLLikeError} onRetry={() => void meQ.refetch()} />
            ) : (
              <div className="buyer-g2">
                <TextFieldRHF name="firstName" label="First name" autoComplete="given-name" />
                <TextFieldRHF name="lastName" label="Last name" autoComplete="family-name" />
              </div>
            )}
            <p className="m3-muted">Your phone number and email are sign-in contacts. Manage them below; they are verified by code.</p>
          </section>

          <SignInContacts />

          {prefQ.loading && !prefs ? (
            <section className="m3-panel" aria-busy="true" aria-label="Loading your preferences">
              <Skeleton width="50%" />
              <Skeleton width="70%" />
            </section>
          ) : prefQ.error && !prefs ? (
            <section className="m3-panel">
              <ErrorState error={prefQ.error as unknown as GraphQLLikeError} onRetry={() => void prefQ.refetch()} />
            </section>
          ) : prefs ? (
            <PrefsPanel timezone={prefs.timezone ?? 'Africa/Lusaka'} />
          ) : (
            <section className="m3-panel m3-stack">
              <h3 className="m3-card__title">Notification preferences</h3>
              <NotAvailable what="Your preferences will appear after your first notification." />
            </section>
          )}

          {ORGANIZER_URL ? (
            <section className="m3-panel buyer-org2">
              <div>
                <h3 className="m3-card__title">Become an organizer</h3>
                <p className="m3-muted">Want to sell tickets for your own events? Apply in the organizer portal, where onboarding takes you through your organization details and documents.</p>
              </div>
              <a className="m3-btn m3-state" data-variant="filled" href={ORGANIZER_URL}>
                Become an organizer
              </a>
            </section>
          ) : null}

          <section className="m3-panel m3-stack" aria-labelledby="acct-title">
            <h3 className="m3-card__title" id="acct-title">Account</h3>
            <div className="m3-setting">
              <div className="m3-setting__text">
                <b>Sign out</b>
                <span className="m3-muted">Sign out of Showstop on this device.</span>
              </div>
              <Button size="sm" onClick={() => setSignOutOpen(true)}>Sign out</Button>
            </div>
            <div className="m3-setting">
              <div className="m3-setting__text">
                <b>Delete my account</b>
                <span className="m3-muted">Your account stays recoverable for 30 days, then your details are anonymised.</span>
                {scheduled ? (
                  <span>
                    <StatusPill tone="warning">Pending deletion</StatusPill> <span className="m3-muted">Scheduled for {fullDate(scheduled)}. You can cancel until then.</span>
                  </span>
                ) : null}
              </div>
              {scheduled ? (
                <Button size="sm" loading={meQ.deleting} onClick={() => void askDeletion(false)}>Cancel deletion request</Button>
              ) : (
                <Button size="sm" danger onClick={() => setDeleteOpen(true)}>Request account deletion</Button>
              )}
            </div>
          </section>
        </Form>
      </div>
      <SaveBar hidden={!isDirty} message="Unsaved changes. Save to keep them, or discard." onSave={() => void form.handleSubmit(save)()} onDiscard={discard} saving={meQ.saving || prefQ.saving} />
      <ConfirmDialog
        open={deleteOpen}
        onClose={() => setDeleteOpen(false)}
        onConfirm={() => void askDeletion(true)}
        title="Request account deletion?"
        description="Your account will be scheduled for deletion in 30 days. You can cancel the request at any time before then. After that your personal details are anonymised. Use or transfer any tickets you still hold before the deletion date."
        confirmLabel="Request deletion"
        cancelLabel="Go back"
        danger
        loading={meQ.deleting}
      />
      <ConfirmDialog
        open={signOutOpen}
        onClose={() => setSignOutOpen(false)}
        onConfirm={() => void auth.logout()}
        title="Sign out?"
        description="You can keep browsing events without an account. Sign in again to see your tickets."
        confirmLabel="Sign out"
        cancelLabel="Go back"
      />
    </SiteShell>
  );
}
