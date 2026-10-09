'use client';

import { ownerLabel } from '@/lib/ownerLabel';
import { RowActions } from '@/components/console/RowActions';
import { useState } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Card, CardHeader, DataTable, EmptyState, ErrorState, StatusPill, useSnackbar } from '@pml.tickets/shared/components/m3';
import { ADMIN_KYB_STATUSES, ADMIN_ORG_STATUSES, useIdentityOrganizations, type AdminKybStatus, type AdminOrgRecord, type AdminOrgStatus } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { FilterBar, useStaff } from '@/components/console';
import { csvText, humanize } from '@/lib/format';
import { PersonCell, copyText, optionsOf, orgRateText, useDebounced } from './common';
import { OrgQuickView } from './OrgQuickView';
import { useOrgActionController } from './useOrgActionController';

const PAGE_SIZE = 20;

export function OrgsTable() {
  const router = useRouter();
  const staff = useStaff();
  const snack = useSnackbar();
  const ctl = useOrgActionController();
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('all');
  const [kyb, setKyb] = useState('all');
  const [ver, setVer] = useState('all');
  const [page, setPage] = useState(1);
  const [quick, setQuick] = useState<AdminOrgRecord | null>(null);
  const search = useDebounced(query);

  const list = useIdentityOrganizations({
    search,
    status: status === 'all' ? null : (status as AdminOrgStatus),
    verified: ver === 'all' ? null : ver === 'yes',
    kybStatus: kyb === 'all' ? null : (kyb as AdminKybStatus),
    page: page - 1,
    size: PAGE_SIZE,
  });
  const rows = list.rows;
  const filtered = query !== '' || status !== 'all' || kyb !== 'all' || ver !== 'all';
  const manage = staff.can('orgs');

  return (
    <>
      <Card>
        <CardHeader title="Organizations" subtitle="Tenants that sell tickets. Open one for team, events, commission and payout account." />
        <FilterBar
          searchLabel="Search organizations"
          query={query}
          onQuery={(q) => {
            setQuery(q);
            setPage(1);
          }}
          filters={[
            { id: 'st', label: 'Status', options: optionsOf(ADMIN_ORG_STATUSES) },
            { id: 'kyb', label: 'KYB status', options: optionsOf(ADMIN_KYB_STATUSES) },
            { id: 'ver', label: 'Verified', options: [{ value: 'yes', label: 'Documents verified' }, { value: 'no', label: 'Not verified' }] },
          ]}
          values={{ st: status, kyb, ver }}
          onFilter={(id, v) => {
            if (id === 'st') setStatus(v);
            else if (id === 'kyb') setKyb(v);
            else setVer(v);
            setPage(1);
          }}
          onClear={() => {
            setQuery('');
            setStatus('all');
            setKyb('all');
            setVer('all');
            setPage(1);
          }}
          actions={
            <Button
              variant="text"
              size="sm"
              icon="copy"
              onClick={() => {
                copyText(csvText([['Organization', 'Owner', 'Status', 'KYB', 'Commission'], ...rows.map((o) => [o.name, ownerLabel(o), humanize(o.status), humanize(o.kybStatus), orgRateText(o)])]));
                snack.show('CSV copied');
              }}
            >
              Copy CSV
            </Button>
          }
        />
        <DataTable<AdminOrgRecord>
          caption="Organizations"
          loading={list.loading && list.rows.length === 0}
          error={list.error && list.rows.length === 0 ? <ErrorState error={list.error} onRetry={list.refetch} /> : undefined}
          empty={<EmptyState icon="building" title="No organizations match." description={filtered ? 'Try a different search or clear the filters.' : 'Organizations appear here once organizers apply.'} />}
          rows={rows}
          getRowId={(o) => o.id}
          columns={[
            { id: 'org', header: 'Organization', rowHeader: true, cell: (o) => <PersonCell name={o.name} sub={`${humanize(o.type)} · ${o.businessAddress?.city ?? '—'}`} /> },
            { id: 'owner', header: 'Owner', cell: (o) => ownerLabel(o) },
            { id: 'status', header: 'Status', cell: (o) => <StatusPill status={o.status} /> },
            { id: 'kyb', header: 'KYB', cell: (o) => <StatusPill status={o.kybStatus} /> },
            { id: 'commission', header: 'Commission', cell: (o) => orgRateText(o) },
            {
              id: 'payout',
              header: 'Payout account',
              cell: (o) => (o.payoutConfig?.isConfigured ? <StatusPill status={o.payoutAccountVerified ? 'VERIFIED' : 'PENDING_VERIFICATION'} /> : <span className="m3-muted">None</span>),
            },
            { id: 'events', header: 'Events', cell: (o) => String(o.totalEvents ?? '—') },
          ]}
          rowActions={(o) => (
            <RowActions
              name={o.name}
              primary={{ label: 'Quick view', onSelect: () => setQuick(o) }}
              items={[
                { id: 'page', label: 'Open full page', onSelect: () => router.push(`/org/${o.id}`) },
                ...(manage
                  ? [
                      ...(o.status === 'SUSPENDED' ? [{ id: 'unsuspend', label: 'Unsuspend', onSelect: () => ctl.run('unsuspend', o) }] : []),
                      ...(o.status === 'ACTIVE' ? [{ id: 'suspend', label: 'Suspend…', danger: true, onSelect: () => ctl.run('suspend', o) }] : []),
                      { id: 'commission', label: 'Commission override', onSelect: () => ctl.run('commission', o) },
                      { id: 'status', label: 'Update status', onSelect: () => ctl.run('status', o) },
                    ]
                  : []),
              ]}
            />
          )}
          pagination={{ page, pageSize: list.pageSize || PAGE_SIZE, total: list.total, onPageChange: setPage }}
        />
      </Card>
      <OrgQuickView org={quick} onClose={() => setQuick(null)} onOpen={(o) => router.push(`/org/${o.id}`)} controller={ctl} />
      {ctl.dialogs}
    </>
  );
}
