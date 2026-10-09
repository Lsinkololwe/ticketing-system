'use client';

import { use, useState } from 'react';
import { useEventTicketHolders } from '@pml.tickets/shared/api/organization-admin/modules/checkin';
import { useMyEventDetail } from '@pml.tickets/shared/api/organization-admin/modules/events';
import { PageHeader, Tabs } from '@pml.tickets/shared/components/m3';
import { AttendeesView } from '@/components/checkin/AttendeesView';
import { EventCheckInTab } from '@/components/checkin/EventCheckInTab';

export default function CheckInPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = use(params);
  const { event } = useMyEventDetail(id);
  const holders = useEventTicketHolders(id, { size: 100 });
  const [tab, setTab] = useState('gate');
  return (
    <>
      <PageHeader
        title="Check-in"
        subtitle={event?.title}
        breadcrumbs={[{ label: 'Events', href: '/events' }, { label: event?.title ?? 'Event', href: `/events/${id}` }, { label: 'Check-in' }]}
      />
      <Tabs
        label="Check-in sections"
        value={tab}
        onChange={setTab}
        tabs={[
          { id: 'gate', label: 'Gate' },
          { id: 'attendees', label: 'Attendees' },
        ]}
      >
        {(active) =>
          active === 'gate' ? (
            <EventCheckInTab eventId={id} />
          ) : (
            <AttendeesView
              attendees={holders.holders}
              total={holders.total}
              hasMore={holders.hasMore}
              loading={holders.loading}
              error={holders.error}
              onRetry={() => void holders.refetch()}
            />
          )
        }
      </Tabs>
    </>
  );
}
