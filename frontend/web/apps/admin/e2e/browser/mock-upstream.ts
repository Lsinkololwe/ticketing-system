import fs from 'node:fs';
import path from 'node:path';
import { buildSchema, graphql, getNamedType, isEnumType, isListType, isNonNullType, isObjectType, isInterfaceType, isUnionType, isScalarType, type GraphQLResolveInfo, type GraphQLSchema, type GraphQLOutputType, type GraphQLField } from 'graphql';
import type { FakeUpstream, GqlHandler } from '../../../../e2e-harness/browser/playwright';

/**
 * Test-only schema-driven fixtures for the admin console. Every GraphQL operation the app can issue
 * is executed against the composed supergraph SDL (`scripts/codegen-local.sh` writes it) with a
 * deterministic generator that picks realistic values from the field and type names. A spec overrides
 * what it cares about with `upstream.gql({ Op: ... })` AFTER `mockAll`, or with `fields` here
 * (`'Type.field': value | (ctx) => value`). Lives in e2e only; app code ships no sample data.
 */
const WEB = path.resolve(__dirname, '../../../..');
const SDL = path.join(WEB, 'node_modules/.cache/pml-supergraph.graphql');

let schemaCache: GraphQLSchema | null = null;
const baseSchema = () => (schemaCache ??= buildSchema(fs.readFileSync(SDL, 'utf8'), { assumeValid: true, assumeValidSDL: true }));

/** Operation names declared anywhere in the shared admin API or the app. */
export function operationNames(): string[] {
  const names = new Set<string>();
  const walk = (dir: string) => {
    for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
      const p = path.join(dir, e.name);
      if (e.isDirectory()) {
        if (['node_modules', '.next', '__tests__', 'e2e'].includes(e.name)) continue;
        walk(p);
      } else if (/\.(ts|tsx)$/.test(e.name) && !/\.(test|spec)\./.test(e.name) && !/graphql\/index\.ts$/.test(p)) {
        for (const m of fs.readFileSync(p, 'utf8').matchAll(/\b(?:query|mutation)\s+([A-Z][A-Za-z0-9_]*)/g)) names.add(m[1]);
      }
    }
  };
  walk(path.join(WEB, 'libs/shared/src/api'));
  walk(path.join(WEB, 'apps/admin/src'));
  return [...names];
}

export const PEOPLE = ['Natasha Mulenga', 'Peter Zulu', 'Grace Phiri', 'Joseph Tembo', 'Mwiza Kapata', 'Esther Mumba', 'Lweendo Hamoonga', 'Chanda Banda'];
export const ORGS = ['Showstop Live', 'Copperbelt Concerts', 'Zambezi Events', 'Kafue Arts', 'Lusaka Comedy Co', 'Victoria Falls Promotions'];
export const EVENTS = ['Lusaka Jazz Night', 'Copperbelt Comedy Club', 'Falls Music Festival', 'Kafue Food Fair', 'Zambezi Gospel Fest', 'Chipata Cup Final'];
const CITIES = ['Lusaka', 'Ndola', 'Kitwe', 'Livingstone', 'Kabwe', 'Chipata'];
const PROVINCES = ['Lusaka', 'Copperbelt', 'Southern', 'Central', 'Eastern', 'Northern'];
const BANKS = ['Zanaco', 'Stanbic', 'FNB Zambia', 'Absa', 'Indo-Zambia Bank'];
const SENTENCES = ['Doors open early and the line moves quickly.', 'A relaxed evening with live acts and food stalls.', 'Bring a valid ID; tickets are checked at the gate.', 'Seats are limited, early arrival is advised.'];

const hash = (s: string) => {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) h = Math.imul(h ^ s.charCodeAt(i), 16777619);
  return (h >>> 0);
};

export interface FieldCtx { parentType: string; field: string; index: number; path: string; source: Record<string, unknown>; args: Record<string, unknown>; }
export type FieldFn = (c: FieldCtx) => unknown;
export interface MockOptions {
  /** 'Type.field' (or '*.field') -> value or function. Wins over the generator and over source properties. */
  fields?: Record<string, unknown | FieldFn>;
  /** Number of items in every list (default 5); 0 makes every list empty. */
  listSize?: number;
  /** Per-field list size, 'Type.field' or '*.field'. */
  listSizes?: Record<string, number>;
}

const pathKey = (info: GraphQLResolveInfo) => {
  const parts: Array<string | number> = [];
  for (let p: GraphQLResolveInfo['path'] | undefined = info.path; p; p = p.prev) parts.unshift(p.key);
  return parts;
};
const lastIndex = (parts: Array<string | number>) => {
  for (let i = parts.length - 1; i >= 0; i--) if (typeof parts[i] === 'number') return parts[i] as number;
  return 0;
};

function stringFor(type: string, field: string, i: number, h: number): string {
  const f = field.toLowerCase();
  const t = type.toLowerCase();
  const pick = <T,>(a: T[]) => a[(i + h) % a.length];
  if (/^period$|^month$|^day$/.test(f)) return `2026-${String(1 + (i % 12)).padStart(2, '0')}-${String(1 + ((i * 3) % 27)).padStart(2, '0')}`;
  if (/email/.test(f)) return `${pick(PEOPLE).toLowerCase().replace(/ /g, '.')}@example.zm`;
  if (/^(first)?name$|displayname|fullname|username$/.test(f) || f.endsWith('name')) {
    if (f === 'firstname') return pick(PEOPLE).split(' ')[0];
    if (f === 'lastname') return pick(PEOPLE).split(' ')[1];
    if (/bank/.test(f)) return pick(BANKS);
    if (/org|organizer|business|company/.test(f) || /organi[sz]/.test(t)) return pick(ORGS);
    if (/event/.test(f) || /event/.test(t)) return pick(EVENTS);
    if (/city/.test(f) || /city/.test(t)) return pick(CITIES);
    if (/province/.test(f) || /province/.test(t)) return pick(PROVINCES);
    if (/venue|location/.test(f) || /venue|location/.test(t)) return `${pick(CITIES)} Showgrounds`;
    if (/categor/.test(t)) return ['Music', 'Comedy', 'Sport', 'Theatre', 'Festivals'][(i + h) % 5];
    if (/user|account|staff|member|buyer|customer|admin|actor|reviewer|holder|owner|attendee|payer/.test(`${t} ${f}`)) return pick(PEOPLE);
    return pick(PEOPLE);
  }
  if (/^title$|eventtitle/.test(f)) return pick(EVENTS);
  if (/description|summary|notes?$|comment|reason|message|body|detail|text$/.test(f)) return pick(SENTENCES);
  if (/city/.test(f)) return pick(CITIES);
  if (/province/.test(f)) return pick(PROVINCES);
  if (/phone|msisdn/.test(f)) return `+26097${String(1000000 + ((i + h) % 8999999))}`;
  if (/currency/.test(f)) return 'ZMW';
  if (/bank/.test(f)) return pick(BANKS);
  if (/account(number|no)/.test(f)) return `0100${String(1000000 + ((i + h) % 8999999))}`;
  if (/reference|^code$|number$/.test(f)) return `${t.replace(/[^a-z]/g, '').slice(0, 3).toUpperCase() || 'REF'}-${String(1000 + ((i * 7 + h) % 8999))}`;
  if (/url|image|logo|avatar|banner|poster/.test(f)) return 'data:image/gif;base64,R0lGODlhAQABAIAAAAAAAP///yH5BAEAAAAALAAAAAABAAEAAAIBRAA7';
  if (/id$/.test(f)) return `${t.replace(/[^a-z]/g, '').slice(0, 8)}-${(i + 1) * 31 + h % 1000}`;
  if (/slug/.test(f)) return `item-${i + 1}`;
  return `${field} ${i + 1}`;
}

function scalarFor(name: string, type: string, field: string, i: number, h: number, path = ''): unknown {
  const f = field.toLowerCase();
  switch (name) {
    case 'ID': return `${type.toLowerCase().replace(/[^a-z]/g, '').slice(0, 8)}-${hash(path) % 100000}`;
    case 'String': return stringFor(type, field, i, h);
    case 'Boolean': return ((i + h) % 3) !== 0 && !/(suspended|deleted|locked|disputed|flagged|failed|overdue|escalated)/.test(f);
    case 'Int': return /page$|offset/.test(f) ? 0 : /size|limit/.test(f) ? 20 : /total/.test(f) ? 6 : 1 + ((i * 3 + h) % 24);
    case 'Float': return /percent|rate|ratio/.test(f) ? 5 + ((i + h) % 40) / 4 : 100 + ((i * 37 + h) % 9000);
    case 'Long': return 1000 + ((i * 11 + h) % 9000);
    case 'BigDecimal': return /rate|percent|commission|pct/.test(f) ? '5' : String(250 + ((i * 417 + h) % 24000));
    case 'DateTime': {
      if (/bucket/.test(f)) return new Date(Date.UTC(2026, (i % 12), 1)).toISOString();
      const day = 86400000;
      const base = Date.UTC(2026, 9, 5, 9, 0, 0);
      const future = /start|end|event|expir|due|scheduled|until|deadline/.test(f);
      return new Date(base + (future ? 1 : -1) * (1 + ((i * 2 + h) % 20)) * day - ((i * 3600000) % day)).toISOString();
    }
    case 'JSON': return {};
    case 'PhoneNumber': return `+26097${String(1000000 + ((i + h) % 8999999))}`;
    default: return `${name} ${i + 1}`;
  }
}

export function mockedResolver(o: MockOptions = {}) {
  const listSize = o.listSize ?? 5;
  return (source: unknown, args: Record<string, unknown>, ctx0: unknown, info: GraphQLResolveInfo) => {
    const salt = typeof ctx0 === 'string' ? ctx0 : '';
    const parent = info.parentType.name;
    const fieldName = info.fieldName;
    const parts = pathKey(info);
    const index = lastIndex(parts);
    const key = `${salt}|${parts.join('.')}`;
    const src = (source ?? {}) as Record<string, unknown>;
    const fns = o.fields ?? {};
    const hit = fns[`${parent}.${fieldName}`] ?? fns[`*.${fieldName}`];
    const ctx: FieldCtx = { parentType: parent, field: fieldName, index, path: key, source: src, args };
    if (hit !== undefined) return typeof hit === 'function' ? (hit as FieldFn)(ctx) : hit;
    if (fieldName in src && src[fieldName] !== undefined && typeof src[fieldName] !== 'function') return src[fieldName];
    const h = hash(`${parent}.${fieldName}`);
    const gen = (t: GraphQLOutputType, i = index): unknown => {
      if (isNonNullType(t)) return gen(t.ofType, i);
      if (isListType(t)) {
        const n = o.listSizes?.[`${parent}.${fieldName}`] ?? o.listSizes?.[`*.${fieldName}`] ?? listSize;
        const inner = getNamedType(t.ofType);
        // A list of enum values holds each value at most once, as the real API does.
        const len = isEnumType(inner) ? Math.min(n, 2, inner.getValues().length) : n;
        return Array.from({ length: len }, (_, k) => gen(t.ofType, k));
      }
      const named = getNamedType(t);
      if (isEnumType(named)) {
        const vs = named.getValues();
        return vs[(i + h) % vs.length].value;
      }
      if (isScalarType(named)) return scalarFor(named.name, parent, fieldName, i, h, key);
      if (isUnionType(named)) return { __typename: info.schema.getPossibleTypes(named)[0].name };
      if (isInterfaceType(named)) return { __typename: info.schema.getPossibleTypes(named)[0].name };
      if (isObjectType(named)) return {};
      return null;
    };
    return gen(info.returnType);
  };
}

/** Executes one request against the generator. List items get distinct indices via the response path. */
export async function runMock(schema: GraphQLSchema, query: string, vars: Record<string, unknown>, name: string, o: MockOptions) {
  const r = await graphql({ schema, source: query, variableValues: vars, operationName: name, contextValue: JSON.stringify(vars), fieldResolver: mockedResolver(o) as never });
  if (r.errors?.length) throw new Error(`mock ${name}: ${r.errors.map((e) => e.message).join('; ')}`);
  return (r.data ?? {}) as Record<string, unknown>;
}

/**
 * Registers a fixture for every known operation. Call first in a spec; later `upstream.gql({...})`
 * calls replace single operations (spreading `await runMock` results is also possible).
 */
export function mockAll(upstream: FakeUpstream, o: MockOptions & { only?: string[] } = {}) {
  const schema = baseSchema();
  const handlers: Record<string, GqlHandler> = {};
  for (const name of o.only ?? operationNames()) handlers[name] = (vars, req) => runMock(schema, req.query, vars, name, o);
  upstream.gql(handlers);
}
export type { GraphQLField };
