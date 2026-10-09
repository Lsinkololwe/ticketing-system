/** Plain module (no 'use client') so the route can validate the segment on the server. */
export const TRANSACTIONS_TABS = ['payments', 'tickets', 'reservations', 'recovery', 'refdata', 'audit', 'announce'] as const;
