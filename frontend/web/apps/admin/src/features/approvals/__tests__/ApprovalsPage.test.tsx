import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const push = vi.hoisted(() => vi.fn());
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn() }) }));
vi.mock('../OrgApplications', () => ({ OrgApplications: () => <p>orgs body</p> }));
vi.mock('../EventApprovals', () => ({ EventApprovals: () => <p>events body</p> }));
vi.mock('../DocumentVerification', () => ({ DocumentVerification: () => <p>docs body</p> }));
vi.mock('@pml.tickets/shared/api/admin/modules', () => ({
  useOrganizerApplications: () => ({ applications: [{ status: 'PENDING_REVIEW' }, { status: 'ACTIVE' }] }),
  usePendingApprovalEvents: () => ({ events: [{ id: '1' }, { id: '2' }, { id: '3' }] }),
  useApprovalOrganizationDocuments: () => ({ organizations: [{ verificationDocuments: [{ status: 'PENDING' }, { status: 'APPROVED' }] }] }),
}));

import { ApprovalsPage } from '../ApprovalsPage';

describe('ApprovalsPage', () => {
  it('renders the tab body, pending counts and routes tabs to /approvals/<tab>', () => {
    renderConsole(<ApprovalsPage tab="events" />);
    expect(screen.getByText('events body')).toBeInTheDocument();
    expect(screen.getByRole('tab', { name: /Organizers/ })).toHaveTextContent('1');
    expect(screen.getByRole('tab', { name: /Events/ })).toHaveTextContent('3');
    fireEvent.click(screen.getByRole('tab', { name: /Organizers/ }));
    fireEvent.click(screen.getByRole('tab', { name: /Documents/ }));
    expect(push.mock.calls.map((c) => c[0])).toEqual(['/approvals/orgs', '/approvals/docs']);
  });

  it('denies roles without the approvals module', () => {
    renderConsole(<ApprovalsPage tab="orgs" />, { roles: ['FINANCE'] });
    expect(screen.getByText("You don't have access to this")).toBeInTheDocument();
  });
});
