import { fireEvent, render, screen, waitFor } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { MediaView, formatSize, type MediaViewProps } from './MediaView';
import type { MediaAssetRow } from '@/lib/api/media';

const row = (o: Partial<MediaAssetRow>): MediaAssetRow => ({
  id: 'm1', url: 'https://img.test/c.jpg', fileName: 'cover.jpg', contentType: 'image/jpeg', sizeBytes: 2097152, title: null, altText: 'Cover',
  eventId: null, status: 'ACTIVE', flaggedReason: null, removedReason: null, createdAt: '2026-09-01T10:00:00Z', ...o,
} as MediaAssetRow);
const base = (o: Partial<MediaViewProps> = {}): MediaViewProps => ({
  items: [row({}), row({ id: 'm2', fileName: 'logo.png', sizeBytes: 10240, altText: 'Logo' })],
  canManage: true, onUpload: vi.fn().mockResolvedValue(null), onSave: vi.fn().mockResolvedValue(undefined), onDelete: vi.fn().mockResolvedValue(undefined), ...o,
});

describe('MediaView', () => {
  it('shows loading and error states', () => {
    const { rerender } = render(<MediaView {...base({ items: [], loading: true })} />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    rerender(<MediaView {...base({ items: [], error: { message: 'boom' } })} />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
  it('shows the empty library', () => {
    render(<MediaView {...base({ items: [] })} />);
    expect(screen.getByText('No images yet')).toBeInTheDocument();
  });
  it('lists, toggles view, opens details and deletes', async () => {
    const p = base();
    render(<MediaView {...p} />);
    expect(screen.getByText(`2 images · ${formatSize(2107392)} used`)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('radio', { name: 'List' }));
    expect(screen.getByRole('table')).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Details' })[0]!);
    expect(screen.getByRole('dialog')).toHaveTextContent('Image details');
    fireEvent.click(screen.getByRole('button', { name: 'Delete image' }));
    fireEvent.click(screen.getAllByRole('button', { name: 'Delete image' }).at(-1)!);
    await waitFor(() => expect(p.onDelete).toHaveBeenCalledWith('m1'));
  });
  it('filters by search and hides management for readers', () => {
    render(<MediaView {...base({ canManage: false })} />);
    expect(screen.queryByText('Upload images')).toBeNull();
    fireEvent.change(screen.getByLabelText('Search images'), { target: { value: 'zzz' } });
    expect(screen.getByText('No images match')).toBeInTheDocument();
  });
});
