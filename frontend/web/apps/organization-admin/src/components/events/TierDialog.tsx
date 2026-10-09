'use client';

import { useWatch } from 'react-hook-form';
import { Dialog, FormCell, FormGrid } from '@pml.tickets/shared/components/m3';
import {
  ChipsRHF,
  Form,
  FormActions,
  MoneyRHF,
  SwitchRHF,
  TextAreaRHF,
  TextFieldRHF,
  useZodForm,
} from '@pml.tickets/shared';
import type { OrgTier, TierInput } from '@/lib/api/events';
import { tierDefaults, tierSchema, tierToInput, type TierFormValues } from './schemas';

export interface TierDialogProps {
  tier?: OrgTier | null;
  maxPerBooking?: number;
  onClose: () => void;
  /** Throw to surface a server error on the form. */
  onSave: (input: TierInput) => Promise<unknown>;
}

export function TierDialog({ tier, onClose, onSave }: TierDialogProps) {
  const form = useZodForm(tierSchema(tier?.soldQuantity ?? 0), { defaultValues: tierDefaults(tier) });
  const hidden = useWatch({ control: form.control, name: 'isHidden' });

  return (
    <Dialog open wide onClose={onClose} title={tier ? 'Edit ticket tier' : 'Add ticket tier'}>
      <Form
        form={form}
        guardLeave={false}
        aria-label="Ticket tier"
        onSubmit={async (v: TierFormValues) => {
          await onSave(tierToInput(v));
          onClose();
        }}
      >
        <FormGrid>
          <FormCell span={6}><TextFieldRHF name="name" label="Name" /></FormCell>
          <FormCell span={3}><MoneyRHF name="price" label="Price" /></FormCell>
          <FormCell span={3}><TextFieldRHF name="quantity" label="Quantity" type="number" min={1} /></FormCell>
          <FormCell span={12}><TextAreaRHF name="description" label="Description" rows={2} /></FormCell>
          <FormCell span={3}><TextFieldRHF name="minPerOrder" label="Min per order" type="number" min={1} /></FormCell>
          <FormCell span={3}><TextFieldRHF name="maxPerOrder" label="Max per order" type="number" min={1} /></FormCell>
          <FormCell span={3}><TextFieldRHF name="salesStartAt" label="Sales start" type="datetime-local" /></FormCell>
          <FormCell span={3}><TextFieldRHF name="salesEndAt" label="Sales end" type="datetime-local" /></FormCell>
          <FormCell span={6}><MoneyRHF name="earlyBirdPrice" label="Early-bird price" /></FormCell>
          <FormCell span={6}><TextFieldRHF name="earlyBirdEndsAt" label="Early-bird ends" type="datetime-local" /></FormCell>
          <FormCell span={12}><ChipsRHF name="benefits" label="Perks" helperText="Press Enter or comma after each perk." /></FormCell>
          <FormCell span={6}><SwitchRHF name="isHidden" label="Hidden tier" hint="Only people with the access code can buy." /></FormCell>
          {hidden ? <FormCell span={6}><TextFieldRHF name="accessCode" label="Access code" /></FormCell> : null}
        </FormGrid>
        <FormActions submitLabel={tier ? 'Save tier' : 'Add tier'} onCancel={onClose} />
      </Form>
    </Dialog>
  );
}
