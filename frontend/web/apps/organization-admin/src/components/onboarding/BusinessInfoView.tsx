'use client';

import { useEffect, useMemo } from 'react';
import { useFormContext, useWatch } from 'react-hook-form';
import { Banner, Button, Card, CardHeader, FormCell, FormGrid, Skeleton } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, SelectRHF, TextAreaRHF, TextFieldRHF, PhoneRHF, useZodForm } from '@pml.tickets/shared';
import { useReferenceOptions, useCountryOptions, type ReferenceOption } from '@pml.tickets/shared/api/graphql/shared/reference';
import { requiredDocuments, toBusinessTypeSpecs, toDocumentSpecs } from '@/lib/onboarding/documents';
import { WizardSteps } from './steps';
import { businessInfoSchemaFor, type BusinessInfoInput, type BusinessInfoOutput } from './schemas';

export type BusinessInfoValues = BusinessInfoInput;

export interface BusinessInfoViewProps {
  defaultValues: BusinessInfoValues;
  /** Receives the parsed values. Throw to map a server error onto the form. */
  onSubmit: (values: BusinessInfoOutput) => Promise<void>;
  onBack: () => void;
  changesNote?: string | null;
}

function OrgTypeChoice({ types }: { types: ReturnType<typeof useReferenceOptions> }) {
  const { setValue, control, formState } = useFormContext<BusinessInfoValues>();
  const current = useWatch({ control, name: 'type' });
  const error = formState.errors.type?.message;
  return (
    <>
      <div className="oc-opt-grid" role="group" aria-label="Organization type" data-field="type">
        {types.options.map((o) => (
          <button
            key={o.value}
            type="button"
            className="oc-opt m3-state"
            aria-pressed={current === o.value}
            data-testid={`org-type-${o.value}`}
            onClick={() => setValue('type', o.value, { shouldDirty: true, shouldTouch: true, shouldValidate: true })}
          >
            <span className="m3-stack">
              <b>{o.label}</b>
              <small>{o.description}</small>
            </span>
          </button>
        ))}
      </div>
      {types.loading ? <Skeleton /> : null}
      {!types.loading && types.empty ? (
        <Banner tone="warning">Not available yet: the organizer types could not be loaded. Reload to try again.</Banner>
      ) : null}
      {error ? (
        <span role="alert" className="m3-field__error">
          {error}
        </span>
      ) : null}
    </>
  );
}

/** Legacy stored provinces ("NORTH_WESTERN", "Lusaka") are matched to a listed row by normalised code or name. */
const normalise = (v: string) => v.toLowerCase().replace(/[^a-z0-9]/g, '');
export function matchProvince(value: string | null | undefined, provinces: readonly ReferenceOption[]): string {
  if (!value) return '';
  if (provinces.some((p) => p.value === value)) return value;
  const n = normalise(value);
  return provinces.find((p) => normalise(p.value) === n || normalise(p.label) === n)?.value ?? value;
}

function ProvinceNormaliser({ provinces }: { provinces: readonly ReferenceOption[] }) {
  const { control, setValue } = useFormContext<BusinessInfoValues>();
  const current = useWatch({ control, name: 'province' });
  useEffect(() => {
    const matched = matchProvince(current, provinces);
    if (provinces.length && matched !== current) setValue('province', matched);
  }, [current, provinces, setValue]);
  return null;
}

function DocCountHint({ required }: { required: (code: string) => number }) {
  const { control } = useFormContext<BusinessInfoValues>();
  const t = useWatch({ control, name: 'businessType' });
  const n = t ? required(t) : null;
  return <>{n ? `${n} documents will be requested.` : 'Decides which documents we ask for.'}</>;
}

/** Step 1: organization type, business details, address (react-hook-form + zod). */
export function BusinessInfoView(p: BusinessInfoViewProps) {
  const types = useReferenceOptions('ORGANIZER_TYPE');
  const businessTypes = useReferenceOptions('BUSINESS_TYPE');
  const documents = useReferenceOptions('KYB_DOCUMENT_TYPE');
  const provinces = useReferenceOptions('PROVINCE');
  const { countries } = useCountryOptions();
  const schema = useMemo(
    () =>
      businessInfoSchemaFor({
        types: types.options.map((o) => o.value),
        businessTypes: businessTypes.options.map((o) => o.value),
        provinces: provinces.options.map((o) => o.value),
      }),
    [types.options, businessTypes.options, provinces.options],
  );
  const specs = useMemo(() => toBusinessTypeSpecs(businessTypes.options), [businessTypes.options]);
  const docSpecs = useMemo(() => toDocumentSpecs(documents.options), [documents.options]);
  const form = useZodForm(schema, { defaultValues: p.defaultValues });
  return (
    <Form form={form} onSubmit={(v) => p.onSubmit(v)} guardLeave={false} aria-label="Business information" className="m3-stack">
      <div className="m3-stack">
        <h1 className="m3-page-title">Organizer onboarding</h1>
        <p className="m3-page-sub">Apply, upload your documents and track the review.</p>
      </div>
      <WizardSteps current={0} />
      {p.changesNote ? (
        <Banner tone="warning" title="Reviewer comments:">
          {p.changesNote}
        </Banner>
      ) : null}
      <Card>
        <CardHeader title="What type of organizer are you?" subtitle="This is shown on your public profile." />
        <OrgTypeChoice types={types} />
      </Card>
      <Card>
        <CardHeader title="Business information" subtitle="Shown to buyers and used for payouts and tax." />
        <FormGrid>
          <FormCell span={8}>
            <TextFieldRHF name="name" label="Organization name" required autoComplete="organization" />
          </FormCell>
          <FormCell span={4}>
            <SelectRHF
              name="businessType"
              label="Legal business type"
              required
              placeholder="Select"
              disabled={businessTypes.loading}
              options={businessTypes.options.map((o) => ({ value: o.value, label: o.label }))}
              helperText={
                !businessTypes.loading && businessTypes.empty ? (
                  'Not available yet: the business types could not be loaded'
                ) : (
                  <DocCountHint required={(code) => requiredDocuments(specs, docSpecs, code).length} />
                )
              }
            />
          </FormCell>
          <FormCell span={12}>
            <TextFieldRHF name="tagline" label="Tagline" maxLength={50} helperText="A short phrase that describes you (optional)" />
          </FormCell>
          <FormCell span={12}>
            <TextAreaRHF name="description" label="About your organization" rows={4} />
          </FormCell>
          <FormCell span={4}>
            <TextFieldRHF name="taxId" label="TPIN" inputMode="numeric" />
          </FormCell>
          <FormCell span={4}>
            <TextFieldRHF name="businessRegistrationNumber" label="Registration number" helperText="PACRA number, if registered" />
          </FormCell>
          <FormCell span={4}>
            <PhoneRHF key={String(p.defaultValues.businessPhone)} name="businessPhone" label="Business phone" countries={countries} />
          </FormCell>
          <FormCell span={6}>
            <TextFieldRHF name="businessEmail" label="Business email" type="email" required />
          </FormCell>
          <FormCell span={6}>
            <TextFieldRHF name="website" label="Website" type="url" />
          </FormCell>
          <FormCell span={4}>
            <TextFieldRHF name="city" label="City" required />
          </FormCell>
          <FormCell span={4}>
            <SelectRHF
              name="province"
              label="Province"
              required
              disabled={provinces.loading}
              options={provinces.options.map((o) => ({ value: o.value, label: o.label }))}
              helperText={!provinces.loading && provinces.empty ? 'Not available yet: the provinces could not be loaded' : undefined}
            />
            <ProvinceNormaliser provinces={provinces.options} />
          </FormCell>
          <FormCell span={4}>
            <TextFieldRHF name="country" label="Country" />
          </FormCell>
          <FormCell span={4}>
            <TextFieldRHF name="facebook" label="Facebook" />
          </FormCell>
          <FormCell span={4}>
            <TextFieldRHF name="instagram" label="Instagram" />
          </FormCell>
          <FormCell span={4}>
            <TextFieldRHF name="twitter" label="X / Twitter" />
          </FormCell>
        </FormGrid>
      </Card>
      <FormActions
        submitLabel="Continue"
        align="between"
        leading={
          <Button variant="outlined" type="button" onClick={p.onBack}>
            Back
          </Button>
        }
      />
    </Form>
  );
}
