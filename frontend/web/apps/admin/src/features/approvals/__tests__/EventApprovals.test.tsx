import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { menuAction, menuItem } from '@/test/menu';
import { renderConsole } from '@/test/render';

const push = vi.hoisted(() => vi.fn());
const api = vi.hoisted(() => ({
  state: { events: [] as any[], timelines: [] as any[], loading: false, error: null as Error | null },
  config: { approvalSlaHours: 72, approvalWarningThresholdHours: 24, requireCommentsOnRejection: true, requireCommentsOnChangesRequested: false, escalationRecipientRole: 'SUPER_ADMIN', escalationReminderIntervalHours: 12 },
  refetch: vi.fn(),
  approve: vi.fn(),
  reject: vi.fn(),
  requestChanges: vi.fn(),
  wf: { assign: vi.fn(), unassign: vi.fn(), comment: vi.fn(), acknowledge: vi.fn(), escalate: vi.fn(), busy: false },
}));
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn() }) }));
vi.mock('@pml.tickets/shared/api/admin/modules', () => ({
  usePendingApprovalEvents: () => ({ ...api.state, refetch: api.refetch }),
  usePlatformConfiguration: () => ({ config: api.config }),
  useEventDecisions: () => ({ approve: api.approve, reject: api.reject, requestChanges: api.requestChanges, submitting: false }),
  useApprovalWorkflow: () => api.wf,
  useReviewerCandidates: () => ({ reviewers: [{ id: 'r2', fullName: 'Peter Zulu' }], loading: false }),
  useApprovalTimeline: () => ({
    timeline: { timelineEvents: [
      { id: 't1', timestamp: '2026-01-02T08:00:00Z', action: 'SUBMITTED', actorName: 'Organizer', description: 'Submitted', comments: null, isEscalationRelated: false },
      { id: 't2', timestamp: '2026-01-03T08:00:00Z', action: 'COMMENT_ADDED', actorName: 'Natasha', description: 'c', comments: 'Looks fine', isEscalationRelated: false },
    ], escalation: null },
    loading: false, error: null, refetch: vi.fn(),
  }),
}));

import { EventApprovals } from '../EventApprovals';

const ev = (id: string, blockers: string[] = []) => ({
  id, title: `Event ${id}`, status: 'PENDING_APPROVAL', organizerId: 'o', organizerName: `Org ${id}`, eventDateTime: '2026-03-01T18:00:00Z',
  cityName: 'Lusaka', locationName: 'Venue', totalCapacity: 100, minTicketPrice: 50, currency: 'ZMW',
  submittedForApprovalAt: '2026-01-02T08:00:00Z', approvalBlockers: blockers, category: { id: 'c', name: 'Music' },
});
const tl = (id: string, over: Record<string, unknown> = {}) => ({
  eventId: id, assignedReviewerId: null, assignedReviewerName: null, submittedAt: '2026-01-02T08:00:00Z',
  slaDeadline: new Date(Date.now() + 100 * 36e5).toISOString(), isOverdue: false, hoursUntilDeadline: 100, submissionCount: 1, hasActiveEscalation: false, escalation: null, ...over,
});

beforeEach(() => {
  vi.clearAllMocks();
  api.state = { events: [ev('1'), ev('2', ['NO_CAPACITY'])], timelines: [tl('1'), tl('2', { submissionCount: 2, assignedReviewerId: 'staff-1', assignedReviewerName: 'Test Staff' })], loading: false, error: null };
  api.approve.mockResolvedValue({ success: true });
  api.reject.mockResolvedValue({ success: true });
  api.requestChanges.mockResolvedValue({ success: true });
  api.wf.assign.mockResolvedValue({}); api.wf.unassign.mockResolvedValue({}); api.wf.escalate.mockResolvedValue({});
  api.refetch.mockResolvedValue({});
});

describe('EventApprovals', () => {
  it('renders the queue with SLA, reviewer, blockers and per-row actions', () => {
    renderConsole(<EventApprovals />);
    ['Event', 'Submitted', 'SLA clock', 'Reviewer', 'Blockers', 'Escalation'].forEach((h) => expect(screen.getByRole('columnheader', { name: h })).toBeInTheDocument());
    expect(screen.getByText('No capacity set')).toBeInTheDocument();
    expect(screen.getByText('Submission 2')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: /^Review Event/ })).toHaveLength(2);
    expect(menuItem('More actions for Event 1', 'Claim')).toBeInTheDocument();
    expect(menuItem('More actions for Event 2', 'Release claim')).toBeInTheDocument();
    expect(menuItem('More actions for Event 1', 'Assign reviewer')).toBeInTheDocument();
  });

  it('shows the designed empty state', () => {
    api.state = { events: [], timelines: [], loading: false, error: null };
    renderConsole(<EventApprovals />);
    expect(screen.getByText('No events are waiting for approval.')).toBeInTheDocument();
  });

  it('claims through assignEventReviewer with the staff identity', async () => {
    renderConsole(<EventApprovals />);
    menuAction('More actions for Event 1', 'Claim');
    await waitFor(() => expect(api.wf.assign).toHaveBeenCalledWith('1', 'staff-1', 'Test Staff'));
  });

  it('assigns another reviewer through the form dialog', async () => {
    renderConsole(<EventApprovals />);
    menuAction('More actions for Event 1', 'Assign reviewer');
    const dlg = screen.getByRole('dialog', { name: 'Assign reviewer' });
    fireEvent.change(within(dlg).getByLabelText('Reviewer'), { target: { value: 'r2' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Assign' }));
    await waitFor(() => expect(api.wf.assign).toHaveBeenCalledWith('1', 'r2', 'Peter Zulu'));
  });

  it('refuses approval with blockers but offers request changes', () => {
    renderConsole(<EventApprovals />);
    fireEvent.click(screen.getAllByRole('button', { name: /^Review Event/ })[1]);
    fireEvent.click(screen.getByRole('button', { name: 'Approve event' }));
    expect(screen.getByText('Cannot approve yet')).toBeInTheDocument();
    expect(api.approve).not.toHaveBeenCalled();
  });

  it('approves a clear event after confirmation and shows timeline and comments', async () => {
    renderConsole(<EventApprovals />);
    fireEvent.click(screen.getAllByRole('button', { name: /^Review Event/ })[0]);
    expect(screen.getByText('Approval checklist')).toBeInTheDocument();
    expect(screen.getByText('Looks fine')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Approve event' }));
    fireEvent.click(within(screen.getByRole('alertdialog', { name: /Approve Event 1/ })).getByRole('button', { name: 'Approve event' }));
    await waitFor(() => expect(api.approve).toHaveBeenCalledWith('1'));
  });

  it('requires comments to reject when the platform rule says so', async () => {
    renderConsole(<EventApprovals />);
    fireEvent.click(screen.getAllByRole('button', { name: /^Review Event/ })[0]);
    fireEvent.click(screen.getByRole('button', { name: 'Reject' }));
    const dlg = screen.getByRole('dialog', { name: /Reject Event 1/ });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reject event' }));
    expect(api.reject).not.toHaveBeenCalled();
    fireEvent.change(within(dlg).getByRole('textbox'), { target: { value: 'Missing venue details' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Reject event' }));
    await waitFor(() => expect(api.reject).toHaveBeenCalledWith('1', 'Missing venue details'));
  });

  it('bulk approve skips events with blockers', async () => {
    renderConsole(<EventApprovals />);
    const boxes = screen.getAllByRole('checkbox');
    fireEvent.click(boxes[0]);
    fireEvent.click(screen.getByRole('button', { name: 'Approve selected' }));
    const dlg = screen.getByRole('dialog');
    expect(within(dlg).getByText(/Event 2/)).toBeInTheDocument();
    expect(within(dlg).getByText('Approve 1 event?')).toBeInTheDocument();
    fireEvent.click(within(dlg).getByRole('button', { name: 'Approve' }));
    await waitFor(() => expect(api.approve).toHaveBeenCalledTimes(1));
    expect(api.approve).toHaveBeenCalledWith('1', 'Bulk approval');
  });

  it('compare needs exactly two events', () => {
    renderConsole(<EventApprovals />);
    const boxes = screen.getAllByRole('checkbox');
    fireEvent.click(boxes[1]);
    fireEvent.click(screen.getByRole('button', { name: 'Compare side by side' }));
    expect(screen.getByText('Select exactly two events to compare')).toBeInTheDocument();
    fireEvent.click(boxes[2]);
    fireEvent.click(screen.getByRole('button', { name: 'Compare side by side' }));
    expect(screen.getByText('Compare events')).toBeInTheDocument();
  });

  it('open full page navigates to the event', () => {
    renderConsole(<EventApprovals />);
    fireEvent.click(screen.getAllByRole('button', { name: /^Review Event/ })[0]);
    fireEvent.click(screen.getByRole('button', { name: 'Open full page' }));
    expect(push).toHaveBeenCalledWith('/event/1');
  });
});
