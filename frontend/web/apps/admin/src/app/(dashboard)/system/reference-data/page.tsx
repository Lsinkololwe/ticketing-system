'use client';

/**
 * Reference Data Management (Admin)
 *
 * One screen drives every reference type. The left rail is built from the backend type registry
 * (`useReferenceTypes`), grouped by domain; the right pane is a table of rows for the selected type
 * with inline create/edit, active toggle, and delete. System rows are locked (deactivate, not delete).
 *
 * All data + mutations come from the shared module — no inline GraphQL, per the shared-library rule.
 */

import { useMemo, useState } from 'react';
import {
  Badge,
  Box,
  Button,
  Callout,
  Dialog,
  Flex,
  Heading,
  IconButton,
  Spinner,
  Switch,
  Table,
  Text,
  TextArea,
  TextField,
  Tooltip,
} from '@radix-ui/themes';
import {
  Database,
  EditPencil,
  InfoCircle,
  Lock,
  Plus,
  Trash,
  WarningTriangle,
} from 'iconoir-react';
import {
  useReferenceTypes,
  useReferenceDataAdmin,
  useCreateReferenceData,
  useUpdateReferenceData,
  useDeleteReferenceData,
  useToggleReferenceDataActive,
  type ReferenceData,
  type ReferenceType,
  type ReferenceTypeInfo,
} from '@pml.tickets/shared/api/admin/modules/reference-data';

interface FormState {
  code: string;
  name: string;
  description: string;
  parentCode: string;
  displayOrder: string;
  isActive: boolean;
  metadataJson: string;
}

const EMPTY_FORM: FormState = {
  code: '',
  name: '',
  description: '',
  parentCode: '',
  displayOrder: '0',
  isActive: true,
  metadataJson: '{}',
};

export default function ReferenceDataPage() {
  const { types, loading: typesLoading } = useReferenceTypes();
  const [selectedType, setSelectedType] = useState<ReferenceType | null>(null);
  const [search, setSearch] = useState('');

  // Default to the first type once the registry loads.
  const activeType = selectedType ?? types[0]?.type ?? null;
  const activeInfo = types.find((t) => t.type === activeType) ?? null;

  const groups = useMemo(() => groupTypes(types), [types]);

  return (
    <Box p="5">
      <Flex align="center" gap="3" mb="4">
        <Database width={24} height={24} />
        <Box>
          <Heading size="6">Reference data</Heading>
          <Text size="2" color="gray">
            Manage every lookup list on the platform — operators, banks, currencies, genres, reasons and more.
          </Text>
        </Box>
      </Flex>

      <Flex gap="5" align="start" direction={{ initial: 'column', md: 'row' }}>
        {/* ── Type rail ── */}
        <Box style={{ minWidth: 240, width: 240, flexShrink: 0 }}>
          {typesLoading ? (
            <Spinner />
          ) : (
            groups.map((group) => (
              <Box key={group.group} mb="4">
                <Text className="ds-label" as="p">
                  {group.groupLabel}
                </Text>
                <Flex direction="column" gap="1" mt="2">
                  {group.items.map((t) => (
                    <Button
                      key={t.type}
                      data-testid={`ref-type-${t.type}`}
                      variant={t.type === activeType ? 'solid' : 'ghost'}
                      color={t.type === activeType ? undefined : 'gray'}
                      radius="medium"
                      style={{ justifyContent: 'flex-start' }}
                      onClick={() => setSelectedType(t.type)}
                    >
                      {t.label}
                    </Button>
                  ))}
                </Flex>
              </Box>
            ))
          )}
        </Box>

        {/* ── Table pane ── */}
        <Box style={{ flex: 1, minWidth: 0, width: '100%' }}>
          {activeType && activeInfo ? (
            <ReferenceTypePanel
              key={activeType}
              type={activeType}
              info={activeInfo}
              search={search}
              onSearchChange={setSearch}
            />
          ) : (
            <Text color="gray">Select a reference type.</Text>
          )}
        </Box>
      </Flex>
    </Box>
  );
}

// ============================================================================
// Panel for a single type
// ============================================================================

function ReferenceTypePanel({
  type,
  info,
  search,
  onSearchChange,
}: {
  type: ReferenceType;
  info: ReferenceTypeInfo;
  search: string;
  onSearchChange: (v: string) => void;
}) {
  const { items, loading, error } = useReferenceDataAdmin(type);
  const { setActive } = useToggleReferenceDataActive(type);
  const { remove } = useDeleteReferenceData(type);

  const [dialogOpen, setDialogOpen] = useState(false);
  const [editing, setEditing] = useState<ReferenceData | null>(null);
  const [banner, setBanner] = useState<string | null>(null);

  const filtered = items.filter(
    (i) =>
      i.name.toLowerCase().includes(search.toLowerCase()) ||
      i.code.toLowerCase().includes(search.toLowerCase())
  );

  const openCreate = () => {
    setEditing(null);
    setDialogOpen(true);
  };
  const openEdit = (item: ReferenceData) => {
    setEditing(item);
    setDialogOpen(true);
  };

  const onDelete = async (item: ReferenceData) => {
    setBanner(null);
    const res = await remove(item.id);
    if (res && !res.success) setBanner(res.message ?? 'Delete failed');
  };

  const onToggle = async (item: ReferenceData) => {
    setBanner(null);
    const res = await setActive(item.id, !item.isActive);
    if (res && !res.success) setBanner(res.message ?? 'Update failed');
  };

  return (
    <Box>
      <Flex justify="between" align="center" mb="3" gap="3" wrap="wrap">
        <Box>
          <Heading size="4">{info.label}</Heading>
          <Text size="1" color="gray">
            {items.length} {items.length === 1 ? 'entry' : 'entries'}
            {info.requiredMetadataKeys.length > 0 && (
              <> · required metadata: {info.requiredMetadataKeys.join(', ')}</>
            )}
          </Text>
        </Box>
        <Flex gap="2">
          <TextField.Root
            placeholder="Search…"
            value={search}
            onChange={(e) => onSearchChange(e.target.value)}
            style={{ width: 220 }}
          />
          <Button data-testid="ref-add" onClick={openCreate}>
            <Plus width={16} height={16} /> Add
          </Button>
        </Flex>
      </Flex>

      {banner && (
        <Callout.Root color="red" mb="3">
          <Callout.Icon><WarningTriangle /></Callout.Icon>
          <Callout.Text>{banner}</Callout.Text>
        </Callout.Root>
      )}
      {error && (
        <Callout.Root color="red" mb="3">
          <Callout.Icon><WarningTriangle /></Callout.Icon>
          <Callout.Text>Failed to load reference data.</Callout.Text>
        </Callout.Root>
      )}

      <Box style={{ overflowX: 'auto' }}>
        <Table.Root variant="surface">
          <Table.Header>
            <Table.Row>
              <Table.ColumnHeaderCell>Code</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Name</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Parent</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Order</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Active</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell>Source</Table.ColumnHeaderCell>
              <Table.ColumnHeaderCell align="right">Actions</Table.ColumnHeaderCell>
            </Table.Row>
          </Table.Header>
          <Table.Body>
            {loading && filtered.length === 0 ? (
              <Table.Row>
                <Table.Cell colSpan={7}><Flex align="center" gap="2" py="3"><Spinner /> Loading…</Flex></Table.Cell>
              </Table.Row>
            ) : filtered.length === 0 ? (
              <Table.Row>
                <Table.Cell colSpan={7}><Text color="gray">No entries.</Text></Table.Cell>
              </Table.Row>
            ) : (
              filtered.map((item) => (
                <Table.Row key={item.id} align="center">
                  <Table.RowHeaderCell>
                    <Text style={{ fontFamily: 'var(--code-font-family, monospace)' }}>{item.code}</Text>
                  </Table.RowHeaderCell>
                  <Table.Cell>
                    <Text weight="medium">{item.name}</Text>
                    {item.description && (
                      <Text as="p" size="1" color="gray">{item.description}</Text>
                    )}
                  </Table.Cell>
                  <Table.Cell>
                    {item.parentCode ? <Badge variant="soft" color="gray">{item.parentCode}</Badge> : <Text color="gray">—</Text>}
                  </Table.Cell>
                  <Table.Cell>{item.displayOrder}</Table.Cell>
                  <Table.Cell>
                    <Switch
                      data-testid={`ref-active-${item.id}`}
                      checked={item.isActive}
                      onCheckedChange={() => onToggle(item)}
                      aria-label={`Toggle ${item.name}`}
                    />
                  </Table.Cell>
                  <Table.Cell>
                    {item.isSystem ? (
                      <Badge color="blue" variant="soft">
                        <Lock width={12} height={12} /> System
                      </Badge>
                    ) : (
                      <Badge color="gray" variant="soft">Custom</Badge>
                    )}
                  </Table.Cell>
                  <Table.Cell align="right">
                    <Flex gap="2" justify="end">
                      <Tooltip content="Edit">
                        <IconButton variant="soft" color="gray" onClick={() => openEdit(item)}>
                          <EditPencil width={16} height={16} />
                        </IconButton>
                      </Tooltip>
                      <Tooltip content={item.isSystem ? 'System rows cannot be deleted — deactivate instead' : 'Delete'}>
                        <IconButton
                          variant="soft"
                          color="red"
                          disabled={item.isSystem}
                          onClick={() => onDelete(item)}
                        >
                          <Trash width={16} height={16} />
                        </IconButton>
                      </Tooltip>
                    </Flex>
                  </Table.Cell>
                </Table.Row>
              ))
            )}
          </Table.Body>
        </Table.Root>
      </Box>

      {dialogOpen && (
        <ReferenceDataDialog
          type={type}
          info={info}
          editing={editing}
          onClose={() => setDialogOpen(false)}
        />
      )}
    </Box>
  );
}

// ============================================================================
// Create / edit dialog
// ============================================================================

function ReferenceDataDialog({
  type,
  info,
  editing,
  onClose,
}: {
  type: ReferenceType;
  info: ReferenceTypeInfo;
  editing: ReferenceData | null;
  onClose: () => void;
}) {
  const { create, loading: creating } = useCreateReferenceData(type);
  const { update, loading: updating } = useUpdateReferenceData(type);
  const isEdit = !!editing;

  const [form, setForm] = useState<FormState>(
    editing
      ? {
          code: editing.code,
          name: editing.name,
          description: editing.description ?? '',
          parentCode: editing.parentCode ?? '',
          displayOrder: String(editing.displayOrder),
          isActive: editing.isActive,
          metadataJson: JSON.stringify(editing.metadata ?? {}, null, 2),
        }
      : EMPTY_FORM
  );
  const [formError, setFormError] = useState<string | null>(null);

  const set = <K extends keyof FormState>(key: K, value: FormState[K]) =>
    setForm((f) => ({ ...f, [key]: value }));

  const submit = async () => {
    setFormError(null);
    let metadata: Record<string, unknown>;
    try {
      metadata = form.metadataJson.trim() ? JSON.parse(form.metadataJson) : {};
    } catch {
      setFormError('Metadata must be valid JSON.');
      return;
    }
    const missing = info.requiredMetadataKeys.filter((k) => metadata[k] == null || metadata[k] === '');
    if (missing.length > 0) {
      setFormError(`Missing required metadata: ${missing.join(', ')}`);
      return;
    }

    const displayOrder = Number.parseInt(form.displayOrder, 10) || 0;

    const res = isEdit
      ? await update(editing!.id, {
          name: form.name,
          description: form.description || null,
          parentCode: form.parentCode || null,
          displayOrder,
          isActive: form.isActive,
          metadata,
        })
      : await create({
          type,
          code: form.code,
          name: form.name,
          description: form.description || null,
          parentCode: form.parentCode || null,
          displayOrder,
          isActive: form.isActive,
          metadata,
        });

    if (res && !res.success) {
      setFormError(res.message ?? 'Save failed');
      return;
    }
    onClose();
  };

  const busy = creating || updating;

  return (
    <Dialog.Root open onOpenChange={(o) => !o && onClose()}>
      <Dialog.Content maxWidth="520px">
        <Dialog.Title>{isEdit ? `Edit ${info.label}` : `Add to ${info.label}`}</Dialog.Title>
        <Dialog.Description size="2" color="gray" mb="4">
          {isEdit ? 'Update this reference entry.' : 'Create a new reference entry.'}
        </Dialog.Description>

        <Flex direction="column" gap="3">
          <label>
            <Text as="div" size="2" mb="1" weight="medium">Code</Text>
            <TextField.Root
              placeholder="e.g. ZANACO"
              value={form.code}
              disabled={isEdit}
              onChange={(e) => set('code', e.target.value)}
            />
            {isEdit && <Text size="1" color="gray">Code is immutable.</Text>}
          </label>

          <label>
            <Text as="div" size="2" mb="1" weight="medium">Name</Text>
            <TextField.Root
              placeholder="Display name"
              value={form.name}
              onChange={(e) => set('name', e.target.value)}
            />
          </label>

          <label>
            <Text as="div" size="2" mb="1" weight="medium">Description</Text>
            <TextField.Root
              placeholder="Optional"
              value={form.description}
              onChange={(e) => set('description', e.target.value)}
            />
          </label>

          <Flex gap="3">
            <label style={{ flex: 1 }}>
              <Text as="div" size="2" mb="1" weight="medium">Parent code</Text>
              <TextField.Root
                placeholder="Optional"
                value={form.parentCode}
                onChange={(e) => set('parentCode', e.target.value)}
              />
            </label>
            <label style={{ width: 120 }}>
              <Text as="div" size="2" mb="1" weight="medium">Order</Text>
              <TextField.Root
                type="number"
                value={form.displayOrder}
                onChange={(e) => set('displayOrder', e.target.value)}
              />
            </label>
          </Flex>

          <label>
            <Flex align="center" gap="2">
              <Switch
                data-testid="ref-form-active"
                checked={form.isActive}
                onCheckedChange={(v) => set('isActive', v)}
              />
              <Text size="2">Active</Text>
            </Flex>
          </label>

          <label>
            <Flex align="center" gap="1" mb="1">
              <Text as="div" size="2" weight="medium">Metadata (JSON)</Text>
              {info.requiredMetadataKeys.length > 0 && (
                <Tooltip content={`Required: ${info.requiredMetadataKeys.join(', ')}`}>
                  <InfoCircle width={14} height={14} />
                </Tooltip>
              )}
            </Flex>
            <TextArea
              rows={5}
              style={{ fontFamily: 'var(--code-font-family, monospace)' }}
              value={form.metadataJson}
              onChange={(e) => set('metadataJson', e.target.value)}
            />
            {info.requiredMetadataKeys.length > 0 && (
              <Text size="1" color="gray">Required keys: {info.requiredMetadataKeys.join(', ')}</Text>
            )}
          </label>

          {formError && (
            <Callout.Root color="red">
              <Callout.Icon><WarningTriangle /></Callout.Icon>
              <Callout.Text>{formError}</Callout.Text>
            </Callout.Root>
          )}
        </Flex>

        <Flex gap="3" mt="4" justify="end">
          <Dialog.Close>
            <Button data-testid="ref-cancel" variant="soft" color="gray" disabled={busy}>Cancel</Button>
          </Dialog.Close>
          <Button data-testid="ref-submit" onClick={submit} disabled={busy || !form.code || !form.name}>
            {busy ? <Spinner /> : null}
            {isEdit ? 'Save changes' : 'Create'}
          </Button>
        </Flex>
      </Dialog.Content>
    </Dialog.Root>
  );
}

// ============================================================================
// Helpers
// ============================================================================

interface TypeGroup {
  group: string;
  groupLabel: string;
  items: ReferenceTypeInfo[];
}

function groupTypes(types: ReferenceTypeInfo[]): TypeGroup[] {
  const order: string[] = [];
  const map = new Map<string, TypeGroup>();
  for (const t of types) {
    if (!map.has(t.group)) {
      map.set(t.group, { group: t.group, groupLabel: t.groupLabel, items: [] });
      order.push(t.group);
    }
    map.get(t.group)!.items.push(t);
  }
  return order.map((g) => map.get(g)!);
}
