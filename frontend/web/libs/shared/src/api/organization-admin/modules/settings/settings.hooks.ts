'use client';

/**
 * React hooks for the signed-in user's own settings.
 *
 * Types come from codegen — never hand-defined.
 */

import { useQuery, useMutation } from '@apollo/client/react';
import type { FetchPolicy } from '@apollo/client';
import {
  MY_NOTIFICATION_PREFERENCES,
  UPDATE_NOTIFICATION_PREFERENCES,
  UPDATE_PROFILE,
} from './settings.queries';
import type { NotificationPreferences } from '../../../../types/graphql';

/**
 * The caller's notification preferences.
 *
 * `preferences` is null until loaded. The settings screen must not render its
 * toggles from hardcoded defaults in that window — showing "marketing emails:
 * off" before the real value arrives invites the user to save a setting they
 * never chose.
 */
export function useMyNotificationPreferences(options?: {
  fetchPolicy?: FetchPolicy;
  skip?: boolean;
}) {
  const { data, loading, error, refetch } = useQuery<{
    myNotificationPreferences: NotificationPreferences | null;
  }>(MY_NOTIFICATION_PREFERENCES, {
    fetchPolicy: options?.fetchPolicy ?? 'cache-and-network',
    errorPolicy: 'all',
    skip: options?.skip ?? false,
  });

  return {
    preferences: data?.myNotificationPreferences ?? null,
    loading,
    error,
    refetch,
  };
}

/** Only the preference keys this app actually exposes a control for. */
export interface NotificationPreferencesPatch {
  emailEnabled?: boolean;
  smsEnabled?: boolean;
  whatsappEnabled?: boolean;
  pushEnabled?: boolean;
  inAppEnabled?: boolean;
  ticketNotifications?: boolean;
  eventReminders?: boolean;
  eventUpdates?: boolean;
  paymentNotifications?: boolean;
  teamNotifications?: boolean;
  marketingEmails?: boolean;
  systemAnnouncements?: boolean;
}

/**
 * Update notification preferences.
 *
 * Takes a PATCH, not the whole object. Every input field is optional
 * server-side, so sending only what changed means a preference this build does
 * not know about cannot be reset by saving this form.
 */
export function useUpdateNotificationPreferences() {
  const [mutate, { loading }] = useMutation<{
    updateNotificationPreferences: NotificationPreferences | null;
  }>(UPDATE_NOTIFICATION_PREFERENCES);

  const updatePreferences = async (patch: NotificationPreferencesPatch) => {
    try {
      const result = await mutate({ variables: { input: patch } });
      return { success: !!result.data?.updateNotificationPreferences, error: null as string | null };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  };

  return { updatePreferences, loading };
}

/**
 * Fields this app will write to a user profile.
 *
 * The server types `updateProfile`'s input as `JSON`, so nothing at the schema
 * level constrains what can be sent. This interface is that constraint — widen
 * it here deliberately rather than passing an arbitrary object at a call site.
 *
 * Note `jobTitle` is absent: `User` has no such field, so collecting it would
 * discard it on save.
 */
export interface ProfilePatch {
  firstName?: string;
  lastName?: string;
  phoneNumber?: string;
  gender?: string;
}

export function useUpdateProfile() {
  const [mutate, { loading }] = useMutation<{
    updateProfile: { id: string; firstName: string; lastName: string; fullName: string } | null;
  }>(UPDATE_PROFILE);

  const updateProfile = async (patch: ProfilePatch) => {
    try {
      const result = await mutate({ variables: { input: patch } });
      return { success: !!result.data?.updateProfile, error: null as string | null };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  };

  return { updateProfile, loading };
}
