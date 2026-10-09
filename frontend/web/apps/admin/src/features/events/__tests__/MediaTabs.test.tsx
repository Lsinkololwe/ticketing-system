import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const h = vi.hoisted(() => ({
  media: { assets: [] as any[], totalElements: 0, loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  mod: { flag: vi.fn().mockResolvedValue(undefined), remove: vi.fn().mockResolvedValue(undefined), restore: vi.fn().mockResolvedValue(undefined), busy: false },
  stock: { images: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  stockActs: { upload: vi.fn().mockResolvedValue({}), update: vi.fn().mockResolvedValue({}), remove: vi.fn().mockResolvedValue(undefined), busy: false },
}));
vi.mock('@pml.tickets/shared/api/admin/modules/media-ops', () => ({
  useMediaAssets: () => h.media,
  useMediaModeration: () => h.mod,
  useStockImages: () => h.stock,
  useStockImageActions: () => h.stockActs,
}));
import { MediaModerationTab, StockImagesTab } from '../MediaTabs';

const asset = (o: object = {}) => ({ id: 'm1', eventId: 'e1', organizationId: 'o1', fileName: 'banner.jpg', title: null, altText: 'Crowd', contentType: 'image/jpeg', sizeBytes: 204800, url: 'https://cdn.test/banner.jpg', status: 'ACTIVE', flaggedReason: null, removedReason: null, removedAt: null, ...o });
const stockImg = (o: object = {}) => ({ id: 's1', title: 'Stage', altText: 'A stage', url: 'https://cdn.test/s.jpg', purpose: 'EVENT_COVER', categoryCode: 'MUSIC', active: true, ...o });

beforeEach(() => {
  vi.clearAllMocks();
  h.media.assets = [asset(), asset({ id: 'm2', fileName: 'x.png', status: 'FLAGGED', flaggedReason: 'Offensive' }), asset({ id: 'm3', fileName: 'y.png', status: 'REMOVED', removedReason: 'Copyright' })];
  h.media.totalElements = 3; h.media.error = undefined;
  h.stock.images = [stockImg()]; h.stock.error = undefined;
});

describe('MediaModerationTab', () => {
  it('lists uploads with real images and status-based actions', () => {
    renderConsole(<MediaModerationTab />);
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getAllByRole('img').length).toBe(2);
    expect(screen.getByText('Offensive')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Restore' })).toHaveLength(2);
  });
  it('flags with a required reason and removes after a reason', async () => {
    renderConsole(<MediaModerationTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Flag…' }));
    let dlg = await screen.findByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Flag image' }));
    await waitFor(() => expect(h.mod.flag).not.toHaveBeenCalled());
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Looks inappropriate' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Flag image' }));
    await waitFor(() => expect(h.mod.flag).toHaveBeenCalledWith('m1', 'Looks inappropriate'));
    fireEvent.click(screen.getAllByRole('button', { name: 'Remove…' })[0]);
    dlg = await screen.findByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Copyright claim' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Remove image' }));
    await waitFor(() => expect(h.mod.remove).toHaveBeenCalledWith('m1', 'Copyright claim'));
  });
  it('restores, locks for roles without mediaMod, and shows empty and error', async () => {
    const { unmount } = renderConsole(<MediaModerationTab />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Restore' })[0]);
    await waitFor(() => expect(h.mod.restore).toHaveBeenCalledWith('m2'));
    unmount();
    const r2 = renderConsole(<MediaModerationTab />, { roles: ['FINANCE'] });
    expect(screen.getByRole('button', { name: 'Flag…' })).toBeDisabled();
    r2.unmount();
    h.media.assets = [];
    const r3 = renderConsole(<MediaModerationTab />);
    expect(screen.getByText('No images to moderate.')).toBeInTheDocument();
    r3.unmount();
    h.media.error = new Error('down');
    renderConsole(<MediaModerationTab />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
});

describe('StockImagesTab', () => {
  it('lists images, toggles active and deletes after confirmation', async () => {
    renderConsole(<StockImagesTab />);
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    fireEvent.click(screen.getByRole('switch', { name: 'Stage active' }));
    await waitFor(() => expect(h.stockActs.update).toHaveBeenCalledWith('s1', { active: false }));
    fireEvent.click(screen.getByRole('button', { name: 'Delete…' }));
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Delete image' }));
    await waitFor(() => expect(h.stockActs.remove).toHaveBeenCalledWith('s1'));
  });
  it('validates the upload form and file', async () => {
    renderConsole(<StockImagesTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Upload image' }));
    const dlg = await screen.findByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Upload image' }));
    expect((await within(dlg).findAllByText('Give the image a title')).length).toBeGreaterThan(0);
    expect(h.stockActs.upload).not.toHaveBeenCalled();
  });
  it('empty state with upload, and locked role', () => {
    h.stock.images = [];
    const { unmount } = renderConsole(<StockImagesTab />);
    expect(screen.getByText('No stock images yet.')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Upload image' })).not.toBeDisabled();
    unmount();
    renderConsole(<StockImagesTab />, { roles: ['FINANCE'] });
    expect(screen.getByRole('button', { name: 'Upload image' })).toBeDisabled();
  });
});
