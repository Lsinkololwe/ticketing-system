import fs from 'node:fs';
import path from 'node:path';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import {
  fieldErrors,
  resolveError,
  resetUnrecognisedReporting,
  sessionAction,
  type ErrorClassification,
} from './errors';

/**
 * Every registry code renders, and the retry button is decided by data.
 *
 * <h2>The registry is parsed, not retyped</h2>
 * A copy of the registry table in this file would be a third copy of it — after
 * the markdown table and the Java enum — and it would be the one nobody updates. The count is
 * asserted **exactly** rather than loosely: a regex that silently stops matching
 * half the table still passes a `> 50` check, and then "every code renders"
 * means "every code the regex happened to find renders".
 */

const SPEC = path.resolve(
  __dirname,
  '..', '..', '..', '..', '..', '..',
  'specs', '_platform', '005-error-contract', 'spec.md'
);

interface RegistryRow {
  code: string;
  classification: ErrorClassification;
  retryable: boolean;
}

function registryFromSpec(): RegistryRow[] {
  const markdown = fs.readFileSync(SPEC, 'utf8');
  const rows: RegistryRow[] = [];

  const pattern =
    /^\|\s*`([A-Z][A-Z_]+)`\s*\|[^|]*\|\s*`?(BAD_REQUEST|UNAUTHENTICATED|PERMISSION_DENIED|NOT_FOUND|FAILED_PRECONDITION|UNAVAILABLE|INTERNAL|UNKNOWN)`?\s*\|\s*\*{0,2}(yes|no)\*{0,2}\s*\|/gm;

  let match: RegExpExecArray | null;
  while ((match = pattern.exec(markdown)) !== null) {
    rows.push({
      code: match[1],
      classification: match[2] as ErrorClassification,
      retryable: match[3] === 'yes',
    });
  }
  return rows;
}

/**
 * The number of constants in the backend enum, read from the source rather than
 * restated here. A constant in this file went stale the first time a code was
 * added, and failing then said nothing about the frontend.
 */
const ERROR_CODE_JAVA = path.resolve(
  __dirname,
  '..', '..', '..', '..', '..', '..',
  'backend', 'shared-library', 'src', 'main', 'java', 'com', 'pml', 'shared', 'error', 'ErrorCode.java'
);
const REGISTRY_SIZE = (fs.readFileSync(ERROR_CODE_JAVA, 'utf8').match(/^\s*[A-Z][A-Z_]+\(/gm) ?? []).length;

describe('error contract · FE-1 every code renders a state', () => {
  const registry = registryFromSpec();

  beforeEach(() => resetUnrecognisedReporting());

  it('parses exactly the registry the backend enum holds', () => {
    expect(REGISTRY_SIZE, 'ErrorCode.java was not read — the path moved').toBeGreaterThan(50);
    expect(
      registry.length,
      `expected ${REGISTRY_SIZE} rows from §4. A different number means either the ` +
        'registry changed (update ErrorCode.java and this constant together) or the ' +
        'table format changed and this regex is now matching a subset.'
    ).toBe(REGISTRY_SIZE);
  });

  it('every registry code resolves to a non-empty message', () => {
    const unrendered = registry.filter((row) => {
      const resolved = resolveError({
        extensions: {
          errorCode: row.code,
          classification: row.classification,
          retryable: row.retryable,
        },
      });
      return !resolved.message || resolved.message.trim().length === 0;
    });

    expect(
      unrendered.map((row) => row.code),
      'these codes render nothing, which is a blank panel where an explanation belongs'
    ).toEqual([]);
  });

  it('never renders the raw code to a user', () => {
    // The failure this prevents is subtle: showing `TIER_SOLD_OUT` looks like a
    // deliberate label, so it survives review and reaches a customer.
    const leaking = registry.filter((row) =>
      resolveError({
        extensions: { errorCode: row.code, classification: row.classification },
      }).message.includes(row.code)
    );

    expect(leaking.map((row) => row.code)).toEqual([]);
  });

  it('an unrecognised code still renders, and logs', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    const resolved = resolveError({
      extensions: { errorCode: 'CODE_FROM_A_NEWER_SERVER' },
    });

    expect(resolved.message).not.toBe('');
    expect(resolved.message).not.toContain('CODE_FROM_A_NEWER_SERVER');
    expect(
      warn,
      'without a log the screen looks fine and nobody learns the client is behind'
    ).toHaveBeenCalledTimes(1);

    warn.mockRestore();
  });

  it('repeats of the same unknown code are logged once', () => {
    const warn = vi.spyOn(console, 'warn').mockImplementation(() => undefined);

    // A failing list view fires the same refusal per row. Two hundred identical
    // console lines get muted, which is the same as not logging at all.
    for (let i = 0; i < 5; i += 1) {
      resolveError({ extensions: { errorCode: 'SAME_UNKNOWN_CODE' } });
    }

    expect(warn).toHaveBeenCalledTimes(1);
    warn.mockRestore();
  });
});

describe('error contract · FE-2 retryable decides the retry button', () => {
  beforeEach(() => resetUnrecognisedReporting());

  it('a retryable refusal offers retry', () => {
    expect(
      resolveError({
        extensions: {
          errorCode: 'RESOURCE_CONFLICT',
          classification: 'FAILED_PRECONDITION',
          retryable: true,
        },
      }).action
    ).toBe('retry');
  });

  it('a reused idempotency key never offers retry', () => {
    // The one that matters: retrying is the double charge the key prevents.
    expect(
      resolveError({
        extensions: {
          errorCode: 'IDEMPOTENCY_KEY_REUSED',
          classification: 'FAILED_PRECONDITION',
          retryable: false,
        },
      }).action
    ).not.toBe('retry');
  });

  it('a permission refusal never offers retry', () => {
    expect(
      resolveError({
        extensions: {
          errorCode: 'ACTOR_NOT_PERMITTED',
          classification: 'PERMISSION_DENIED',
          retryable: false,
        },
      }).action
    ).toBe('none');
  });

  it('every registry row agrees with the spec on retryability', () => {
    const disagreeing = registryFromSpec().filter((row) => {
      const resolved = resolveError({
        extensions: {
          errorCode: row.code,
          classification: row.classification,
          retryable: row.retryable,
        },
      });
      return resolved.retryable !== row.retryable;
    });

    expect(
      disagreeing.map((row) => row.code),
      'the client must not second-guess the registry about what can be retried'
    ).toEqual([]);
  });

  it('a revoked token ends the session rather than retrying', () => {
    const resolved = resolveError({
      extensions: {
        errorCode: 'TOKEN_REVOKED',
        classification: 'UNAUTHENTICATED',
        retryable: false,
      },
    });

    expect(resolved.action).toBe('sign-in');
  });
});

describe('error contract · FE-4 field errors land on fields', () => {
  beforeEach(() => resetUnrecognisedReporting());

  it('maps each path to copy derived from the constraint name', () => {
    const errors = fieldErrors({
      extensions: {
        errorCode: 'COMMAND_NOT_WELL_FORMED',
        classification: 'BAD_REQUEST',
        fields: [
          { path: 'input.title', constraint: 'NotBlank' },
          { path: 'input.email', constraint: 'Email' },
        ],
      },
    });

    expect(errors).toEqual({
      'input.title': 'Required',
      'input.email': 'Enter a valid email address',
    });
  });

  it('keeps the index so a bulk form can mark the right row', () => {
    const errors = fieldErrors({
      extensions: {
        fields: [{ path: 'invitations[3].email', constraint: 'Email' }],
      },
    });

    expect(Object.keys(errors)).toEqual(['invitations[3].email']);
  });

  it('shows one message per field even when two constraints fail', () => {
    const errors = fieldErrors({
      extensions: {
        fields: [
          { path: 'input.name', constraint: 'NotBlank' },
          { path: 'input.name', constraint: 'Size' },
        ],
      },
    });

    // "Wrong length" is a consequence of being empty and disappears when it is
    // fixed. Stacking both into one line under an input reads as two problems.
    expect(errors).toEqual({ 'input.name': 'Required' });
  });

  it('an unknown constraint still renders something', () => {
    expect(
      fieldErrors({ extensions: { fields: [{ path: 'x', constraint: 'CustomRule' }] } })
    ).toEqual({ x: 'Not valid' });
  });

  it('returns nothing for a non-validation error, so forms can call it always', () => {
    expect(fieldErrors({ extensions: { errorCode: 'TIER_SOLD_OUT' } })).toEqual({});
    expect(fieldErrors(null)).toEqual({});
    expect(fieldErrors(undefined)).toEqual({});
  });

  it('survives a malformed fields payload', () => {
    expect(
      fieldErrors({ extensions: { fields: 'not-an-array' as never } })
    ).toEqual({});
  });
});

describe('error contract · FE-3 a revoked token ends the session', () => {
  it('reads the contract key, not graphql-java’s generic one', () => {
    // The failure this pins is silent: `extensions.code` is a real key that this
    // platform never populates, so a reader of it gets `undefined` for every
    // refusal. The transport layer then never ends a session, and nothing looks
    // broken until a revoked token is quietly accepted.
    expect(sessionAction({ extensions: { errorCode: 'TOKEN_REVOKED' } })).toBe('end-session');
    expect(
      sessionAction({ extensions: { code: 'TOKEN_REVOKED' } as never }),
      'only extensions.errorCode is the contract'
    ).toBe('none');
  });

  it('separates a revoked token from simply not being signed in', () => {
    // Both send the user to sign in, and only one must also drop local state:
    // re-presenting a revoked token fails forever, so leaving it in storage
    // leaves the user apparently signed in until something asks the server.
    expect(sessionAction({ extensions: { errorCode: 'TOKEN_REVOKED' } })).toBe('end-session');
    expect(sessionAction({ extensions: { errorCode: 'ACTOR_NOT_AUTHENTICATED' } })).toBe(
      'reauthenticate'
    );
  });

  it('leaves every other refusal to the screen', () => {
    for (const errorCode of ['TIER_SOLD_OUT', 'ACTOR_NOT_PERMITTED', 'INTERNAL_ERROR']) {
      expect(sessionAction({ extensions: { errorCode } })).toBe('none');
    }
    expect(sessionAction(null)).toBe('none');
    expect(sessionAction({})).toBe('none');
  });

  it('never signs the user out over a permission refusal', () => {
    // ACTOR_NOT_PERMITTED means the session is valid and the actor lacks a
    // right. Ending it here would log people out of pages they merely cannot see.
    expect(sessionAction({ extensions: { errorCode: 'ACTOR_NOT_PERMITTED' } })).toBe('none');
  });
});
