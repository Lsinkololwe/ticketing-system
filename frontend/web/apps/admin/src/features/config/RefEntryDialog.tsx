'use client';

import { useMemo } from 'react';
import type { FieldValues } from 'react-hook-form';
import { z } from 'zod';
import { Dialog, FormCell, FormGrid } from '@pml.tickets/shared/components/m3';
import { Form, FormActions } from '@pml.tickets/shared/forms/Form';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import { SwitchRHF, TextAreaRHF, TextFieldRHF } from '@pml.tickets/shared/forms/fields';
import type { ReferenceData } from '@pml.tickets/shared/api/admin/modules/reference-data';
import { humanize } from '@/lib/format';

/** swiftCode -> Swift code */
export const keyLabel = (k: string) => humanize(k.replace(/([a-z])([A-Z])/g, '$1_$2'));

/**
 * How the form takes each metadata key. The backend says WHICH keys a type needs (`requiredMetadataKeys`) but
 * not their shapes, so the input kind is a property of the key here: a list is typed comma separated and sent
 * as an array, a flag is a switch, a count is a number. Any other key is text.
 */
export type MetadataKind = 'text' | 'list' | 'flag' | 'number';
const METADATA_KINDS: Record<string, MetadataKind> = {
  requiredDocuments: 'list',
  msisdnPrefixes: 'list',
  invitable: 'flag',
  locked: 'flag',
  days: 'number',
  minAge: 'number',
  decimals: 'number',
  rate: 'number',
  latitude: 'number',
  longitude: 'number',
};
export const kindOf = (key: string): MetadataKind => METADATA_KINDS[key] ?? 'text';

export interface RefEntryValues {
  name: string;
  code: string;
  description: string;
  displayOrder?: number;
  /** Strings, numbers, booleans and string arrays, by the key's kind. */
  metadata: Record<string, unknown>;
}

const splitList = (text: string) => text.split(',').map((x) => x.trim()).filter(Boolean);

/**
 * @param choices keys whose list items must be codes the platform lists (e.g. `requiredDocuments` against the
 *                KYB document types). A key with no loaded choices is not checked here; the server is.
 */
export function refEntrySchema(existingCodes: string[], metadataKeys: string[], choices: Record<string, readonly string[]> = {}) {
  const field = (k: string) => {
    switch (kindOf(k)) {
      case 'flag':
        return z.boolean({ error: 'Required for this type' });
      case 'number':
        return z.number({ error: 'Enter a number' });
      case 'list':
        return z
          .string({ error: 'Required for this type' })
          .transform(splitList)
          .pipe(
            z
              .array(z.string())
              .min(1, 'Enter at least one value, separated by commas')
              .refine((items) => !choices[k]?.length || items.every((i) => choices[k].includes(i)), {
                message: `Use only listed values: ${(choices[k] ?? []).join(', ')}`,
              }),
          );
      default:
        return z.string({ error: 'Required for this type' }).trim().min(1, 'Required for this type');
    }
  };
  return z.object({
    name: z.string({ error: 'Enter a name' }).trim().min(1, 'Enter a name'),
    code: z
      .string({ error: 'Enter a code' })
      .trim()
      .toUpperCase()
      .regex(/^[A-Z0-9_]{2,24}$/, 'Capital letters, digits and underscores (2 to 24 characters)')
      .refine((c) => !existingCodes.includes(c), 'Already exists'),
    description: z.string(),
    displayOrder: z.number({ error: 'Whole number, 0 or more' }).int('Whole number, 0 or more').min(0, 'Whole number, 0 or more').optional(),
    metadata: z.object(Object.fromEntries(metadataKeys.map((k) => [k, field(k)]))),
  });
}

function initialOf(key: string, value: unknown): unknown {
  switch (kindOf(key)) {
    case 'flag':
      return value === true;
    case 'number':
      return typeof value === 'number' ? value : undefined;
    case 'list':
      return Array.isArray(value) ? value.join(', ') : '';
    default:
      return value == null ? '' : String(value);
  }
}

export interface RefEntryDialogProps {
  /** null creates a new entry. */
  entry: ReferenceData | null;
  typeLabel: string;
  existingCodes: string[];
  metadataKeys: string[];
  /** Listed codes a list-valued key may use, by key. */
  metadataChoices?: Record<string, readonly string[]>;
  onClose: () => void;
  onSubmit: (values: RefEntryValues) => Promise<void>;
}

/** Create / edit dialog for one reference entry. Mount only while open so every opening starts clean. */
export function RefEntryDialog({ entry, typeLabel, existingCodes, metadataKeys, metadataChoices, onClose, onSubmit }: RefEntryDialogProps) {
  const isNew = entry === null;
  const schema = useMemo(
    () => refEntrySchema(isNew ? existingCodes.map((c) => c.toUpperCase()) : [], metadataKeys, metadataChoices),
    [isNew, existingCodes, metadataKeys, metadataChoices],
  );
  const meta = (entry?.metadata ?? {}) as Record<string, unknown>;
  const form = useZodForm(schema, {
    defaultValues: {
      name: entry?.name ?? '',
      code: entry?.code ?? '',
      description: entry?.description ?? '',
      displayOrder: entry?.displayOrder ?? undefined,
      metadata: Object.fromEntries(metadataKeys.map((k) => [k, initialOf(k, meta[k])])) as never,
    },
  });
  const title = isNew ? `New ${typeLabel.toLowerCase()} entry` : 'Edit entry';

  return (
    <Dialog open onClose={onClose} title={title}>
      <Form<FieldValues, RefEntryValues> form={form as never} onSubmit={(v: RefEntryValues) => onSubmit(v)} guardLeave={false} aria-label={title}>
        <FormGrid>
          <FormCell span={12}>
            <TextFieldRHF name="name" label="Name" required />
          </FormCell>
          <FormCell span={12}>
            <TextFieldRHF
              name="code"
              label="Code"
              required
              disabled={!isNew}
              helperText={isNew ? 'Capital letters, digits and underscores. Cannot be changed later.' : 'The code cannot be changed.'}
            />
          </FormCell>
          <FormCell span={12}>
            <TextAreaRHF name="description" label="Description" rows={2} />
          </FormCell>
          <FormCell span={6}>
            <TextFieldRHF name="displayOrder" label="Display order" type="number" inputMode="numeric" />
          </FormCell>
          {metadataKeys.map((k) => (
            <FormCell key={k} span={6}>
              {kindOf(k) === 'flag' ? (
                <SwitchRHF name={`metadata.${k}`} label={keyLabel(k)} />
              ) : kindOf(k) === 'number' ? (
                <TextFieldRHF name={`metadata.${k}`} label={keyLabel(k)} type="number" inputMode="decimal" required />
              ) : (
                <TextFieldRHF
                  name={`metadata.${k}`}
                  label={keyLabel(k)}
                  required
                  helperText={kindOf(k) === 'list' ? 'Separate values with commas' : undefined}
                />
              )}
            </FormCell>
          ))}
        </FormGrid>
        <FormActions submitLabel={isNew ? 'Create' : 'Save changes'} onCancel={onClose} />
      </Form>
    </Dialog>
  );
}
