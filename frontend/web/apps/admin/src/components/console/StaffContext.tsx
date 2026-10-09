'use client';

import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { can as canDo, type ActionKey } from '@/lib/permissions';
import type { StaffRole } from '@/config/navigation';

export interface Staff {
  id: string;
  name: string;
  email: string;
  roles: StaffRole[];
}

const StaffContext = createContext<Staff | null>(null);

export function StaffProvider({ staff, children }: { staff: Staff; children: ReactNode }) {
  return <StaffContext.Provider value={staff}>{children}</StaffContext.Provider>;
}

export function useStaff(): Staff & { can: (key: ActionKey) => boolean } {
  const staff = useContext(StaffContext);
  if (!staff) throw new Error('useStaff must be used inside <StaffProvider>');
  return useMemo(() => ({ ...staff, can: (key: ActionKey) => canDo(staff.roles, key) }), [staff]);
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
