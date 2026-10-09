'use client';

import { TextFieldRHF } from '@pml.tickets/shared';
import { Banner, Card, CardHeader, FormCell, FormGrid, TextField } from '@pml.tickets/shared/components/m3';
import { RunningOrderCard } from './ListCards';
import type { TabProps } from './types';

export function WhenTab({ dateLocked }: TabProps) {
  return (
    <div className="m3-stack">
      <Card>
        <CardHeader
          title="Date and time"
          subtitle={
            dateLocked
              ? 'Dates are locked on approved and live events. Use Reschedule on the Publish tab to move them.'
              : 'All times are Central Africa Time (Lusaka).'
          }
        />
        {dateLocked ? <Banner tone="info">Dates are locked. Reschedule keeps ticket holders informed.</Banner> : null}
        <FormGrid>
          <FormCell span={6}>
            <TextFieldRHF name="start" label="Starts" type="datetime-local" required disabled={dateLocked} />
          </FormCell>
          <FormCell span={6}>
            <TextFieldRHF name="end" label="Ends" type="datetime-local" required disabled={dateLocked} />
          </FormCell>
          <FormCell span={4}>
            <TextFieldRHF name="doorsOpen" label="Doors open" type="time" />
          </FormCell>
          <FormCell span={4}>
            <TextField label="Time zone" value="Africa/Lusaka (CAT)" readOnly />
          </FormCell>
        </FormGrid>
      </Card>
      <Card>
        <CardHeader title="Go live automatically" subtitle="Optional. An approved event publishes itself at this time." />
        <FormGrid>
          <FormCell span={6}>
            <TextFieldRHF name="publishAt" label="Publish on" type="datetime-local" helperText="Leave empty to publish by hand." />
          </FormCell>
        </FormGrid>
      </Card>
      <RunningOrderCard />
    </div>
  );
}
