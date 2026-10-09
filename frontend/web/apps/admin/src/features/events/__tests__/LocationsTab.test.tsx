import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const h = vi.hoisted(() => ({
  prov: { create: vi.fn(), update: vi.fn(), remove: vi.fn(), loading: false },
  city: { create: vi.fn(), update: vi.fn(), remove: vi.fn(), loading: false },
}));
vi.mock('@pml.tickets/shared/api/admin/modules/catalog-admin', () => ({
  useProvincesAdmin: () => ({
    provinces: [
      { id: 'p1', name: 'Lusaka', code: 'LSK', country: 'Zambia', cityCount: 2, isActive: true },
      { id: 'p2', name: 'Copperbelt', code: 'CB', country: 'Zambia', cityCount: 0, isActive: true },
    ],
    loading: false,
    error: undefined,
    refetch: vi.fn(),
  }),
  useCitiesAdmin: () => ({
    cities: [
      { id: 'c1', name: 'Lusaka', code: 'LSK', provinceId: 'p1', province: 'Lusaka', country: 'Zambia', eventCount: 4, isActive: true },
      { id: 'c2', name: 'Ndola', code: 'NDL', provinceId: 'p2', province: 'Copperbelt', country: 'Zambia', eventCount: 0, isActive: true },
    ],
    loading: false,
    error: undefined,
    refetch: vi.fn(),
  }),
  useProvinceMutations: () => h.prov,
  useCityMutations: () => h.city,
}));

import { LocationsTab } from '../LocationsTab';

beforeEach(() => {
  vi.clearAllMocks();
  for (const m of [h.prov, h.city]) for (const k of ['create', 'update', 'remove'] as const) m[k].mockResolvedValue({ success: true, message: null });
});

describe('LocationsTab', () => {
  it('renders the provinces and cities tables', () => {
    renderConsole(<LocationsTab />);
    expect(screen.getByRole('table', { name: 'Provinces' })).toBeInTheDocument();
    expect(screen.getByRole('table', { name: 'Cities' })).toBeInTheDocument();
    expect(screen.getByText('Zambia has 10 provinces.')).toBeInTheDocument();
  });

  it('blocks deleting a province with cities and a city with events', () => {
    renderConsole(<LocationsTab />);
    fireEvent.click(screen.getAllByRole('button', { name: 'More actions for Lusaka' })[0]);
    fireEvent.click(screen.getByRole('menuitem', { name: 'Delete' }));
    expect(screen.getByText(/2 cities belong to Lusaka/)).toBeInTheDocument();
  });

  it('deletes an empty province after confirmation', async () => {
    renderConsole(<LocationsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Copperbelt' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Delete' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    await vi.waitFor(() => expect(h.prov.remove).toHaveBeenCalledWith('p2'));
  });

  it('validates the province code', async () => {
    renderConsole(<LocationsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'New province' }));
    const dlg = screen.getByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Name'), { target: { value: 'Northern' } });
    fireEvent.change(within(dlg).getByLabelText('Code'), { target: { value: 'n' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Create' }));
    expect(await within(dlg).findByText('2 to 6 capital letters')).toBeInTheDocument();
    fireEvent.change(within(dlg).getByLabelText('Code'), { target: { value: 'NTH' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Create' }));
    await vi.waitFor(() => expect(h.prov.create).toHaveBeenCalledWith({ name: 'Northern', code: 'NTH', country: 'Zambia' }));
  });

  it('creates a city in a chosen province', async () => {
    renderConsole(<LocationsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'New city' }));
    const dlg = screen.getByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('City'), { target: { value: 'Kitwe' } });
    fireEvent.change(within(dlg).getByLabelText('Code'), { target: { value: 'KTW' } });
    fireEvent.change(within(dlg).getByLabelText('Province'), { target: { value: 'p2' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Create' }));
    await vi.waitFor(() => expect(h.city.create).toHaveBeenCalledWith({ name: 'Kitwe', code: 'KTW', provinceId: 'p2', country: 'Zambia' }));
  });

  it('filters cities by province', () => {
    renderConsole(<LocationsTab />);
    const filter = screen.getAllByLabelText('Province').find((el) => el.tagName === 'SELECT')!;
    fireEvent.change(filter, { target: { value: 'p2' } });
    const cities = screen.getByRole('table', { name: 'Cities' });
    expect(within(cities).getByText('Ndola')).toBeInTheDocument();
    expect(within(cities).queryByText('Lusaka')).not.toBeInTheDocument();
  });
});
