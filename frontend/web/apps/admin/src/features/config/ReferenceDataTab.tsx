'use client';

import { RowActions } from '@/components/console/RowActions';
import { useStepUp } from '@/lib/useStepUp';
import { useMemo, useState } from 'react';
import { useRouter } from 'next/navigation';
import {
  Banner,
  Button,
  Card,
  CardHeader,
  ConfirmDialog,
  DataTable,
  EmptyState,
  ErrorState,
  Select,
  StatusPill,
  useSnackbar,
} from '@pml.tickets/shared/components/m3';
import {
  useCreateReferenceData,
  useDeleteReferenceData,
  useReferenceDataAdminAll,
  useReferenceTypes,
  useToggleReferenceDataActive,
  useUpdateReferenceData,
  type ReferenceData,
  type ReferenceType,
} from '@pml.tickets/shared/api/admin/modules/reference-data';
import { FilterBar, Tiles, useStaff } from '@/components/console';
import { useAdminEventCategories } from '@pml.tickets/shared/api/admin/modules/event';
import { useCitiesAdmin, useProvincesAdmin } from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { useReferenceOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { humanize } from '@/lib/format';
import { RefEntryDialog, type RefEntryValues } from './RefEntryDialog';
import { needText } from '@/lib/permissions';

/**
 * Types that have their own screens (categories, provinces and cities are managed under Events), so the
 * generic table does not offer them. Everything else the registry lists as a taxonomy is managed here.
 */
const OWN_PAGE: ReadonlyArray<ReferenceType> = ['EVENT_CATEGORY', 'PROVINCE', 'CITY'];

const PAGE = 10;

/**
 * Reference data: every taxonomy list the registry (`referenceTypes`) offers, except categories, provinces and
 * cities. Mounted at /config/refdata and /transactions/refdata.
 */
export function ReferenceDataTab() {
  const router = useRouter();
  const { can } = useStaff();
  const { show } = useSnackbar();
  const editable = can('cfgEdit');

  const [chosenType, setType] = useState<ReferenceType | null>(null);
  const [query, setQuery] = useState('');
  const [status, setStatus] = useState('all');
  const [page, setPage] = useState(0);
  const [editing, setEditing] = useState<ReferenceData | 'new' | null>(null);
  const [deleting, setDeleting] = useState<ReferenceData | null>(null);

  const { categories } = useAdminEventCategories({ size: 100 });
  const { provinces } = useProvincesAdmin();
  const { cities } = useCitiesAdmin();
  const { types } = useReferenceTypes();
  // The registry decides what is managed here: every taxonomy type, grouped as the backend groups them.
  const managed = useMemo(() => types.filter((t) => t.group !== 'WORKFLOW' && !OWN_PAGE.includes(t.type)), [types]);
  const selected: ReferenceType | null = chosenType ?? managed[0]?.type ?? null;
  const type = selected as ReferenceType; // only read below once a type exists (the writers and the table are inert without one)
  const groups = useMemo(() => {
    const byGroup = new Map<string, { label: string; types: typeof managed }>();
    for (const t of managed) {
      const g = byGroup.get(t.group) ?? { label: t.groupLabel, types: [] };
      g.types.push(t);
      byGroup.set(t.group, g);
    }
    return [...byGroup.values()];
  }, [managed]);
  const { items, loading, error, refetch } = useReferenceDataAdminAll(selected);
  // A legal type's required documents must be document types the platform lists.
  const kybTypes = useReferenceOptions('KYB_DOCUMENT_TYPE', { skip: type !== 'BUSINESS_TYPE' });
  const metadataChoices = useMemo(() => ({ requiredDocuments: kybTypes.options.map((o) => o.value) }), [kybTypes.options]);
  const { create } = useCreateReferenceData(type);
  const { update } = useUpdateReferenceData(type);
  const { remove, loading: removing } = useDeleteReferenceData(type);
  const { guard } = useStepUp();
  const { setActive } = useToggleReferenceDataActive(type);

  const typeLabel = (t: ReferenceType) => types.find((x) => x.type === t)?.label ?? humanize(t);
  const requiredKeys = useMemo(() => types.find((x) => x.type === type)?.requiredMetadataKeys ?? [], [types, type]);

  const rows = useMemo(() => {
    const q = query.trim().toLowerCase();
    return items.filter(
      (r) =>
        (status === 'all' || (status === 'active') === r.isActive) &&
        (!q || `${r.name} ${r.code} ${r.description ?? ''}`.toLowerCase().includes(q)),
    );
  }, [items, query, status]);
  const pageRows = rows.slice(page * PAGE, page * PAGE + PAGE);

  const open = (row: ReferenceData | 'new') => setEditing(row);

  const submit = async (v: RefEntryValues) => {
    const metadata = requiredKeys.length ? (v.metadata as Record<string, unknown>) : null;
    const description = v.description.trim() || null;
    const displayOrder = v.displayOrder ?? null;
    try {
      if (editing === 'new') {
        await guard(() => create({ type, code: v.code, name: v.name, description, displayOrder, metadata, semantic: null, allowedTransitions: null, parentType: null, parentCode: null, isActive: null }));
        show({ message: 'Entry created' });
      } else if (editing) {
        await guard(() => update(editing.id, {
          name: v.name, description, displayOrder, metadata,
          semantic: null, allowedTransitions: null, parentType: null, parentCode: null, isActive: null, effectiveFrom: null, effectiveTo: null,
        }));
        show({ message: 'Entry updated' });
      }
      setEditing(null);
      refetch();
    } catch {
      show({ message: 'Could not save the entry. Try again.', tone: 'error' });
      throw new Error('save failed');
    }
  };

  const toggle = async (row: ReferenceData) => {
    try {
      await guard(() => setActive(row.id, !row.isActive));
      refetch();
      show({ message: `${row.name} ${row.isActive ? 'deactivated' : 'activated'}`, actionLabel: 'Undo', onAction: () => void setActive(row.id, row.isActive) });
    } catch {
      show({ message: 'Could not change the status. Try again.' });
    }
  };

  const confirmDelete = async () => {
    if (!deleting) return;
    try {
      await guard(() => remove(deleting.id));
      refetch();
      show({ message: `${deleting.name} deleted` });
      setDeleting(null);
    } catch {
      show({ message: 'Could not delete the entry. Deactivate it instead.' });
      setDeleting(null);
    }
  };

  return (
    <div className="m3-stack">
      <Card as="section" aria-label="Reference data shortcuts">
        <CardHeader title="Reference data shortcuts" subtitle="Event categories, provinces and cities have their own pages." />
        <Tiles
          label="Reference data shortcuts"
          items={[
            { id: 'cats', label: 'Event categories', value: categories.length, onSelect: () => router.push('/events/categories') },
            { id: 'prov', label: 'Provinces and cities', value: `${provinces.length} and ${cities.length}`, onSelect: () => router.push('/events/locations') },
          ]}
        />
      </Card>

      <Card as="section" aria-label="Reference data">
        <CardHeader
          title="Reference data"
          subtitle="Every list the platform owns: countries, currencies, banks, mobile money operators, reasons, document and business types, roles, notification and report vocabularies. Changes are published to the apps when you save."
        />
        {!editable ? <Banner tone="info">{needText('cfgEdit')}</Banner> : null}
        <FilterBar
          searchLabel="Search reference entries"
          query={query}
          onQuery={(q) => {
            setQuery(q);
            setPage(0);
          }}
          values={{ status }}
          filters={[
            {
              id: 'status',
              label: 'Status',
              options: [
                { value: 'active', label: 'Active' },
                { value: 'inactive', label: 'Inactive' },
              ],
            },
          ]}
          onFilter={(_, v) => {
            setStatus(v);
            setPage(0);
          }}
          onClear={() => {
            setQuery('');
            setStatus('all');
            setPage(0);
          }}
          actions={
            <>
              <Select
                label="Type"
                density="compact"
                value={type}
                disabled={managed.length === 0}
                onChange={(e) => {
                  setType(e.target.value as ReferenceType);
                  setPage(0);
                  setQuery('');
                  setStatus('all');
                }}
              >
                {groups.map((g) => (
                  <optgroup key={g.label} label={g.label}>
                    {g.types.map((t) => (
                      <option key={t.type} value={t.type}>
                        {t.label}
                      </option>
                    ))}
                  </optgroup>
                ))}
              </Select>
              <Button variant="filled" size="sm" icon="add" disabled={!editable || selected === null} onClick={() => open('new')}>
                New entry
              </Button>
            </>
          }
        />
        <DataTable<ReferenceData>
          caption={`${typeLabel(type)} entries`}
          rows={pageRows}
          getRowId={(r) => r.id}
          loading={loading && items.length === 0}
          error={error && items.length === 0 ? <ErrorState error={error} onRetry={() => void refetch()} /> : undefined}
          empty={
            <EmptyState
              title="No reference entries yet."
              description={rows.length === 0 && items.length > 0 ? 'No entries match the filters.' : undefined}
              action={
                editable && items.length === 0 ? (
                  <Button variant="filled" onClick={() => open('new')}>
                    New entry
                  </Button>
                ) : undefined
              }
            />
          }
          columns={[
            { id: 'type', header: 'Type', cell: (r) => typeLabel(r.type) },
            { id: 'name', header: 'Name', rowHeader: true, cell: (r) => <strong>{r.name}</strong> },
            { id: 'code', header: 'Code', cell: (r) => <span className="m3-mono">{r.code}</span> },
            { id: 'status', header: 'Status', cell: (r) => <StatusPill status={r.isActive ? 'ACTIVE' : 'INACTIVE'} /> },
          ]}
          rowActions={(r) => (
            <RowActions
              name={r.name}
              primary={{ label: 'Edit', disabled: !editable, onSelect: () => open(r) }}
              items={[
                { id: 'toggle', label: r.isActive ? 'Deactivate' : 'Activate', disabled: !editable, onSelect: () => void toggle(r) },
                { id: 'delete', label: 'Delete', danger: true, disabled: !editable || r.isSystem, onSelect: () => setDeleting(r) },
              ]}
            />
          )}
          actionsHeader="Actions"
          pagination={{ page: page + 1, pageSize: PAGE, total: rows.length, onPageChange: (n) => setPage(n - 1) }}
        />
      </Card>

      {editing !== null ? (
        <RefEntryDialog
          entry={editing === 'new' ? null : editing}
          typeLabel={typeLabel(type)}
          existingCodes={items.map((r) => r.code)}
          metadataKeys={requiredKeys}
          metadataChoices={metadataChoices}
          onClose={() => setEditing(null)}
          onSubmit={submit}
        />
      ) : null}

      <ConfirmDialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => void confirmDelete()}
        title={`Delete entry "${deleting?.name ?? ''}"?`}
        description="It is removed from the list and from the pickers that use it. This cannot be undone; deactivate it instead to keep the history."
        confirmLabel="Delete"
        danger
        loading={removing}
      />
    </div>
  );
}
