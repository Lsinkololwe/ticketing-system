'use client';

import { useMemo, useState } from 'react';
import { Card, CardHeader, DataTable, ErrorState } from '@pml.tickets/shared/components/m3';
import { FilterBar } from '@/components/console';
import { humanize } from '@/lib/format';
import { usePermissionCatalogue, type CataloguePermission } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { ROLE_LABELS, STAFF_ROLES } from '@/config/navigation';

type PermissionRow = CataloguePermission & { heldBy: string };

const PAGE = 8;

/** Read-only permission catalogue (search, module filter, client paging). */
export function PermissionCatalogue({ id = 'permission-catalogue' }: { id?: string }) {
  const [query, setQuery] = useState('');
  const [mod, setMod] = useState('all');
  const [page, setPage] = useState(0);

  // Which role holds which permission is asked of identity, role by role: organization and event roles come
  // from the platform's role lists, staff roles from the console's own role map.
  const orgRoles = useReferenceOptions('ORGANIZATION_ROLE');
  const eventRoles = useReferenceOptions('EVENT_ROLE');
  const roles = useMemo(
    () => [
      ...orgRoles.options.map((o) => ({ role: o.value, label: o.label })),
      ...eventRoles.options.map((o) => ({ role: o.value, label: o.label })),
      ...STAFF_ROLES.map((r) => ({ role: r, label: ROLE_LABELS[r] })),
    ],
    [orgRoles.options, eventRoles.options],
  );
  const { rows: PERMISSIONS, loading, error, refetch } = usePermissionCatalogue(roles);

  const modules = useMemo(() => [...new Set(PERMISSIONS.map((p) => p.module))], [PERMISSIONS]);
  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return PERMISSIONS.filter(
      (p) => (mod === 'all' || p.module === mod) && (!q || `${p.code} ${p.description} ${p.heldBy}`.toLowerCase().includes(q)),
    );
  }, [PERMISSIONS, query, mod]);
  const pageRows = rows.slice(page * PAGE, page * PAGE + PAGE);

  return (
    <Card as="section" aria-labelledby={`${id}-title`} id={id}>
      <CardHeader
        title={<span id={`${id}-title`}>Permission catalogue</span>}
        subtitle={`Read only. ${PERMISSIONS.length} permission codes. Which role holds which permission is fixed in the platform code, so it cannot be edited here.`}
      />
      <FilterBar
        searchLabel="Search permissions"
        query={query}
        onQuery={(q) => {
          setQuery(q);
          setPage(0);
        }}
        filters={[{ id: 'mod', label: 'Module', options: modules.map((m) => ({ value: m, label: humanize(m) })) }]}
        values={{ mod }}
        onFilter={(_, v) => {
          setMod(v);
          setPage(0);
        }}
        onClear={() => {
          setQuery('');
          setMod('all');
          setPage(0);
        }}
      />
      <DataTable<PermissionRow>
        caption="Permission catalogue"
        rows={pageRows}
        loading={loading && PERMISSIONS.length === 0}
        error={error && PERMISSIONS.length === 0 ? <ErrorState error={error} onRetry={() => void refetch()} /> : undefined}
        getRowId={(p) => p.code}
        columns={[
          { id: 'code', header: 'Permission', rowHeader: true, cell: (p) => <span className="m3-mono">{p.code}</span> },
          { id: 'allows', header: 'Allows', cell: (p) => p.description },
          { id: 'scope', header: 'Scope', cell: (p) => humanize(p.scope) },
          { id: 'heldBy', header: 'Held by', cell: (p) => p.heldBy },
        ]}
        empty={<p className="m3-muted">No permissions match.</p>}
        pagination={{ page: page + 1, pageSize: PAGE, total: rows.length, onPageChange: (n) => setPage(n - 1) }}
      />
    </Card>
  );
}
