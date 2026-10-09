import { fireEvent, render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import { TeamView, parseTeamTab } from './TeamView';

describe('TeamView', () => {
  it('shows the five sections and the active panel', () => {
    const onTab = vi.fn();
    render(<TeamView tab="members" onTabChange={onTab} canInvite onInvitePeople={() => undefined} renderTab={(t) => <p>panel {t}</p>} />);
    expect(screen.getByRole('heading', { level: 1, name: 'Team' })).toBeInTheDocument();
    const names = screen.getAllByRole('tab').map((t) => t.textContent);
    expect(names).toEqual(['Members', 'Invitations', 'Event access', 'Ownership', 'Roles and access']);
    expect(screen.getByText('panel members')).toBeInTheDocument();
    fireEvent.click(screen.getByRole('tab', { name: 'Ownership' }));
    expect(onTab).toHaveBeenCalledWith('owner');
  });
  it('invites from the header', () => {
    const onInvite = vi.fn();
    render(<TeamView tab="members" onTabChange={() => undefined} canInvite onInvitePeople={onInvite} renderTab={() => null} />);
    fireEvent.click(screen.getByRole('button', { name: 'Invite people' }));
    expect(onInvite).toHaveBeenCalled();
  });
  it('hides invite when not allowed and parses tabs', () => {
    render(<TeamView tab="members" onTabChange={() => undefined} canInvite={false} onInvitePeople={() => undefined} renderTab={() => null} />);
    expect(screen.queryByRole('button', { name: 'Invite people' })).toBeNull();
    expect(parseTeamTab('roles')).toBe('roles');
    expect(parseTeamTab('nope')).toBe('members');
  });
});
