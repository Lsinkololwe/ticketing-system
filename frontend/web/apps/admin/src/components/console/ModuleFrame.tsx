'use client';

/**
 * Page frame for a module: header, role gate and the tab row. Tabs are real
 * routes (/<module>/<tab>) so every view is deep-linkable.
 */
import type { ReactNode } from 'react';
import { useRouter } from 'next/navigation';
import { Button, Card, EmptyState, PageHeader, Tabs, type Crumb } from '@pml.tickets/shared/components/m3';
import { MODULE_BY_ID, ROLE_LABELS, STAFF_ROLES, canOpenModule, tabsFor, type ModuleId } from '@/config/navigation';
import { useStaff } from './StaffContext';
import { TopTools } from './TopTools';

export function AccessDenied({ module }: { module: ModuleId }) {
  const m = MODULE_BY_ID[module];
  const needed = STAFF_ROLES.filter((r) => m.roles.includes(r)).map((r) => ROLE_LABELS[r]);
  const router = useRouter();
  return (
    <Card>
      <EmptyState
        icon="lock"
        title="You don't have access to this"
        description={`${m.label} needs one of these roles: ${needed.join(', ')}.`}
        action={
          <Button variant="filled" onClick={() => router.push('/dashboard')}>
            Back to my dashboard
          </Button>
        }
      />
    </Card>
  );
}

export interface ModuleFrameProps {
  module: ModuleId;
  tab?: string;
  title: string;
  subtitle?: string;
  actions?: ReactNode;
  breadcrumbs?: Crumb[];
  onBack?: () => void;
  /** Pending counts shown on tabs, keyed by tab id. */
  tabCounts?: Record<string, number | undefined>;
  children: ReactNode;
}

export function ModuleFrame({ module, tab, title, subtitle, actions, breadcrumbs, onBack, tabCounts, children }: ModuleFrameProps) {
  const staff = useStaff();
  const router = useRouter();
  const allowed = canOpenModule(staff.roles, module);
  const tabs = tabsFor(staff.roles, module);
  const m = MODULE_BY_ID[module];
  const tabAllowed = !tab || tabs.some((t) => t.id === tab);

  if (!allowed || !tabAllowed) {
    return (
      <>
        <PageHeader title="No access" subtitle="You are signed in, but this area is not part of your role" actions={<TopTools />} />
        <AccessDenied module={module} />
      </>
    );
  }

  return (
    <>
      <PageHeader
        title={title}
        subtitle={subtitle}
        actions={
          <>
            {actions}
            <TopTools />
          </>
        }
        breadcrumbs={breadcrumbs}
        onBack={onBack}
      />
      {tab && tabs.length > 0 ? (
        <div className="adm-tabsrow">
          <Tabs
            label={`${m.label} sections`}
            variant="seg"
            value={tab}
            onChange={(id) => router.push(`${m.path}/${id}`)}
            tabs={tabs.map((t) => ({ id: t.id, label: t.label, count: tabCounts?.[t.id] }))}
          />
        </div>
      ) : null}
      {children}
    </>
  );
}
