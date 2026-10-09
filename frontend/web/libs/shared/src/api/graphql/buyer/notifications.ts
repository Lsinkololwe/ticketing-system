'use client';

import { gql } from '@apollo/client';
import { useMutation, useQuery } from '@apollo/client/react';
import type { UnreadNotificationCountQuery, MyNotificationsQuery, MyNotificationsQueryVariables, BuyerMyNotificationPreferencesQuery, BuyerMarkAllNotificationsReadMutation } from '../../../types/graphql';

export type NotificationType = string;
export interface NotificationRow {
  id: string;
  type: NotificationType;
  title: string;
  body: string;
  actionUrl: string | null;
  status: string;
  readAt: string | null;
  createdAt: string;
}

export interface NotificationPrefs {
  emailEnabled: boolean;
  smsEnabled: boolean;
  whatsappEnabled: boolean;
  pushEnabled: boolean;
  inAppEnabled: boolean;
  ticketNotifications: boolean;
  eventReminders: boolean;
  eventUpdates: boolean;
  paymentNotifications: boolean;
  teamNotifications: boolean;
  marketingEmails: boolean;
  systemAnnouncements: boolean;
  reminderHoursBefore: number;
  quietHoursStart: string | null;
  quietHoursEnd: string | null;
  timezone: string | null;
}

const UNREAD = gql`
  query UnreadNotificationCount {
    unreadNotificationCount
  }
`;
const LIST = gql`
  query MyNotifications($pagination: CursorPaginationInput) {
    myNotifications(pagination: $pagination) {
      edges {
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
      pageInfo {
        hasNext
        endCursor
      }
      totalCount
    }
  }
`;
const MARK_READ = gql`
  mutation MarkNotificationRead($notificationId: ID!) {
    markNotificationRead(notificationId: $notificationId) {
      id
      readAt
      status
    }
  }
`;
const MARK_ALL = gql`
  mutation BuyerMarkAllNotificationsRead {
    markAllNotificationsRead
  }
`;
const DELETE = gql`
  mutation DeleteNotification($notificationId: ID!) {
    deleteNotification(notificationId: $notificationId)
  }
`;
const PREFS = gql`
  query BuyerMyNotificationPreferences {
    myNotificationPreferences {
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
      reminderHoursBefore
      quietHoursStart
      quietHoursEnd
      timezone
    }
  }
`;
const UPDATE_PREFS = gql`
  mutation BuyerUpdateNotificationPreferences($input: UpdateNotificationPreferencesInput!) {
    updateNotificationPreferences(input: $input) {
      reminderHoursBefore
    }
  }
`;

/** Unread badge count. Skipped for anonymous visitors. */
export function useUnreadCount(enabled: boolean) {
  const { data } = useQuery<UnreadNotificationCountQuery>(UNREAD, { skip: !enabled, pollInterval: 60_000 });
  return data?.unreadNotificationCount ?? 0;
}

export function useMyNotifications(size: number) {
  const { data, loading, error, fetchMore, refetch } = useQuery<MyNotificationsQuery, MyNotificationsQueryVariables>(LIST, { variables: { pagination: { first: size, after: null, last: null, before: null } }, fetchPolicy: 'cache-and-network' });
  const conn = data?.myNotifications;
  return {
    notes: (conn?.edges ?? []).map((e) => e.node),
    totalCount: conn?.totalCount ?? 0,
    hasNext: conn?.pageInfo.hasNext ?? false,
    loading,
    error,
    refetch,
    loadMore: () =>
      fetchMore({
        variables: { pagination: { first: size, after: conn?.pageInfo.endCursor ?? null, last: null, before: null } },
        updateQuery: (prev, { fetchMoreResult }) =>
          fetchMoreResult
            ? {
                ...fetchMoreResult,
                myNotifications: {
                  ...fetchMoreResult.myNotifications,
                  edges: [...prev.myNotifications.edges, ...fetchMoreResult.myNotifications.edges],
                },
              }
            : prev,
      }),
  };
}

export function useNotificationActions() {
  const [markRead] = useMutation(MARK_READ, { refetchQueries: [UNREAD, LIST] });
  const [del] = useMutation(DELETE, { refetchQueries: [UNREAD, LIST] });
  const [markAll, { loading: markingAll }] = useMutation<BuyerMarkAllNotificationsReadMutation>(MARK_ALL, { refetchQueries: [UNREAD, LIST] });
  return {
    markingAll,
    /** Marks every unread notification read; resolves with how many changed. */
    markAllRead: async () => (await markAll()).data?.markAllNotificationsRead ?? 0,
    markRead: (notificationId: string) => markRead({ variables: { notificationId } }),
    remove: (notificationId: string) => del({ variables: { notificationId } }),
  };
}

export function useNotificationPrefs() {
  const { data, loading, error, refetch } = useQuery<BuyerMyNotificationPreferencesQuery>(PREFS, {
    fetchPolicy: 'cache-and-network',
  });
  const [update, { loading: saving, error: saveError }] = useMutation(UPDATE_PREFS, { refetchQueries: [PREFS] });
  return {
    prefs: data?.myNotificationPreferences ?? null,
    loading,
    error,
    refetch,
    saving,
    saveError,
    save: (input: Partial<NotificationPrefs>) => update({ variables: { input } }),
  };
}
