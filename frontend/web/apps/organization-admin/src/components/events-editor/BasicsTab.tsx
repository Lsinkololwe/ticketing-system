'use client';

import { MediaPickerField } from '@/components/common/MediaPickerField';
import { RichTextField } from '@/components/common/RichTextField';
import { SelectRHF, SwitchRHF, TextFieldRHF } from '@pml.tickets/shared';
import { Card, CardHeader, FormCell, FormGrid } from '@pml.tickets/shared/components/m3';
import { GalleryCard } from './GalleryCard';
import type { ReferenceOptions } from './types';

export function BasicsTab({ categories, ageRestrictions }: { categories: ReferenceOptions['categories']; ageRestrictions: Array<{ code: string; name: string }> }) {
  return (
    <div className="m3-stack">
      <Card>
        <CardHeader title="Event details" subtitle="The essentials buyers see on cards, search and the event page." />
        <FormGrid>
          <FormCell span={12}>
            <TextFieldRHF name="title" label="Event title" required maxLength={90} showCount helperText="Shown on cards and the event page." />
          </FormCell>
          <FormCell span={12}>
            <TextFieldRHF name="tagline" label="Tagline" maxLength={100} helperText="One line under the title." />
          </FormCell>
          <FormCell span={6}>
            <SelectRHF
              name="categoryId"
              label="Category"
              placeholder="Choose a category"
              options={categories.map((c) => ({ value: c.id, label: c.name }))}
            />
          </FormCell>
          <FormCell span={6}>
            <SelectRHF
              name="ageRestriction"
              label="Age restriction"
              placeholder="No restriction"
              options={ageRestrictions.map((a) => ({ value: a.code, label: a.name }))}
            />
          </FormCell>
        </FormGrid>
      </Card>
      <Card>
        <CardHeader title="Description" subtitle="Tell buyers what to expect. Shown under About this event." />
        <RichTextField name="description" label="Full description" rows={6} helperText="Shown on the event page under About this event." />
      </Card>
      <Card>
        <CardHeader title="Cover image" subtitle="Used on the event page, listing card and basket. Landscape works best." />
        <FormGrid>
          <FormCell span={12}>
            <MediaPickerField name="bannerImageUrl" label="Cover image" noun="cover image" />
          </FormCell>
          <FormCell span={12}>
            <TextFieldRHF name="bannerAltText" label="Cover image description" maxLength={200} helperText="Read aloud by screen readers." />
          </FormCell>
        </FormGrid>
      </Card>
      <GalleryCard />
      <Card>
        <CardHeader title="Format" />
        <SwitchRHF name="isVirtual" label="Virtual event" hint="Attendees join online. No physical venue is required." />
        <SwitchRHF name="isFreeEvent" label="Free event" hint="No ticket price is charged." />
      </Card>
    </div>
  );
}
