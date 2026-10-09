'use client';

import { useFieldArray, useFormContext, type ArrayPath, type FieldValues } from 'react-hook-form';

/**
 * Field-array helper for repeating rows (ticket tiers, team invites, line items).
 * Rows carry a stable `key` (never use the index as React key). Row field names are
 * `${name}.${index}.${field}` for the `*RHF` wrappers.
 *
 * ```tsx
 * const rows = useFieldRows<EventInput>('tiers', () => ({ name: '', price: undefined, capacity: undefined }), { min: 1, max: 10 });
 * rows.fields.map((row, i) => <TextFieldRHF key={row.key} name={rows.path(i, 'name')} label="Tier name" />)
 * <Button onClick={rows.append} disabled={!rows.canAdd}>Add tier</Button>
 * ```
 */
export function useFieldRows<T extends FieldValues, Row = unknown>(
  name: string,
  makeRow: () => Row,
  limits: { min?: number; max?: number } = {}
) {
  const { control } = useFormContext<T>();
  const array = useFieldArray({ control, name: name as ArrayPath<T>, keyName: 'key' });
  const { min = 0, max = Infinity } = limits;
  const count = array.fields.length;
  return {
    fields: array.fields as unknown as Array<{ key: string }>,
    count,
    canAdd: count < max,
    canRemove: count > min,
    path: (index: number, field: string) => `${name}.${index}.${field}`,
    append: () => {
      if (count < max) array.append(makeRow() as never);
    },
    remove: (index: number) => {
      if (count > min) array.remove(index);
    },
    move: array.move,
    insert: (index: number) => {
      if (count < max) array.insert(index, makeRow() as never);
    },
  };
}
