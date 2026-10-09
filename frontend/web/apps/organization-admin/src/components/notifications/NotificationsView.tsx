'use client';

import Link from 'next/link';
import { useState } from 'react';
import { Button, Card, CardHeader, EmptyState, ErrorState, PageHeader, SegmentedButton, Skeleton, StatusPill } from '@pml.tickets/shared/components/m3';
import { formatRelativeTime } from '@/lib/format/figure';
import { statusLabel } from '@/components/console/Status';
import type { NotificationRow } from '@/lib/api/notifications';

export interface NotificationsViewProps {
  notifications: NotificationRow[];
  unread: number;
  loading: boolean;
  error?: { message?: string } | null;
  onRetry?: () => void;
  onMarkRead: (id: string) => void;
  onMarkAllRead: () => void;
}

export function NotificationsView({ notifications, unread, loading, error, onRetry, onMarkRead, onMarkAllRead }: NotificationsViewProps) {
  const [filter, setFilter] = useState<'all' | 'unread'>('all');
  const rows = filter === 'unread' ? notifications.filter((n) => !n.readAt) : notifications;
  return (
    <div data-testid="notifications-page">
      <PageHeader
        title="Notifications"
        subtitle={`${unread} unread`}
        actions={
          unread ? (
            <Button variant="tonal" onClick={onMarkAllRead}>
              Mark all read
            </Button>
          ) : null
        }
      />
      <Card>
        <CardHeader
          title="Your notifications"
          actions={
            <SegmentedButton
              label="Show"
              value={filter}
              onChange={setFilter}
              options={[
                { value: 'all', label: 'All' },
                { value: 'unread', label: `Unread (${unread})` },
              ]}
            />
          }
        />
        {error && !notifications.length ? (
          <ErrorState error={error} onRetry={onRetry} />
        ) : loading && !notifications.length ? (
          <div className="m3-stack" role="status" aria-label="Loading" data-testid="loading">
            <Skeleton />
            <Skeleton />
            <Skeleton />
          </div>
        ) : rows.length === 0 ? (
          <EmptyState icon="bell" title={filter === 'unread' ? 'No unread notifications' : 'You are all caught up.'} />
        ) : (
          <ul className="m3-list">
            {rows.map((n) => (
              <li key={n.id} className="m3-list__item">
                <div className="m3-list__main">
                  <b>
                    {n.title} {!n.readAt ? <StatusPill tone="warning">New</StatusPill> : null}
                  </b>
                  {n.body ? <span className="m3-list__support">{n.body}</span> : null}
                  <span className="m3-list__support">
                    {statusLabel(n.type)} · {formatRelativeTime(n.createdAt)}
                  </span>
                </div>
                {n.actionUrl && n.actionUrl.startsWith('/') ? (
                  <Link className="m3-link" href={n.actionUrl}>
                    Open
                  </Link>
                ) : null}
                {!n.readAt ? (
                  <Button size="sm" variant="text" onClick={() => onMarkRead(n.id)} aria-label={`Mark ${n.title} as read`}>
                    Mark read
                  </Button>
                ) : null}
              </li>
            ))}
          </ul>
        )}
      </Card>
    </div>
  );
}
