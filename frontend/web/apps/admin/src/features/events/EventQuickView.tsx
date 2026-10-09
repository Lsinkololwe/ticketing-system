'use client';

import { useRouter } from 'next/navigation';
import { Banner, Button, ErrorState, Skeleton, SideSheet, StatusPill, KeyValue, Timeline, useSnackbar } from '@pml.tickets/shared/components/m3';
import { useAdminEventDetail, useEventApprovalTimeline, useFeatureEvent } from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { useStaff } from '@/components/console';
import { formatDateTime, formatNumber, humanize, money } from '@/lib/format';
import { ApprovalChecklist } from './ApprovalChecklist';
import { CANCELLABLE } from './helpers';
import type { CancelTarget } from './CancelEventDialog';

/** Right-hand quick view of one event with "Open full page" (mirrors the prototype drawer). */
export function EventQuickView({ eventId, onClose, onCancel }: { eventId: string | null; onClose: () => void; onCancel: (e: CancelTarget) => void }) {
  return (
    <SideSheet open={eventId !== null} onClose={onClose} title="Event" subtitle="Quick view">
      {eventId ? <Body key={eventId} eventId={eventId} onClose={onClose} onCancel={onCancel} /> : null}
    </SideSheet>
  );
}

function Body({ eventId, onClose, onCancel }: { eventId: string; onClose: () => void; onCancel: (e: CancelTarget) => void }) {
  const router = useRouter();
  const snackbar = useSnackbar();
  const { can } = useStaff();
  const { event, loading, error, refetch } = useAdminEventDetail(eventId);
  const { timeline } = useEventApprovalTimeline(eventId);
  const { feature, loading: featuring } = useFeatureEvent();

  if (error && !event) return <ErrorState error={error} onRetry={refetch} />;
  if (!event) return loading ? <Skeleton /> : <p className="m3-muted">This event could not be found.</p>;

  const pending = event.status === 'PENDING_APPROVAL' || event.status === 'CHANGES_REQUESTED';
  const toggle = async () => {
    const res = await feature(event.id, !event.featured);
    snackbar.show({
      message: res.success ? (event.featured ? `${event.title} is no longer featured` : `${event.title} is now featured on the buyer home page`) : (res.message ?? 'Could not change the featured flag'),
      tone: res.success ? 'neutral' : 'error',
    });
  };

  return (
    <div className="m3-stack" data-testid="event-quick-view">
      <h3>{event.title}</h3>
      <div className="m3-row">
        <StatusPill status={event.status} />
        {event.featured ? <StatusPill tone="success">Featured</StatusPill> : null}
        {event.isOverdue ? <StatusPill tone="error">Overdue</StatusPill> : null}
        {timeline?.hasActiveEscalation ? <StatusPill tone="warning">{`Escalation: ${humanize(timeline.escalation?.status ?? 'PENDING')}`}</StatusPill> : null}
      </div>
      {event.rejectionReason && event.status === 'REJECTED' ? <Banner tone="error">Rejected: {event.rejectionReason}</Banner> : null}
      <KeyValue columns
        items={[
          { label: 'Organization', value: event.organizerName },
          { label: 'Category', value: event.category?.name ?? '-' },
          { label: 'Date', value: formatDateTime(event.eventDateTime) },
          { label: 'Venue', value: event.locationName || '-' },
          { label: 'Location', value: [event.location?.city ?? event.cityName, event.location?.province].filter(Boolean).join(', ') || '-' },
          { label: 'Capacity', value: event.totalCapacity ? formatNumber(event.totalCapacity) : '-' },
          { label: 'Sold', value: formatNumber(event.soldTickets) },
          { label: 'Ticket price from', value: event.minTicketPrice != null ? money(event.minTicketPrice) : '-' },
          { label: 'Reviewer', value: timeline?.assignedReviewerName ?? 'Unassigned' },
          { label: 'Submissions', value: timeline?.submissionCount ?? '-' },
        ]}
      />
      {pending || event.approvalBlockers.length > 0 ? (
        <section aria-label="Approval checklist">
          <h3>Approval checklist</h3>
          <ApprovalChecklist blockers={event.approvalBlockers} />
        </section>
      ) : null}
      <section aria-label="Approval timeline">
        <h3>Approval timeline</h3>
        {timeline && timeline.timelineEvents.length > 0 ? (
          <Timeline
            label="Approval timeline"
            items={timeline.timelineEvents.map((t) => ({
              id: t.id,
              title: humanize(t.action),
              time: formatDateTime(t.timestamp),
              detail: [t.actorName, t.comments].filter(Boolean).join(' - '),
            }))}
          />
        ) : (
          <p className="m3-muted">No approval activity recorded yet.</p>
        )}
      </section>
      <div className="m3-row">
        <Button
          variant="tonal"
          onClick={() => {
            onClose();
            router.push(`/event/${event.id}`);
          }}
        >
          Open full page
        </Button>
        {event.status === 'PUBLISHED' && can('featureEvent') ? (
          <Button variant="tonal" loading={featuring} onClick={() => void toggle()}>
            {event.featured ? 'Remove feature' : 'Feature event'}
          </Button>
        ) : null}
        {CANCELLABLE.includes(event.status) && can('featureEvent') ? (
          <Button variant="outlined" danger onClick={() => onCancel({ id: event.id, title: event.title, soldTickets: event.soldTickets })}>
            Cancel event
          </Button>
        ) : null}
      </div>
    </div>
  );
}
