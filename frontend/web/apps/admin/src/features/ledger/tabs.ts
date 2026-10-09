/** Plain module (no 'use client') so the route can validate the segment on the server. */
export const LEDGER_TABS = ['coa', 'journal', 'tb', 'platform', 'commission', 'recon'] as const;
export type LedgerTabId = (typeof LEDGER_TABS)[number];
