'use client';

import { ModuleFrame } from '@/components/console/ModuleFrame';
import {
  useApprovalOrganizationDocuments,
  useOrganizerApplications,
  usePendingApprovalEvents,
} from '@pml.tickets/shared/api/admin/modules';
import { DocumentVerification } from './DocumentVerification';
import { EventApprovals } from './EventApprovals';
import { OrgApplications } from './OrgApplications';

import type { ApprovalsTab } from './tabs';
export type { ApprovalsTab } from './tabs';

/** /approvals/[tab]: Organizers, Events, Documents. Tab badges count what is waiting. */
export function ApprovalsPage({ tab }: { tab: ApprovalsTab }) {
  const orgs = useOrganizerApplications();
  const events = usePendingApprovalEvents();
  const docs = useApprovalOrganizationDocuments();

  const counts = {
    orgs: orgs.applications.filter((o) => o.status === 'PENDING_REVIEW').length,
    events: events.events.length,
    docs: docs.organizations.reduce((n, o) => n + (o.verificationDocuments ?? []).filter((d) => d.status === 'PENDING').length, 0),
  };

  return (
    <ModuleFrame
      module="approvals"
      tab={tab}
      title="Approvals"
      subtitle="Review organizers, event submissions and verification documents"
      tabCounts={counts}
    >
      {tab === 'orgs' ? <OrgApplications /> : tab === 'events' ? <EventApprovals /> : <DocumentVerification />}
    </ModuleFrame>
  );
}
