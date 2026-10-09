// @vitest-environment jsdom
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { act, fireEvent, render, screen, within } from '@testing-library/react';
import { categoryOf } from '../category';

const markRead = vi.fn().mockResolvedValue({});
const markAllRead = vi.fn().mockResolvedValue(1);
const remove = vi.fn().mockResolvedValue({});
const loadMore = vi.fn();
let state = { notes: [] as unknown[], hasNext: false, loading: false, error: null as unknown };
let unread = 0;

vi.mock('next/link', () => ({ default: ({ href, children, ...r }: { href: string; children: React.ReactNode }) => <a href={href} {...r}>{children}</a> }));
vi.mock('@/components/shell/SiteShell', () => ({ SiteShell: ({ children }: { children: React.ReactNode }) => <div>{children}</div> }));
vi.mock('@pml.tickets/shared', async (orig) => ({
  ...(await orig<object>()),
  useMyNotifications: () => ({ ...state, totalCount: state.notes.length, refetch: vi.fn(), loadMore }),
  useUnreadCount: () => unread,
  useNotificationActions: () => ({ markRead, remove, markAllRead, markingAll: false }),
}));

import { NotificationsClient } from '../NotificationsClient';
import { SnackbarProvider } from '@pml.tickets/shared/components/m3';

const note = (id: string, o: Record<string, unknown> = {}) => ({ id, type: 'TICKET_PURCHASED', title: `Title ${id}`, body: `Body ${id}`, actionUrl: null, status: 'SENT', readAt: null, createdAt: new Date(Date.now() - 3 * 3_600_000).toISOString(), ...o });
const mount = () => render(<SnackbarProvider><NotificationsClient /></SnackbarProvider>);

beforeEach(() => {
  vi.clearAllMocks();
  vi.useRealTimers();
  state = { notes: [note('1'), note('2', { readAt: '2026-10-01T00:00:00Z', type: 'PAYMENT_SUCCESSFUL' })], hasNext: false, loading: false, error: null };
  unread = 1;
});

describe('categoryOf', () => {
  it('maps backend types to buyer categories', () => {
    expect(categoryOf('TICKET_TRANSFERRED')).toBe('Tickets');
    expect(categoryOf('REFUND_APPROVED')).toBe('Payment');
    expect(categoryOf('EVENT_REMINDER')).toBe('Reminder');
    expect(categoryOf('EVENT_CANCELLED')).toBe('Event update');
    expect(categoryOf('SYSTEM_ANNOUNCEMENT')).toBe('System');
  });
});

describe('NotificationsClient', () => {
  it('lists notifications, flags unread ones and counts them', () => {
    mount();
    expect(screen.getByText('1 unread')).toBeInTheDocument();
    expect(screen.getByText('Title 1')).toBeInTheDocument();
    expect(screen.getAllByRole('img', { name: 'Unread' })).toHaveLength(1);
    expect(screen.getAllByText('3 h ago')).toHaveLength(2);
  });
  it('marks one and all as read', () => {
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Mark as read' }));
    expect(markRead).toHaveBeenCalledWith('1');
    fireEvent.click(screen.getByRole('button', { name: 'Mark all as read' }));
    expect(markAllRead).toHaveBeenCalledTimes(1);
    expect(markRead).toHaveBeenCalledTimes(1);
  });
  it('deletes after the undo window and can undo', () => {
    vi.useFakeTimers();
    mount();
    fireEvent.click(screen.getByRole('button', { name: 'Delete notification: Title 1' }));
    expect(screen.queryByText('Title 1')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Undo' }));
    expect(screen.getByText('Title 1')).toBeInTheDocument();
    act(() => { vi.advanceTimersByTime(7000); });
    expect(remove).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Delete notification: Title 2' }));
    act(() => { vi.advanceTimersByTime(6500); });
    expect(remove).toHaveBeenCalledWith('2');
  });
  it('links to settings and loads more', () => {
    state.hasNext = true;
    mount();
    expect(screen.getByRole('link', { name: 'Settings' })).toHaveAttribute('href', '/profile');
    fireEvent.click(screen.getByRole('button', { name: 'Load more' }));
    expect(loadMore).toHaveBeenCalled();
  });
  it('has caught-up, empty, loading and error states', () => {
    unread = 0;
    state.notes = [];
    const a = mount();
    expect(screen.getByText("You're all caught up")).toBeInTheDocument();
    expect(screen.getByText('No notifications')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Mark all as read' })).toBeDisabled();
    a.unmount();
    state.loading = true;
    const b = mount();
    expect(screen.getByLabelText('Loading notifications')).toBeInTheDocument();
    b.unmount();
    state = { notes: [], hasNext: false, loading: false, error: { message: 'x' } };
    mount();
    expect(within(document.body).getByTestId('error-state')).toBeInTheDocument();
  });
});
