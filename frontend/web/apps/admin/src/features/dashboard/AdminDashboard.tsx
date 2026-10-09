'use client';

import { useMemo } from 'react';
import { Card, CardHeader, StatusPill } from '@pml.tickets/shared/components/m3';
import { useAdminEvents, useAdminUsers, useEventStats, usePendingOrganizations, usePlatformConfiguration, useUserStats } from '@pml.tickets/shared';
import { useApprovalOrganizationDocuments } from '@pml.tickets/shared/api/admin/modules';
import { RecentActivity } from './RecentActivity';
import { ago, formatNumber, humanize, slaOf, type SlaState } from '@/lib/format';
import { useTimedPendingCounts } from './data';
import { KpiRow, ListCard, PollNote, RoleBanner, type ListRow } from './parts';

const DEFAULT_SLA = 48;
const DEFAULT_WARN = 24;
const RANK: Record<SlaState, number> = { overdue: 0, warn: 1, ok: 2 };
const SLA_TONE = { overdue: 'error', warn: 'warning', ok: 'success' } as const;

export function monthGrid(year: number, month: number) {
  const first = new Date(year, month, 1);
  const pad = (first.getDay() + 6) % 7;
  const days = new Date(year, month + 1, 0).getDate();
  return { pad, days };
}

export function AdminDashboard() {
  const { counts, updatedAt } = useTimedPendingCounts();
  const { config } = usePlatformConfiguration();
  const sla = config?.approvalSlaHours ?? DEFAULT_SLA;
  const warn = config?.approvalWarningThresholdHours ?? DEFAULT_WARN;
  const orgs = usePendingOrganizations({ page: 0, size: 10 });
  const pendEvents = useAdminEvents({ status: 'PENDING_APPROVAL', size: 10 });
  const live = useAdminEvents({ status: 'PUBLISHED', size: 100 });
  const approved = useAdminEvents({ status: 'APPROVED', size: 100 });
  const docs = useApprovalOrganizationDocuments();
  const evStats = useEventStats();
  const uStats = useUserStats();
  const locked = useAdminUsers({ accountStatus: 'LOCKED', size: 3 });
  const suspended = useAdminUsers({ accountStatus: 'SUSPENDED', size: 3 });
  const unverified = useAdminUsers({ accountStatus: 'PENDING_VERIFICATION', size: 3 });

  const queue = useMemo(() => {
    const now = new Date();
    return [
      ...orgs.organizations.map((o) => ({ id: `o-${o.id}`, kind: 'Organizer', name: o.name, at: o.submittedAt as string | null, tab: 'orgs', s: slaOf(o.submittedAt, sla, warn, now) })),
      ...pendEvents.events.map((e) => ({ id: `e-${e.id}`, kind: 'Event', name: e.title, at: e.submittedForApprovalAt as string | null, tab: 'events', s: slaOf(e.submittedForApprovalAt, sla, warn, now) })),
      ...docs.organizations.flatMap((o) =>
        (o.verificationDocuments ?? [])
          .filter((d) => d.status === 'PENDING')
          .map((d) => ({ id: `d-${d.id}`, kind: 'Document', name: `${humanize(d.documentType)} · ${o.name}`, at: d.uploadedAt as string | null, tab: 'docs', s: slaOf(d.uploadedAt, sla, warn, now) }))
      ),
    ].sort((a, b) => RANK[a.s.state] - RANK[b.s.state] || String(a.at).localeCompare(String(b.at)));
  }, [orgs.organizations, pendEvents.events, docs.organizations, sla, warn]);

  const overdue = queue.filter((q) => q.s.state === 'overdue').length;
  const awaiting = counts['pending-approvals'];
  const needAction = uStats.data ? uStats.data.lockedUsers + uStats.data.suspendedUsers + uStats.data.pendingVerificationUsers : undefined;

  const attention: ListRow[] = [
    ...orgs.organizations.slice(0, 4).map((o) => ({
      id: `org-${o.id}`,
      title: o.name,
      support: `Organization · ${o.type ? o.type.toLowerCase().replace(/_/g, ' ') : 'organizer'}`,
      trailing: <StatusPill status={o.status} />,
      go: { label: 'Open', href: `/org/${o.id}` },
    })),
    ...[...locked.users, ...suspended.users, ...unverified.users].slice(0, 6).map((u) => ({
      id: `usr-${u.id}`,
      title: u.fullName ?? u.email,
      support: u.email,
      trailing: <StatusPill status={u.accountStatus} />,
      go: { label: 'Open', href: `/user/${u.id}` },
    })),
  ];

  const now = new Date();
  const { pad, days } = monthGrid(now.getFullYear(), now.getMonth());
  const monthName = now.toLocaleDateString('en-GB', { month: 'long', year: 'numeric' });
  const byDay = new Map<number, string[]>();
  for (const e of [...live.events, ...approved.events, ...pendEvents.events]) {
    const d = new Date(e.eventDateTime as string);
    if (d.getFullYear() === now.getFullYear() && d.getMonth() === now.getMonth()) {
      byDay.set(d.getDate(), [...(byDay.get(d.getDate()) ?? []), e.title]);
    }
  }

  return (
    <>
      <RoleBanner role="ADMIN" />
      <KpiRow
        items={[
          { icon: 'check-circle', label: 'Awaiting approval', value: formatNumber(awaiting), caption: `${overdue} overdue`, go: { module: 'approvals', tab: 'orgs' } },
          { icon: 'clock', label: 'Overdue on SLA', value: String(overdue), caption: `Target ${sla} hours`, go: { module: 'approvals', tab: 'events' } },
          { icon: 'calendar', label: 'Live events', value: evStats.stats ? formatNumber(evStats.stats.publishedEvents) : undefined, missing: 'eventStats', go: { module: 'events', tab: 'all' } },
          { icon: 'users', label: 'Accounts needing action', value: needAction === undefined ? undefined : formatNumber(needAction), missing: 'userStats', caption: 'Locked, suspended or unverified', go: { module: 'users', tab: 'users' } },
        ]}
      />
      <div className="adm-split">
        <ListCard
          title="Approvals queue"
          subtitle={`Most urgent first. Red dots are past the ${sla} hour target.`}
          loading={orgs.loading || pendEvents.loading || docs.loading}
          empty="The queue is empty."
          rows={queue.slice(0, 7).map((q) => ({
            id: q.id,
            title: q.name,
            support: `${q.kind} · submitted ${ago(q.at)}`,
            trailing: <StatusPill tone={SLA_TONE[q.s.state]}>{q.s.label}</StatusPill>,
            go: { label: 'Review', href: `/approvals/${q.tab}` },
          }))}
          foot={{ label: 'Open workbench', href: '/approvals/orgs' }}
        />
        <ListCard
          title="Users and organizations needing action"
          subtitle="Locked, suspended, unverified or awaiting review"
          loading={orgs.loading || locked.loading}
          rows={attention}
        />
      </div>
      <div className="adm-split">
        <Card>
          <CardHeader title="Events calendar" subtitle={`${monthName} · live, approved and awaiting approval`} />
          <div className="adm-cal" role="grid" aria-label={`${monthName} events`}>
            {['Mon', 'Tue', 'Wed', 'Thu', 'Fri', 'Sat', 'Sun'].map((d) => (
              <div key={d} className="adm-cal__h" role="columnheader">{d}</div>
            ))}
            {Array.from({ length: pad }, (_, i) => <div key={`p${i}`} />)}
            {Array.from({ length: days }, (_, i) => i + 1).map((d) => (
              <div key={d} className="adm-cal__d" role="gridcell" data-today={d === now.getDate() ? 'true' : undefined}>
                <b>{d}</b>
                {(byDay.get(d) ?? []).slice(0, 2).map((t, k) => <span key={`${t}-${k}`} title={t}>{t}</span>)}
              </div>
            ))}
          </div>
        </Card>
        <RecentActivity />
      </div>
      <PollNote updatedAt={updatedAt} />
    </>
  );
}
