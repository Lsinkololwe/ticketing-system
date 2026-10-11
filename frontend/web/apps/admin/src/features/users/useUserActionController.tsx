'use client';

import { useStepUp } from '@/lib/useStepUp';
import { useCallback, useState, type ReactNode } from 'react';
import { ConfirmDialog, useSnackbar, type MenuEntry } from '@pml.tickets/shared/components/m3';
import { useUserAdminActions, type AdminUserRecord } from '@pml.tickets/shared/api/admin/modules/identity-admin';
import { useStaffActions } from '@pml.tickets/shared/api/admin/modules/platform-ops';
import { ReasonDialog, useStaff } from '@/components/console';
import { errorMessage } from './common';
import { RolesDialog, UserFormDialog } from './UserDialogs';

export type UserActionId = 'edit' | 'roles' | 'deactivate' | 'lock' | 'suspend' | 'activate' | 'unlock' | 'unsuspend' | 'delete';

export interface UserActionDef {
  id: UserActionId;
  label: string;
  danger?: boolean;
  disabled?: boolean;
  hint?: string;
}

/** Which row actions the signed-in staff member is offered for a user (prototype userActs). */
export function userActionList(u: AdminUserRecord, staff: { id: string; can: (k: 'staffRoles' | 'deleteUser') => boolean }): UserActionDef[] {
  const isStaffUser = u.roles.some((r) => ['ADMIN', 'SUPER_ADMIN', 'FINANCE', 'FINANCE_LEAD'].includes(r));
  const manage = u.id !== staff.id && !(u.roles.includes('SUPER_ADMIN') && !staff.can('staffRoles'));
  const s = u.accountStatus;
  const a: UserActionDef[] = [{ id: 'edit', label: 'Edit' }];
  if (staff.can('staffRoles') || !isStaffUser) a.push({ id: 'roles', label: 'Change roles' });
  if (manage) {
    if (s === 'ACTIVE') a.push({ id: 'deactivate', label: 'Deactivate' }, { id: 'lock', label: 'Lock account' }, { id: 'suspend', label: 'Suspend…', danger: true });
    if (['INACTIVE', 'PENDING_VERIFICATION', 'PENDING_DELETION'].includes(s)) a.push({ id: 'activate', label: 'Activate' });
    if (s === 'LOCKED') a.push({ id: 'unlock', label: 'Unlock account' });
    if (s === 'SUSPENDED') a.push({ id: 'unsuspend', label: 'Unsuspend' });
  }
  if (staff.can('deleteUser') && manage) {
    a.push({ id: 'delete', label: 'Delete user', danger: true });
  }
  return a;
}

export function toMenu(actions: UserActionDef[], run: (id: UserActionId) => void): MenuEntry[] {
  return actions.map((x) => ({ id: x.id, label: x.label, danger: x.danger, disabled: x.disabled, hint: x.hint, onSelect: () => run(x.id) }));
}

/**
 * Owns the dialogs and mutations behind every user action, so the table, the
 * quick-view sheet and the profile page all behave identically.
 * Render `dialogs` once next to the consumer.
 */
export function useUserActionController(): {
  actionsFor: (u: AdminUserRecord) => UserActionDef[];
  run: (id: UserActionId, u: AdminUserRecord) => void;
  dialogs: ReactNode;
} {
  const staff = useStaff();
  const api = useUserAdminActions();
  const staffApi = useStaffActions();
  const snack = useSnackbar();
  const { guard } = useStepUp();
  const [pending, setPending] = useState<{ id: UserActionId; user: AdminUserRecord } | null>(null);
  const [busy, setBusy] = useState(false);
  const close = () => setPending(null);

  const attempt = useCallback(
    async (fn: () => Promise<unknown>, message: string, undo?: () => Promise<unknown>) => {
      setBusy(true);
      try {
        await fn();
        setPending(null);
        snack.show(
          undo
            ? {
                message,
                actionLabel: 'Undo',
                onAction: () => {
                  undo().catch((e) => snack.show({ message: errorMessage(e), tone: 'error' }));
                },
              }
            : message
        );
      } catch (e) {
        snack.show({ message: errorMessage(e), tone: 'error' });
      } finally {
        setBusy(false);
      }
    },
    [snack]
  );

  const run = useCallback(
    (id: UserActionId, u: AdminUserRecord) => {
      if (!staff.can('users')) return;
      const n = u.fullName;
      switch (id) {
        case 'activate':
          void attempt(() => api.activateUser(u.id), `${n} activated`, () => api.deactivateUser(u.id));
          break;
        case 'unlock':
          void attempt(() => api.unlockUser(u.id), `${n} unlocked`);
          break;
        case 'unsuspend':
          void attempt(() => api.unsuspendUser(u.id), `${n} unsuspended`);
          break;
        default:
          setPending({ id, user: u });
      }
    },
    [api, attempt, staff]
  );

  const u = pending?.user ?? null;
  const dialogs = (
    <>
      <UserFormDialog
        open={pending?.id === 'edit'}
        mode="edit"
        user={u}
        loading={busy}
        onClose={close}
        onSubmit={(v) =>
          u &&
          attempt(() => api.updateUser(u.id, { firstName: v.firstName, lastName: v.lastName }), `${u.fullName} updated`)
        }
      />
      <RolesDialog
        open={pending?.id === 'roles'}
        user={u}
        canEditStaffRoles={staff.can('staffRoles')}
        loading={busy}
        onClose={close}
        onSubmit={(roles) => u && attempt(() => guard(() => api.setUserRoles(u.id, roles)), `Roles updated for ${u.fullName}`)}
      />
      <ConfirmDialog
        open={pending?.id === 'deactivate'}
        onClose={close}
        title={`Deactivate ${u?.fullName ?? ''}?`}
        description="They cannot sign in until reactivated."
        confirmLabel="Deactivate"
        danger
        loading={busy}
        onConfirm={() => u && attempt(() => guard(() => api.deactivateUser(u.id)), `${u.fullName} deactivated`, () => api.activateUser(u.id))}
      />
      <ReasonDialog
        open={pending?.id === 'lock'}
        title={`Lock ${u?.fullName ?? ''}?`}
        body="Locking blocks sign-in immediately. Unlock restores access."
        confirmLabel="Lock account"
        danger
        loading={busy}
        onClose={close}
        onConfirm={(reason) => u && attempt(() => guard(() => api.lockUser(u.id, reason)), `${u.fullName} locked`, () => api.unlockUser(u.id))}
      />
      <ReasonDialog
        open={pending?.id === 'suspend'}
        title={`Suspend ${u?.fullName ?? ''}?`}
        body="Suspended users cannot sign in or buy. The reason is kept in the audit log."
        confirmLabel="Suspend user"
        danger
        loading={busy}
        onClose={close}
        onConfirm={(reason) => u && attempt(() => guard(() => api.suspendUser(u.id, reason)), `${u.fullName} suspended`, () => api.unsuspendUser(u.id))}
      />
      <ConfirmDialog
        open={pending?.id === 'delete'}
        onClose={close}
        title={`Delete ${u?.fullName ?? ''}?`}
        description="The account is closed and queued for deletion. It can be restored until the retention period ends. Their tickets and records are kept."
        confirmLabel="Delete user"
        danger
        loading={busy}
        onConfirm={() => u && attempt(() => guard(() => staffApi.deleteUser(u.id)), `${u.fullName} deleted`)}
      />
    </>
  );

  return { actionsFor: (user) => userActionList(user, staff), run, dialogs };
}

