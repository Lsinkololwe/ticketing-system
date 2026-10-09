import type { BffDeps } from './context';
import { serializeCookie } from './cookies';
import { FLOW_TTL_SEC, type FlowRecord } from './flow';
import { parseCookies } from './cookies';

/**
 * Buyer handoff plumbing. The identify routes (challenge/verify/ensure) stay in the buyer app
 * (they talk to identity-service); they use this to keep their server-held state in the
 * encrypted flow record. The handle never leaves the server: `start` reads it from the flow.
 */
export function createFlowApi(deps: BffDeps) {
  async function read(req: Request): Promise<{ id: string | null; rec: FlowRecord }> {
    const id = parseCookies(req.headers.get('cookie')).get(deps.names.flow) ?? null;
    if (!id) return { id: null, rec: {} };
    return { id, rec: (await deps.flows.load(id)) ?? {} };
  }

  /** Merge into the flow, creating the cookie when absent. Returns Set-Cookie strings to attach. */
  async function update(req: Request, fn: (rec: FlowRecord) => FlowRecord): Promise<{ setCookie: string[]; rec: FlowRecord }> {
    const cur = await read(req);
    const id = cur.id && cur.id.length >= 20 ? cur.id : deps.flows.newId();
    const rec = fn(cur.rec);
    await deps.flows.save(id, rec);
    const setCookie = cur.id === id ? [] : [serializeCookie(deps.names.flow, id, { maxAge: FLOW_TTL_SEC, sameSite: 'lax', secure: deps.cfg.secure })];
    return { setCookie, rec };
  }

  /** Called by `ensure` after identity-service issued a handle for `accountId`. */
  function attachHandle(req: Request, h: { value: string; accountId: string; isNew: boolean; ttlSec: number }) {
    const now = deps.cfg.clock();
    return update(req, (rec) => ({
      ...rec,
      handle: { value: h.value, accountId: h.accountId, isNew: h.isNew, issuedAt: now, expiresAt: now + h.ttlSec * 1000 },
    }));
  }

  return { read, update, attachHandle };
}
