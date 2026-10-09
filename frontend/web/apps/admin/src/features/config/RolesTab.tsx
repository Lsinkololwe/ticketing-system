'use client';

import { Card, CardHeader, DataTable, FormCell, FormGrid, List, ListItem, StatusPill } from '@pml.tickets/shared/components/m3';
import { useStaff } from '@/components/console';
import { RoleChip } from '@/components/console/RoleChip';
import { MODULES, ROLE_DESCRIPTIONS, ROLE_LABELS, STAFF_ROLES, canOpenModule, type StaffRole } from '@/config/navigation';
import { ACTIONS } from '@/lib/permissions';
import { ACTION_LABELS, MATRIX_ACTIONS } from './catalogue';
import { useReferenceOptions, type ReferenceOption } from '@pml.tickets/shared/api/graphql/shared/reference';
import { PermissionCatalogue } from './PermissionCatalogue';

interface MatrixRow {
  id: string;
  label: string;
  has: (role: StaffRole) => boolean;
}

function Mark({ granted }: { granted: boolean }) {
  return granted ? (
    <span role="img" aria-label="Granted">
      ✓
    </span>
  ) : (
    <span role="img" aria-label="Hidden" className="m3-muted">
      –
    </span>
  );
}

function Matrix({ caption, rows }: { caption: string; rows: MatrixRow[] }) {
  return (
    <DataTable<MatrixRow>
      caption={caption}
      rows={rows}
      getRowId={(r) => r.id}
      columns={[
        { id: 'area', header: 'Area or action', rowHeader: true, cell: (r) => r.label },
        ...STAFF_ROLES.map((role) => ({
          id: role,
          header: ROLE_LABELS[role],
          align: 'start' as const,
          cell: (r: MatrixRow) => <Mark granted={r.has(role)} />,
        })),
      ]}
    />
  );
}

/** Roles and access: staff roles, read-only permission matrix, organization and event roles, catalogue. */
export function RolesTab() {
  const { roles } = useStaff();
  const orgRoles = useReferenceOptions('ORGANIZATION_ROLE');
  const eventRoles = useReferenceOptions('EVENT_ROLE');
  const areas: MatrixRow[] = MODULES.map((m) => ({ id: m.id, label: m.label, has: (r) => canOpenModule([r], m.id) }));
  const actions: MatrixRow[] = MATRIX_ACTIONS.map((k) => ({
    id: k,
    label: ACTION_LABELS[k],
    has: (r) => (ACTIONS[k] as readonly StaffRole[]).includes(r),
  }));

  return (
    <div className="m3-stack">
      <Card as="section" aria-label="Platform roles">
        <CardHeader
          title="Platform roles"
          subtitle={`You hold: ${roles.length ? roles.map((r) => ROLE_LABELS[r]).join(', ') : 'no platform role'}.`}
        />
        <FormGrid>
          {STAFF_ROLES.map((r) => (
            <FormCell key={r} span={3}>
              <Card variant="filled" aria-label={ROLE_LABELS[r]}>
                <h4 className="m3-card__title"><RoleChip role={r} /></h4>
                <p className="m3-muted">{ROLE_DESCRIPTIONS[r]}</p>
                {roles.includes(r) ? <StatusPill tone="info">Your role</StatusPill> : null}
              </Card>
            </FormCell>
          ))}
        </FormGrid>
      </Card>

      <Card as="section" aria-label="Permission matrix">
        <CardHeader title="Permission matrix" subtitle="Read only. A dash means the area or action is hidden for that role." />
        <h4 className="m3-card__title">Areas in the navigation drawer</h4>
        <Matrix caption="Areas in the navigation drawer" rows={areas} />
        <h4 className="m3-card__title">Actions inside pages</h4>
        <Matrix caption="Actions inside pages" rows={actions} />
      </Card>

      <FormGrid>
        <FormCell span={6}>
          <Card as="section" aria-label="Organization roles">
            <CardHeader title="Organization roles" subtitle="Held by people inside an organization. Read only reference." />
            <RoleList list={orgRoles} />
          </Card>
        </FormCell>
        <FormCell span={6}>
          <Card as="section" aria-label="Event roles">
            <CardHeader title="Event roles" subtitle="Granted per event on top of an organization role. Read only reference." />
            <RoleList list={eventRoles} />
          </Card>
        </FormCell>
      </FormGrid>

      <PermissionCatalogue id="permission-catalogue-roles" />
    </div>
  );
}

function RoleList({ list }: { list: { options: ReferenceOption[]; loading: boolean; empty: boolean; error?: Error } }) {
  if (list.loading) return <p className="m3-muted" role="status">Loading roles…</p>;
  if (list.empty) return <p className="m3-muted">{list.error ? 'The role list could not be loaded.' : 'No roles are listed.'}</p>;
  return (
    <List>
      {list.options.map((o) => (
        <ListItem key={o.value} headline={o.label} support={o.description ?? ''} />
      ))}
    </List>
  );
}
