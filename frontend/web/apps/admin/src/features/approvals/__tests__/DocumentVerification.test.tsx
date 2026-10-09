import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { menuAction } from '@/test/menu';
import { renderConsole } from '@/test/render';
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/test/referenceMock')).referenceModule());
import { referenceFixtures } from '@/test/referenceMock';
referenceFixtures.KYB_DOCUMENT_TYPE = [
  { code: 'TAX_CERT', name: 'Tax certificate' },
  { code: 'NATIONAL_ID', name: 'National ID' },
];


const api = vi.hoisted(() => ({
  state: { organizations: [] as any[], loading: false, error: null as Error | null },
  refetch: vi.fn(), approve: vi.fn(), reject: vi.fn(),
}));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push: vi.fn(), replace: vi.fn() }) }));
vi.mock('@pml.tickets/shared/api/admin/modules', () => ({
  useApprovalOrganizationDocuments: () => ({ ...api.state, refetch: api.refetch }),
  useApproveVerificationDocument: () => ({ approve: api.approve }),
  useRejectVerificationDocument: () => ({ reject: api.reject }),
}));

import { DocumentVerification } from '../DocumentVerification';

const d = (id: string, status: string, over: Record<string, unknown> = {}) => ({ id, documentType: 'TAX_CERT', fileName: `${id}.pdf`, fileSize: 4096, status, uploadedAt: '2026-01-02T08:00:00Z', rejectionReason: null, ...over });

beforeEach(() => {
  vi.clearAllMocks();
  api.state = { organizations: [{ id: 'o1', name: 'Acme Events', verificationDocuments: [d('a', 'PENDING'), d('b', 'REJECTED', { rejectionReason: 'Blurry' }), d('c', 'PENDING')] }], loading: false, error: null };
  api.approve.mockResolvedValue({ id: 'a' });
  api.reject.mockResolvedValue({ id: 'a' });
  api.refetch.mockResolvedValue({});
});

describe('DocumentVerification', () => {
  it('lists documents pending first with Approve / Reject only on pending rows', () => {
    renderConsole(<DocumentVerification />);
    ['Document', 'Organization', 'Uploaded', 'Status'].forEach((h) => expect(screen.getByRole('columnheader', { name: h })).toBeInTheDocument());
    expect(screen.getAllByText('Acme Events')).toHaveLength(3);
    expect(screen.getAllByRole('button', { name: /^Approve (?!selected)/ })).toHaveLength(2);
    expect(screen.getAllByRole('button', { name: /^More actions for/ })).toHaveLength(2);
    expect(screen.getByText('Blurry')).toBeInTheDocument();
  });

  it('shows empty state', () => {
    api.state = { organizations: [], loading: false, error: null };
    renderConsole(<DocumentVerification />);
    expect(screen.getByText('No documents to verify.')).toBeInTheDocument();
  });

  it('approves a single document', async () => {
    renderConsole(<DocumentVerification />);
    fireEvent.click(screen.getAllByRole('button', { name: /^Approve (?!selected)/ })[0]);
    await waitFor(() => expect(api.approve).toHaveBeenCalledWith('a'));
  });

  it('requires a reason to reject', async () => {
    renderConsole(<DocumentVerification />);
    menuAction(screen.getAllByRole('button', { name: /^More actions for/ })[0].getAttribute('aria-label') as string, 'Reject…');
    fireEvent.click(screen.getByRole('button', { name: 'Reject document' }));
    expect(api.reject).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Not legible at all' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reject document' }));
    await waitFor(() => expect(api.reject).toHaveBeenCalledWith('a', 'Not legible at all'));
  });

  it('bulk approves only pending selected documents', async () => {
    renderConsole(<DocumentVerification />);
    const boxes = screen.getAllByRole('checkbox');
    boxes.slice(1).forEach((b) => fireEvent.click(b));
    fireEvent.click(screen.getByRole('button', { name: 'Approve selected' }));
    const dlg = screen.getByRole('alertdialog');
    expect(within(dlg).getByText('Approve 2 documents?')).toBeInTheDocument();
    fireEvent.click(within(dlg).getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(api.approve).toHaveBeenCalledTimes(2));
  });

  it('locks decisions for roles that cannot decide', () => {
    renderConsole(<DocumentVerification />, { roles: ['FINANCE'] });
    expect(screen.queryByRole('button', { name: /^Approve (?!selected)/ })).toBeNull();
  });

  it('filters by the document types the platform lists and names each document by its row', () => {
    renderConsole(<DocumentVerification />);
    expect(screen.getAllByText('Tax certificate').length).toBeGreaterThan(0);
    const type = screen.getByLabelText('Type') as HTMLSelectElement;
    expect(Array.from(type.options).map((o) => o.textContent)).toEqual(['All', 'Tax certificate', 'National ID']);
  });
});
