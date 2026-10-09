import { randomId, sha256Hex } from './crypto';
import type { SessionService } from './session';

export const FLOW_TTL_SEC = 600;
export const HINT_TTL_SEC = 36000;

export interface OAuthFlow {
  kind: 'login' | 'stepup';
  state: string;
  nonce: string;
  verifier: string;
  returnTo: string;
  startedAt: number;
  maxAge?: number;
  /** step-up: the session identity that must come back */
  expectSub?: string;
  expectAccountId?: string | null;
  /** handoff: the account the single-use handle was issued for */
  expectHandleAccountId?: string;
}

export interface FlowRecord {
  oauth?: OAuthFlow;
  /** Server-held proof from /verify; consumed by /ensure. */
  proof?: { value: string; maskedContact: string };
  /** Single-use login handle from /ensure; consumed by start. Never in a URL. */
  handle?: { value: string; accountId: string; isNew: boolean; expiresAt: number; issuedAt: number };
  /** The RP-logout hop already ran for this flow. */
  hopped?: boolean;
  /** Where to land after the hop (the hop returns without query parameters we control). */
  pendingReturnTo?: string;
  ext?: Record<string, unknown>;
}

export class FlowService {
  private prefix: string;
  constructor(private readonly sessions: SessionService) {
    this.prefix = sessions.prefix;
  }
  private key = (id: string) => `${this.prefix}flow:${sha256Hex(id)}`;

  newId() {
    return randomId(32);
  }

  async load(cookieValue: string | null | undefined): Promise<FlowRecord | null> {
    if (!cookieValue || cookieValue.length < 20 || cookieValue.length > 128) return null;
    const raw = await this.sessions.store.get(this.key(cookieValue));
    return this.sessions.openValue<FlowRecord>('flow', sha256Hex(cookieValue), raw);
  }
  async save(cookieValue: string, rec: FlowRecord): Promise<void> {
    await this.sessions.store.set(this.key(cookieValue), this.sessions.sealValue('flow', sha256Hex(cookieValue), rec), FLOW_TTL_SEC);
  }
  async delete(cookieValue: string | null | undefined): Promise<void> {
    if (cookieValue) await this.sessions.store.del(this.key(cookieValue));
  }

  // ---- per-device encrypted id_token hint (RP logout before handoff) ------------------------
  private hintKey = (device: string) => `${this.prefix}hint:${sha256Hex(device)}`;
  async saveHint(device: string, idToken: string): Promise<void> {
    await this.sessions.store.set(this.hintKey(device), this.sessions.sealValue('hint', sha256Hex(device), { idToken }), HINT_TTL_SEC);
  }
  async takeHint(device: string | null | undefined): Promise<string | null> {
    if (!device || device.length < 20) return null;
    const raw = await this.sessions.store.getDel(this.hintKey(device));
    return this.sessions.openValue<{ idToken: string }>('hint', sha256Hex(device), raw)?.idToken ?? null;
  }
  async deleteHint(device: string | null | undefined): Promise<void> {
    if (device) await this.sessions.store.del(this.hintKey(device));
  }

  // ---- tombstones for back-channel logout racing a login -----------------------------------
  async tombstone(sid: string): Promise<void> {
    await this.sessions.store.set(`${this.prefix}tomb:sid:${sid}`, '1', 600);
  }
  async isTombstoned(sid: string): Promise<boolean> {
    return (await this.sessions.store.get(`${this.prefix}tomb:sid:${sid}`)) !== null;
  }
}
