import { readdirSync, readFileSync, statSync } from 'node:fs';
import path from 'node:path';
import { describe, expect, it } from 'vitest';

/**
 * Every list a screen offers is either reference data (served by the backend), a label map typed from the
 * generated GraphQL enum, or UI-only. This test fails when a fourth kind appears: a hand-typed list of
 * values in production code that nothing ties to the platform.
 *
 * What it looks for, in `apps/*\/src` and `libs/shared/src` (tests, e2e and the generated types excluded):
 *
 *  A. a constant whose name says "list of choices" (`*_OPTIONS`, `*_CHANNELS`, `*_ROLES`, `*_STATUSES`, ...)
 *     initialised with two or more string literals, unless its type names a generated GraphQL type (then the
 *     compiler already ties its members to the schema);
 *  B. `options={[ { value: '..', label: '..' }, ... ]}` written inline in JSX;
 *  C. `z.enum([ 'A', 'B', ... ])` in a form schema.
 *
 * Anything that is legitimately none of the above goes in an allowlist below with the reason, and the
 * allowlist is itself checked: an entry that no longer matches anything fails, so it cannot rot into a
 * blanket exemption. To add a reference list, do not allowlist it: add a reference type and read it with
 * `useReferenceOptions` (libs/shared/src/api/graphql/shared/reference).
 */

const WEB = path.resolve(__dirname, '../../../..');
const NAMED = /^(?:[A-Z][A-Z0-9_]*_)?(OPTIONS|CHANNELS|CATEGORIES|PROVINCES|ROLES|PROVIDERS|NETWORKS|BANKS|COUNTRIES|CURRENCIES|TYPES|REASONS|PERIODS|STATUSES|LABELS|CAPABILITIES|PERMISSIONS|SEGMENTS|DOCUMENTS)$/;

/** `relative/file.ts#NAME` -> why it is not reference data. */
const ALLOWED_NAMED: Record<string, string> = {
  // admin
  'apps/admin/src/config/navigation.ts#ROLE_LABELS': 'Typed Record over StaffRole = Extract<generated UserType, ...>: every staff role must be labelled or tsc fails; STAFF_ROLES is derived from its keys',
  'apps/admin/src/config/navigation.ts#RAIL_LABELS': 'UI-only: short names on the phone bottom bar, keyed by console module',
  'apps/admin/src/features/config/approval.ts#APPROVAL_LABELS': 'UI-only: captions of platform-configuration form fields, keyed by the APPROVAL_KEYS field names',
  'apps/admin/src/features/config/catalogue.ts#ACTION_LABELS': 'UI-only: wording of the in-page action rows of the staff role matrix (MATRIX_ACTIONS), whose rules live in lib/permissions',
  'apps/admin/src/features/events/StockImagesTab.tsx#TYPES': 'Upload contract of the stock-image endpoint: accepted image MIME types, checked before the upload starts',
  'apps/admin/src/lib/permissions.ts#LABELS': 'Typed Record over StaffRole: plural role names used in "only X may" sentences',
  'libs/shared/src/api/admin/modules/document/document.schemas.ts#ACCEPTED_MIME_TYPES': 'Upload contract of the document endpoint: accepted MIME types, checked before the upload starts',
  // ticketing
  'apps/ticketing/src/components/home/filters.ts#WHEN_OPTIONS': 'UI-only: relative date windows of the events search, turned into a start and end date before the API call',
  'apps/ticketing/src/components/home/filters.ts#SORT_OPTIONS': 'UI-only: sort choices, mapped to the generated EventDiscoverySort by SORT_TO_API (a Record over it)',
  'libs/shared/src/auth/keycloak-config.ts#KEYCLOAK_ROLES': 'Auth contract: realm role names Keycloak issues, not a list a person chooses from',
  // org-admin B
  'apps/organization-admin/src/components/console/Status.tsx#LABELS': 'Product wording overrides over several enums; unlisted values are humanised, so no value set lives here',
  'apps/organization-admin/src/components/events-editor/model.ts#TAB_LABELS': 'UI-only: titles of the event editor tabs',
  'apps/organization-admin/src/components/onboarding/steps.tsx#WIZARD_LABELS': 'UI-only: step titles of the onboarding wizard',
  'apps/organization-admin/src/lib/api/media.ts#MEDIA_TYPES': 'Upload contract of the files service: image MIME types, checked before the upload starts',
  'apps/organization-admin/src/lib/bff.config.ts#ORGANIZER_ROLES': 'Auth contract: Keycloak realm roles that may open the organizer app',
  'apps/organization-admin/src/lib/bff.config.ts#CSP_OPTIONS': 'Security header directives, not a list a person chooses from',
  // org-admin A
  'apps/organization-admin/src/lib/onboarding/documents.ts#ACCEPTED_MIME_TYPES': 'Upload contract of the identity document endpoint: file types it accepts, checked before a 10 MB upload starts',
  'apps/organization-admin/src/lib/team/roles.ts#CAPABILITIES': 'Permission matrix with no organizer-readable backend source (identity permissions and rolePermissions are ADMIN-only): remaining work',
};
/** `relative/file.tsx` -> number of inline `options={[...]}` literals allowed, and why. */
const ALLOWED_INLINE: Record<string, { count: number; why: string }> = {
  'apps/admin/src/features/events/BuyerPreview.tsx': { count: 1, why: 'UI-only: desktop or phone preview toggle' },
  'apps/admin/src/features/events/MediaTabs.tsx': { count: 1, why: 'UI-only: grid or table view toggle' },
  'apps/admin/src/features/events/StockImagesTab.tsx': { count: 1, why: 'UI-only: grid or table view toggle' },
  'apps/admin/src/features/users/BuyerLookupDialog.tsx': { count: 1, why: 'UI-only: which field a support lookup searches by (email, phone, name)' },
  // ticketing
  'apps/ticketing/src/components/identify/IdentifyStep.tsx': { count: 1, why: 'UI-only: choose whether to be reached by WhatsApp or email, the two contact kinds the sign-in form takes' },
  // org-admin B
  'apps/organization-admin/src/components/events-editor/PreviewPane.tsx': { count: 1, why: 'UI-only: desktop or phone preview toggle' },
  'apps/organization-admin/src/components/finance/BankAccountDialogs.tsx': { count: 2, why: 'UI-only: bank account or mobile wallet switch between the two add-account forms' },
  'apps/organization-admin/src/components/media/MediaView.tsx': { count: 1, why: 'UI-only: grid or list view toggle' },
  'apps/organization-admin/src/components/notifications/NotificationsView.tsx': { count: 1, why: 'UI-only: all or unread filter over the loaded list' },
  'apps/organization-admin/src/components/team/MembersTab.tsx': { count: 1, why: 'UI-only: allow or deny toggle for a per-member permission override' },
};
/** `relative/file.ts` -> number of `z.enum([...])` literals allowed, and why. */
const ALLOWED_ZOD_ENUM: Record<string, { count: number; why: string }> = {
  // ticketing
  'apps/ticketing/src/components/home/filters.ts': { count: 2, why: 'UI-only: the form values of the date-window and sort controls (WHEN_OPTIONS, SORT_OPTIONS)' },
  'apps/ticketing/src/components/contacts/contactForms.ts': { count: 1, why: 'Form mode toggle between the two contact kinds (EMAIL, WHATSAPP) of the add/change dialogs' },
  'apps/ticketing/src/components/identify/identifyForm.ts': { count: 1, why: 'Form mode toggle between the two contact kinds (WHATSAPP, EMAIL) of the sign-in form' },
  'libs/shared/src/auth/bff/config.ts': { count: 1, why: 'Auth contract: which of the three apps a BFF instance serves' },
  // org-admin B
  'apps/organization-admin/src/components/team/schemas.ts': { count: 1, why: 'UI-only: unset, allow or deny toggle for a per-member permission override' },
};

function walk(dir: string, out: string[] = []): string[] {
  for (const name of readdirSync(dir)) {
    if (['node_modules', '.next', 'dist', 'coverage', 'e2e', '__tests__', '__mocks__', 'test'].includes(name)) continue;
    const full = path.join(dir, name);
    const st = statSync(full);
    if (st.isDirectory()) walk(full, out);
    else if (/\.(ts|tsx)$/.test(name) && !/\.(test|spec)\.(ts|tsx)$/.test(name) && !name.endsWith('.d.ts')) out.push(full);
  }
  return out;
}

function sourceFiles(): string[] {
  const files: string[] = [];
  for (const app of readdirSync(path.join(WEB, 'apps'))) {
    const src = path.join(WEB, 'apps', app, 'src');
    try {
      files.push(...walk(src));
    } catch {
      /* an app without src */
    }
  }
  files.push(...walk(path.join(WEB, 'libs/shared/src')));
  return files.filter((f) => !f.endsWith(path.join('types', 'graphql', 'index.ts')));
}

/** Source with comments blanked (offsets kept) so prose never matches. */
function stripComments(source: string): string {
  return source
    .replace(/\/\*[\s\S]*?\*\//g, (m) => m.replace(/[^\n]/g, ' '))
    .replace(/(^|[^:'"`\\])\/\/.*$/gm, (m, lead) => lead + ' '.repeat(m.length - lead.length));
}

/** Text from `start` (an opening bracket) to its match, string-aware. */
function balanced(source: string, start: number): string {
  const open = source[start];
  const close = ({ '[': ']', '{': '}', '(': ')' } as Record<string, string>)[open];
  let depth = 0;
  let quote: string | null = null;
  for (let i = start; i < source.length; i++) {
    const c = source[i];
    if (quote) {
      if (c === '\\') i++;
      else if (c === quote) quote = null;
      continue;
    }
    if (c === "'" || c === '"' || c === '`') quote = c;
    else if (c === open) depth++;
    else if (c === close && --depth === 0) return source.slice(start, i + 1);
  }
  return source.slice(start);
}

const literals = (text: string) => (text.match(/(['"])[^'"\n]*\1/g) ?? []).length;

function generatedTypeNames(): Set<string> {
  const generated = readFileSync(path.join(WEB, 'libs/shared/src/types/graphql/index.ts'), 'utf8');
  return new Set([...generated.matchAll(/^export type (\w+)\b/gm)].map((m) => m[1]));
}

interface Finding { file: string; key: string; detail: string }

function scan() {
  const generated = generatedTypeNames();
  const named: Finding[] = [];
  const inline: Array<{ file: string; count: number }> = [];
  const zodEnum: Array<{ file: string; count: number }> = [];

  for (const file of sourceFiles()) {
    const rel = path.relative(WEB, file).split(path.sep).join('/');
    const source = stripComments(readFileSync(file, 'utf8'));

    for (const m of source.matchAll(/\bconst\s+([A-Z][A-Z0-9_]*)\s*(:[^=;]*?)?=\s*/g)) {
      const name = m[1];
      if (!NAMED.test(name)) continue;
      const annotation = m[2] ?? '';
      const at = m.index + m[0].length;
      const rest = source.slice(at);
      const opener = rest.search(/[[{(]/);
      if (opener < 0 || opener > 60) continue;
      const body = balanced(source, at + opener);
      const head = rest.slice(0, opener);
      if (literals(body) < 2) continue;
      const typedFromSchema = [...annotation.matchAll(/\b[A-Z]\w*\b/g)].some((t) => generated.has(t[0]))
        || [...(head + body.slice(0, 80)).matchAll(/\b(?:satisfies|Record<|Set<|as readonly)\b[^\n]*/g)].some((t) => [...t[0].matchAll(/\b[A-Z]\w*\b/g)].some((x) => generated.has(x[0])))
        || [...body.matchAll(/\bsatisfies\s+[^\n]*/g)].some((t) => [...t[0].matchAll(/\b[A-Z]\w*\b/g)].some((x) => generated.has(x[0])));
      if (typedFromSchema) continue;
      named.push({ file: rel, key: `${rel}#${name}`, detail: `${literals(body)} string literals` });
    }

    let inlineCount = 0;
    for (const m of source.matchAll(/\boptions=\{\s*\[/g)) {
      const body = balanced(source, m.index + m[0].length - 1);
      if ((body.match(/\bvalue:/g) ?? []).length >= 2 && literals(body) >= 2) inlineCount++;
    }
    if (inlineCount) inline.push({ file: rel, count: inlineCount });

    let zodCount = 0;
    for (const m of source.matchAll(/\bz\.enum\(\s*\[/g)) {
      if (literals(balanced(source, m.index + m[0].length - 1)) >= 2) zodCount++;
    }
    if (zodCount) zodEnum.push({ file: rel, count: zodCount });
  }
  return { named, inline, zodEnum };
}

describe('no hand-typed reference lists in production code', () => {
  const found = scan();

  it('scans a real tree (a scan that finds no files would pass for ever)', () => {
    expect(sourceFiles().length).toBeGreaterThan(200);
    expect(generatedTypeNames().has('ReferenceType')).toBe(true);
  });

  it('has no named list of choices outside the allowlist', () => {
    const unexplained = found.named.filter((f) => !(f.key in ALLOWED_NAMED)).map((f) => `${f.key} (${f.detail})`);
    expect(unexplained, 'Read the list from the backend (useReferenceOptions) or type it from the generated enum').toEqual([]);
  });

  it('has no inline options={[{ value, label }, ...]} outside the allowlist', () => {
    const unexplained = found.inline
      .filter((f) => (ALLOWED_INLINE[f.file]?.count ?? 0) < f.count)
      .map((f) => `${f.file}: ${f.count} (allowed ${ALLOWED_INLINE[f.file]?.count ?? 0})`);
    expect(unexplained).toEqual([]);
  });

  it('has no z.enum of literals outside the allowlist', () => {
    const unexplained = found.zodEnum
      .filter((f) => (ALLOWED_ZOD_ENUM[f.file]?.count ?? 0) < f.count)
      .map((f) => `${f.file}: ${f.count} (allowed ${ALLOWED_ZOD_ENUM[f.file]?.count ?? 0})`);
    expect(unexplained).toEqual([]);
  });

  it('keeps no stale allowlist entry', () => {
    const named = new Set(found.named.map((f) => f.key));
    const inline = new Map(found.inline.map((f) => [f.file, f.count]));
    const zod = new Map(found.zodEnum.map((f) => [f.file, f.count]));
    const stale = [
      ...Object.keys(ALLOWED_NAMED).filter((k) => !named.has(k)).map((k) => `named ${k}`),
      ...Object.keys(ALLOWED_INLINE).filter((k) => inline.get(k) !== ALLOWED_INLINE[k].count).map((k) => `inline ${k}`),
      ...Object.keys(ALLOWED_ZOD_ENUM).filter((k) => zod.get(k) !== ALLOWED_ZOD_ENUM[k].count).map((k) => `z.enum ${k}`),
    ];
    expect(stale).toEqual([]);
  });

  it('explains every allowlist entry', () => {
    const blank = [
      ...Object.entries(ALLOWED_NAMED).filter(([, why]) => why.trim().length < 12).map(([k]) => k),
      ...Object.entries(ALLOWED_INLINE).filter(([, v]) => v.why.trim().length < 12).map(([k]) => k),
      ...Object.entries(ALLOWED_ZOD_ENUM).filter(([, v]) => v.why.trim().length < 12).map(([k]) => k),
    ];
    expect(blank).toEqual([]);
  });

  /** Printed on failure by the assertions above; also lets a developer see the whole worklist. */
  it.skipIf(!process.env.REFDATA_LIST)('lists every finding (set REFDATA_LIST=1)', () => {
    // eslint-disable-next-line no-console
    console.log(JSON.stringify(found, null, 1));
  });
});
