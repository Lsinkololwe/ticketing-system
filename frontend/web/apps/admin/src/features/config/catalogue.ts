/**
 * UI layout of the staff role matrix: which in-page actions it lists, in which order, and how it words them.
 * The roles and permission codes themselves are not here: organization and event roles are reference data
 * (`ORGANIZATION_ROLE`, `EVENT_ROLE`) and the permission catalogue is read from identity.
 */

/** Order and names of the "Actions inside pages" rows of the role matrix. */
export const MATRIX_ACTIONS = [
  'decide', 'createAdmin', 'staffRoles', 'users', 'orgs', 'deleteUser', 'featureEvent', 'mediaMod', 'stock',
  'payoutDecide', 'secondApprove', 'closeEscrow', 'postJournal', 'reconcile', 'forceComplete', 'announce', 'cfgEdit', 'cfgHolds', 'escalations',
] as const;

export const ACTION_LABELS: Record<(typeof MATRIX_ACTIONS)[number], string> = {
  decide: 'Approve or reject submissions',
  createAdmin: 'Create staff accounts',
  staffRoles: 'Change staff roles',
  users: 'Manage users',
  orgs: 'Manage organizations',
  deleteUser: 'Delete users',
  featureEvent: 'Feature events',
  mediaMod: 'Moderate media',
  stock: 'Manage stock images',
  payoutDecide: 'Decide payouts',
  secondApprove: 'Give the second approval',
  closeEscrow: 'Close escrow accounts',
  postJournal: 'Post journal entries',
  reconcile: 'Run reconciliation',
  forceComplete: 'Force-complete transactions',
  announce: 'Send announcements',
  cfgEdit: 'Edit platform rules',
  cfgHolds: 'Change timing rules',
  escalations: 'Handle escalations',
};
