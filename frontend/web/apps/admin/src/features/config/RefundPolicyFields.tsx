'use client';

import { useEffect, useMemo, useState } from 'react';
import { useFieldArray, useFormContext } from 'react-hook-form';
import { z } from 'zod';
import { Button, Card, CardHeader, DataTable, Dialog, FormCell, FormGrid, TextArea, TextField } from '@pml.tickets/shared/components/m3';
import { useZodForm } from '@pml.tickets/shared/forms/useZodForm';
import type { ApprovalDraft } from './approval';

type Policy = ApprovalDraft['refundPolicies'][number];

const MAX_RULES = 3;
const num = z.union([z.literal(''), z.coerce.number().int().min(0, 'Cannot be negative')]);
const editSchema = z.object({
  summary: z.string().trim().min(8, 'Describe the policy for buyers (at least 8 characters)'),
  rules: z.array(z.object({ daysBefore: num, percent: num })),
}).superRefine((v, ctx) => {
  v.rules.forEach((r, i) => {
    const d = r.daysBefore === '' , p = r.percent === '';
    if (d !== p) ctx.addIssue({ code: 'custom', path: ['rules', i, p ? 'percent' : 'daysBefore'], message: 'Fill both fields or leave both empty' });
    else if (!p && Number(r.percent) > 100) ctx.addIssue({ code: 'custom', path: ['rules', i, 'percent'], message: 'Percent is at most 100' });
  });
});
type EditValues = z.input<typeof editSchema>;

export const ruleText = (p: Pick<Policy, 'rules'>) =>
  p.rules.length === 0 ? 'No refunds' : [...p.rules].sort((a, b) => b.daysBefore - a.daysBefore).map((r) => `${r.percent}% until ${r.daysBefore} day${r.daysBefore === 1 ? '' : 's'} before`).join(', ');

function EditDialog({ policy, onClose, onSave }: { policy: Policy | null; onClose: () => void; onSave: (v: { summary: string; rules: Policy['rules'] }) => void }) {
  const schema = useMemo(() => editSchema, []);
  const form = useZodForm(schema, { defaultValues: { summary: '', rules: [] as EditValues['rules'] } });
  const { register, handleSubmit, reset, formState: { errors } } = form;
  useEffect(() => {
    if (!policy) return;
    reset({ summary: policy.summary, rules: Array.from({ length: MAX_RULES }, (_, i) => ({ daysBefore: policy.rules[i]?.daysBefore ?? '', percent: policy.rules[i]?.percent ?? '' })) });
  }, [policy, reset]);
  return (
    <Dialog
      open={policy !== null}
      onClose={onClose}
      title={policy ? `Edit ${policy.label} policy` : ''}
      actions={
        <>
          <Button variant="text" onClick={onClose}>Cancel</Button>
          <Button
            variant="filled"
            onClick={handleSubmit((v) => {
              const rules = v.rules
                .filter((r) => r.daysBefore !== '' && r.percent !== '')
                .map((r) => ({ daysBefore: Number(r.daysBefore), percent: Number(r.percent) }))
                .sort((a, b) => b.daysBefore - a.daysBefore);
              onSave({ summary: v.summary, rules });
            })}
          >
            Update policy
          </Button>
        </>
      }
    >
      <p className="m3-muted">This text is shown to organizers and buyers. Applies to new events; events already live keep the rules they were published under.</p>
      <TextArea label="Summary shown to buyers" rows={2} errorText={errors.summary?.message} {...register('summary')} />
      <FormGrid>
        {Array.from({ length: MAX_RULES }, (_, i) => (
          <FormCell key={i} span={12}>
            <FormGrid>
              <FormCell span={6}><TextField label={`Rule ${i + 1}: days before event`} type="number" inputMode="numeric" errorText={errors.rules?.[i]?.daysBefore?.message} {...register(`rules.${i}.daysBefore`)} /></FormCell>
              <FormCell span={6}><TextField label={`Rule ${i + 1}: refund percent`} type="number" inputMode="numeric" errorText={errors.rules?.[i]?.percent?.message} {...register(`rules.${i}.percent`)} /></FormCell>
            </FormGrid>
          </FormCell>
        ))}
      </FormGrid>
    </Dialog>
  );
}

/** Refund policies as the prototype shows them: a read-only table with an Edit dialog; Save configuration publishes. */
export function RefundPolicyFields({ disabled }: { disabled?: boolean }) {
  const { control, setValue } = useFormContext<ApprovalDraft>();
  const { fields } = useFieldArray({ control, name: 'refundPolicies' });
  const [editing, setEditing] = useState<number | null>(null);
  return (
    <Card as="section" aria-label="Refund policies">
      <CardHeader title="Refund policies" subtitle="Organizers pick one per event. Edits apply to new events only." />
      <DataTable<Policy & { id: string; index: number }>
        caption="Refund policy list"
        rows={fields.map((f, index) => ({ ...(f as unknown as Policy), id: f.id, index }))}
        getRowId={(r) => r.id}
        empty={<p className="m3-muted">No refund policies are configured.</p>}
        columns={[
          { id: 'policy', header: 'Policy', rowHeader: true, cell: (p) => <><b>{p.label}</b><br /><span className="m3-mono m3-muted">{p.code}</span></> },
          { id: 'buyers', header: 'What buyers get', cell: (p) => <>{p.summary}<br /><span className="m3-muted">{ruleText(p)}</span></> },
          { id: 'impact', header: 'Impact', cell: () => <span className="m3-muted">Applies to new events; live events keep the rules they were published under.</span> },
        ]}
        rowActions={(p) => (disabled ? null : <Button variant="tonal" size="sm" aria-label={`Edit ${p.label} policy`} onClick={() => setEditing(p.index)}>Edit</Button>)}
      />
      <EditDialog
        policy={editing === null ? null : ((fields[editing] as unknown as Policy) ?? null)}
        onClose={() => setEditing(null)}
        onSave={(v) => {
          if (editing === null) return;
          setValue(`refundPolicies.${editing}.summary`, v.summary, { shouldDirty: true, shouldValidate: true });
          setValue(`refundPolicies.${editing}.rules`, v.rules, { shouldDirty: true, shouldValidate: true });
          setEditing(null);
        }}
      />
    </Card>
  );
}
