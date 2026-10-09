'use client';

import { useState } from 'react';
import { Button, ConfirmDialog, Dialog, RowMenu, StatusPill, useSnackbar } from '@pml.tickets/shared/components/m3';
import {
  useCitiesAdmin,
  useCityMutations,
  useProvinceMutations,
  useProvincesAdmin,
  type CityRow,
  type ProvinceRow,
} from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { useStaff } from '@/components/console';
import { needText } from '@/lib/permissions';
import { CrudCard } from './CrudCard';
import { CrudDialog, type CrudField } from './CrudDialog';
import { citySchema, provinceSchema } from './schemas';
import { COUNTRY } from './helpers';

type Form<T> = { mode: 'new' } | { mode: 'edit'; row: T } | null;

function Provinces({ canEdit }: { canEdit: boolean }) {
  const { provinces, loading, error, refetch } = useProvincesAdmin();
  const mutate = useProvinceMutations();
  const snackbar = useSnackbar();
  const [form, setForm] = useState<Form<ProvinceRow>>(null);
  const [deleting, setDeleting] = useState<ProvinceRow | null>(null);
  const [blocked, setBlocked] = useState<ProvinceRow | null>(null);

  const fields: CrudField[] = [
    { name: 'name', label: 'Name' },
    { name: 'code', label: 'Code' },
    ...(form?.mode === 'edit' ? [{ name: 'active', label: 'Active', kind: 'check' as const }] : []),
  ];
  const self = form?.mode === 'edit' ? form.row : undefined;
  const save = async (v: { name: string; code: string; active: boolean }) => {
    const base = { name: v.name, code: v.code };
    const res = form?.mode === 'edit' ? await mutate.update(form.row.id, { ...base, isActive: v.active }) : await mutate.create({ ...base, country: COUNTRY });
    if (!res.success) throw new Error(res.message ?? 'Could not save the province.');
    snackbar.show(form?.mode === 'edit' ? 'Province updated' : 'Province created');
    setForm(null);
  };
  const toggle = async (p: ProvinceRow) => {
    const res = await mutate.update(p.id, { isActive: !p.isActive });
    snackbar.show({ message: res.success ? `${p.name} ${p.isActive ? 'deactivated' : 'activated'}` : (res.message ?? 'Could not change the province'), tone: res.success ? 'neutral' : 'error' });
  };
  const del = async () => {
    if (!deleting) return;
    const res = await mutate.remove(deleting.id);
    snackbar.show({ message: res.success ? 'Province deleted' : (res.message ?? 'Could not delete the province'), tone: res.success ? 'neutral' : 'error' });
    setDeleting(null);
  };

  return (
    <>
      <CrudCard<ProvinceRow>
        title="Provinces"
        subtitle="Zambia has 10 provinces."
        singular="province"
        plural="provinces"
        rows={provinces}
        loading={loading}
        error={error}
        onRetry={refetch}
        canEdit={canEdit}
        getRowId={(p) => p.id}
        searchText={(p) => `${p.name} ${p.code}`}
        onNew={() => {
          setForm({ mode: 'new' });
        }}
        columns={[
          { id: 'name', header: 'Province', rowHeader: true, cell: (p) => <b>{p.name}</b> },
          { id: 'code', header: 'Code', cell: (p) => <span className="m3-mono">{p.code}</span> },
          { id: 'cities', header: 'Cities', align: 'end', cell: (p) => p.cityCount ?? 0 },
          { id: 'status', header: 'Status', cell: (p) => <StatusPill status={p.isActive ? 'ACTIVE' : 'INACTIVE'} /> },
        ]}
        rowActions={(p) => (
          <>
            <Button
              size="sm"
              variant="tonal"
              disabled={!canEdit}
              onClick={() => {
                      setForm({ mode: 'edit', row: p });
              }}
            >
              Edit
            </Button>
            <RowMenu
              label={`More actions for ${p.name}`}
              items={[
                { id: 'toggle', label: p.isActive ? 'Deactivate' : 'Activate', disabled: !canEdit, onSelect: () => void toggle(p) },
                { id: 'delete', label: 'Delete', danger: true, disabled: !canEdit, onSelect: () => ((p.cityCount ?? 0) > 0 ? setBlocked(p) : setDeleting(p)) },
              ]}
            />
          </>
        )}
      />
      <CrudDialog
        open={form !== null}
        title={form?.mode === 'edit' ? 'Edit province' : 'New province'}
        schema={provinceSchema(provinces, self?.id)}
        fields={fields}
        defaults={form?.mode === 'edit' ? { name: form.row.name, code: form.row.code, active: form.row.isActive } : { name: '', code: '', active: true }}
        submitLabel={form?.mode === 'edit' ? 'Save changes' : 'Create'}
        onClose={() => setForm(null)}
        onSubmit={save}
      />
      <ConfirmDialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => void del()}
        title={`Delete province "${deleting?.name ?? ''}"?`}
        description="It is removed from the list. This cannot be undone."
        confirmLabel="Delete"
        danger
        loading={mutate.loading}
      />
      <Dialog open={blocked !== null} onClose={() => setBlocked(null)} title={`Cannot delete ${blocked?.name ?? ''}`} actions={<Button variant="filled" onClick={() => setBlocked(null)}>Understood</Button>}>
        <p>
          {blocked?.cityCount} {blocked?.cityCount === 1 ? 'city belongs' : 'cities belong'} to {blocked?.name}. Move or delete them first.
        </p>
      </Dialog>
    </>
  );
}

function Cities({ canEdit }: { canEdit: boolean }) {
  const { cities, loading, error, refetch } = useCitiesAdmin();
  const { provinces } = useProvincesAdmin();
  const mutate = useCityMutations();
  const snackbar = useSnackbar();
  const [form, setForm] = useState<Form<CityRow>>(null);
  const [deleting, setDeleting] = useState<CityRow | null>(null);
  const [blocked, setBlocked] = useState<CityRow | null>(null);

  const provinceOptions = provinces.map((p) => ({ value: p.id, label: p.name }));
  const fields: CrudField[] = [
    { name: 'name', label: 'City' },
    { name: 'code', label: 'Code' },
    { name: 'provinceId', label: 'Province', kind: 'select', placeholder: 'Choose a province', options: provinceOptions },
    ...(form?.mode === 'edit' ? [{ name: 'active', label: 'Active', kind: 'check' as const }] : []),
  ];
  const self = form?.mode === 'edit' ? form.row : undefined;
  const save = async (v: { name: string; code: string; provinceId: string; active: boolean }) => {
    const base = { name: v.name, code: v.code, provinceId: v.provinceId, country: COUNTRY };
    const res = form?.mode === 'edit' ? await mutate.update(form.row.id, { ...base, isActive: v.active }) : await mutate.create(base);
    if (!res.success) throw new Error(res.message ?? 'Could not save the city.');
    snackbar.show(form?.mode === 'edit' ? 'City updated' : 'City created');
    setForm(null);
  };
  const toggle = async (c: CityRow) => {
    const res = await mutate.update(c.id, { isActive: !c.isActive });
    snackbar.show({ message: res.success ? `${c.name} ${c.isActive ? 'deactivated' : 'activated'}` : (res.message ?? 'Could not change the city'), tone: res.success ? 'neutral' : 'error' });
  };
  const del = async () => {
    if (!deleting) return;
    const res = await mutate.remove(deleting.id);
    snackbar.show({ message: res.success ? 'City deleted' : (res.message ?? 'Could not delete the city'), tone: res.success ? 'neutral' : 'error' });
    setDeleting(null);
  };

  return (
    <>
      <CrudCard<CityRow>
        title="Cities"
        subtitle="Event locations are chosen from this list."
        singular="city"
        plural="cities"
        rows={cities}
        loading={loading}
        error={error}
        onRetry={refetch}
        canEdit={canEdit}
        getRowId={(c) => c.id}
        searchText={(c) => `${c.name} ${c.province ?? ''}`}
        filters={[
          {
            id: 'prov',
            label: 'Province',
            options: provinces.map((p) => ({ value: p.id, label: p.name })),
            match: (c, v) => c.provinceId === v,
          },
        ]}
        onNew={() => {
          setForm({ mode: 'new' });
        }}
        columns={[
          { id: 'name', header: 'City', rowHeader: true, cell: (c) => <b>{c.name}</b> },
          { id: 'prov', header: 'Province', cell: (c) => c.province ?? '-' },
          { id: 'events', header: 'Events', align: 'end', cell: (c) => c.eventCount ?? 0 },
          { id: 'status', header: 'Status', cell: (c) => <StatusPill status={c.isActive ? 'ACTIVE' : 'INACTIVE'} /> },
        ]}
        rowActions={(c) => (
          <>
            <Button
              size="sm"
              variant="tonal"
              disabled={!canEdit}
              onClick={() => {
                      setForm({ mode: 'edit', row: c });
              }}
            >
              Edit
            </Button>
            <RowMenu
              label={`More actions for ${c.name}`}
              items={[
                { id: 'toggle', label: c.isActive ? 'Deactivate' : 'Activate', disabled: !canEdit, onSelect: () => void toggle(c) },
                { id: 'delete', label: 'Delete', danger: true, disabled: !canEdit, onSelect: () => ((c.eventCount ?? 0) > 0 ? setBlocked(c) : setDeleting(c)) },
              ]}
            />
          </>
        )}
      />
      <CrudDialog
        open={form !== null}
        title={form?.mode === 'edit' ? 'Edit city' : 'New city'}
        schema={citySchema(cities, self?.id)}
        fields={fields}
        defaults={
          form?.mode === 'edit'
            ? { name: form.row.name, code: form.row.code ?? '', provinceId: form.row.provinceId ?? '', active: form.row.isActive }
            : { name: '', code: '', provinceId: '', active: true }
        }
        submitLabel={form?.mode === 'edit' ? 'Save changes' : 'Create'}
        onClose={() => setForm(null)}
        onSubmit={save}
      />
      <ConfirmDialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => void del()}
        title={`Delete city "${deleting?.name ?? ''}"?`}
        description="It is removed from the list. This cannot be undone."
        confirmLabel="Delete"
        danger
        loading={mutate.loading}
      />
      <Dialog open={blocked !== null} onClose={() => setBlocked(null)} title={`Cannot delete ${blocked?.name ?? ''}`} actions={<Button variant="filled" onClick={() => setBlocked(null)}>Understood</Button>}>
        <p>
          {blocked?.eventCount} {blocked?.eventCount === 1 ? 'event is' : 'events are'} in {blocked?.name}. Deactivate the city instead.
        </p>
      </Dialog>
    </>
  );
}

/** Provinces and cities, stacked as in the prototype. */
export function LocationsTab() {
  const { can } = useStaff();
  const canEdit = can('featureEvent');
  return (
    <div className="m3-stack">
      {!canEdit ? <p className="m3-muted">{needText('featureEvent')}</p> : null}
      <Provinces canEdit={canEdit} />
      <Cities canEdit={canEdit} />
    </div>
  );
}
