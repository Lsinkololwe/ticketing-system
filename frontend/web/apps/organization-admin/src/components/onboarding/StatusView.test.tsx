import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { StatusView, type StatusViewProps } from './StatusView';

const org = (status: string, extra = {}) => ({
  status, name: 'Fixture Org', type: 'BUSINESS', kybStatus: 'VERIFIED', taxId: '100', registrationNumber: null,
  submittedAt: '2026-10-01T09:00:00Z', reviewerNote: 'Fix the TPIN', ...extra,
});
const base = (o: Partial<StatusViewProps> = {}): StatusViewProps => ({
  loading: false, organization: org('DRAFT'), docs: [{ type: 'NATIONAL_ID', name: 'National ID', status: 'PENDING' }], ...o,
});

describe('StatusView', () => {
  it('draft: continue application link', () => {
    render(<StatusView {...base()} />);
    expect(screen.getByRole('list', { name: 'Application status' })).toBeInTheDocument();
    expect(screen.getByTestId('status-cta-resubmit')).toHaveAttribute('href', '/apply/documents');
  });
  it('pending review lists documents', () => {
    render(<StatusView {...base({ organization: org('PENDING_REVIEW') })} />);
    expect(screen.getByText('We are reviewing your application')).toBeInTheDocument();
    expect(screen.getByText('National ID')).toBeInTheDocument();
    expect(screen.getByTestId('status-cta-draft-event')).toHaveAttribute('href', '/events/new');
  });
  it('changes requested shows reviewer comments', () => {
    render(<StatusView {...base({ organization: org('CHANGES_REQUESTED') })} />);
    expect(screen.getByText('Fix the TPIN')).toBeInTheDocument();
    expect(screen.getByText('Update documents and resubmit')).toBeInTheDocument();
  });
  it('rejected offers re-apply', () => {
    render(<StatusView {...base({ organization: org('REJECTED') })} />);
    expect(screen.getByTestId('status-cta-reapply')).toHaveAttribute('href', '/apply/business-info');
  });
  it('active shows the summary and overview link', () => {
    render(<StatusView {...base({ organization: org('ACTIVE'), commissionRate: 5 })} />);
    expect(screen.getByText('Your organization is active.')).toBeInTheDocument();
    expect(screen.getByText('5%')).toBeInTheDocument();
    expect(screen.getByTestId('status-cta-dashboard')).toHaveAttribute('href', '/dashboard');
  });
  it('loading, empty and error states', () => {
    const { rerender } = render(<StatusView {...base({ loading: true, organization: null })} />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    rerender(<StatusView {...base({ organization: null })} />);
    expect(screen.getByText('No application yet')).toBeInTheDocument();
    rerender(<StatusView {...base({ error: { message: 'down' } })} />);
    expect(screen.getByTestId('status-error')).toBeInTheDocument();
  });
});
