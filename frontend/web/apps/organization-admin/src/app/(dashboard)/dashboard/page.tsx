'use client';

import {
  useMyCheckInRate,
  useMyDashboardStats,
  useMyPayoutWindow,
  useMyRecentActivity,
  useMyRevenueSeries,
  useMyTicketMix,
  useMyUpcomingEvents,
} from '@pml.tickets/shared/api/organization-admin/modules/dashboard';
import { useMyOrganization } from '@pml.tickets/shared/api/organization-admin/modules/organization';
import { DashboardView } from '@/components/dashboard/DashboardView';
import { useOrgContext } from '@/lib/api/org-context';
import { useMarkAllNotificationsRead, useMyNotifications } from '@/lib/api/notifications';

export default function DashboardPage() {
  const { organization } = useMyOrganization();
  const { capabilities, organization: member } = useOrgContext();
  const { stats, loading } = useMyDashboardStats();
  const { points } = useMyRevenueSeries(12);
  const { mix } = useMyTicketMix();
  const { rate } = useMyCheckInRate();
  const { window: payoutWindow } = useMyPayoutWindow();
  const { events } = useMyUpcomingEvents(4);
  const { activity } = useMyRecentActivity(8);
  const { notifications, unread } = useMyNotifications(10);
  const { markAll } = useMarkAllNotificationsRead();

  return (
    <DashboardView
      orgName={organization?.name ?? member?.name ?? 'Your organization'}
      canViewFinance={capabilities.canViewFinance}
      canCreateEvents={capabilities.canWriteEvents}
      loading={loading}
      stats={stats}
      series={points}
      mix={mix}
      checkIn={rate}
      payoutWindow={payoutWindow}
      upcoming={events}
      activity={activity}
      notifications={notifications}
      unreadNotifications={unread}
      onMarkAllRead={() => void markAll()}
    />
  );
}
