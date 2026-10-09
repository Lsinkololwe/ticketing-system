'use client';

/**
 * Settings operations for the organizer console (identity-service).
 * The shared settings module lacks organization settings, slug check, the full
 * notification preference input and the signed-in user query, so they live here.
 */
import type { UpdateOrganizationInput, SettingsIsSlugAvailableQuery, SettingsIsSlugAvailableQueryVariables, SettingsMeQuery, SettingsMeQueryVariables, SettingsNotificationPrefsQuery, SettingsNotificationPrefsQueryVariables, SettingsOrganizationQuery, SettingsOrganizationQueryVariables } from '@pml.tickets/shared/types/graphql';
import { gql } from '@apollo/client';
import { useLazyQuery, useMutation, useQuery } from '@apollo/client/react';

export interface OrgSettingsFlags {
  requireEventApproval: boolean;
  allowMembersToInvite: boolean;
  inviteRequiresApproval: boolean;
  managersCanViewFinancials: boolean;
  adminsCanRequestPayouts: boolean;
  notifyOwnerOnMemberJoin: boolean;
  notifyOwnerOnEventCreated: boolean;
  notifyOwnerOnPayoutRequest: boolean;
}

export interface SettingsOrganization {
  id: string;
  name: string;
  slug: string;
  tagline: string | null;
  description: string | null;
  logoUrl: string | null;
  bannerUrl: string | null;
  website: string | null;
  socialLinks: { facebook: string | null; instagram: string | null; twitter: string | null; linkedin: string | null; youtube: string | null; tiktok: string | null } | null;
  businessType: string | null;
  taxId: string | null;
  businessRegistrationNumber: string | null;
  businessPhone: string | null;
  businessEmail: string | null;
  businessAddress: { addressLine1: string | null; addressLine2: string | null; city: string | null; province: string | null; country: string | null; postalCode: string | null } | null;
  status: string;
  commissionRate: number | null;
  deletionRequestedAt: string | null;
  deletionScheduledFor: string | null;
  settings: (OrgSettingsFlags & { id: string }) | null;
}

export const SETTINGS_ORGANIZATION = gql`
  query SettingsOrganization {
    myOrganization {
      id name slug tagline description logoUrl bannerUrl website
      socialLinks { facebook instagram twitter linkedin youtube tiktok }
      businessType taxId businessRegistrationNumber yearEstablished businessPhone businessEmail
      businessAddress { addressLine1 addressLine2 city province country postalCode }
      status commissionRate deletionRequestedAt deletionScheduledFor
      settings {
        id requireEventApproval allowMembersToInvite inviteRequiresApproval
        managersCanViewFinancials adminsCanRequestPayouts
        notifyOwnerOnMemberJoin notifyOwnerOnEventCreated notifyOwnerOnPayoutRequest
      }
    }
  }
`;

export const UPDATE_ORGANIZATION = gql`
  mutation SettingsUpdateOrganization($id: ID!, $input: UpdateOrganizationInput!) {
    updateOrganization(id: $id, input: $input) { id name description logoUrl bannerUrl tagline website }
  }
`;

export const REQUEST_ORGANIZATION_DELETION = gql`
  mutation SettingsRequestOrganizationDeletion($organizationId: ID!, $reason: String) {
    requestOrganizationDeletion(organizationId: $organizationId, reason: $reason) { id status deletionRequestedAt deletionScheduledFor }
  }
`;

export const CANCEL_ORGANIZATION_DELETION = gql`
  mutation SettingsCancelOrganizationDeletion($organizationId: ID!) {
    cancelOrganizationDeletion(organizationId: $organizationId) { id status deletionRequestedAt deletionScheduledFor }
  }
`;

export const UPDATE_ORGANIZATION_SETTINGS = gql`
  mutation SettingsUpdateOrganizationSettings($id: ID!, $input: UpdateOrganizationSettingsInput!) {
    updateOrganizationSettings(id: $id, input: $input) {
      id
      settings { id requireEventApproval allowMembersToInvite inviteRequiresApproval managersCanViewFinancials adminsCanRequestPayouts notifyOwnerOnMemberJoin notifyOwnerOnEventCreated notifyOwnerOnPayoutRequest }
    }
  }
`;

export const IS_SLUG_AVAILABLE = gql`
  query SettingsIsSlugAvailable($slug: String!) { isSlugAvailable(slug: $slug) }
`;

export const SETTINGS_ME = gql`
  query SettingsMe { me { id firstName lastName fullName email phoneNumber } }
`;

export const SETTINGS_NOTIFICATION_PREFS = gql`
  query SettingsNotificationPrefs {
    myNotificationPreferences {
      id emailEnabled smsEnabled whatsappEnabled pushEnabled inAppEnabled
      ticketNotifications eventReminders eventUpdates paymentNotifications teamNotifications marketingEmails systemAnnouncements
      reminderHoursBefore quietHoursStart quietHoursEnd timezone
    }
  }
`;

export const SETTINGS_UPDATE_NOTIFICATION_PREFS = gql`
  mutation SettingsUpdateNotificationPrefs($input: UpdateNotificationPreferencesInput!) {
    updateNotificationPreferences(input: $input) { id }
  }
`;

export const SETTINGS_UPDATE_PROFILE = gql`
  mutation SettingsUpdateProfile($input: UpdateUserInput!) { updateMyProfile(input: $input) { id firstName lastName fullName } }
`;

export interface NotificationPrefs {
  emailEnabled: boolean; smsEnabled: boolean; whatsappEnabled: boolean; pushEnabled: boolean; inAppEnabled: boolean;
  ticketNotifications: boolean; eventReminders: boolean; eventUpdates: boolean; paymentNotifications: boolean;
  teamNotifications: boolean; marketingEmails: boolean; systemAnnouncements: boolean;
  reminderHoursBefore: number; quietHoursStart: string | null; quietHoursEnd: string | null; timezone: string | null;
}
export interface SettingsMe { id: string; firstName: string | null; lastName: string | null; fullName: string; email: string | null; phoneNumber: string | null }

export function useSettingsOrganization() {
  const { data: raw, dataState, loading, error, refetch } = useQuery<SettingsOrganizationQuery, SettingsOrganizationQueryVariables>(SETTINGS_ORGANIZATION, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return { organization: data?.myOrganization ?? null, loading, error, refetch };
}

export function useSaveOrganization() {
  const [profile] = useMutation(UPDATE_ORGANIZATION);
  const [flags] = useMutation(UPDATE_ORGANIZATION_SETTINGS);
  return {
    saveProfile: (id: string, input: UpdateOrganizationInput) => profile({ variables: { id, input } }),
    saveFlags: (id: string, input: Partial<OrgSettingsFlags>) => flags({ variables: { id, input } }),
  };
}

export function useOrganizationDeletion() {
  const [request, r] = useMutation(REQUEST_ORGANIZATION_DELETION, { refetchQueries: [SETTINGS_ORGANIZATION, 'OrganizerContext'] });
  const [cancel, c] = useMutation(CANCEL_ORGANIZATION_DELETION, { refetchQueries: [SETTINGS_ORGANIZATION, 'OrganizerContext'] });
  return {
    requestDeletion: (organizationId: string, reason?: string) => request({ variables: { organizationId, reason: reason ?? null } }),
    cancelDeletion: (organizationId: string) => cancel({ variables: { organizationId } }),
    loading: r.loading || c.loading,
  };
}

export function useSlugCheck() {
  const [run, { data, loading }] = useLazyQuery<SettingsIsSlugAvailableQuery, SettingsIsSlugAvailableQueryVariables>(IS_SLUG_AVAILABLE, { fetchPolicy: 'network-only' });
  return { check: (slug: string) => run({ variables: { slug } }), available: data?.isSlugAvailable ?? null, checking: loading };
}

export function useSettingsMe() {
  const { data: raw, dataState, loading, error, refetch } = useQuery<SettingsMeQuery, SettingsMeQueryVariables>(SETTINGS_ME, { fetchPolicy: 'cache-and-network', errorPolicy: 'all' });
  const data = dataState === 'complete' ? raw : undefined;
  const [save] = useMutation(SETTINGS_UPDATE_PROFILE, { refetchQueries: [SETTINGS_ME] });
  return { me: data?.me ?? null, loading, error, refetch, saveName: (firstName: string, lastName: string) => save({ variables: { input: { firstName, lastName } } }) };
}

export function useSettingsNotificationPrefs() {
  const { data: raw, dataState, loading, error, refetch } = useQuery<SettingsNotificationPrefsQuery, SettingsNotificationPrefsQueryVariables>(SETTINGS_NOTIFICATION_PREFS, {
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  const [save] = useMutation(SETTINGS_UPDATE_NOTIFICATION_PREFS, { refetchQueries: [SETTINGS_NOTIFICATION_PREFS] });
  return { prefs: data?.myNotificationPreferences ?? null, loading, error, refetch, savePrefs: (input: Partial<NotificationPrefs>) => save({ variables: { input } }) };
}
