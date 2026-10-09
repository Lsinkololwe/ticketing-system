import { beforeEach, describe, expect, it, vi } from 'vitest';
import { fireEvent, screen, waitFor, within } from '@testing-library/react';
import { menuAction, menuItem } from '@/test/menu';
import { renderConsole } from '@/test/render';
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/test/referenceMock')).referenceModule());
import { referenceFixtures } from '@/test/referenceMock';
referenceFixtures.MOBILE_MONEY_OPERATOR = [
  { code: 'MTN', name: 'MTN Mobile Money', metadata: { providerCode: 'MTN_MOMO_ZMB' } },
  { code: 'AIRTEL', name: 'Airtel Money', metadata: { providerCode: 'AIRTEL_OAPI_ZMB' } },
];


const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => '/transactions/payments',
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock('next/dynamic', () => ({ default: () => () => <div>Reference data tab</div> }));

const h = vi.hoisted(() => ({
  attempts: { items: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  tickets: { items: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn(), pageInfo: { totalCount: 0 } },
  resv: { items: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn(), pageInfo: { totalCount: 0 } },
  events: { events: [{ id: 'e1', title: 'Jazz Night' }] as any[] },
  a: {
    addNote: vi.fn().mockResolvedValue(undefined), setReviewStatus: vi.fn().mockResolvedValue(undefined),
    updateTicket: vi.fn().mockResolvedValue(undefined), regenerateQr: vi.fn().mockResolvedValue(undefined),
    bulkCancel: vi.fn().mockResolvedValue({ processedCount: 1, failedCount: 0 }), forceExpire: vi.fn().mockResolvedValue(undefined),
    send: vi.fn().mockResolvedValue(2), busy: false,
  },
  payouts: { items: [] as any[], loading: false, error: undefined, refetch: vi.fn() },
  searches: [] as any[],
  stuck: { items: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn(), pageInfo: { totalCount: 0 } },
  dual: { proposals: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  rec: { resume: vi.fn().mockResolvedValue({}), retry: vi.fn().mockResolvedValue([]), forceComplete: vi.fn().mockResolvedValue({}), busy: false },
  dualActions: { confirm: vi.fn().mockResolvedValue({}), withdraw: vi.fn().mockResolvedValue({}), busy: false },
  audit: { entries: [] as any[], pageInfo: { totalCount: 0, pageSize: 20 }, loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  ann: { announcements: [] as any[], loading: false, error: undefined as Error | undefined, refetch: vi.fn() },
  annActions: { broadcast: vi.fn().mockResolvedValue({}), cancelAnnouncement: vi.fn().mockResolvedValue({}), busy: false },
  risk: { summary: null as any, loading: false, error: undefined, refetch: vi.fn() },
}));
vi.mock('@pml.tickets/shared/api/admin/modules/payments-ops', () => ({
  usePaymentAttemptSearch: (o: { filter?: { reviewStatus?: string; statuses?: string[] } } = {}) => {
    const f = o.filter ?? {};
    const items = h.attempts.items.filter((a) => (!f.reviewStatus || a.reviewStatus === f.reviewStatus) && (!f.statuses || f.statuses.includes(a.status)));
    h.searches.push(f);
    return { ...h.attempts, items, pageInfo: { totalCount: items.length, pageSize: 20 } };
  },
  useStuckTransactions: () => h.stuck,
  useDualControlQueue: () => h.dual,
  usePaymentRecoveryActions: () => h.rec,
  useDualControlActions: () => h.dualActions,
  usePaymentRiskSummary: () => h.risk,
  canConfirmProposal: (p: any, id: string | null) => p.status === 'PENDING' && p.canConfirm && p.proposedById !== id,
}));
vi.mock('@pml.tickets/shared/api/admin/modules/platform-ops', () => ({
  useAuditLogs: () => h.audit,
  useSystemAnnouncements: () => h.ann,
  useAnnouncementActions: () => h.annActions,
}));

vi.mock('@pml.tickets/shared/api/admin/modules/transactions', async (orig) => ({
  ...(await orig<object>()),
  useTicketSearch: () => h.tickets,
  useReservationsByEvent: (id: string | null) => (id ? h.resv : { ...h.resv, items: [] }),
  usePaymentAttemptActions: () => h.a,
  useTicketActions: () => h.a,
  useForceExpireReservation: () => h.a,
}));
vi.mock('@pml.tickets/shared/api/admin/modules/event', () => ({ useAdminEvents: () => h.events }));
vi.mock('@pml.tickets/shared/api/admin/modules/finance', () => ({ useRecoveryQueue: () => h.payouts }));

import { PaymentsTab } from '../PaymentsTab';
import { TicketsTab } from '../TicketsTab';
import { ReservationsTab } from '../ReservationsTab';
import { RecoveryTab } from '../RecoveryTab';
import { AuditTab } from '../AuditTab';
import { AnnounceTab, announceSchema } from '../AnnounceTab';
import { TransactionsPage } from '../TransactionsPage';

const old = () => new Date(Date.now() - 3 * 3600e3).toISOString();
const attempt = (o: object = {}) => ({ id: 'p1', depositId: 'dep-1', attemptNumber: 'PAY-20261001-AAAAA', ticketId: 'T-1', eventId: 'e1', buyerId: 'u-9', amount: 250, currency: 'ZMW', provider: 'MTN_MOMO_ZMB', payerPhone: '+260971234567', status: 'PROCESSING', providerStatus: null, providerTransactionId: null, failureCode: null, failureMessage: null, webhookProcessed: false, retryCount: 0, lastError: null, fulfilled: false, reviewStatus: null, reviewedBy: null, reviewedAt: null, reviewNotes: null, notes: null, createdAt: old(), updatedAt: null, expiresAt: null, ...o });
const ticket = (o: object = {}) => ({ id: 't1', ticketNumber: 'TKT-001', eventId: 'e1', eventTitle: 'Jazz Night', buyerName: 'Mary', buyerEmail: null, buyerPhone: '+260971234567', ticketCategoryCode: 'GA', ticketCategoryName: 'General', price: 100, currency: 'ZMW', status: 'ISSUED', qrCode: 'qr_abcdef12', purchaseDate: null, cancelledAt: null, cancellationReason: null, ...o });

beforeEach(() => {
  Object.assign(h.attempts, { items: [], loading: false, error: undefined });
  Object.assign(h.tickets, { items: [], loading: false, error: undefined });
  Object.assign(h.resv, { items: [], loading: false, error: undefined });
  Object.values(h.a).forEach((f) => typeof f === 'function' && 'mockClear' in f && (f as any).mockClear());
});

describe('Payments', () => {
  it('lists attempts, flags stuck ones and opens a detail sheet via a button', async () => {
    h.attempts.items = [attempt(), attempt({ id: 'p2', attemptNumber: 'PAY-2', status: 'COMPLETED' })];
    renderConsole(<PaymentsTab />);
    expect(screen.getByRole('table', { name: 'Payment attempts' })).toBeInTheDocument();
    expect(screen.getAllByText('Stuck')).toHaveLength(1);
    fireEvent.click(screen.getAllByRole('button', { name: 'Open' })[0]);
    const sheet = await screen.findByRole('dialog');
    expect(within(sheet).getByRole('button', { name: 'Add note…' })).toBeInTheDocument();
    expect(within(sheet).getByRole('button', { name: 'Resolve…' })).toBeInTheDocument();
    expect(within(sheet).getByText('This attempt has not been scored.')).toBeInTheDocument();
  });
  it('filters by status', () => {
    h.attempts.items = [attempt(), attempt({ id: 'p2', attemptNumber: 'PAY-2', status: 'COMPLETED' })];
    renderConsole(<PaymentsTab />);
    fireEvent.change(screen.getByLabelText('Status'), { target: { value: 'COMPLETED' } });
    expect(h.searches.at(-1)).toEqual(expect.objectContaining({ statuses: ['COMPLETED'] }));
    expect(screen.getAllByRole('button', { name: 'Open' })).toHaveLength(1);
  });
  it('requires a note before saving', async () => {
    h.attempts.items = [attempt()];
    renderConsole(<PaymentsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Open' }));
    fireEvent.click(await screen.findByRole('button', { name: 'Add note…' }));
    const dlgs = await screen.findAllByRole('dialog');
    const dlg = dlgs[dlgs.length - 1];
    fireEvent.click(within(dlg).getByRole('button', { name: 'Add note' }));
    expect(await within(dlg).findAllByText('Write a short note')).not.toHaveLength(0);
    fireEvent.change(within(dlg).getByLabelText('Note'), { target: { value: 'called buyer' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Add note' }));
    await waitFor(() => expect(h.a.addNote).toHaveBeenCalledWith('dep-1', 'called buyer'));
  });
  it('shows empty, loading and error states', () => {
    const r1 = renderConsole(<PaymentsTab />);
    expect(screen.getByText('No payment attempts match.')).toBeInTheDocument();
    r1.unmount();
    h.attempts.error = new Error('x');
    renderConsole(<PaymentsTab />);
    expect(screen.getByTestId('error-state')).toBeInTheDocument();
  });
});

describe('Tickets', () => {
  it('has per-row actions and bulk cancel with a required reason', async () => {
    h.tickets.items = [ticket(), ticket({ id: 't2', ticketNumber: 'TKT-002', status: 'CANCELLED' })];
    renderConsole(<TicketsTab />);
    expect(menuItem('More actions for ticket TKT-001', 'Regenerate QR')).toBeInTheDocument();
    expect(menuItem('More actions for ticket TKT-002', 'Cancel ticket…')).toHaveAttribute('aria-disabled', 'true');
    fireEvent.click(screen.getByLabelText('Select row t1'));
    fireEvent.click(screen.getByRole('button', { name: 'Cancel selected' }));
    const dlg = await screen.findByRole('dialog');
    fireEvent.click(within(dlg).getByRole('button', { name: 'Cancel tickets' }));
    expect(await within(dlg).findByText(/at least 5 characters/)).toBeInTheDocument();
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'event cancelled' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Cancel tickets' }));
    await waitFor(() => expect(h.a.bulkCancel).toHaveBeenCalledWith(['t1'], 'event cancelled'));
  });
  it('validates the phone format on update', async () => {
    h.tickets.items = [ticket()];
    renderConsole(<TicketsTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Update ticket TKT-001' }));
    const dlg = await screen.findByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Holder phone'), { target: { value: '0971' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Save changes' }));
    expect((await within(dlg).findAllByText(/Use \+260 then/)).length).toBeGreaterThan(0);
    expect(h.a.updateTicket).not.toHaveBeenCalled();
  });
  it('regenerates the QR after confirmation', async () => {
    h.tickets.items = [ticket()];
    renderConsole(<TicketsTab />);
    menuAction('More actions for ticket TKT-001', 'Regenerate QR');
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Regenerate QR' }));
    await waitFor(() => expect(h.a.regenerateQr).toHaveBeenCalledWith('t1'));
  });
  it('shows the empty state', () => {
    renderConsole(<TicketsTab />);
    expect(screen.getByText('No tickets match.')).toBeInTheDocument();
  });
});

describe('Reservations', () => {
  it('asks for an event, then lists holds with Force expire for HELD only', async () => {
    h.resv.items = [
      { id: 'res-00000001', eventId: 'e1', userId: 'u1', status: 'HELD', totalAmount: 100, currency: 'ZMW', expiresAt: new Date(Date.now() + 300000).toISOString(), createdAt: '', confirmedAt: null, releasedAt: null, failedAt: null, failureReason: null, items: [{ tierName: 'GA', quantity: 2 }] },
      { id: 'res-00000002', eventId: 'e1', userId: 'u2', status: 'CONFIRMED', totalAmount: 50, currency: 'ZMW', expiresAt: '2026-10-01T10:00:00Z', createdAt: '', confirmedAt: null, releasedAt: null, failedAt: null, failureReason: null, items: [] },
    ];
    renderConsole(<ReservationsTab />);
    expect(screen.getAllByText('Choose an event').length).toBeGreaterThan(0);
    fireEvent.change(screen.getByLabelText('Event'), { target: { value: 'e1' } });
    expect(await screen.findAllByRole('button', { name: /^More actions for reservation/ })).toHaveLength(1);
    menuAction(/^More actions for reservation/, 'Force expire');
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Expire hold' }));
    await waitFor(() => expect(h.a.forceExpire).toHaveBeenCalledWith('res-00000001'));
  });
});

describe('Recovery', () => {
  beforeEach(() => {
    h.stuck.items = []; h.stuck.pageInfo = { totalCount: 0 };
    h.dual.proposals = [];
    h.attempts.items = [];
  });
  it('summarises stuck value and runs resume, retry and force-complete through the backend', async () => {
    h.stuck.items = [attempt(), attempt({ id: 'p2', depositId: 'dep-2', attemptNumber: 'PAY-2', amount: 50 })];
    h.stuck.pageInfo = { totalCount: 2 };
    h.rec.retry.mockResolvedValue([{ depositId: 'dep-1', result: 'RETRIED', detail: null }]);
    renderConsole(<RecoveryTab />, { roles: ['SUPER_ADMIN'] });
    expect(screen.getByText('K 300')).toBeInTheDocument();
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    fireEvent.click(screen.getAllByRole('button', { name: /^Resume attempt/ })[0]);
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Resume' }));
    await waitFor(() => expect(h.rec.resume).toHaveBeenCalledWith('dep-1'));
    fireEvent.click(screen.getByLabelText('Select row p1'));
    fireEvent.click(screen.getByRole('button', { name: 'Retry selected' }));
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Retry all' }));
    await waitFor(() => expect(h.rec.retry).toHaveBeenCalledWith(['dep-1']));
    fireEvent.click(screen.getByLabelText('Select row p1'));
    fireEvent.click(screen.getByRole('button', { name: /Force-complete/ }));
    const dlg = await screen.findByRole('alertdialog').catch(() => screen.getByRole('dialog'));
    fireEvent.change(within(dlg).getByLabelText('Reason'), { target: { value: 'Money confirmed received in PawaPay' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Propose force-complete' }));
    await waitFor(() => expect(h.rec.forceComplete).toHaveBeenCalledWith(['dep-1'], 'Money confirmed received in PawaPay'));
  });
  it('hides force-complete for non super admins and states why', () => {
    h.stuck.items = [attempt()];
    renderConsole(<RecoveryTab />, { roles: ['FINANCE_LEAD'] });
    fireEvent.click(screen.getByLabelText('Select row p1'));
    expect(screen.queryByRole('button', { name: /Force-complete/ })).toBeNull();
    expect(screen.getByText(/Only super admins can force-complete/)).toBeInTheDocument();
  });
  it('marks for review through the review status mutation and switches views', async () => {
    h.stuck.items = [attempt()];
    h.attempts.items = [attempt({ id: 'p3', attemptNumber: 'PAY-3', status: 'FAILED', reviewStatus: 'PENDING_REVIEW' })];
    renderConsole(<RecoveryTab />);
    menuAction('More actions for attempt PAY-20261001-AAAAA', 'Mark for review');
    await waitFor(() => expect(h.a.setReviewStatus).toHaveBeenCalledWith('dep-1', 'PENDING_REVIEW'));
    fireEvent.click(screen.getByRole('tab', { name: 'Review queue' }));
    expect(await screen.findByRole('button', { name: /^More actions for attempt PAY-3/ })).toBeInTheDocument();
  });
  it('shows the empty stuck list', () => {
    renderConsole(<RecoveryTab />);
    expect(screen.getByText('No stuck transactions. Everything is moving.')).toBeInTheDocument();
  });
  it('dual control: the maker can only withdraw, another approver can confirm with a reason', async () => {
    const p = (id: string, by: string) => ({ id, action: 'FORCE_COMPLETE_PAYMENT_ATTEMPTS', amount: null, canConfirm: true, status: 'PENDING', proposedById: by, proposalReason: 'Stuck', proposedAt: '2026-10-05T07:00:00Z', expiresAt: '2026-10-06T07:00:00Z', subjectIds: ['d'], subjectType: 'PAYMENT_ATTEMPT' });
    h.dual.proposals = [p('mine', 'staff-1'), p('theirs', 'someone-else')];
    renderConsole(<RecoveryTab />, { roles: ['SUPER_ADMIN'] });
    fireEvent.click(screen.getByRole('tab', { name: /Second approval/ }));
    const list = await screen.findByRole('list', { name: 'Dual control queue' });
    expect(within(list).getAllByRole('button', { name: 'Confirm' })).toHaveLength(1);
    expect(within(list).getByRole('button', { name: 'Withdraw' })).toBeInTheDocument();
    expect(within(list).getByText('A second person must confirm.')).toBeInTheDocument();
    fireEvent.click(within(list).getByRole('button', { name: 'Confirm' }));
    const dlg = await screen.findByRole('dialog');
    fireEvent.change(within(dlg).getByLabelText('Why are you confirming it?'), { target: { value: 'Checked the PawaPay portal' } });
    fireEvent.click(within(dlg).getByRole('button', { name: 'Confirm action' }));
    await waitFor(() => expect(h.dualActions.confirm).toHaveBeenCalledWith('theirs', 'Checked the PawaPay portal'));
    fireEvent.click(within(list).getByRole('button', { name: 'Withdraw' }));
    await waitFor(() => expect(h.dualActions.withdraw).toHaveBeenCalledWith('mine'));
  });
});

describe('Audit and announcements', () => {
  it('audit lists entries with a details dialog', () => {
    h.audit.entries = [{ id: 'l1', action: 'USER_SUSPENDED', at: '2026-10-05T07:00:00Z', actorId: 'root', resourceType: 'user', resourceId: 'u1', status: 'SUCCESS', source: 'audit', metadata: { reason: 'abuse' } }];
    renderConsole(<AuditTab />);
    expect(screen.queryByText(/Not available yet/)).toBeNull();
    expect(screen.getByText('User suspended')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Details' }));
    expect(screen.getByText(/"reason": "abuse"/)).toBeInTheDocument();
  });
  it('audit empty and error states', () => {
    h.audit.entries = [];
    const { unmount } = renderConsole(<AuditTab />);
    expect(screen.getByText('No audit entries match.')).toBeInTheDocument();
    unmount();
    h.audit.error = new Error('down');
    renderConsole(<AuditTab />);
    expect(screen.getByRole('alert')).toBeInTheDocument();
    h.audit.error = undefined;
  });
  it('announcement schema validates', () => {
    const ok = { segment: 'ALL', severity: 'INFO', title: 'Hi there', message: 'Maintenance tonight', startsAt: '', endsAt: '' };
    expect(announceSchema.safeParse(ok).success).toBe(true);
    expect(announceSchema.safeParse({ ...ok, title: 'x' }).success).toBe(false);
    expect(announceSchema.safeParse({ ...ok, startsAt: '2026-10-06T10:00', endsAt: '2026-10-06T09:00' }).success).toBe(false);
  });
  it('validates, confirms and broadcasts to the chosen audience', async () => {
    renderConsole(<AnnounceTab />);
    fireEvent.click(screen.getByRole('button', { name: 'Send announcement' }));
    expect((await screen.findAllByText('Enter a title')).length).toBeGreaterThan(0);
    expect(h.annActions.broadcast).not.toHaveBeenCalled();
    fireEvent.change(screen.getByLabelText('Audience'), { target: { value: 'ORGANIZERS' } });
    fireEvent.change(screen.getByLabelText('Title'), { target: { value: 'Maintenance' } });
    fireEvent.change(screen.getByLabelText('Message'), { target: { value: 'Read-only from 02:00' } });
    fireEvent.click(screen.getByRole('button', { name: 'Send announcement' }));
    fireEvent.click(within(await screen.findByRole('alertdialog')).getByRole('button', { name: 'Send now' }));
    await waitFor(() => expect(h.annActions.broadcast).toHaveBeenCalledWith(expect.objectContaining({ segment: 'ORGANIZERS', severity: 'INFO', title: 'Maintenance', message: 'Read-only from 02:00' })));
  });
  it('lists sent announcements and cancels a live one', async () => {
    h.ann.announcements = [{ id: 'an1', title: 'Live one', message: 'Hello', segment: 'ALL', severity: 'INFO', startsAt: '2020-01-01T00:00:00Z', endsAt: null, cancelledAt: null, createdAt: '2020-01-01T00:00:00Z' }];
    renderConsole(<AnnounceTab />);
    expect(screen.getByText('Live one')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Cancel' }));
    await waitFor(() => expect(h.annActions.cancelAnnouncement).toHaveBeenCalledWith('an1'));
    h.ann.announcements = [];
  });
  it('is locked for roles without announce permission', () => {
    renderConsole(<AnnounceTab />, { roles: ['FINANCE'] });
    expect(screen.queryByRole('button', { name: 'Send announcement' })).toBeNull();
  });
});

describe('TransactionsPage', () => {
  it('tabs navigate to their routes', () => {
    renderConsole(<TransactionsPage tab="payments" />);
    const names = ['payments', 'tickets', 'reservations', 'recovery', 'refdata', 'audit', 'announce'];
    const tabs = screen.getAllByRole('tab');
    expect(tabs).toHaveLength(7);
    names.forEach((n, i) => {
      fireEvent.click(tabs[i]);
      expect(push).toHaveBeenLastCalledWith(`/transactions/${n}`);
    });
  });
  it('finance lead only sees four tabs', () => {
    renderConsole(<TransactionsPage tab="payments" />, { roles: ['FINANCE_LEAD'] });
    expect(screen.getAllByRole('tab')).toHaveLength(4);
  });
  it('renders the lazily loaded reference data tab', () => {
    renderConsole(<TransactionsPage tab="refdata" />);
    expect(screen.getByText('Reference data tab')).toBeInTheDocument();
  });
});

describe('Payments provider filter', () => {
  it('offers the gateway provider codes of the platform\'s mobile money operators', () => {
    renderConsole(<PaymentsTab />, { roles: ['FINANCE'] });
    const provider = screen.getByLabelText('Provider') as HTMLSelectElement;
    expect(Array.from(provider.options).map((o) => [o.value, o.textContent])).toEqual([
      ['all', 'All'],
      ['MTN_MOMO_ZMB', 'MTN Mobile Money'],
      ['AIRTEL_OAPI_ZMB', 'Airtel Money'],
    ]);
  });
});
