'use client';

/**
 * Notifications for the signed-in organizer (identity-service).
 * Operations are defined here because the shared organization-admin module has
 * no notifications hook yet; types are declared against the schema.
 */
import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';

export interface NotificationRow {
  id: string;
  type: string;
  title: string;
  body: string;
  actionUrl: string | null;
  status: string;
  readAt: string | null;
  createdAt: string;
}

interface NotificationsData {
  myNotifications: { edges: Array<{ cursor: string; node: NotificationRow }>; totalCount: number | null };
  unreadNotificationCount: number;
}

export const MY_NOTIFICATIONS = gql`
  query OrganizerNotifications($first: Int) {
    myNotifications(pagination: { first: $first }) {
      totalCount
      edges {
        cursor
        node {
          id
          type
          title
          body
          actionUrl
          status
          readAt
          createdAt
        }
      }
    }
    unreadNotificationCount
  }
`;

export const MARK_ALL_NOTIFICATIONS_READ = gql`
  mutation OrganizerMarkAllNotificationsRead {
    markAllNotificationsRead
  }
`;

export const MARK_NOTIFICATION_READ = gql`
  mutation OrganizerMarkNotificationRead($notificationId: ID!) {
    markNotificationRead(notificationId: $notificationId) {
      id
      readAt
      status
    }
  }
`;

export function useMyNotifications(first = 20) {
  const { data: raw, dataState, loading, error, refetch } = useQuery<NotificationsData>(MY_NOTIFICATIONS, {
    variables: { first },
    fetchPolicy: 'cache-and-network',
    errorPolicy: 'all',
  });
  const data = dataState === 'complete' ? raw : undefined;
  return {
    notifications: data?.myNotifications?.edges.map((e) => e.node) ?? [],
    unread: data?.unreadNotificationCount ?? 0,
    loading,
    error,
    refetch,
  };
}

export function useMarkAllNotificationsRead() {
  const [mutate, state] = useMutation(MARK_ALL_NOTIFICATIONS_READ, { refetchQueries: [MY_NOTIFICATIONS] });
  return { markAll: () => mutate(), ...state };
}

export function useMarkNotificationRead() {
  const [mutate, state] = useMutation(MARK_NOTIFICATION_READ, { refetchQueries: [MY_NOTIFICATIONS] });
  return { markRead: (notificationId: string) => mutate({ variables: { notificationId } }), ...state };
}
