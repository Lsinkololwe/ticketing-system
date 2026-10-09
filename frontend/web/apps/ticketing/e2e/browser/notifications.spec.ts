import { harnessTest as test, expect, captureConsole, gqlErrors } from '../../../../e2e-harness/browser/playwright';
import { NOTIFICATIONS, notifications, signedInBase } from './fixtures';
import { settle, shot } from './_kit';

test.describe('notifications', () => {
  test('populated: unread count, mark one read, mark all read, settings link', async ({ page, upstream, signInAs }, info) => {
    let rows: unknown[] = NOTIFICATIONS;
    upstream.gql({
      ...signedInBase(),
      MyNotifications: () => notifications(rows).MyNotifications,
      UnreadNotificationCount: () => notifications(rows).UnreadNotificationCount,
      MarkNotificationRead: { markNotificationRead: { __typename: 'Notification', id: 'n1', readAt: new Date().toISOString(), status: 'READ' } },
      BuyerMarkAllNotificationsRead: () => {
        rows = NOTIFICATIONS.map((n) => ({ ...n, readAt: new Date().toISOString(), status: 'READ' }));
        return { markAllNotificationsRead: 1 };
      },
    });
    await signInAs({ roles: ['CUSTOMER'] });
    const log = captureConsole(page);
    await page.goto('/notifications');
    await expect(page.getByRole('heading', { level: 1, name: 'Notifications' })).toBeVisible();
    await expect(page.getByText('1 unread')).toBeVisible();
    await expect(page.getByText('Your tickets are ready')).toBeVisible();
    await shot(page, 'notifications', info);
    await page.getByRole('button', { name: 'Mark all as read' }).click();
    await expect(page.getByText("You're all caught up")).toBeVisible();
    expect(upstream.calls('BuyerMarkAllNotificationsRead')).toHaveLength(1);
    await shot(page, 'notifications-read', info);
    await expect(page.getByRole('link', { name: 'Settings' })).toHaveAttribute('href', '/profile');
    await settle(page, upstream, log);
  });

  test('empty', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...signedInBase(), ...notifications([]) });
    await signInAs({ roles: ['CUSTOMER'] });
    const log = captureConsole(page);
    await page.goto('/notifications');
    await expect(page.getByText('No notifications')).toBeVisible();
    await shot(page, 'notifications-empty', info);
    await settle(page, upstream, log);
  });

  test('error', async ({ page, upstream, signInAs }, info) => {
    upstream.gql({ ...signedInBase(), MyNotifications: gqlErrors({ message: 'boom', extensions: { errorCode: 'INTERNAL_ERROR', classification: 'INTERNAL', retryable: true } }) });
    await signInAs({ roles: ['CUSTOMER'] });
    await page.goto('/notifications');
    await expect(page.getByTestId('error-state')).toBeVisible();
    await expect(page.getByRole('button', { name: 'Try again' })).toBeVisible();
    await shot(page, 'notifications-error', info);
  });

  test('signed out is sent to sign in with a return path', async ({ page, upstream }) => {
    upstream.gql({});
    await page.goto('/notifications');
    await expect(page).toHaveURL(/\/auth\?next=%2Fnotifications|\/auth/);
  });
});
