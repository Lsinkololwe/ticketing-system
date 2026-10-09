'use client';

import { useState } from 'react';
import { Button, Dialog, StatusPill, ConfirmDialog, RowMenu, useSnackbar } from '@pml.tickets/shared/components/m3';
import { useAdminEventCategories } from '@pml.tickets/shared/api/admin/modules/event';
import { useCategoryMutations } from '@pml.tickets/shared/api/admin/modules/catalog-admin';
import { useStaff } from '@/components/console';
import { needText } from '@/lib/permissions';
import { CrudCard } from './CrudCard';
import { CrudDialog, type CrudField } from './CrudDialog';
import { categorySchema } from './schemas';

type Cat = {
  id: string;
  name: string;
  code: string;
  description: string | null;
  eventCount: number | null;
  isActive: boolean;
};

const FIELDS: CrudField[] = [
  { name: 'name', label: 'Name' },
  { name: 'code', label: 'Code' },
  { name: 'description', label: 'Description', kind: 'textarea' },
];

/** Event categories: CRUD with in-use rules (a category with events can only be deactivated). */
export function CategoriesTab() {
  const { categories, loading, error, refetch } = useAdminEventCategories({ size: 100 });
  const mutate = useCategoryMutations();
  const snackbar = useSnackbar();
  const { can } = useStaff();
  const canEdit = can('featureEvent');
  const [form, setForm] = useState<{ mode: 'new' } | { mode: 'edit'; row: Cat } | null>(null);
  const [deleting, setDeleting] = useState<Cat | null>(null);
  const [blocked, setBlocked] = useState<Cat | null>(null);

  const rows = categories as Cat[];
  const save = async (values: { name: string; code: string; description: string; active: boolean }) => {
    const description = values.description || null;
    if (form?.mode === 'edit') {
      const row = form.row;
      // The code is fixed once created: events refer to a category by it.
      let res = await mutate.update(row.id, { name: values.name, description });
      if (res.success && values.active !== row.isActive) res = await mutate.setActive(row.id, values.active);
      if (!res.success) throw new Error(res.message ?? 'Could not save the category.');
      snackbar.show('Category updated');
    } else {
      const res = await mutate.create({ name: values.name, code: values.code, description });
      if (!res.success) throw new Error(res.message ?? 'Could not create the category.');
      snackbar.show('Category created');
    }
    setForm(null);
  };

  const toggle = async (row: Cat) => {
    const res = await mutate.setActive(row.id, !row.isActive);
    snackbar.show({ message: res.success ? `${row.name} ${row.isActive ? 'deactivated' : 'activated'}` : (res.message ?? 'Could not change the category'), tone: res.success ? 'neutral' : 'error' });
  };

  const del = async () => {
    if (!deleting) return;
    const res = await mutate.remove(deleting.id);
    snackbar.show({ message: res.success ? 'Category deleted' : (res.message ?? 'Could not delete the category'), tone: res.success ? 'neutral' : 'error' });
    setDeleting(null);
  };

  const fields: CrudField[] =
    form?.mode === 'edit'
      ? [...FIELDS.filter((f) => f.name !== 'code'), { name: 'active', label: 'Active', kind: 'check' }]
      : FIELDS;
  const initial =
    form?.mode === 'edit'
      ? { name: form.row.name, code: form.row.code, description: form.row.description ?? '', active: form.row.isActive }
      : { name: '', code: '', description: '', active: true };
  const schema = categorySchema(rows, form?.mode === 'edit' ? form.row.id : undefined);

  return (
    <>
      {!canEdit ? <p className="m3-muted">{needText('featureEvent')}</p> : null}
      <CrudCard<Cat>
        title="Event categories"
        subtitle="Each event has exactly one category. Active categories are published to the organizer and buyer apps when you save."
        singular="category"
        plural="categories"
        rows={rows}
        loading={loading}
        error={error}
        onRetry={refetch}
        canEdit={canEdit}
        getRowId={(r) => r.id}
        searchText={(r) => `${r.name} ${r.code} ${r.description ?? ''}`}
        onNew={() => {
          setForm({ mode: 'new' });
        }}
        columns={[
          { id: 'name', header: 'Name', rowHeader: true, cell: (r) => <b>{r.name}</b> },
          { id: 'code', header: 'Code', cell: (r) => <span className="m3-mono">{r.code}</span> },
          { id: 'desc', header: 'Description', cell: (r) => r.description || '-' },
          // Counted for active categories only; an inactive one shows a dash rather than a false 0.
          { id: 'events', header: 'Events', align: 'end', cell: (r) => r.eventCount ?? '-' },
          { id: 'status', header: 'Status', cell: (r) => <StatusPill status={r.isActive ? 'ACTIVE' : 'INACTIVE'} /> },
        ]}
        rowActions={(r) => (
          <>
            <Button
              size="sm"
              variant="tonal"
              disabled={!canEdit}
              onClick={() => {
                setForm({ mode: 'edit', row: r });
              }}
            >
              Edit
            </Button>
            <RowMenu
              label={`More actions for ${r.name}`}
              items={[
                { id: 'toggle', label: r.isActive ? 'Deactivate' : 'Activate', disabled: !canEdit, onSelect: () => void toggle(r) },
                { id: 'delete', label: 'Delete', danger: true, disabled: !canEdit, onSelect: () => ((r.eventCount ?? 0) > 0 ? setBlocked(r) : setDeleting(r)) },
              ]}
            />
          </>
        )}
      />
      <CrudDialog
        open={form !== null}
        title={form?.mode === 'edit' ? 'Edit category' : 'New category'}
        schema={schema}
        fields={fields}
        defaults={initial}
        submitLabel={form?.mode === 'edit' ? 'Save changes' : 'Create'}
        onClose={() => setForm(null)}
        onSubmit={save}
      />
      <ConfirmDialog
        open={deleting !== null}
        onClose={() => setDeleting(null)}
        onConfirm={() => void del()}
        title={`Delete category "${deleting?.name ?? ''}"?`}
        description="Reference data is kept, because events refer to a category by its code. The category is deactivated: it stays on existing events and cannot be chosen for new ones."
        confirmLabel="Delete"
        danger
        loading={mutate.loading}
      />
      <Dialog
        open={blocked !== null}
        onClose={() => setBlocked(null)}
        title={`Cannot delete ${blocked?.name ?? ''}`}
        actions={
          <Button variant="filled" onClick={() => setBlocked(null)}>
            Understood
          </Button>
        }
      >
        <p>
          {blocked?.eventCount} {blocked?.eventCount === 1 ? 'event uses' : 'events use'} this category. Deactivate it instead so it stays on existing events but
          cannot be chosen for new ones.
        </p>
      </Dialog>
    </>
  );
}
