import { describe, expect, it, vi, beforeEach } from 'vitest';
import { fireEvent, screen, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const h = vi.hoisted(() => ({
  cats: vi.fn(),
  m: { create: vi.fn(), update: vi.fn(), remove: vi.fn(), setActive: vi.fn(), loading: false },
}));
vi.mock('@pml.tickets/shared/api/admin/modules/event', () => ({ useAdminEventCategories: h.cats }));
vi.mock('@pml.tickets/shared/api/admin/modules/catalog-admin', () => ({ useCategoryMutations: () => h.m }));

import { CategoriesTab } from '../CategoriesTab';

const cat = (over = {}) => ({ id: 'k1', name: 'Music', code: 'MUSIC', description: 'Live', eventCount: 0, isActive: true, ...over });
beforeEach(() => {
  vi.clearAllMocks();
  h.cats.mockReturnValue({ categories: [cat(), cat({ id: 'k2', name: 'Sport', code: 'SPORT', eventCount: 3, isActive: false })], loading: false, error: undefined, refetch: vi.fn() });
  h.m.create.mockResolvedValue({ success: true, message: null });
  h.m.update.mockResolvedValue({ success: true, message: null });
  h.m.remove.mockResolvedValue({ success: true, message: null });
  h.m.setActive.mockResolvedValue({ success: true, message: null });
});

describe('CategoriesTab', () => {
  it('lists categories with counts and status', () => {
    renderConsole(<CategoriesTab />);
    for (const n of ['Name', 'Code', 'Description', 'Events', 'Status']) expect(screen.getByRole('columnheader', { name: n })).toBeInTheDocument();
    expect(screen.getByText('MUSIC')).toBeInTheDocument();
    expect(screen.getByText('Inactive')).toBeInTheDocument();
  });

  it('validates the code format and uniqueness before creating', async () => {
    renderConsole(<CategoriesTab />);
    fireEvent.click(screen.getByRole('button', { name: 'New category' }));
    const dlg = screen.getByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Name'), { target: { value: 'Music' } });
    fireEvent.change(within(dlg).getByLabelText('Code'), { target: { value: 'bad code' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Create' }));
    expect(await within(dlg).findByText('Use capital letters and underscores, 2 to 20 characters')).toBeInTheDocument();
    expect(within(dlg).getByText('Already exists')).toBeInTheDocument();
    expect(h.m.create).not.toHaveBeenCalled();
    fireEvent.change(within(dlg).getByLabelText('Name'), { target: { value: 'Arts' } });
    fireEvent.change(within(dlg).getByLabelText('Code'), { target: { value: 'ARTS' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Create' }));
    await vi.waitFor(() => expect(h.m.create).toHaveBeenCalledWith({ name: 'Arts', code: 'ARTS', description: null }));
  });

  it('refuses to delete a category that is in use and offers deactivation', () => {
    renderConsole(<CategoriesTab />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Sport' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Delete' }));
    expect(screen.getByText('Cannot delete Sport')).toBeInTheDocument();
    expect(screen.getByText(/3 events use this category/)).toBeInTheDocument();
    expect(h.m.remove).not.toHaveBeenCalled();
  });

  it('deletes an unused category after confirmation', async () => {
    renderConsole(<CategoriesTab />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Music' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Delete' }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete' }));
    await vi.waitFor(() => expect(h.m.remove).toHaveBeenCalledWith('k1'));
  });

  it('activates and deactivates', async () => {
    renderConsole(<CategoriesTab />);
    fireEvent.click(screen.getByRole('button', { name: 'More actions for Sport' }));
    fireEvent.click(screen.getByRole('menuitem', { name: 'Activate' }));
    await vi.waitFor(() => expect(h.m.setActive).toHaveBeenCalledWith('k2', true));
  });

  it('shows a dash instead of a count for a category with no count', () => {
    h.cats.mockReturnValue({ categories: [cat({ eventCount: null, isActive: false })], loading: false, error: undefined, refetch: vi.fn() });
    renderConsole(<CategoriesTab />);
    expect(screen.getByRole('cell', { name: '-' })).toBeInTheDocument();
  });

  it('keeps the code fixed when editing and sends only name and description', async () => {
    renderConsole(<CategoriesTab />);
    fireEvent.click(within(screen.getByRole('row', { name: /MUSIC/ })).getByRole('button', { name: 'Edit' }));
    const dlg = screen.getByRole('dialog');
    expect(within(dlg).queryByLabelText('Code')).toBeNull();
    fireEvent.change(within(dlg).getByLabelText('Name'), { target: { value: 'Live music' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Save changes' }));
    await vi.waitFor(() => expect(h.m.update).toHaveBeenCalledWith('k1', { name: 'Live music', description: 'Live' }));
  });

  it('shows empty, loading and error states', () => {
    h.cats.mockReturnValue({ categories: [], loading: false, error: undefined, refetch: vi.fn() });
    const a = renderConsole(<CategoriesTab />);
    expect(screen.getByText('No categories yet.')).toBeInTheDocument();
    a.unmount();
    h.cats.mockReturnValue({ categories: [], loading: true, error: undefined, refetch: vi.fn() });
    const b = renderConsole(<CategoriesTab />);
    expect(document.querySelector('.m3-skeleton')).not.toBeNull();
    b.unmount();
    h.cats.mockReturnValue({ categories: [], loading: false, error: new Error('x'), refetch: vi.fn() });
    renderConsole(<CategoriesTab />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });

  it('is locked for roles that cannot edit', () => {
    renderConsole(<CategoriesTab />, { roles: ['FINANCE'] });
    expect(screen.getByRole('button', { name: 'New category' })).toBeDisabled();
  });
});
