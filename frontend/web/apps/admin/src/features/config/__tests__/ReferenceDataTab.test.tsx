import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/test/referenceMock')).referenceModule());
import { referenceFixtures } from '@/test/referenceMock';


const push = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({
  items: [] as Array<Record<string, unknown>>,
  loading: false,
  error: null as null | Error,
  refetch: vi.fn(),
  create: vi.fn(),
  update: vi.fn(),
  remove: vi.fn(),
  setActive: vi.fn(),
  lastType: '' as string,
}));

vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn() }) }));
vi.mock('@pml.tickets/shared/api/admin/modules/reference-data', () => ({
  useReferenceTypes: () => ({
    types: [
      { type: 'CANCELLATION_REASON', label: 'Cancellation reasons', group: 'g', groupLabel: 'G', requiredMetadataKeys: [] },
      { type: 'BANK', label: 'Banks', group: 'g', groupLabel: 'G', requiredMetadataKeys: ['swiftCode'] },
      { type: 'BUSINESS_TYPE', label: 'Legal business types', group: 'k', groupLabel: 'KYB', requiredMetadataKeys: ['requiredDocuments'] },
      { type: 'ORGANIZATION_ROLE', label: 'Organization roles', group: 'a', groupLabel: 'Access', requiredMetadataKeys: ['invitable'] },
      { type: 'REPORT_PERIOD', label: 'Report periods', group: 'r', groupLabel: 'Reporting', requiredMetadataKeys: ['days', 'bucket'] },
      // not offered here: they have their own pages, or are workflow statuses
      { type: 'EVENT_CATEGORY', label: 'Event categories', group: 'g', groupLabel: 'G', requiredMetadataKeys: [] },
      { type: 'PAYOUT_STATUS', label: 'Payout statuses', group: 'WORKFLOW', groupLabel: 'Workflow', requiredMetadataKeys: [] },
    ],
    loading: false,
    error: null,
  }),
  useReferenceDataAdminAll: (type: string | null) => {
    api.lastType = type ?? "";
    return { items: api.items, loading: api.loading, error: api.error, refetch: api.refetch };
  },
  useCreateReferenceData: () => ({ create: api.create, loading: false }),
  useUpdateReferenceData: () => ({ update: api.update, loading: false }),
  useDeleteReferenceData: () => ({ remove: api.remove, loading: false }),
  useToggleReferenceDataActive: () => ({ setActive: api.setActive, loading: false }),
}));

vi.mock('@pml.tickets/shared/api/admin/modules/event', () => ({ useAdminEventCategories: () => ({ categories: [{ id: 'k1' }] }) }));
vi.mock('@pml.tickets/shared/api/admin/modules/catalog-admin', () => ({ useProvincesAdmin: () => ({ provinces: [{ id: 'p1' }] }), useCitiesAdmin: () => ({ cities: [{ id: 'c1' }] }) }));
import { menuAction, menuItem } from '@/test/menu';
import { ReferenceDataTab } from '../ReferenceDataTab';

const row = (over: Record<string, unknown>) => ({
  id: 'r1', type: 'CANCELLATION_REASON', code: 'VENUE_UNAVAILABLE', name: 'Venue unavailable', description: null,
  displayOrder: 1, isActive: true, isSystem: false, metadata: null, ...over,
});

beforeEach(() => {
  api.items = [row({}), row({ id: 'r2', code: 'OTHER', name: 'Other', isActive: false, isSystem: true })];
  api.loading = false;
  api.error = null;
  api.create = vi.fn().mockResolvedValue({});
  api.update = vi.fn().mockResolvedValue({});
  api.remove = vi.fn().mockResolvedValue('r1');
  api.setActive = vi.fn().mockResolvedValue({});
  push.mockClear();
});

describe('ReferenceDataTab', () => {
  it('offers every taxonomy type the registry lists, grouped, and none that have their own page or are statuses', () => {
    renderConsole(<ReferenceDataTab />);
    const select = screen.getByLabelText('Type') as HTMLSelectElement;
    const offered = Array.from(select.options).map((o) => o.value);
    expect(offered).toEqual(['CANCELLATION_REASON', 'BANK', 'BUSINESS_TYPE', 'ORGANIZATION_ROLE', 'REPORT_PERIOD']);
    expect(Array.from(select.querySelectorAll('optgroup')).map((g) => g.label)).toEqual(['G', 'KYB', 'Access', 'Reporting']);
  });

  it('takes list, flag and number metadata in their own shapes and sends them typed', async () => {
    referenceFixtures.KYB_DOCUMENT_TYPE = [{ code: 'NATIONAL_ID', name: 'National ID' }, { code: 'TAX_CERTIFICATE', name: 'Tax certificate' }];
    renderConsole(<ReferenceDataTab />);
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'BUSINESS_TYPE' } });
    fireEvent.click(screen.getByRole('button', { name: 'New entry' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText(/Name/), { target: { value: 'Cooperative' } });
    fireEvent.change(within(dialog).getByLabelText(/^Code/), { target: { value: 'coop' } });
    fireEvent.change(within(dialog).getByLabelText(/Required documents/), { target: { value: 'NATIONAL_ID, BOGUS' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect((await within(dialog).findAllByText(/Use only listed values: NATIONAL_ID, TAX_CERTIFICATE/)).length).toBeGreaterThan(0);
    expect(api.create).not.toHaveBeenCalled();
    fireEvent.change(within(dialog).getByLabelText(/Required documents/), { target: { value: 'NATIONAL_ID, TAX_CERTIFICATE' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    await waitFor(() => expect(api.create).toHaveBeenCalledWith(expect.objectContaining({
      type: 'BUSINESS_TYPE', code: 'COOP', metadata: { requiredDocuments: ['NATIONAL_ID', 'TAX_CERTIFICATE'] },
    })));
  });

  it('a switch metadata key is sent as a boolean and a number key as a number', async () => {
    renderConsole(<ReferenceDataTab />);
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'REPORT_PERIOD' } });
    fireEvent.click(screen.getByRole('button', { name: 'New entry' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText(/Name/), { target: { value: 'Last 6 months' } });
    fireEvent.change(within(dialog).getByLabelText(/^Code/), { target: { value: 'SIX_M' } });
    fireEvent.change(within(dialog).getByLabelText(/Days/), { target: { value: '180' } });
    fireEvent.change(within(dialog).getByLabelText(/Bucket/), { target: { value: 'MONTH' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    await waitFor(() => expect(api.create).toHaveBeenCalledWith(expect.objectContaining({ metadata: { days: 180, bucket: 'MONTH' } })));
  });

  it('lists entries with action buttons (rows are not clickable)', () => {
    renderConsole(<ReferenceDataTab />);
    const table = screen.getByRole('table', { name: 'Cancellation reasons entries' });
    expect(within(table).getByText('Venue unavailable')).toBeInTheDocument();
    expect(within(table).getAllByRole('button', { name: /^Edit / })).toHaveLength(2);
    expect(menuItem('More actions for Other', 'Activate')).toBeInTheDocument();
    expect(menuItem('More actions for Other', 'Delete')).toHaveAttribute('aria-disabled', 'true');
  });

  it('switches type and filters by search and status', () => {
    renderConsole(<ReferenceDataTab />);
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'BANK' } });
    expect(api.lastType).toBe('BANK');
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'inactive' } });
    expect(screen.queryByText('Venue unavailable')).toBeNull();
    expect(screen.getByText('Other')).toBeInTheDocument();
    fireEvent.change(screen.getByRole('searchbox', { name: 'Search reference entries' }), { target: { value: 'nomatch' } });
    expect(screen.getByText('No entries match the filters.')).toBeInTheDocument();
  });

  it('validates and creates an entry, including required metadata', async () => {
    renderConsole(<ReferenceDataTab />);
    fireEvent.change(screen.getByLabelText('Type'), { target: { value: 'BANK' } });
    fireEvent.click(screen.getByRole('button', { name: 'New entry' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect(await within(dialog).findByText('Enter a name')).toBeInTheDocument();
    expect(within(dialog).getByText('Capital letters, digits and underscores (2 to 24 characters)')).toBeInTheDocument();
    expect(within(dialog).getByText('Required for this type')).toBeInTheDocument();
    fireEvent.change(within(dialog).getByLabelText(/Name/), { target: { value: 'Zanaco' } });
    fireEvent.change(within(dialog).getByLabelText(/^Code/), { target: { value: 'zanaco' } });
    fireEvent.change(within(dialog).getByLabelText(/Swift code/), { target: { value: 'ZNCOZMLU' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    await waitFor(() =>
      expect(api.create).toHaveBeenCalledWith({ type: 'BANK', code: 'ZANACO', name: 'Zanaco', description: null, displayOrder: null, metadata: { swiftCode: 'ZNCOZMLU' }, semantic: null, allowedTransitions: null, parentType: null, parentCode: null, isActive: null }),
    );
  });

  it('rejects duplicate codes', async () => {
    renderConsole(<ReferenceDataTab />);
    fireEvent.click(screen.getByRole('button', { name: 'New entry' }));
    const dialog = await screen.findByRole('dialog');
    fireEvent.change(within(dialog).getByLabelText(/Name/), { target: { value: 'X' } });
    fireEvent.change(within(dialog).getByLabelText(/^Code/), { target: { value: 'other' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Create' }));
    expect(await within(dialog).findByText('Already exists')).toBeInTheDocument();
    expect(api.create).not.toHaveBeenCalled();
  });

  it('edits, toggles and deletes', async () => {
    renderConsole(<ReferenceDataTab />);
    fireEvent.click(screen.getAllByRole('button', { name: /^Edit / })[0]);
    const dialog = await screen.findByRole('dialog');
    expect(within(dialog).getByLabelText(/^Code/)).toBeDisabled();
    fireEvent.change(within(dialog).getByLabelText(/Name/), { target: { value: 'Venue closed' } });
    fireEvent.click(within(dialog).getByRole('button', { name: 'Save changes' }));
    await waitFor(() => expect(api.update).toHaveBeenCalledWith('r1', expect.objectContaining({ name: 'Venue closed' })));

    menuAction('More actions for Venue unavailable', 'Deactivate');
    await waitFor(() => expect(api.setActive).toHaveBeenCalledWith('r1', false));

    menuAction('More actions for Venue unavailable', 'Delete');
    const confirm = await screen.findByRole('alertdialog');
    fireEvent.click(within(confirm).getByRole('button', { name: 'Delete' }));
    await waitFor(() => expect(api.remove).toHaveBeenCalledWith('r1'));
  });

  it('shows loading, error and empty states', () => {
    api.items = [];
    api.loading = true;
    const a = renderConsole(<ReferenceDataTab />);
    expect(document.querySelector('[aria-busy="true"]')).not.toBeNull();
    a.unmount();
    api.loading = false;
    api.error = Object.assign(new Error('down'), { extensions: { retryable: true } });
    const b = renderConsole(<ReferenceDataTab />);
    fireEvent.click(screen.getByRole('button', { name: /try again|retry/i }));
    expect(api.refetch).toHaveBeenCalled();
    b.unmount();
    api.error = null;
    renderConsole(<ReferenceDataTab />);
    expect(screen.getByText('No reference entries yet.')).toBeInTheDocument();
  });

  it('gates writes by role and navigates shortcuts', () => {
    renderConsole(<ReferenceDataTab />, { roles: ['FINANCE_LEAD'] });
    expect(screen.getByRole('button', { name: 'New entry' })).toBeDisabled();
    expect(screen.getAllByRole('button', { name: /^Edit / })[0]).toBeDisabled();
    fireEvent.click(screen.getByRole('button', { name: /^Event categories/ }));
    fireEvent.click(screen.getByRole('button', { name: /^Provinces and cities/ }));
    expect(push.mock.calls.map((c) => c[0])).toEqual(['/events/categories', '/events/locations']);
  });
});
