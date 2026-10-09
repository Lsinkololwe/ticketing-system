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
import type {
  UpdateUserInput,
  MyNotificationPreferencesQuery,
  MyNotificationPreferencesQueryVariables,
  UpdateNotificationPreferencesMutation,
  UpdateNotificationPreferencesMutationVariables,
  UpdateMyProfileMutation,
  UpdateMyProfileMutationVariables,
} from '../../../../types/graphql';

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
  const { data, loading, error, refetch } = useQuery<
    MyNotificationPreferencesQuery,
    MyNotificationPreferencesQueryVariables
  >(MY_NOTIFICATION_PREFERENCES, {
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
  const [mutate, { loading }] = useMutation<
    UpdateNotificationPreferencesMutation,
    UpdateNotificationPreferencesMutationVariables
  >(UPDATE_NOTIFICATION_PREFERENCES);

  const updatePreferences = async (patch: NotificationPreferencesPatch) => {
    try {
      const result = await mutate({
        variables: {
          input: {
            emailEnabled: patch.emailEnabled ?? null,
            smsEnabled: patch.smsEnabled ?? null,
            whatsappEnabled: patch.whatsappEnabled ?? null,
            pushEnabled: patch.pushEnabled ?? null,
            inAppEnabled: patch.inAppEnabled ?? null,
            ticketNotifications: patch.ticketNotifications ?? null,
            eventReminders: patch.eventReminders ?? null,
            eventUpdates: patch.eventUpdates ?? null,
            paymentNotifications: patch.paymentNotifications ?? null,
            teamNotifications: patch.teamNotifications ?? null,
            marketingEmails: patch.marketingEmails ?? null,
            systemAnnouncements: patch.systemAnnouncements ?? null,
            // This app exposes no controls for quiet hours, reminder lead time
            // or timezone, so those keys are always sent as "leave unchanged".
            quietHoursEnd: null,
            quietHoursStart: null,
            reminderHoursBefore: null,
            timezone: null,
          },
        },
      });
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
 * Fields this app writes to a user profile — the subset of `UpdateUserInput`
 * this screen collects. `jobTitle` is deliberately absent: `User` has no such
 * field, so collecting it would discard it on save.
 */
export type ProfilePatch = Partial<Pick<UpdateUserInput, 'firstName' | 'lastName' | 'gender'>>;

export function useUpdateProfile() {
  const [mutate, { loading }] = useMutation<UpdateMyProfileMutation, UpdateMyProfileMutationVariables>(
    UPDATE_PROFILE
  );

  const updateProfile = async (patch: ProfilePatch) => {
    try {
      const result = await mutate({
        variables: {
          input: {
            firstName: patch.firstName ?? null,
            lastName: patch.lastName ?? null,
            gender: patch.gender ?? null,
          },
        },
      });
      return { success: !!result.data?.updateMyProfile, error: null as string | null };
    } catch (error) {
      return {
        success: false,
        error: error instanceof Error ? error.message : String(error),
      };
    }
  };

  return { updateProfile, loading };
}
