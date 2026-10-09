'use client';

import type { ReactNode } from 'react';
import { PageHeader, Tabs } from '@pml.tickets/shared/components/m3';

export const SETTINGS_TABS = [
  { id: 'profile', label: 'Organization profile' },
  { id: 'org', label: 'Organization settings' },
  { id: 'notif', label: 'Notifications' },
  { id: 'platform', label: 'Platform rules' },
  { id: 'me', label: 'My profile' },
  { id: 'danger', label: 'Delete organization' },
] as const;
export type SettingsTab = (typeof SETTINGS_TABS)[number]['id'];

export function parseTab(v: string | null | undefined, isOwner: boolean): SettingsTab {
  const t = SETTINGS_TABS.find((x) => x.id === v);
  if (!t || (t.id === 'danger' && !isOwner)) return 'profile';
  return t.id;
}

export interface SettingsViewProps {
  tab: SettingsTab;
  onTab: (t: SettingsTab) => void;
  isOwner: boolean;
  panels: Record<SettingsTab, ReactNode>;
}

export function SettingsView({ tab, onTab, isOwner, panels }: SettingsViewProps) {
  const tabs = SETTINGS_TABS.filter((t) => t.id !== 'danger' || isOwner).map((t) => ({ id: t.id, label: t.label }));
  return (
    <div data-testid="settings-page">
      <PageHeader title="Settings" subtitle="Organization profile, preferences and your account." />
      <Tabs label="Settings sections" variant="seg" tabs={tabs} value={tab} onChange={(id) => onTab(id as SettingsTab)}>
        {(id) => <div className="oc-section oc-settings-col">{panels[id as SettingsTab]}</div>}
      </Tabs>
    </div>
  );
}
