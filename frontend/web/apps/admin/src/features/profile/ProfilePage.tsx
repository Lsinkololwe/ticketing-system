'use client';

import { Tabs } from '@pml.tickets/shared/components/m3';
import { ModuleFrameLess } from './ModuleFrameLess';
import { PersonalInfo } from './PersonalInfo';
import { SecurityTab } from './SecurityTab';

/** My profile: editable name (updateMyProfile) and the security tab (two-step status, signed-in devices). */
export function ProfilePage() {
  return (
    <ModuleFrameLess title="My profile" subtitle="Your staff account and sign-in security.">
      <Tabs
        label="Profile sections"
        variant="seg"
        defaultValue="personal"
        tabs={[
          { id: 'personal', label: 'Personal info' },
          { id: 'security', label: 'Security and sessions' },
        ]}
      >
        {(tab) => (tab === 'personal' ? <PersonalInfo /> : <SecurityTab />)}
      </Tabs>
    </ModuleFrameLess>
  );
}
