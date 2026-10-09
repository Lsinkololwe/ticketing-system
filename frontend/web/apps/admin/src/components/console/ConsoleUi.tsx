'use client';

import { createContext, useContext, type ReactNode } from 'react';
import type { MenuEntry } from '@pml.tickets/shared/components/m3';

export interface ConsoleUi {
  openPalette: () => void;
  bellItems: MenuEntry[];
  pendingTotal: number;
  signOut: () => void;
  openProfile: () => void;
}

const Ctx = createContext<ConsoleUi | null>(null);

export function ConsoleUiProvider({ value, children }: { value: ConsoleUi; children: ReactNode }) {
  return <Ctx.Provider value={value}>{children}</Ctx.Provider>;
}

export function useConsoleUi(): ConsoleUi | null {
  return useContext(Ctx);
}
