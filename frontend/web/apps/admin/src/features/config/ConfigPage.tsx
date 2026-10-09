'use client';

import { ModuleFrame } from '@/components/console';
import { RulesTab } from './RulesTab';
import { RolesTab } from './RolesTab';
import { ReferenceDataTab } from './ReferenceDataTab';

export type ConfigTab = 'rules' | 'roles' | 'refdata';

/** /config/[tab]: platform rules, roles and access, reference data. */
export function ConfigPage({ tab }: { tab: ConfigTab }) {
  return (
    <ModuleFrame module="config" tab={tab} title="Settings" subtitle="Rules, reference data and who can do what">
      {tab === 'rules' ? <RulesTab /> : tab === 'roles' ? <RolesTab /> : <ReferenceDataTab />}
    </ModuleFrame>
  );
}
