'use client';

import { Banner, Button, KeyValue, SideSheet, StatusPill } from '@pml.tickets/shared/components/m3';
import { useIdentityUser, type AdminUserRecord } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { useStaff } from '@/components/console';
import { formatDate, formatDateTime } from '@/lib/format';
import { UserActivity } from './UserActivity';
import { MaskedValue, RolePills, VerifiedPill } from './common';
import type { useUserActionController } from './useUserActionController';

export interface UserQuickViewProps {
  user: AdminUserRecord | null;
  onClose: () => void;
  onOpen: (u: AdminUserRecord) => void;
  controller: ReturnType<typeof useUserActionController>;
}

/** Right-hand quick view of a user (prototype UDR); the full profile is one click away. */
export function UserQuickView({ user, onClose, onOpen, controller }: UserQuickViewProps) {
  const staff = useStaff();
  const detail = useIdentityUser(user?.id ?? null);
  const u = detail.user ?? user;
  const orgs = (detail.user?.organizationMemberships ?? []).map((m) => m.organization?.name).filter(Boolean);
  const acts = u && staff.can('users') ? controller.actionsFor(u).filter((a) => a.id !== 'edit').slice(0, 3) : [];
  return (
    <SideSheet
      open={Boolean(user)}
      onClose={onClose}
      title={u?.fullName ?? 'User'}
      subtitle={u ? <RolePills roles={u.roles} /> : undefined}
      actions={
        u ? (
          <>
            <Button variant="tonal" onClick={() => onOpen(u)}>
              Open full profile
            </Button>
            {acts.map((a) => (
              <Button
                key={a.id}
                variant="text"
                danger={a.danger}
                disabled={a.disabled}
                onClick={() => {
                  onClose();
                  controller.run(a.id, u);
                }}
              >
                {a.label}
              </Button>
            ))}
          </>
        ) : null
      }
    >
      {u ? (
        <div className="m3-stack">
          <div className="m3-row">
            <StatusPill status={u.accountStatus} />
          </div>
          {u.accountStatus === 'SUSPENDED' ? (
            <Banner tone="error" title="Suspended">
              {u.suspendReason || 'This account is suspended and cannot sign in or buy.'}
            </Banner>
          ) : null}
          {u.accountStatus === 'LOCKED' ? (
            <Banner tone="error" title="Locked">
              {u.lockReason || 'This account is locked and cannot sign in.'}
            </Banner>
          ) : null}
          <KeyValue columns
            items={[
              { label: 'Username', value: <span className="m3-mono">{u.username ?? '—'}</span> },
              { label: 'Email', value: <span className="m3-row"><MaskedValue kind="email" value={u.email} label="email" /><VerifiedPill verified={u.emailVerified} /></span> },
              { label: 'Phone', value: <span className="m3-row"><MaskedValue kind="phone" value={u.phoneNumber} label="phone" /><VerifiedPill verified={u.phoneVerified} /></span> },
              { label: 'Two-step verification', value: u.twoFactorEnabled ? 'On' : 'Off' },
              { label: 'Joined', value: formatDate(u.createdAt) },
              { label: 'Last sign-in', value: formatDateTime(u.lastLoginAt) },
              { label: 'Organizations', value: orgs.length ? orgs.join(', ') : '—' },
            ]}
          />
          <div>
            <h3>Recent activity</h3>
            <UserActivity resourceId={u.id} />
          </div>
        </div>
      ) : null}
    </SideSheet>
  );
}
