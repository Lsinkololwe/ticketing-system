'use client';

import { useState } from 'react';
import { z } from 'zod';
import {
  Banner,
  Button,
  Card,
  CardHeader,
  ConfirmDialog,
  FormCell,
  FormGrid,
  Select,
  StatusPill,
  TextArea,
  TextField,
  useSnackbar,
  type DataColumn,
} from '@pml.tickets/shared/components/m3';
import { Form } from '@pml.tickets/shared/forms/Form';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { useAnnouncementActions, useSystemAnnouncements, type AnnouncementRow } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { useStaff } from '@/components/console';
import { ListCard } from '@/features/ledger/ListCard';
import { formatDateTime, humanize } from '@/lib/format';
import { needText } from '@/lib/permissions';
import { ANNOUNCEMENT_SEGMENT_LABELS, ALERT_SEVERITY_LABELS, enumOptions, enumSchema } from '@/lib/enumLabels';

export const AUDIENCES = enumOptions(ANNOUNCEMENT_SEGMENT_LABELS);
const SEVERITIES = enumOptions(ALERT_SEVERITY_LABELS);

export const announceSchema = z
  .object({
    segment: enumSchema(ANNOUNCEMENT_SEGMENT_LABELS),
    severity: enumSchema(ALERT_SEVERITY_LABELS),
    title: z.string().trim().min(3, 'Enter a title'),
    message: z.string().trim().min(5, 'Write the message'),
    startsAt: z.string(),
    endsAt: z.string(),
  })
  .refine((v) => !v.startsAt || !v.endsAt || new Date(v.endsAt) > new Date(v.startsAt), { path: ['endsAt'], message: 'Must be after the start' });
type Values = z.output<typeof announceSchema>;

const iso = (local: string) => (local ? new Date(local).toISOString() : undefined);
const audienceLabel = (s: string) => AUDIENCES.find((a) => a.value === s)?.label ?? humanize(s);

/** Lifecycle of an announcement from its schedule. */
export function announcementState(a: Pick<AnnouncementRow, 'cancelledAt' | 'startsAt' | 'endsAt'>, now = Date.now()): 'CANCELLED' | 'SCHEDULED' | 'ENDED' | 'LIVE' {
  if (a.cancelledAt) return 'CANCELLED';
  if (new Date(a.startsAt).getTime() > now) return 'SCHEDULED';
  if (a.endsAt && new Date(a.endsAt).getTime() <= now) return 'ENDED';
  return 'LIVE';
}

function SentList({ canCancel }: { canCancel: boolean }) {
  const { announcements, loading, error, refetch } = useSystemAnnouncements();
  const { cancelAnnouncement, busy } = useAnnouncementActions();
  const snackbar = useSnackbar();
  const cols: Array<DataColumn<AnnouncementRow>> = [
    { id: 'a', header: 'Announcement', rowHeader: true, cell: (a) => <><b>{a.title}</b><br /><span className="m3-muted">{a.message}</span></> },
    { id: 'aud', header: 'Audience', cell: (a) => audienceLabel(a.segment) },
    { id: 'sev', header: 'Severity', cell: (a) => <StatusPill status={a.severity} /> },
    { id: 'when', header: 'Shown', cell: (a) => <>{formatDateTime(a.startsAt)}{a.endsAt ? <><br /><span className="m3-muted">until {formatDateTime(a.endsAt)}</span></> : null}</> },
    { id: 'state', header: 'State', cell: (a) => <StatusPill status={announcementState(a)} /> },
  ];
  return (
    <ListCard<AnnouncementRow>
      title="Sent announcements"
      subtitle="Newest first"
      caption="Sent announcements"
      rows={[...announcements].sort((a, b) => b.createdAt.localeCompare(a.createdAt))}
      columns={cols}
      getRowId={(a) => a.id}
      searchLabel="Search announcements"
      searchText={(a) => `${a.title} ${a.message}`}
      rowActions={(a) =>
        canCancel && ['LIVE', 'SCHEDULED'].includes(announcementState(a)) ? (
          <Button
            variant="text"
            size="sm"
            danger
            loading={busy}
            onClick={() => void cancelAnnouncement(a.id).then(() => snackbar.show('Announcement cancelled'), (e: Error) => snackbar.show(e.message || 'Could not cancel the announcement'))}
          >
            Cancel
          </Button>
        ) : null
      }
      loading={loading}
      error={error}
      onRetry={refetch}
      empty={{ title: 'No announcements sent yet.' }}
    />
  );
}

export function AnnounceTab() {
  const { can } = useStaff();
  const { broadcast, busy } = useAnnouncementActions();
  const snackbar = useSnackbar();
  const [pending, setPending] = useState<Values | null>(null);
  const form = useZodForm(announceSchema, { defaultValues: { segment: 'ALL', severity: 'INFO', title: '', message: '', startsAt: '', endsAt: '' } });
  const { register, formState: { errors } } = form;

  return (
    <div className="m3-stack">
      {can('announce') ? (
        <Card>
          <CardHeader title="Send a system announcement" subtitle="Appears in the notification centre for the audience you choose. Leave the start empty to publish now." />
          <Form form={form} guardLeave={false} aria-label="Send a system announcement" onSubmit={(v) => setPending(v)}>
            <FormGrid>
              <FormCell span={6}>
                <Select label="Audience" {...register('segment')}>
                  {AUDIENCES.map((a) => <option key={a.value} value={a.value}>{a.label}</option>)}
                </Select>
              </FormCell>
              <FormCell span={6}>
                <Select label="Severity" {...register('severity')}>
                  {SEVERITIES.map((a) => <option key={a.value} value={a.value}>{a.label}</option>)}
                </Select>
              </FormCell>
              <FormCell span={12}><TextField label="Title" density="form" {...register('title')} errorText={errors.title?.message} /></FormCell>
              <FormCell span={12}><TextArea label="Message" rows={4} {...register('message')} errorText={errors.message?.message} /></FormCell>
              <FormCell span={6}><TextField label="Starts (optional)" type="datetime-local" density="form" {...register('startsAt')} /></FormCell>
              <FormCell span={6}><TextField label="Ends (optional)" type="datetime-local" density="form" {...register('endsAt')} errorText={errors.endsAt?.message} /></FormCell>
            </FormGrid>
            <div className="m3-row" style={{ justifyContent: 'flex-end' }}>
              <Button type="submit" variant="filled">Send announcement</Button>
            </div>
          </Form>
        </Card>
      ) : (
        <Banner tone="info">{needText('announce')}.</Banner>
      )}
      <SentList canCancel={can('announce')} />
      <ConfirmDialog
        open={pending !== null}
        onClose={() => setPending(null)}
        title={`Send to ${pending ? audienceLabel(pending.segment).toLowerCase() : ''}?`}
        description={pending ? `"${pending.title}" will be shown to ${audienceLabel(pending.segment).toLowerCase()}${pending.startsAt ? ` from ${formatDateTime(iso(pending.startsAt))}` : ' now'}. You can cancel it later, but people who already saw it cannot unsee it.` : undefined}
        confirmLabel="Send now"
        loading={busy}
        onConfirm={async () => {
          if (!pending) return;
          try {
            await broadcast({ title: pending.title, message: pending.message, segment: pending.segment, severity: pending.severity, startsAt: iso(pending.startsAt), endsAt: iso(pending.endsAt) });
            snackbar.show('Announcement sent');
            form.reset();
          } catch (e) {
            snackbar.show((e as Error).message || 'Could not send the announcement');
          } finally {
            setPending(null);
          }
        }}
      />
    </div>
  );
}
