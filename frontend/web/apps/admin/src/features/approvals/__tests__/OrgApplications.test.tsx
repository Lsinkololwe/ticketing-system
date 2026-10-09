import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const push = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({
  state: { applications: [] as any[], loading: false, error: null as Error | null },
  refetch: vi.fn(),
  approve: vi.fn(),
  reject: vi.fn(),
  requestChanges: vi.fn(),
  approveDoc: vi.fn(),
  rejectDoc: vi.fn(),
}));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn() }) }));
vi.mock('@pml.tickets/shared/api/admin/modules', () => ({
  useOrganizerApplications: () => ({ ...api.state, refetch: api.refetch }),
  usePlatformConfiguration: () => ({ config: { approvalSlaHours: 72, approvalWarningThresholdHours: 24 } }),
  useApproveOrganization: () => ({ approve: api.approve, loading: false }),
  useRejectOrganization: () => ({ reject: api.reject }),
  useRequestOrganizationChanges: () => ({ requestChanges: api.requestChanges }),
  useApproveVerificationDocument: () => ({ approve: api.approveDoc }),
  useRejectVerificationDocument: () => ({ reject: api.rejectDoc }),
}));

import { OrgApplications } from '../OrgApplications';

const doc = (id: string, status: string) => ({ id, documentType: 'ID_DOCUMENT', fileName: `${id}.pdf`, fileSize: 2048, status, uploadedAt: '2026-01-02T08:00:00Z', rejectionReason: null });
const org = (id: string, over: Record<string, unknown> = {}) => ({
  id, name: `Org ${id}`, type: 'COMPANY', status: 'PENDING_REVIEW', kybStatus: 'PENDING', description: null,
  businessEmail: 'a@b.test', businessPhone: '+260', businessAddress: { city: 'Lusaka', province: 'Lusaka', country: 'Zambia' },
  taxId: 'T1', businessRegistrationNumber: 'R1', rejectionReason: null, submittedAt: new Date(Date.now() - 3_600_000).toISOString(),
  payoutAccountVerified: false, owner: { id: 'u', fullName: `Owner ${id}` }, verificationDocuments: [doc(`d${id}`, 'APPROVED')],
  payoutConfig: { verified: true, isConfigured: true, bankAccount: { bankName: 'Zanaco', maskedAccountNumber: '**** 1234', verified: true }, mobileMoneyAccount: null },
  ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  api.state = { applications: [org('1'), org('2', { verificationDocuments: [doc('x', 'PENDING')] })], loading: false, error: null };
  api.approve.mockResolvedValue({ id: '1' });
  api.reject.mockResolvedValue({ id: '1' });
  api.refetch.mockResolvedValue({});
});

describe('OrgApplications', () => {
  it('renders the queue with SLA pills and Review buttons (rows are not clickable)', () => {
    renderConsole(<OrgApplications />);
    expect(screen.getByRole('table', { name: 'Organizer applications' })).toBeInTheDocument();
    ['Organization', 'Owner', 'Submitted', 'Age and SLA', 'Documents', 'Status'].forEach((h) => expect(screen.getByRole('columnheader', { name: h })).toBeInTheDocument());
    expect(screen.getAllByText(/Due in/).length).toBe(2);
    expect(screen.getAllByRole('button', { name: 'Review' })).toHaveLength(2);
    expect(screen.getByText(/72 hour review target/)).toBeInTheDocument();
  });

  it('falls back to the owner email when the owner has no name (contact-code sign-ups)', () => {
    api.state = { applications: [org('9', { owner: { id: 'u', fullName: '', email: null, contacts: [{ valueMasked: 'o***@example.test', primary: true }] } })], loading: false, error: null };
    renderConsole(<OrgApplications />);
    expect(screen.getAllByText('o***@example.test').length).toBeGreaterThan(0);
  });

  it('shows empty, loading and error states', () => {
    api.state = { applications: [], loading: false, error: null };
    const { unmount } = renderConsole(<OrgApplications />);
    expect(screen.getByText('No organizer applications in the queue.')).toBeInTheDocument();
    unmount();
    api.state = { applications: [], loading: true, error: null };
    const l = renderConsole(<OrgApplications />);
    expect(l.container.querySelector('[aria-busy="true"]')).not.toBeNull();
    l.unmount();
    api.state = { applications: [], loading: false, error: new Error('boom') };
    renderConsole(<OrgApplications />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });

  it('requires exactly two rows to compare, then shows differing rows', () => {
    renderConsole(<OrgApplications />);
    const boxes = screen.getAllByRole('checkbox');
    fireEvent.click(boxes[1]);
    fireEvent.click(screen.getByRole('button', { name: 'Compare side by side' }));
    expect(screen.getByText('Select exactly two applications to compare')).toBeInTheDocument();
    fireEvent.click(boxes[2]);
    fireEvent.click(screen.getByRole('button', { name: 'Compare side by side' }));
    const dlg = screen.getByRole('dialog');
    expect(within(dlg).getByText('Compare applications')).toBeInTheDocument();
    expect(within(dlg).getAllByText('Differs').length).toBeGreaterThan(0);
  });

  it('opens the review sheet with documents and payout account, and opens the organization page', () => {
    renderConsole(<OrgApplications />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Review' })[0]);
    expect(screen.getByText('Verification documents')).toBeInTheDocument();
    expect(screen.getByText('Payout account')).toBeInTheDocument();
    expect(screen.getByText('Zanaco')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Open organization' }));
    expect(push).toHaveBeenCalledWith('/org/1');
  });

  it('blocks approval until every document is approved', () => {
    renderConsole(<OrgApplications />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Review' })[1]);
    fireEvent.click(screen.getByRole('button', { name: 'Approve organizer' }));
    expect(screen.getByText('Documents need review first')).toBeInTheDocument();
    expect(api.approve).not.toHaveBeenCalled();
  });

  it('approves with the platform default rate when the field is empty', async () => {
    renderConsole(<OrgApplications />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Review' })[0]);
    fireEvent.click(screen.getByRole('button', { name: 'Approve organizer' }));
    const dlg = screen.getByRole('dialog', { name: /Approve Org 1/ });
    expect(within(dlg).queryByText(/Not available yet/)).toBeNull();
    fireEvent.click(within(dlg).getByRole('button', { name: 'Approve organizer' }));
    await waitFor(() => expect(api.approve).toHaveBeenCalledWith('1', null));
  });

  it('approves with a custom commission rate and validates the range', async () => {
    renderConsole(<OrgApplications />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Review' })[0]);
    fireEvent.click(screen.getByRole('button', { name: 'Approve organizer' }));
    const dlg = screen.getByRole('dialog', { name: /Approve Org 1/ });
    fireEvent.change(within(dlg).getByLabelText('Commission rate (%)'), { target: { value: '120' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Approve organizer' }));
    expect(await within(dlg).findByText('Enter a percentage from 0 to 100')).toBeInTheDocument();
    expect(api.approve).not.toHaveBeenCalled();
    fireEvent.change(within(dlg).getByLabelText('Commission rate (%)'), { target: { value: '7.5' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Approve organizer' }));
    await waitFor(() => expect(api.approve).toHaveBeenCalledWith('1', 7.5));
  });

  it('requires a reason to reject', async () => {
    renderConsole(<OrgApplications />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Review' })[0]);
    fireEvent.click(screen.getByRole('button', { name: 'Reject' }));
    fireEvent.click(screen.getByRole('button', { name: 'Reject application' }));
    expect(api.reject).not.toHaveBeenCalled();
    fireEvent.change(screen.getByRole('textbox'), { target: { value: 'Documents are unreadable' } });
    fireEvent.click(screen.getByRole('button', { name: 'Reject application' }));
    await waitFor(() => expect(api.reject).toHaveBeenCalledWith('1', 'Documents are unreadable'));
  });

  it('hides decisions for roles that cannot decide', () => {
    renderConsole(<OrgApplications />, { roles: ['FINANCE'] });
    fireEvent.click(screen.getAllByRole('button', { name: 'Review' })[0]);
    expect(screen.queryByRole('button', { name: 'Approve organizer' })).toBeNull();
    expect(screen.getByText(/Only super admins and admins can approve or reject submissions/)).toBeInTheDocument();
  });
});
