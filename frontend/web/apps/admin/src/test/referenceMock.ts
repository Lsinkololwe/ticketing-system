/**
 * Test double for the shared reference hook. Fixtures are defined by each test (`referenceFixtures.X = [...]`);
 * a type a test does not set answers as a loaded, empty list. Use with:
 *
 *   vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/test/referenceMock')).referenceModule());
 */
export interface RefRow {
  code: string;
  name: string;
  description?: string | null;
  parentCode?: string | null;
  metadata?: Record<string, unknown>;
}

export const referenceFixtures: Record<string, RefRow[] | 'loading' | 'error'> = {};

export function resetReferenceFixtures() {
  for (const k of Object.keys(referenceFixtures)) delete referenceFixtures[k];
}

export function referenceModule() {
  return {
    useReferenceOptions: (type: string, settings?: { skip?: boolean; parentCode?: string | null }) => {
      const f = settings?.skip ? [] : (referenceFixtures[type] ?? []);
      const rows = f === 'loading' || f === 'error' ? [] : f;
      const options = rows
        .filter((r) => !settings?.parentCode || r.parentCode === settings.parentCode)
        .map((r) => ({ value: r.code, label: r.name, description: r.description ?? null, parentCode: r.parentCode ?? null, metadata: r.metadata ?? {} }));
      const byCode = new Map(options.map((o) => [o.value, o]));
      return {
        items: [],
        options,
        byCode,
        loading: f === 'loading',
        error: f === 'error' ? new Error('unavailable') : undefined,
        empty: f !== 'loading' && options.length === 0,
        ready: f !== 'loading' && f !== 'error',
        labelOf: (code: string | null | undefined) => (code ? byCode.get(code)?.label ?? code : ''),
      };
    },
  };
}

/** Fixture rows for the permission catalogue; a test may replace the array. */
export const permissionFixtures: Array<{ code: string; module: string; description: string; scope: string; heldBy: string }> = [
  { code: 'event:create', module: 'event', description: 'Create events', scope: 'ORGANIZATION', heldBy: 'Owner, Manager' },
  { code: 'payout:approve', module: 'payout', description: 'Approve payouts', scope: 'PLATFORM', heldBy: 'Finance' },
  { code: 'payout:request', module: 'payout', description: 'Request payouts', scope: 'ORGANIZATION', heldBy: 'Owner, Admin (when enabled)' },
];

/** `vi.mock('.../identity-admin', async (orig) => (await import('@/test/referenceMock')).identityAdminWithPermissions(await orig()))` */
export function identityAdminWithPermissions(original: object) {
  return {
    ...original,
    usePermissionCatalogue: () => ({ rows: permissionFixtures, loading: false, error: undefined, refetch: () => undefined }),
  };
}
