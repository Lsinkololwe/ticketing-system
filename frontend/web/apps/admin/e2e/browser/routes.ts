/** Every console route, the prototype hash it corresponds to, and the roles that may open it. */
export interface RouteSpec { app: string; proto: string; roles: string[]; name: string }
const OPS = ['SUPER_ADMIN', 'ADMIN'];
const ALL = ['SUPER_ADMIN', 'ADMIN', 'FINANCE', 'FINANCE_LEAD'];
const r = (app: string, proto: string, roles: string[]): RouteSpec => ({ app, proto, roles, name: app.replace(/^\//, '').replace(/\//g, '_') });
export const ROUTES: RouteSpec[] = [
  r('/dashboard', 'dash', ALL),
  r('/approvals/orgs', 'approvals/orgs', OPS), r('/approvals/events', 'approvals/events', OPS), r('/approvals/docs', 'approvals/docs', OPS),
  r('/events/all', 'events/all', OPS), r('/events/categories', 'events/categories', OPS), r('/events/locations', 'events/locations', OPS), r('/events/media', 'events/media', OPS), r('/events/stock', 'events/stock', OPS),
  r('/users/users', 'users/users', OPS), r('/users/orgs', 'users/orgs', OPS),
  r('/finance/payouts', 'finance/payouts', ALL), r('/finance/refunds', 'finance/refunds', ALL), r('/finance/escrow', 'finance/escrow', ALL), r('/finance/chargebacks', 'finance/chargebacks', ALL), r('/finance/banks', 'finance/banks', ALL),
  r('/ledger/coa', 'ledger/coa', ALL), r('/ledger/journal', 'ledger/journal', ALL), r('/ledger/tb', 'ledger/tb', ALL), r('/ledger/platform', 'ledger/platform', OPS), r('/ledger/commission', 'ledger/commission', ALL), r('/ledger/recon', 'ledger/recon', ALL),
  r('/transactions/payments', 'system/payments', ALL), r('/transactions/tickets', 'system/tickets', ALL), r('/transactions/reservations', 'system/reservations', ALL),
  r('/transactions/recovery', 'system/recovery', ALL), r('/transactions/refdata', 'system/refdata', OPS), r('/transactions/audit', 'system/audit', ALL), r('/transactions/announce', 'system/announce', OPS),
  r('/analytics', 'analytics', ALL), r('/health', 'health', OPS),
  r('/config/rules', 'config/rules', OPS), r('/config/roles', 'config/roles', OPS), r('/config/refdata', 'config/refdata', OPS),
  r('/profile', 'profile', ALL),
];
