import { createHmac, randomBytes } from 'node:crypto';
import type { KeyValueStore } from './store';
import type { Logger } from './logger';
import { MemoryStore } from './store/memory';

export type RateSubject = 'ip' | 'flow' | 'session' | 'contact' | 'device' | 'account';

export interface RateRule {
  subject: RateSubject;
  limit: number;
  windowSec: number;
  /** Block the subject for this long once the limit is hit/exceeded. */
  penaltySec?: number;
  /** Set the penalty when the limit is REACHED (failure counters) instead of when exceeded. */
  blockAtLimit?: boolean;
}

export type PolicyName =
  | 'start'
  | 'callback'
  | 'callback_fail'
  | 'challenge'
  | 'verify'
  | 'ensure'
  | 'contacts'
  | 'contacts_challenge'
  | 'logout'
  | 'backchannel'
  | 'stepup'
  | 'api'
  | 'api_anon';

interface Policy {
  rules: RateRule[];
  /** Redis unavailable: auth routes refuse (503) after a tiny in-memory allowance, the API proxy keeps serving. */
  failMode: 'closed' | 'open';
}

/** Section 10 of the design. `start` is tightened for the platform admin app in `defaultPolicies`. */
export function defaultPolicies(app: string): Record<PolicyName, Policy> {
  return {
    start: {
      rules:
        app === 'admin'
          ? [{ subject: 'ip', limit: 10, windowSec: 60 }]
          : [
              { subject: 'ip', limit: 20, windowSec: 60 },
              { subject: 'flow', limit: 5, windowSec: 600 },
            ],
      failMode: 'closed',
    },
    callback: { rules: [{ subject: 'ip', limit: 30, windowSec: 60 }], failMode: 'closed' },
    callback_fail: { rules: [{ subject: 'ip', limit: 5, windowSec: 600, penaltySec: 900, blockAtLimit: true }], failMode: 'closed' },
    challenge: {
      rules: [
        { subject: 'ip', limit: 10, windowSec: 60 },
        { subject: 'ip', limit: 30, windowSec: 3600 },
        { subject: 'contact', limit: 5, windowSec: 600, penaltySec: 1800 },
        { subject: 'contact', limit: 10, windowSec: 86400 },
        { subject: 'device', limit: 5, windowSec: 600 },
      ],
      failMode: 'closed',
    },
    verify: {
      rules: [
        { subject: 'ip', limit: 15, windowSec: 60 },
        { subject: 'flow', limit: 8, windowSec: 300 },
      ],
      failMode: 'closed',
    },
    ensure: {
      rules: [
        { subject: 'ip', limit: 15, windowSec: 60 },
        { subject: 'flow', limit: 3, windowSec: 600 },
      ],
      failMode: 'closed',
    },
    contacts: {
      rules: [
        { subject: 'account', limit: 10, windowSec: 60 },
        { subject: 'ip', limit: 10, windowSec: 60 },
      ],
      failMode: 'closed',
    },
    contacts_challenge: {
      rules: [
        { subject: 'account', limit: 5, windowSec: 600 },
        { subject: 'ip', limit: 20, windowSec: 60 },
      ],
      failMode: 'closed',
    },
    logout: {
      rules: [
        { subject: 'session', limit: 10, windowSec: 60 },
        { subject: 'ip', limit: 30, windowSec: 60 },
      ],
      failMode: 'closed',
    },
    backchannel: { rules: [{ subject: 'ip', limit: 60, windowSec: 60 }], failMode: 'closed' },
    stepup: { rules: [{ subject: 'session', limit: 5, windowSec: 600 }], failMode: 'closed' },
    api: { rules: [{ subject: 'session', limit: 600, windowSec: 60 }], failMode: 'open' },
    api_anon: { rules: [{ subject: 'ip', limit: 120, windowSec: 60 }], failMode: 'open' },
  };
}

export type Subjects = Partial<Record<RateSubject, string | null | undefined>>;

export interface RateDecision {
  allowed: boolean;
  /** True when Redis is down and the policy is fail-closed: respond 503, not 429. */
  unavailable?: boolean;
  retryAfterSec: number;
  rule?: string;
}

/** Trusted-proxy client IP: the XFF entry `hops` from the RIGHT. 0 hops: XFF is not trusted. */
export function resolveClientIp(headers: Headers, hops: number): string {
  if (hops <= 0) return 'unknown';
  const xff = headers.get('x-forwarded-for');
  if (!xff) return 'unknown';
  const parts = xff.split(',').map((s) => s.trim()).filter(Boolean);
  const idx = parts.length - hops;
  if (idx < 0) return 'unknown';
  const ip = parts[idx];
  return /^[0-9a-fA-F:.]{2,45}$/.test(ip) ? ip : 'unknown';
}

export class RateLimiter {
  private readonly policies: Record<string, Policy>;
  private readonly memory: MemoryStore;
  private readonly secret: Buffer;

  constructor(
    private readonly store: KeyValueStore,
    private readonly opts: {
      app: string;
      prefix: string;
      secret: Buffer;
      clock: () => number;
      overrides?: Record<string, RateRule[]>;
      logger: Logger;
    }
  ) {
    const base = defaultPolicies(opts.app) as Record<string, Policy>;
    for (const [name, rules] of Object.entries(opts.overrides ?? {})) {
      base[name] = { rules, failMode: base[name]?.failMode ?? 'closed' };
    }
    this.policies = base;
    this.memory = new MemoryStore(opts.clock);
    this.secret = opts.secret;
  }

  private subjectKey(subject: string): string {
    return createHmac('sha256', this.secret).update(subject).digest('hex').slice(0, 32);
  }

  /** Counts one hit against every rule whose subject is present. Denies on the first exceeded rule. */
  async consume(policyName: string, subjects: Subjects): Promise<RateDecision> {
    const policy = this.policies[policyName];
    if (!policy) throw new Error(`unknown rate-limit policy ${policyName}`);
    const now = this.opts.clock();
    try {
      for (const rule of policy.rules) {
        const subject = subjects[rule.subject];
        if (!subject) continue;
        const sk = this.subjectKey(subject);
        const blockKey = `${this.opts.prefix}rlblock:${policyName}:${rule.subject}:${sk}`;
        const blocked = await this.store.get(blockKey);
        if (blocked) return { allowed: false, retryAfterSec: Math.max(1, Number(blocked)), rule: rule.subject };
        const key = `${this.opts.prefix}rl:${policyName}:${rule.subject}:${rule.limit}:${rule.windowSec}:${sk}`;
        const r = await this.store.slidingWindow(key, now, rule.windowSec * 1000, rule.limit, `${now}:${randomBytes(6).toString('hex')}`);
        if (!r.allowed) {
          if (rule.penaltySec) await this.store.set(blockKey, String(rule.penaltySec), rule.penaltySec);
          return { allowed: false, retryAfterSec: Math.max(1, Math.ceil(r.retryAfterMs / 1000)), rule: rule.subject };
        }
        if (rule.blockAtLimit && rule.penaltySec && r.count >= rule.limit) {
          await this.store.set(blockKey, String(rule.penaltySec), rule.penaltySec);
        }
      }
      return { allowed: true, retryAfterSec: 0 };
    } catch (err) {
      this.opts.logger.warn('ratelimit.store_unavailable', { policy: policyName, err });
      return this.fallback(policyName, policy, subjects, now);
    }
  }

  /** True when a penalty block is active for any present subject (does not count a hit). */
  async isBlocked(policyName: string, subjects: Subjects): Promise<number> {
    const policy = this.policies[policyName];
    if (!policy) return 0;
    try {
      for (const rule of policy.rules) {
        const subject = subjects[rule.subject];
        if (!subject || !rule.penaltySec) continue;
        const v = await this.store.get(`${this.opts.prefix}rlblock:${policyName}:${rule.subject}:${this.subjectKey(subject)}`);
        if (v) return Math.max(1, Number(v));
      }
    } catch {
      return 0;
    }
    return 0;
  }

  private async fallback(name: string, policy: Policy, subjects: Subjects, now: number): Promise<RateDecision> {
    const closed = policy.failMode === 'closed';
    for (const rule of policy.rules) {
      const subject = subjects[rule.subject];
      if (!subject) continue;
      const limit = closed ? Math.min(5, rule.limit) : rule.limit;
      const windowMs = closed ? 60_000 : rule.windowSec * 1000;
      const r = await this.memory.slidingWindow(`${name}:${rule.subject}:${rule.limit}:${this.subjectKey(subject)}`, now, windowMs, limit, `${now}:${randomBytes(4).toString('hex')}`);
      if (!r.allowed) {
        return { allowed: false, unavailable: closed, retryAfterSec: Math.max(1, Math.ceil(r.retryAfterMs / 1000)), rule: rule.subject };
      }
    }
    return { allowed: true, retryAfterSec: 0 };
  }
}
