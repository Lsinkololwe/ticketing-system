'use client';

import { NotificationsView } from '@/components/notifications/NotificationsView';
import { useMarkAllNotificationsRead, useMarkNotificationRead, useMyNotifications } from '@/lib/api/notifications';

export default function NotificationsPage() {
  const { notifications, unread, loading, error, refetch } = useMyNotifications(50);
  const { markAll } = useMarkAllNotificationsRead();
  const { markRead } = useMarkNotificationRead();
  return (
    <NotificationsView
      notifications={notifications}
      unread={unread}
      loading={loading}
      error={error}
      onRetry={() => void refetch()}
      onMarkRead={(id) => void markRead(id)}
      onMarkAllRead={() => void markAll()}
    />
  );
}
