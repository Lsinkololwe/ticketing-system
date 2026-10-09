'use client';

import { useFieldArray, useFormContext } from 'react-hook-form';
import { TextAreaRHF, TextFieldRHF } from '@pml.tickets/shared';
import { Button, Card, CardHeader, FormCell, FormGrid, IconButton } from '@pml.tickets/shared/components/m3';

function RowActions({ i, n, label, move, remove }: { i: number; n: number; label: string; move: (a: number, b: number) => void; remove: (i: number) => void }) {
  return (
    <span className="m3-row">
      <IconButton icon="arrow-up" label={`Move ${label} ${i + 1} up`} disabled={i === 0} onClick={() => move(i, i - 1)} />
      <IconButton icon="arrow-down" label={`Move ${label} ${i + 1} down`} disabled={i === n - 1} onClick={() => move(i, i + 1)} />
      <IconButton icon="delete" danger label={`Remove ${label} ${i + 1}`} onClick={() => remove(i)} />
    </span>
  );
}

/** Running order shown on the event page, in this order. */
export function RunningOrderCard() {
  const { control } = useFormContext();
  const rows = useFieldArray({ control, name: 'runningOrder', keyName: 'rowKey' });
  return (
    <Card>
      <CardHeader
        title="Running order"
        subtitle="Shown on the event page, in this order."
        actions={<Button variant="tonal" size="sm" icon="add" onClick={() => rows.append({ time: '', title: '' } as never)}>Add item</Button>}
      />
      {rows.fields.length === 0 ? <p className="m3-muted">No items yet.</p> : null}
      <div className="m3-stack">
        {rows.fields.map((r, i) => (
          <FormGrid key={(r as unknown as { rowKey: string }).rowKey}>
            <FormCell span={3}><TextFieldRHF name={`runningOrder.${i}.time`} label={`Time ${i + 1}`} type="time" /></FormCell>
            <FormCell span={6}><TextFieldRHF name={`runningOrder.${i}.title`} label={`Item ${i + 1}`} placeholder="What happens" /></FormCell>
            <FormCell span={3}><RowActions i={i} n={rows.fields.length} label="item" move={rows.move} remove={rows.remove} /></FormCell>
          </FormGrid>
        ))}
      </div>
    </Card>
  );
}

/** Frequently asked questions, shown as Good to know on the event page. */
export function FaqCard() {
  const { control } = useFormContext();
  const rows = useFieldArray({ control, name: 'faqs', keyName: 'rowKey' });
  return (
    <Card>
      <CardHeader
        title="Frequently asked questions"
        subtitle="Shown as Good to know on the event page."
        actions={<Button variant="tonal" size="sm" icon="add" onClick={() => rows.append({ question: '', answer: '' } as never)}>Add question</Button>}
      />
      {rows.fields.length === 0 ? <p className="m3-muted">No questions yet. Common ones: gate times, parking, what to bring.</p> : null}
      <div className="m3-stack">
        {rows.fields.map((r, i) => (
          <div key={(r as unknown as { rowKey: string }).rowKey} className="m3-stack">
            <div className="m3-row"><b>Question {i + 1}</b><RowActions i={i} n={rows.fields.length} label="question" move={rows.move} remove={rows.remove} /></div>
            <TextFieldRHF name={`faqs.${i}.question`} label={`Question ${i + 1}`} maxLength={120} />
            <TextAreaRHF name={`faqs.${i}.answer`} label={`Answer ${i + 1}`} rows={3} />
          </div>
        ))}
      </div>
    </Card>
  );
}
