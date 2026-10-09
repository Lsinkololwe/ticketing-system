'use client';

import { useEffect } from 'react';
import { useFormContext } from 'react-hook-form';
import { SelectRHF, SwitchRHF, TextAreaRHF, TextFieldRHF } from '@pml.tickets/shared';
import { Card, CardHeader, FormCell, FormGrid, TextField } from '@pml.tickets/shared/components/m3';
import { useEditorValues } from './useEditorValues';
import type { ReferenceOptions } from './types';

export function VenueTab({ provinces, cities }: Pick<ReferenceOptions, 'provinces' | 'cities'>) {
  const values = useEditorValues();
  const { setValue } = useFormContext();
  const cityOptions = values.province ? cities.filter((c) => !c.province || c.province === values.province) : cities;

  // Choosing a city sets its province.
  useEffect(() => {
    const c = cities.find((x) => x.name === values.city);
    if (c?.province && c.province !== values.province) setValue('province', c.province, { shouldDirty: true });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [values.city]);

  return (
    <div className="m3-stack">
      <Card>
        <CardHeader title="Venue" subtitle="Where the event takes place." />
        <FormGrid>
          {values.isVirtual ? (
            <FormCell span={12}>
              <TextFieldRHF name="virtualEventUrl" label="Virtual event link" type="url" required placeholder="https://" />
            </FormCell>
          ) : null}
          <FormCell span={6}>
            <TextFieldRHF name="venue" label="Venue name" required={!values.isVirtual} />
          </FormCell>
          <FormCell span={6}>
            <TextFieldRHF name="address" label="Street address" />
          </FormCell>
          <FormCell span={4}>
            <SelectRHF name="province" label="Province" placeholder="Choose a province" options={provinces.map((p) => ({ value: p.name, label: p.name }))} />
          </FormCell>
          <FormCell span={4}>
            <SelectRHF name="city" label="City" placeholder="Choose a city" options={cityOptions.map((c) => ({ value: c.name, label: c.name }))} />
          </FormCell>
          <FormCell span={4}>
            <TextField label="Country" value="Zambia" readOnly />
          </FormCell>
          <FormCell span={4}>
            <TextFieldRHF name="capacity" label="Total capacity" inputMode="numeric" helperText="The most people the venue can hold." />
          </FormCell>
        </FormGrid>
      </Card>
      <Card>
        <CardHeader title="Getting there" subtitle="Practical information shown on the event page." />
        <FormGrid>
          <FormCell span={12}>
            <TextAreaRHF name="parking" label="Parking" rows={2} maxLength={240} />
          </FormCell>
          <FormCell span={12}>
            <TextAreaRHF name="transport" label="Public transport" rows={2} maxLength={240} />
          </FormCell>
          <FormCell span={12}>
            <TextAreaRHF name="bag" label="Bag and item policy" rows={2} maxLength={240} />
          </FormCell>
        </FormGrid>
      </Card>
      <Card>
        <CardHeader title="Accessibility" subtitle="Helps buyers who need step-free access, interpreters or assistance." />
        <SwitchRHF name="accessibility.wheelchairAccessible" label="Wheelchair accessible" />
        {values.accessibility.wheelchairAccessible ? (
          <FormGrid>
            <FormCell span={6}>
              <TextFieldRHF name="accessibility.wheelchairSeatsAvailable" label="Wheelchair spaces available" inputMode="numeric" />
            </FormCell>
          </FormGrid>
        ) : null}
        <SwitchRHF name="accessibility.signLanguageInterpreter" label="Sign language interpreter" />
        <SwitchRHF name="accessibility.hearingLoopAvailable" label="Hearing loop available" />
        <SwitchRHF name="accessibility.accessibleParking" label="Accessible parking" />
        <SwitchRHF name="accessibility.accessibleRestrooms" label="Accessible restrooms" />
        <SwitchRHF name="accessibility.assistanceDogsAllowed" label="Assistance dogs allowed" />
        <TextAreaRHF name="accessibility.additionalNotes" label="Additional notes" rows={2} />
      </Card>
    </div>
  );
}
