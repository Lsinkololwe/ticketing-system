'use client';

import { ownerLabel } from '@/lib/ownerLabel';
import { Banner, Button, KeyValue, SideSheet, StatusPill } from '@pml.tickets/shared/components/m3';
import type { AdminOrgRecord } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { useStaff } from '@/components/console';
import { formatDate, humanize } from '@/lib/format';
import { MaskedValue, orgRateText } from './common';
import type { useOrgActionController } from './useOrgActionController';

export interface OrgQuickViewProps {
  org: AdminOrgRecord | null;
  onClose: () => void;
  onOpen: (o: AdminOrgRecord) => void;
  controller: ReturnType<typeof useOrgActionController>;
}

/** Right-hand quick view of an organization (prototype ODR). */
export function OrgQuickView({ org, onClose, onOpen, controller }: OrgQuickViewProps) {
  const staff = useStaff();
  const manage = staff.can('orgs');
  return (
    <SideSheet
      open={Boolean(org)}
      onClose={onClose}
      title={org?.name ?? 'Organization'}
      subtitle={org ? `${humanize(org.type)} · ${[org.businessAddress?.city, org.businessAddress?.province].filter(Boolean).join(', ') || '—'}` : undefined}
      actions={
        org ? (
          <>
            <Button variant="tonal" onClick={() => onOpen(org)}>
              Open full page
            </Button>
            {manage ? (
              <Button variant="text" onClick={() => controller.run('commission', org)}>
                Commission override
              </Button>
            ) : null}
            {manage && org.status === 'ACTIVE' ? (
              <Button variant="text" danger onClick={() => { onClose(); controller.run('suspend', org); }}>
                Suspend…
              </Button>
            ) : null}
            {manage && org.status === 'SUSPENDED' ? (
              <Button variant="text" onClick={() => { onClose(); controller.run('unsuspend', org); }}>
                Unsuspend
              </Button>
            ) : null}
          </>
        ) : null
      }
    >
      {org ? (
        <div className="m3-stack">
          <div className="m3-row">
            <StatusPill status={org.status} />
            <StatusPill status={org.kybStatus}>KYB: {humanize(org.kybStatus)}</StatusPill>
          </div>
          {org.status === 'SUSPENDED' ? (
            <Banner tone="error" title="Suspended">
              {org.suspensionReason || 'Events are hidden and payouts are blocked.'}
            </Banner>
          ) : null}
          <KeyValue columns
            items={[
              { label: 'Owner', value: ownerLabel(org) },
              { label: 'Email', value: <MaskedValue kind="email" value={org.businessEmail} label="organization email" /> },
              { label: 'Phone', value: <MaskedValue kind="phone" value={org.businessPhone} label="organization phone" /> },
              { label: 'Documents', value: org.documentsVerified ? 'All verified' : 'Not fully verified' },
              { label: 'Joined', value: formatDate(org.createdAt) },
            ]}
          />
          <div>
            <h3>Commercial</h3>
            <KeyValue columns
              items={[
                { label: 'Commission', value: orgRateText(org) },
                { label: 'Events', value: String(org.totalEvents ?? '—') },
              ]}
            />
          </div>
        </div>
      ) : null}
    </SideSheet>
  );
}
