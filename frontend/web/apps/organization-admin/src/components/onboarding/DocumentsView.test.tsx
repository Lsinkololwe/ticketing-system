import { render, screen, waitFor, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { DocumentsView, type DocumentsViewProps } from './DocumentsView';
import { documentFileSchema } from './schemas';

const required = [
  { type: 'NATIONAL_ID', name: 'National ID', hint: 'NRC or passport' },
  { type: 'TAX_CERTIFICATE', name: 'Tax certificate', hint: 'ZRA' },
];
const base = (o: Partial<DocumentsViewProps> = {}): DocumentsViewProps => ({
  loading: false, businessTypeLabel: 'sole proprietor', required, slots: {}, onUpload: vi.fn(async () => undefined), onBack: vi.fn(), onNext: vi.fn(), ...o,
});
const pdf = (name = 'id.pdf', size = 10) => {
  const f = new File(['x'], name, { type: 'application/pdf' });
  Object.defineProperty(f, 'size', { value: size });
  return f;
};

describe('documentFileSchema', () => {
  it('accepts a PDF, JPEG, PNG or WEBP under 10 MB and rejects others', () => {
    for (const type of ['application/pdf', 'image/jpeg', 'image/png', 'image/webp']) {
      expect(documentFileSchema.safeParse({ file: [new File(['x'], 'a', { type })] }).success).toBe(true);
    }
    expect(documentFileSchema.safeParse({ file: [new File(['x'], 'a.gif', { type: 'image/gif' })] }).error?.issues[0]?.message).toBe('That file type is not supported');
    expect(documentFileSchema.safeParse({ file: [pdf('big.pdf', 11 * 1024 * 1024)] }).error?.issues[0]?.message).toBe('Each file must be 10 MB or smaller');
    expect(documentFileSchema.safeParse({ file: [] }).error?.issues[0]?.message).toBe('Choose a file');
  });
});

describe('DocumentsView', () => {
  it('lists required documents and blocks Continue until all are supplied', () => {
    render(<DocumentsView {...base()} />);
    expect(screen.getByTestId('document-slot-NATIONAL_ID')).toBeInTheDocument();
    expect(screen.getByTestId('documents-progress-text')).toHaveTextContent('0 of 2 uploaded');
    expect(screen.getByRole('button', { name: 'Continue' })).toBeDisabled();
  });

  it('enables Continue when uploaded', async () => {
    const p = base({ slots: { NATIONAL_ID: { status: 'PENDING', fileName: 'id.pdf' }, TAX_CERTIFICATE: { status: 'APPROVED', fileName: 't.pdf' } } });
    render(<DocumentsView {...p} />);
    await userEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(p.onNext).toHaveBeenCalled();
  });

  it('uploads a chosen file immediately', async () => {
    const p = base();
    render(<DocumentsView {...p} />);
    const file = pdf();
    await userEvent.upload(screen.getAllByLabelText(/Choose file for National ID/)[0] as HTMLInputElement, file);
    await waitFor(() => expect(p.onUpload).toHaveBeenCalledWith('NATIONAL_ID', file));
  });

  it('shows the type message for an unsupported file and does not upload', async () => {
    const p = base();
    render(<DocumentsView {...p} />);
    const gif = new File(['x'], 'a.gif', { type: 'image/gif' });
    await userEvent.upload(screen.getAllByLabelText(/Choose file for National ID/)[0] as HTMLInputElement, gif, { applyAccept: false });
    expect(await screen.findByText('That file type is not supported')).toBeInTheDocument();
    expect(p.onUpload).not.toHaveBeenCalled();
  });

  it('maps an upload failure onto the document form', async () => {
    const p = base({ onUpload: vi.fn(async () => { throw new Error('Storage unavailable'); }) });
    render(<DocumentsView {...p} />);
    await userEvent.upload(screen.getAllByLabelText(/Choose file for Tax certificate/)[0] as HTMLInputElement, pdf());
    const slot = screen.getByTestId('document-slot-TAX_CERTIFICATE');
    expect(await within(slot).findByRole('alert')).toBeInTheDocument();
    expect(within(screen.getByTestId('document-slot-NATIONAL_ID')).queryByRole('alert')).toBeNull();
  });

  it('shows progress and rejection reasons', () => {
    render(<DocumentsView {...base({ slots: { NATIONAL_ID: { status: 'UPLOADING', progress: 40, fileName: 'a.pdf' }, TAX_CERTIFICATE: { status: 'REJECTED', rejectionReason: 'Blurry' } } })} />);
    expect(screen.getByText('Uploading 40%')).toBeInTheDocument();
    expect(screen.getByText('Blurry')).toBeInTheDocument();
  });

  it('handles loading and missing business type', async () => {
    const { rerender } = render(<DocumentsView {...base({ loading: true })} />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    const p = base({ businessTypeLabel: null });
    rerender(<DocumentsView {...p} />);
    await userEvent.click(screen.getByTestId('back-to-business-info'));
    expect(p.onBack).toHaveBeenCalled();
  });
});
