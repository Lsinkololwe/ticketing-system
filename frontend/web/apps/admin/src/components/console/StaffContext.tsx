'use client';

import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { can as canDo, type ActionKey } from '@/lib/permissions';
import type { StaffRole } from '@/config/navigation';
import type { PermissionCode } from '@pml.tickets/shared/generated/permission-codes';

export interface Staff {
  id: string;
  name: string;
  email: string;
  /** Shown to the person; decisions use `permissions`. */
  roles: StaffRole[];
  /** What the session's roles grant, from the catalogue. */
  permissions: PermissionCode[];
}

const StaffContext = createContext<Staff | null>(null);

export function StaffProvider({ staff, children }: { staff: Staff; children: ReactNode }) {
  return <StaffContext.Provider value={staff}>{children}</StaffContext.Provider>;
}

export function useStaff(): Staff & { can: (key: ActionKey) => boolean } {
  const staff = useContext(StaffContext);
  if (!staff) throw new Error('useStaff must be used inside <StaffProvider>');
  return useMemo(() => ({ ...staff, can: (key: ActionKey) => canDo(staff.permissions, key) }), [staff]);
}

export function initials(name: string): string {
  return name
    .split(/\s+/)
    .filter(Boolean)
    .map((w) => w[0])
    .join('')
    .slice(0, 2)
    .toUpperCase();
}
