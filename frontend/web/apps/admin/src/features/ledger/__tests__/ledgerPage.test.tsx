import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const push = vi.fn();
vi.mock('next/navigation', () => ({
  useRouter: () => ({ push, replace: vi.fn(), back: vi.fn(), refresh: vi.fn(), prefetch: vi.fn() }),
  usePathname: () => '/ledger/tb',
  useSearchParams: () => new URLSearchParams(),
}));
vi.mock('@pml.tickets/shared/api/admin/modules/ledger', async (orig) => ({
  ...(await orig<object>()),
  useJournalEntries: () => ({ items: [], pageInfo: { totalCount: 3 }, loading: false, refetch: vi.fn() }),
  useChartOfAccounts: () => ({ items: [], loading: false, refetch: vi.fn() }),
  useTrialBalance: () => ({ items: [], loading: false, refetch: vi.fn() }),
}));

import { LedgerPage } from '../LedgerPage';

describe('LedgerPage', () => {
  it('renders the tab row, draft count, and navigates every tab', () => {
    renderConsole(<LedgerPage tab="tb" />);
    expect(screen.getByRole('heading', { name: 'Ledger' })).toBeInTheDocument();
    const tabs = screen.getAllByRole('tab');
    expect(tabs.map((t) => t.textContent?.replace(/\d+/g, '').trim())).toEqual([
      'Chart of accounts', 'Journal entries', 'Trial balance', 'Platform accounts', 'Commission', 'Reconciliation',
    ]);
    expect(screen.getByRole('tab', { name: /Journal entries/ })).toHaveTextContent('3');
    const paths = ['coa', 'journal', 'platform', 'commission', 'recon'];
    for (const p of paths) {
      fireEvent.click(tabs[['coa', 'journal', 'tb', 'platform', 'commission', 'recon'].indexOf(p)]);
      expect(push).toHaveBeenLastCalledWith(`/ledger/${p}`);
    }
  });
  it('denies roles outside the ledger module', () => {
    renderConsole(<LedgerPage tab="coa" />, { roles: [] });
    expect(screen.getByText('No access')).toBeInTheDocument();
  });
});
