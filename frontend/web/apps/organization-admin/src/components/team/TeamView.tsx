'use client';

import type { ReactNode } from 'react';
import { Button, PageHeader, Tabs } from '@pml.tickets/shared/components/m3';

export const TEAM_TABS = [
  { id: 'members', label: 'Members' },
  { id: 'invites', label: 'Invitations' },
  { id: 'access', label: 'Event access' },
  { id: 'owner', label: 'Ownership' },
  { id: 'roles', label: 'Roles and access' },
] as const;
export type TeamTab = (typeof TEAM_TABS)[number]['id'];

export function parseTeamTab(value: string | null | undefined): TeamTab {
  return TEAM_TABS.some((t) => t.id === value) ? (value as TeamTab) : 'members';
}

interface Props {
  tab: TeamTab;
  onTabChange: (tab: TeamTab) => void;
  /** Show the "Invite people" action (owner/admin of an active organization). */
  canInvite: boolean;
  onInvitePeople: () => void;
  renderTab: (tab: TeamTab) => ReactNode;
}

/** Team page frame: header, "Invite people" and the five sections. */
export function TeamView({ tab, onTabChange, canInvite, onInvitePeople, renderTab }: Props) {
  return (
    <div data-testid="team-page">
      <PageHeader
        title="Team"
        subtitle="Members, invitations, event access and ownership."
        actions={canInvite ? <Button variant="filled" icon="add" onClick={onInvitePeople}>Invite people</Button> : null}
      />
      <Tabs variant="seg"
        label="Team sections"
        tabs={TEAM_TABS.map((t) => ({ id: t.id, label: t.label }))}
        value={tab}
        onChange={(id) => onTabChange(id as TeamTab)}
      >
        {(active) => <div className="oc-section">{renderTab(active as TeamTab)}</div>}
      </Tabs>
    </div>
  );
}
