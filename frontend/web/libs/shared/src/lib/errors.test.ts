import fs from 'node:fs';
import path from 'node:path';

import { describe, expect, it } from 'vitest';

import { endsSession, resolveError, type ErrorClassification } from './errors';

/**
 * Every code in the error registry maps to a rendered state.
 *
 * <h2>The registry is parsed, not retyped</h2>
 * The registry table holds 108 codes. A copy of that list in this file would be
 * a third description of the contract, free to disagree with both the table and
 * the backend — and the failure it hides is a code that renders nothing at all,
 * which reaches a user as a blank panel where an explanation should be.
 *
 * <p>So the test reads the table. A code added to it is covered here the moment
 * it lands, without anyone remembering to update a fixture.</p>
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

/** Registry rows: `| \`CODE\` | Exception | CLASSIFICATION | yes/no | … |`. */
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

describe('error contract', () => {
  const registry = registryFromSpec();

  it('parses a substantial registry from the spec', () => {
    // An empty parse would make every assertion below vacuous — the table format
    // changing is far more likely than the registry emptying.
    expect(registry.length, 'no rows parsed from §4 — the table format changed').toBeGreaterThan(
      50
    );
  });

  it('every code in §4 renders a message and a defined action', () => {
    const unrendered = registry
      .map((row) => ({
        row,
        resolved: resolveError({
          extensions: {
            errorCode: row.code,
            classification: row.classification,
            retryable: row.retryable,
          },
        }),
      }))
      .filter(
        ({ resolved }) =>
          !resolved.message.trim() || !['retry', 'sign-in', 'none'].includes(resolved.action)
      )
      .map(({ row }) => row.code);

    expect(
      unrendered,
      'a code with no rendered state reaches the user as a blank panel'
    ).toEqual([]);
  });

  it('offers retry exactly when the server says the operation is retryable', () => {
    // The rule that matters most. Offering retry on a non-retryable refusal
    // gives the user a button that fails identically every time; withholding it
    // on a retryable one makes a transient outage look permanent.
    const wrong = registry.filter((row) => {
      const resolved = resolveError({
        extensions: {
          errorCode: row.code,
          classification: row.classification,
          retryable: row.retryable,
        },
      });
      if (resolved.action === 'retry') return !row.retryable;
      // RATE_LIMIT_EXCEEDED is retryable but deliberately offers no button —
      // an instant retry is the behaviour the limit exists to prevent.
      return row.retryable && resolved.code !== 'RATE_LIMIT_EXCEEDED';
    });

    expect(wrong.map((row) => row.code), 'retry offered against the contract').toEqual([]);
  });

  it('TOKEN_REVOKED ends the session rather than offering a retry', () => {
    const resolved = resolveError({
      extensions: {
        errorCode: 'TOKEN_REVOKED',
        classification: 'UNAUTHENTICATED',
        retryable: false,
      },
    });

    expect(resolved.action).toBe('sign-in');
    expect(endsSession({ extensions: { errorCode: 'TOKEN_REVOKED' } })).toBe(true);
    expect(endsSession({ extensions: { errorCode: 'NOT_FOUND' } })).toBe(false);
  });

  it('reads the contract from an Apollo combined error (errors[0].extensions)', () => {
    const apollo = { message: 'boom', errors: [{ message: 'boom', extensions: { errorCode: 'INTERNAL_ERROR', classification: 'INTERNAL', retryable: true } }] };
    const resolved = resolveError(apollo);
    expect(resolved.code).toBe('INTERNAL_ERROR');
    expect(resolved.retryable).toBe(true);
    expect(resolved.action).toBe('retry');
    expect(endsSession({ errors: [{ extensions: { errorCode: 'TOKEN_REVOKED' } }] })).toBe(true);
  });

  it('renders something honest when extensions are absent entirely', () => {
    // A transport failure, or a server predating the contract. Rendering nothing
    // is how an error becomes a blank panel, so the fallback has to be real.
    for (const error of [null, undefined, {}, { message: 'boom' }]) {
      const resolved = resolveError(error);
      expect(resolved.message.trim()).not.toBe('');
      expect(resolved.action).toBe('none');
      expect(resolved.retryable).toBe(false);
    }
  });

  it('never leaks a raw code or an internal detail into the message', () => {
    for (const row of registry) {
      const { message } = resolveError({
        extensions: {
          errorCode: row.code,
          classification: row.classification,
          retryable: row.retryable,
        },
      });

      expect(
        message,
        `${row.code} leaks its code into the copy the user reads`
      ).not.toContain(row.code);
      expect(message).not.toMatch(/[A-Z]{2,}_[A-Z]/);
    }
  });

  it('surfaces the correlation id so a user can quote it to support', () => {
    const resolved = resolveError({
      extensions: { errorCode: 'INTERNAL_ERROR', classification: 'INTERNAL', correlationId: 'abc-123' },
    });

    expect(resolved.correlationId).toBe('abc-123');
  });
});
