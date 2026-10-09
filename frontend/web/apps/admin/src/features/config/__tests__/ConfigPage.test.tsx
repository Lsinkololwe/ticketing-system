import { describe, expect, it, vi } from 'vitest';
import { fireEvent, screen } from '@testing-library/react';
import { renderConsole } from '@/test/render';

const push = vi.hoisted(() => vi.fn());
vi.mock('next/navigation', () => ({ useRouter: () => ({ push, replace: vi.fn() }) }));
vi.mock('../RulesTab', () => ({ RulesTab: () => <p>rules body</p> }));
vi.mock('../RolesTab', () => ({ RolesTab: () => <p>roles body</p> }));
vi.mock('../ReferenceDataTab', () => ({ ReferenceDataTab: () => <p>refdata body</p> }));

import { ConfigPage } from '../ConfigPage';

describe('ConfigPage', () => {
  it('renders the tab body and routes tabs to /config/<tab>', () => {
    renderConsole(<ConfigPage tab="roles" />);
    expect(screen.getByText('roles body')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: 'Platform rules' }));
    fireEvent.click(screen.getByRole('tab', { name: 'Reference data' }));
    expect(push.mock.calls.map((c) => c[0])).toEqual(['/config/rules', '/config/refdata']);
  });

  it('denies roles without the settings module', () => {
    renderConsole(<ConfigPage tab="rules" />, { roles: ['FINANCE'] });
    expect(screen.getByText("You don't have access to this")).toBeInTheDocument();
  });
});
