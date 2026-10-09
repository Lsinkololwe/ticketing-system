import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { NotificationsView } from './NotificationsView';

const rows = [
  { id: 'n1', type: 'PAYOUT', title: 'Payout sent', body: 'K 100', actionUrl: '/finance', status: 'SENT', readAt: null, createdAt: '2026-10-01T10:00:00Z' },
  { id: 'n2', type: 'SYSTEM', title: 'Old news', body: '', actionUrl: null, status: 'SENT', readAt: '2026-10-01T11:00:00Z', createdAt: '2026-09-01T10:00:00Z' },
];
const base = { notifications: rows, unread: 1, loading: false, onMarkRead: vi.fn(), onMarkAllRead: vi.fn() };

describe('NotificationsView', () => {
  it('lists, filters unread, marks read and links', () => {
    const onMarkRead = vi.fn();
    const onMarkAllRead = vi.fn();
    render(<NotificationsView {...base} onMarkRead={onMarkRead} onMarkAllRead={onMarkAllRead} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Notifications' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Open' })).toHaveAttribute('href', '/finance');
    fireEvent.click(screen.getByRole('button', { name: 'Mark Payout sent as read' }));
    expect(onMarkRead).toHaveBeenCalledWith('n1');
    fireEvent.click(screen.getByRole('button', { name: 'Mark all read' }));
    expect(onMarkAllRead).toHaveBeenCalled();
    fireEvent.click(screen.getByRole('radio', { name: /Unread/ }));
    expect(screen.queryByText('Old news')).toBeNull();
  });
  it('shows empty, loading and error states', () => {
    const { rerender } = render(<NotificationsView {...base} notifications={[]} unread={0} />);
    expect(screen.getByText('You are all caught up.')).toBeInTheDocument();
    rerender(<NotificationsView {...base} notifications={[]} unread={0} loading />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    rerender(<NotificationsView {...base} notifications={[]} unread={0} error={{ message: 'Boom' }} />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
});
