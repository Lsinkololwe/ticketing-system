import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { FormProvider, useForm } from 'react-hook-form';
import { describe, expect, it, vi } from 'vitest';

const upload = vi.fn();
vi.mock('@/lib/api/media', () => ({
  useMyMedia: () => ({ items: [{ id: 'm1', url: 'https://cdn.test/a.jpg', fileName: 'a.jpg', title: 'Poster', altText: 'Poster' }], loading: false, error: undefined }),
  useMediaActions: () => ({ upload }),
  fileToBase64: async () => 'QUJD',
  MEDIA_TYPES: ['image/jpeg', 'image/png', 'image/webp'],
  MEDIA_MAX_BYTES: 5 * 1048576,
}));

import { MediaPickerField } from './MediaPickerField';

let latest = '';
function Harness({ initial = '' }: { initial?: string }) {
  const form = useForm({ defaultValues: { logoUrl: initial } });
  latest = form.watch('logoUrl');
  return (
    <FormProvider {...form}>
      <MediaPickerField name="logoUrl" label="Logo" noun="logo" />
    </FormProvider>
  );
}

describe('MediaPickerField', () => {
  it('chooses an image from the media library', () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: 'Choose logo' }));
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Use Poster' }));
    expect(latest).toBe('https://cdn.test/a.jpg');
    expect(screen.getByRole('button', { name: 'Change logo' })).toBeInTheDocument();
  });
  it('removes the image', () => {
    render(<Harness initial="https://cdn.test/a.jpg" />);
    fireEvent.click(screen.getByRole('button', { name: 'Remove' }));
    expect(latest).toBe('');
  });
  it('uploads a new image and uses the returned address; refuses other types', async () => {
    upload.mockResolvedValue({ data: { uploadMedia: { url: 'https://cdn.test/new.png' } } });
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: 'Choose logo' }));
    const input = screen.getByLabelText('Upload an image') as HTMLInputElement;
    fireEvent.change(input, { target: { files: [new File(['x'], 'doc.pdf', { type: 'application/pdf' })] } });
    expect(await screen.findByRole('alert')).toHaveTextContent('Only JPG, PNG or WEBP');
    fireEvent.change(input, { target: { files: [new File(['x'], 'logo.png', { type: 'image/png' })] } });
    await waitFor(() => expect(latest).toBe('https://cdn.test/new.png'));
    expect(upload).toHaveBeenCalledWith(expect.objectContaining({ fileName: 'logo.png', contentType: 'image/png', contentBase64: 'QUJD' }));
  });
});
