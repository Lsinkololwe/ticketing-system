import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { BusinessInfoView, type BusinessInfoValues } from './BusinessInfoView';
import { businessInfoSchema, businessInfoSchemaFor } from './schemas';
import { matchProvince } from './BusinessInfoView';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});

const valid: BusinessInfoValues = {
  name: 'Fixture Org', type: 'BUSINESS', businessType: 'LIMITED_COMPANY', tagline: '', description: '',
  businessEmail: 'a@b.co', businessPhone: '+260971234567', website: '', businessRegistrationNumber: '', taxId: '',
  city: 'Lusaka', province: 'LUS', country: 'Zambia', facebook: '', instagram: '', twitter: '',
};
// eslint-disable-next-line @typescript-eslint/no-explicit-any
const setup = (o: Partial<BusinessInfoValues> = {}, onSubmit: (v: any) => Promise<void> = vi.fn(async () => undefined), extra = {}) => {
  const onBack = vi.fn();
  render(<BusinessInfoView defaultValues={{ ...valid, ...o }} onSubmit={onSubmit} onBack={onBack} {...extra} />);
  return { onSubmit: onSubmit as ReturnType<typeof vi.fn>, onBack, user: userEvent.setup() };
};

describe('businessInfoSchema', () => {
  it('rejects bad email and phone with messages', () => {
    const r = businessInfoSchema.safeParse({ ...valid, businessEmail: 'x', businessPhone: '123', website: 'nope', facebook: '!!' });
    expect(r.success).toBe(false);
    const msgs = r.error?.issues.map((i) => i.message) ?? [];
    expect(msgs).toEqual(expect.arrayContaining(['Enter a valid email address', 'Enter a valid phone number']));
  });
  it('normalises the phone to E.164', () => {
    expect(businessInfoSchema.parse({ ...valid, businessPhone: '0971234567' }).businessPhone).toBe('+260971234567');
  });
});

describe('businessInfoSchemaFor', () => {
  const codes = { types: ['BUSINESS'], businessTypes: ['LIMITED_COMPANY'], provinces: ['LUS'] };
  it('accepts only the codes the platform listed', () => {
    expect(businessInfoSchemaFor(codes).safeParse(valid).success).toBe(true);
    const r = businessInfoSchemaFor(codes).safeParse({ ...valid, type: 'CLUB', businessType: 'TRUST', province: 'MARS' });
    const msgs = r.error?.issues.map((i) => i.message) ?? [];
    expect(msgs).toEqual(expect.arrayContaining(['Choose one of the listed organizer types', 'Choose one of the listed business types', 'Choose one of the listed provinces']));
  });
  it('still requires a choice when a list is unavailable, and leaves the judgement to the server', () => {
    expect(businessInfoSchemaFor({ types: [], businessTypes: [], provinces: [] }).safeParse(valid).success).toBe(true);
    expect(businessInfoSchemaFor({ types: [], businessTypes: [], provinces: [] }).safeParse({ ...valid, province: '' }).success).toBe(false);
  });
});

describe('matchProvince', () => {
  const provinces = [
    { value: 'NW', label: 'North-Western', description: null, parentCode: null, metadata: {} },
    { value: 'LUS', label: 'Lusaka', description: null, parentCode: null, metadata: {} },
  ];
  it('keeps a listed code and maps legacy stored values by normalised name', () => {
    expect(matchProvince('LUS', provinces)).toBe('LUS');
    expect(matchProvince('NORTH_WESTERN', provinces)).toBe('NW');
    expect(matchProvince('Lusaka', provinces)).toBe('LUS');
    expect(matchProvince('MARS', provinces)).toBe('MARS');
    expect(matchProvince('', provinces)).toBe('');
  });
});

describe('BusinessInfoView', () => {
  it('offers the platform lists: organizer types with their hints, legal types, provinces and countries', () => {
    setup();
    expect(screen.getByTestId('org-type-NON_PROFIT')).toHaveTextContent('A registered NGO or trust');
    expect(screen.getByRole('option', { name: 'Sole proprietorship' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'North-Western' })).toBeInTheDocument();
    expect(screen.getByRole('option', { name: 'Zimbabwe (+263)' })).toBeInTheDocument();
    expect(screen.getByText('3 documents will be requested.')).toBeInTheDocument();
  });

  it('maps a legacy stored province onto the listed row', async () => {
    setup({ province: 'NORTH_WESTERN' });
    await waitFor(() => expect(screen.getByLabelText(/Province/)).toHaveValue('NW'));
  });

  it('renders the stepper at step 1 and the org type choices', () => {
    setup();
    expect(screen.getByRole('heading', { level: 1, name: 'Organizer onboarding' })).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'Application steps' })).toBeInTheDocument();
    expect(screen.getByTestId('org-type-BUSINESS')).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByTestId('org-type-INDIVIDUAL')).toHaveAttribute('aria-pressed', 'false');
  });

  it('shows validation messages and focuses the first invalid field', async () => {
    const { user, onSubmit } = setup({ name: '', city: '' });
    await user.click(screen.getByRole('button', { name: 'Continue' }));
    expect((await screen.findAllByText('Required')).length).toBeGreaterThan(0);
    await waitFor(() => expect(screen.getByLabelText(/Organization name/)).toHaveFocus());
    expect(onSubmit).not.toHaveBeenCalled();
  });

  it('requires a business type', async () => {
    const { user } = setup({ businessType: '' as never });
    await user.click(screen.getByRole('button', { name: 'Continue' }));
    expect((await screen.findAllByText(/Choose a business type/)).length).toBeGreaterThan(0);
  });

  it('submits the parsed payload', async () => {
    const { user, onSubmit } = setup();
    await user.click(screen.getByTestId('org-type-RELIGIOUS'));
    await user.click(screen.getByRole('button', { name: 'Continue' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    expect(onSubmit.mock.calls[0]![0]).toMatchObject({ name: 'Fixture Org', type: 'RELIGIOUS', businessPhone: '+260971234567', province: 'LUS' });
  });

  it('maps server field violations onto the inputs', async () => {
    const err = {
      graphQLErrors: [
        { message: 'x', extensions: { errorCode: 'COMMAND_NOT_WELL_FORMED', classification: 'BAD_REQUEST', fields: [{ path: 'input.taxId', constraint: 'Pattern' }] } },
      ],
    };
    const { user } = setup({}, vi.fn(async () => { throw err; }));
    await user.click(screen.getByRole('button', { name: 'Continue' }));
    expect(await screen.findByText('Wrong format')).toBeInTheDocument();
    expect(screen.getByLabelText('TPIN')).toHaveAttribute('aria-invalid', 'true');
  });

  it('shows an unmapped server refusal in the banner', async () => {
    const err = { graphQLErrors: [{ message: 'x', extensions: { errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION' } }] };
    const { user } = setup({}, vi.fn(async () => { throw err; }));
    await user.click(screen.getByRole('button', { name: 'Continue' }));
    expect(await screen.findByText('Those tickets have sold out.')).toBeInTheDocument();
  });

  it('guards against a double submit', async () => {
    let release: () => void = () => undefined;
    const onSubmit = vi.fn(() => new Promise<void>((r) => { release = r; }));
    const { user } = setup({}, onSubmit);
    await user.dblClick(screen.getByRole('button', { name: 'Continue' }));
    await waitFor(() => expect(onSubmit).toHaveBeenCalledTimes(1));
    release();
  });

  it('shows reviewer comments and goes back', async () => {
    const { user, onBack } = setup({}, undefined, { changesNote: 'Add TPIN' });
    expect(screen.getByText('Add TPIN')).toBeInTheDocument();
    await user.click(screen.getByRole('button', { name: 'Back' }));
    expect(onBack).toHaveBeenCalled();
  });
});
