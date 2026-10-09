'use client';

import { enumValues, DISCOUNT_TYPE_LABELS } from '@/lib/format/enumLabels';
import { useController, useFormContext } from 'react-hook-form';
import { Checkbox, Dialog, FormCell, FormGrid } from '@pml.tickets/shared/components/m3';
import {
  Form,
  FormActions,
  MoneyRHF,
  SelectRHF,
  SwitchRHF,
  TextFieldRHF,
  DateRHF,
  useZodForm,
} from '@pml.tickets/shared';
import type { PromoInput, PromoRow } from '@/lib/api/promos';
import type { OrgTier } from '@/lib/api/events';
import { promoDefaults, promoSchema, promoToInput, type PromoFormValues } from './schemas';

export interface PromoDialogProps {
  promo?: PromoRow | null;
  tiers: OrgTier[];
  existingCodes: string[];
  onClose: () => void;
  /** Throw to surface a server error on the form. */
  onSave: (input: PromoInput, active: boolean) => Promise<unknown>;
}

function TierChecks({ tiers }: { tiers: OrgTier[] }) {
  const { control } = useFormContext();
  const { field } = useController({ control, name: 'applicableTiers' });
  const value = (field.value as string[] | undefined) ?? [];
  return (
    <fieldset className="m3-stack" data-field="applicableTiers">
      <legend>Applies to</legend>
      {tiers.map((t) => (
        <Checkbox
          key={t.id}
          label={t.name}
          checked={value.includes(t.id)}
          onChange={(e) => field.onChange(e.target.checked ? [...value, t.id] : value.filter((x) => x !== t.id))}
        />
      ))}
      <small className="m3-muted">Leave all unticked to apply to every tier.</small>
    </fieldset>
  );
}

export function PromoDialog({ promo, tiers, existingCodes, onClose, onSave }: PromoDialogProps) {
  const form = useZodForm(promoSchema(existingCodes, Boolean(promo)), { defaultValues: promoDefaults(promo) });
  return (
    <Dialog open wide onClose={onClose} title={promo ? 'Edit promo code' : 'New promo code'}>
      <Form
        form={form}
        guardLeave={false}
        aria-label="Promo code"
        fieldMap={{ PROMO_CODE_EXISTS: 'code', DUPLICATE_CODE: 'code' }}
        onSubmit={async (v: PromoFormValues) => {
          await onSave(promoToInput(v), v.isActive);
          onClose();
        }}
      >
        <FormGrid>
          <FormCell span={6}><TextFieldRHF name="code" label="Code" disabled={Boolean(promo)} helperText="Letters and numbers, 4 to 20 characters." /></FormCell>
          <FormCell span={6}>
            <SelectRHF
              name="discountType"
              label="Discount type"
              options={enumValues(DISCOUNT_TYPE_LABELS).map((value) => ({ value, label: DISCOUNT_TYPE_LABELS[value] }))}
            />
          </FormCell>
          <FormCell span={3}><TextFieldRHF name="discountValue" label="Discount value" type="number" min={0} /></FormCell>
          <FormCell span={3}><TextFieldRHF name="maxUses" label="Maximum uses" type="number" min={0} helperText="0 for unlimited" /></FormCell>
          <FormCell span={3}><DateRHF name="validFrom" label="Valid from" /></FormCell>
          <FormCell span={3}><DateRHF name="validUntil" label="Valid until" /></FormCell>
          <FormCell span={6}><MoneyRHF name="minPurchaseAmount" label="Minimum purchase" /></FormCell>
          <FormCell span={6}><MoneyRHF name="maxDiscountAmount" label="Maximum discount" helperText="Percentage codes only. Leave empty for no cap." /></FormCell>
          <FormCell span={12}><TierChecks tiers={tiers} /></FormCell>
          <FormCell span={12}><SwitchRHF name="isActive" label="Active" /></FormCell>
        </FormGrid>
        <FormActions submitLabel={promo ? 'Save code' : 'Create code'} onCancel={onClose} />
      </Form>
    </Dialog>
  );
}
