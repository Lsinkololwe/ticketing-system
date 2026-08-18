/**
 * User Settings GraphQL Operations (Organization Admin App)
 *
 * The signed-in user's own profile and notification preferences. Both resolved
 * by identity-service, both scoped to the caller's JWT — neither takes a user
 * id, so one user cannot read or write another's settings.
 *
 * @see backend/identity-service/src/main/resources/graphql/schema.graphqls
 */

import { gql } from '@apollo/client';

/** The caller's notification preferences. */
export const MY_NOTIFICATION_PREFERENCES = gql`
  query MyNotificationPreferences {
    myNotificationPreferences {
      id
      emailEnabled
      smsEnabled
      whatsappEnabled
      pushEnabled
      inAppEnabled
      ticketNotifications
      eventReminders
      eventUpdates
      paymentNotifications
      teamNotifications
      marketingEmails
      systemAnnouncements
    }
  }
`;

/**
 * Update notification preferences.
 *
 * Every field on the input is optional, so this sends a partial update rather
 * than the whole object — a client that round-trips the full set will silently
 * revert any preference added to the schema since its bundle was built.
 */
export const UPDATE_NOTIFICATION_PREFERENCES = gql`
  mutation UpdateNotificationPreferences($input: UpdateNotificationPreferencesInput!) {
    updateNotificationPreferences(input: $input) {
      id
      emailEnabled
      smsEnabled
      whatsappEnabled
      pushEnabled
      inAppEnabled
      ticketNotifications
      eventReminders
      eventUpdates
      paymentNotifications
      teamNotifications
      marketingEmails
      systemAnnouncements
    }
  }
`;

/**
 * Update the caller's own profile.
 *
 * The server types this input as `JSON`, so there is no schema-level guarantee
 * about its shape — the typed wrapper in `settings.hooks.ts` is the only thing
 * constraining what this app sends. Widen it there, not at the call site.
 */
export const UPDATE_PROFILE = gql`
  mutation UpdateProfile($input: JSON!) {
    updateProfile(input: $input) {
      id
      firstName
      lastName
      fullName
    }
  }
`;
