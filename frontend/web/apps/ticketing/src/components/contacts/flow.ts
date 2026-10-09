import { describeContactError } from '@/lib/contacts/messages';
import type { CodeChallenge } from '@/lib/contacts/types';

/**
 * State machine for one contact operation (add / change / remove) in the dialog.
 * Pure: the component performs the requests and dispatches their outcome here.
 *
 *   input -> sending -> code -> verifying -> done
 *                         ^         |-> code    (wrong code, attempts left)
 *                         |         |-> pending (202: server still working)
 *                         |         |-> input   (expired / lost claim / not allowed to continue)
 *                         |         |-> blocked (cannot be done at all)
 */
export type FlowKind = 'add' | 'change' | 'remove' | 'primary';
export type Phase = 'input' | 'sending' | 'code' | 'verifying' | 'pending' | 'done' | 'blocked';

export interface FlowError {
  code: string;
  attemptsRemaining?: number;
  retryAfterSeconds?: number;
  lockedUntil?: string;
}

export interface FlowState {
  kind: FlowKind;
  phase: Phase;
  challenge: CodeChallenge | null;
  primary: CodeChallenge | null;
  changeId: string | null;
  /** Bumped whenever a (new) code is sent, to restart countdowns. */
  stamp: number;
  attemptsLeft: number | null;
  error: FlowError | null;
  /** Seconds until the server lets the buyer try again, when locked or rate limited. */
  lockedSeconds: number;
  /** Seconds the pending poll should wait before asking again. */
  pendingRetry: number;
}

export type FlowAction =
  | { type: 'send' }
  | { type: 'challenge'; challenge: CodeChallenge; primary?: CodeChallenge | null; changeId?: string | null }
  | { type: 'resent'; target: 'new' | 'current'; challenge: CodeChallenge }
  | { type: 'verify' }
  | { type: 'failure'; error: FlowError }
  | { type: 'pending'; retryAfterSeconds: number }
  | { type: 'done' }
  | { type: 'restart' }
  | { type: 'blocked'; code: string };

export function initialFlow(kind: FlowKind, blockedCode?: string): FlowState {
  return {
    kind,
    phase: blockedCode ? 'blocked' : 'input',
    challenge: null,
    primary: null,
    changeId: null,
    stamp: 0,
    attemptsLeft: null,
    error: blockedCode ? { code: blockedCode } : null,
    lockedSeconds: 0,
    pendingRetry: 2,
  };
}

function lockSeconds(e: FlowError, now: number): number {
  if (e.lockedUntil) {
    const t = Date.parse(e.lockedUntil);
    if (!Number.isNaN(t)) return Math.max(0, Math.ceil((t - now) / 1000));
  }
  return Math.max(0, e.retryAfterSeconds ?? 0);
}

export function flowReducer(s: FlowState, a: FlowAction, now: number = Date.now()): FlowState {
  switch (a.type) {
    case 'send':
      return { ...s, phase: 'sending', error: null };
    case 'challenge':
      return {
        ...s,
        phase: 'code',
        challenge: a.challenge,
        primary: a.primary ?? null,
        changeId: a.changeId ?? s.changeId,
        stamp: s.stamp + 1,
        attemptsLeft: null,
        error: null,
        lockedSeconds: 0,
      };
    case 'resent':
      // A fresh code replaced one of the live ones; the other code and the phase are untouched.
      return a.target === 'new'
        ? { ...s, phase: 'code', challenge: a.challenge, stamp: s.stamp + 1, error: null }
        : { ...s, phase: 'code', primary: a.challenge, error: null };
    case 'verify':
      return { ...s, phase: 'verifying', error: null };
    case 'pending':
      return { ...s, phase: 'pending', error: null, pendingRetry: Math.max(1, a.retryAfterSeconds) };
    case 'done':
      return { ...s, phase: 'done', error: null };
    case 'restart':
      return { ...initialFlow(s.kind), stamp: s.stamp };
    case 'blocked':
      return { ...s, phase: 'blocked', error: { code: a.code } };
    case 'failure': {
      const info = describeContactError(a.error.code);
      const hadChallenge = s.challenge !== null;
      const base = { ...s, error: a.error };
      if (a.error.code === 'OTP_INVALID') {
        return { ...base, phase: 'code', attemptsLeft: a.error.attemptsRemaining ?? null };
      }
      if (info.kind === 'blocked' || info.kind === 'session') {
        return { ...base, phase: 'blocked' };
      }
      if (info.kind === 'locked') {
        return { ...base, phase: hadChallenge ? 'code' : 'input', lockedSeconds: lockSeconds(a.error, now) };
      }
      if (info.kind === 'restart' || (info.kind === 'field' && s.phase === 'verifying')) {
        // The challenge is spent or the claim was lost: nothing to type a code into any more.
        return { ...base, phase: 'input', challenge: null, primary: null, changeId: null, attemptsLeft: null };
      }
      // 'field' on send, 'retry': stay where the buyer can act again.
      return { ...base, phase: hadChallenge && s.phase !== 'sending' ? 'code' : 'input' };
    }
  }
}
