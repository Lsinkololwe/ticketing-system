export type ApprovalsTab = 'orgs' | 'events' | 'docs';
/** Plain module (no 'use client') so the route can validate the segment on the server. */
export const APPROVALS_TABS: readonly ApprovalsTab[] = ['orgs', 'events', 'docs'];
