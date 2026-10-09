'use client';

import { useEffect, useRef, useState } from 'react';
import { Button, EmptyState, ErrorState, Icon, SectionHeader, Skeleton, useSnackbar } from '@pml.tickets/shared/components/m3';
import type { GraphQLLikeError } from '@pml.tickets/shared';
import { useMyNotifications, useNotificationActions, useUnreadCount, type NotificationRow } from '@pml.tickets/shared';
import { ago } from '@/lib/format';
import { LinkBtn } from '@/components/LinkBtn';
import { SiteShell } from '@/components/shell/SiteShell';
import { CATEGORY_ICON, categoryOf } from './category';

const PAGE = 5;
const UNDO_MS = 6000;

/** Inbox: unread dot, mark as read, delete with undo, mark all as read, load more. */
export function NotificationsClient() {
  const snack = useSnackbar();
  const { notes, hasNext, loading, error, refetch, loadMore } = useMyNotifications(PAGE);
  const unread = useUnreadCount(true);
  const actions = useNotificationActions();
  const [hidden, setHidden] = useState<Set<string>>(new Set());
  const timers = useRef(new Map<string, ReturnType<typeof setTimeout>>());

  useEffect(() => {
    const map = timers.current;
    return () => map.forEach(clearTimeout);
  }, []);

  const remove = (n: NotificationRow) => {
    setHidden((h) => new Set(h).add(n.id));
    // The delete is only sent once the undo window has passed.
    timers.current.set(
      n.id,
      setTimeout(() => {
        timers.current.delete(n.id);
        void actions.remove(n.id);
      }, UNDO_MS)
    );
    snack.show({
      message: 'Notification deleted',
      actionLabel: 'Undo',
      duration: UNDO_MS,
      onAction: () => {
        clearTimeout(timers.current.get(n.id));
        timers.current.delete(n.id);
        setHidden((h) => {
          const next = new Set(h);
          next.delete(n.id);
          return next;
        });
      },
    });
  };

  const markAll = async () => {
    try {
      await actions.markAllRead();
      snack.show('All notifications marked as read');
    } catch {
      snack.show({ message: 'We could not mark them as read. Try again.', tone: 'error' });
    }
  };

  const list = notes.filter((n) => !hidden.has(n.id));
  const first = loading && notes.length === 0;
  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-narrow buyer-page">
        <SectionHeader
          level={1}
          eyebrow="Inbox"
          title="Notifications"
          description={<span aria-live="polite">{unread ? `${unread} unread` : "You're all caught up"}</span>}
          actions={
            <>
              <Button size="sm" disabled={!unread} loading={actions.markingAll} onClick={() => void markAll()}>
                Mark all as read
              </Button>
              <LinkBtn size="sm" href="/profile">
                Settings
              </LinkBtn>
            </>
          }
        />
        {first ? (
          <div aria-busy="true" aria-label="Loading notifications" className="m3-stack">
            <Skeleton shape="block" width="100%" />
            <Skeleton shape="block" width="100%" />
          </div>
        ) : error && !notes.length ? (
          <ErrorState error={error as unknown as GraphQLLikeError} onRetry={() => void refetch()} />
        ) : list.length === 0 ? (
          <EmptyState icon="bell" title="No notifications" description="New booking, payment and reminder messages appear here." />
        ) : (
          <ul className="buyer-nlist">
            {list.map((n) => {
              const cat = categoryOf(n.type);
              const isUnread = !n.readAt;
              return (
                <li key={n.id} className="buyer-nit" data-unread={isUnread ? 'true' : undefined}>
                  <span className="buyer-nic">
                    <Icon name={CATEGORY_ICON[cat]} />
                  </span>
                  <div className="buyer-nb">
                    <div className="buyer-nt">
                      <b>{n.title}</b>
                      <span className="m3-muted">{ago(n.createdAt)}</span>
                    </div>
                    <p>{n.body}</p>
                    <div className="buyer-na2">
                      <span className="buyer-pill">{cat}</span>
                      {isUnread ? (
                        <Button variant="link" onClick={() => void actions.markRead(n.id)}>
                          Mark as read
                        </Button>
                      ) : null}
                      <Button variant="link" danger aria-label={`Delete notification: ${n.title}`} onClick={() => remove(n)}>
                        Delete
                      </Button>
                    </div>
                  </div>
                  {isUnread ? <i className="buyer-udot" role="img" aria-label="Unread" /> : null}
                </li>
              );
            })}
          </ul>
        )}
        {hasNext ? (
          <div className="buyer-more">
            <span className="m3-muted">Showing {list.length}</span>
            <Button loading={loading} onClick={() => void loadMore()}>
              Load more
            </Button>
          </div>
        ) : null}
      </div>
    </SiteShell>
  );
}
