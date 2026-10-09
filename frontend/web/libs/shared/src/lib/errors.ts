/**
 * What a client should do about a refusal.
 *
 * <h2>Branch on the code, never on the message</h2>
 * `extensions` is the contract and the message is for humans. A client that matches on message text breaks the moment someone
 * improves the copy, and it breaks silently — the branch simply stops being
 * taken. Everything here reads `extensions`.
 *
 * <h2>Why this maps classifications rather than every code</h2>
 * The registry holds 93 codes. Writing 93 bespoke UI branches would produce a
 * table nobody can keep complete, and the first unmapped code would render
 * blank — which is the failure mode a closed registry exists to prevent. So the
 * default behaviour comes from the eight classifications every code carries,
 * and a code only appears by name where the product genuinely needs different
 * handling. An unrecognised code therefore still renders something honest
 * rather than nothing.
 *
 * <p>`errorContract.test.ts` parses the registry table and asserts every code in
 * it resolves to a rendered state, so "complete" is measured against the
 * registry rather than asserted here.</p>
 */

/** The eight classifications the registry assigns every code. */
export type ErrorClassification =
  | 'BAD_REQUEST'
  | 'UNAUTHENTICATED'
  | 'PERMISSION_DENIED'
  | 'NOT_FOUND'
  | 'FAILED_PRECONDITION'
  | 'UNAVAILABLE'
  | 'INTERNAL'
  | 'UNKNOWN';

/** What the UI should offer the user. */
export type ErrorAction = 'retry' | 'sign-in' | 'none';

/** One offending input field, as the error contract puts it on the wire. */
export interface FieldViolation {
  /** The input path the client sent, e.g. `input.title` or `items[1].name`. */
  path: string;
  /**
   * The constraint's name — `NotBlank`, `Size`, `Email`.
   *
   * <p>Never a message. The server sends names precisely so this layer can
   * translate them; a server-composed sentence would arrive in one language and
   * would also carry the rejected value back to the screen.</p>
   */
  constraint: string;
}

export interface ErrorExtensions {
  errorCode?: string;
  retryable?: boolean;
  classification?: ErrorClassification;
  correlationId?: string;
  fields?: FieldViolation[];
  [key: string]: unknown;
}

export interface GraphQLLikeError {
  message?: string;
  extensions?: ErrorExtensions;
  /**
   * Apollo Client 4 wraps GraphQL errors in a `CombinedGraphQLErrors` whose `errors` array carries the
   * per-error `extensions`. The contract fields live on the first one, so read them from there.
   */
  errors?: ReadonlyArray<{ message?: string; extensions?: ErrorExtensions }>;
}

/** The error that carries the contract extensions: the error itself, or the first of an Apollo combined error. */
function carrier(error: GraphQLLikeError | null | undefined): { extensions?: ErrorExtensions } | null | undefined {
  if (error?.extensions) return error;
  return error?.errors?.[0] ?? error;
}

export interface ResolvedError {
  code: string;
  classification: ErrorClassification;
  /** Whether retrying this same operation could succeed. */
  retryable: boolean;
  action: ErrorAction;
  /** Human-facing copy. Never parsed, only displayed. */
  message: string;
  /** Shown so a user can quote it to support; the log carries the detail. */
  correlationId?: string;
}

/**
 * Copy per classification.
 *
 * <p>Written for the person reading it, not the engineer: it says what happened
 * and what they can do, and never names an internal system. `INTERNAL`
 * deliberately reassures — the platform's own guidance is that "your money is
 * fine, we are checking" is the difference between a user retrying a payment
 * and not.</p>
 */
const MESSAGE_BY_CLASSIFICATION: Record<ErrorClassification, string> = {
  BAD_REQUEST: 'Something in that request was not right. Check the details and try again.',
  UNAUTHENTICATED: 'Please sign in to continue.',
  PERMISSION_DENIED: 'You do not have access to do that.',
  NOT_FOUND: 'We could not find that.',
  FAILED_PRECONDITION: 'That cannot be done right now.',
  UNAVAILABLE: 'We are having trouble reaching a service. Please try again in a moment.',
  INTERNAL: 'Something went wrong on our side. Nothing you did caused it, and nothing has been lost.',
  UNKNOWN: 'Something went wrong. Please try again.',
};

/**
 * Codes whose handling differs from their classification's default.
 *
 * <p>Kept deliberately short. Every entry is a claim that this code needs
 * something its classification does not already give it, and a long list here
 * is the 93-branch table this design exists to avoid.</p>
 */
const SPECIAL_CASES: Record<string, Partial<ResolvedError>> = {
  // Classified UNAUTHENTICATED, so the default would offer sign-in —
  // but the session is actively revoked, so the app must *end* it rather than
  // let a stale one linger behind a sign-in prompt.
  TOKEN_REVOKED: {
    action: 'sign-in',
    message: 'Your session has ended. Please sign in again.',
  },
  // Retryable, but not immediately: an instant retry button invites
  // exactly the behaviour the limit exists to stop.
  RATE_LIMIT_EXCEEDED: {
    action: 'none',
    message: 'Too many requests. Please wait a moment before trying again.',
  },
  // Not the user's doing — a client asked for more rows than the
  // platform serves, so there is nothing for them to retry or fix.
  PAGE_SIZE_EXCEEDED: {
    action: 'none',
    message: 'That request asked for too much data at once.',
  },
};

/** Classification implied by a code when the server did not send one. */
const FALLBACK_CLASSIFICATION: ErrorClassification = 'UNKNOWN';

/** Codes already reported, so one bad endpoint does not flood the console. */
const reportedCodes = new Set<string>();

/**
 * Records that the server sent something this client cannot classify.
 *
 * <p>Deduplicated by code: a failing list view can fire the same refusal on
 * every row, and a console with two hundred identical lines is read as noise
 * and muted, which is the same as not logging at all.</p>
 */
function reportUnrecognised(code: string, classification: unknown): void {
  if (reportedCodes.has(code)) {
    return;
  }
  reportedCodes.add(code);
  console.warn(
    `[errors] no rendered state for errorCode "${code}" ` +
      `(classification: ${String(classification)}). Showing the generic message. ` +
      'Add it to the shared error mapping, or regenerate against ET-PLT-005 §4.'
  );
}

/** Test seam: forget what has been reported. */
export function resetUnrecognisedReporting(): void {
  reportedCodes.clear();
}

/**
 * Resolves one GraphQL error into what the UI should render.
 *
 * <p>Tolerates a missing `extensions` block entirely: a transport failure or a
 * server that predates the contract still has to render something, and
 * rendering nothing is how an error becomes a blank panel.</p>
 */
export function resolveError(error: GraphQLLikeError | null | undefined): ResolvedError {
  const extensions = carrier(error)?.extensions ?? {};
  const code = extensions.errorCode ?? 'UNKNOWN';
  const rawClassification = extensions.classification;
  const recognised =
    typeof rawClassification === 'string' &&
    rawClassification in MESSAGE_BY_CLASSIFICATION;

  if (!recognised) {
    // The user still gets a sentence — rendering nothing, or rendering
    // `TIER_SOLD_OUT` verbatim, are the two failure modes this exists to stop.
    // But someone has to learn the client is behind the registry, and only a log
    // does that: the screen looks fine, so nobody reports it.
    reportUnrecognised(code, rawClassification);
  }

  const classification = recognised
    ? (rawClassification as ErrorClassification)
    : FALLBACK_CLASSIFICATION;
  const retryable = extensions.retryable === true;

  const base: ResolvedError = {
    code,
    classification,
    retryable,
    // Retry is offered when, and only when, the server says retrying could work.
    // Offering it otherwise produces a button that fails identically every time.
    action: retryable
      ? 'retry'
      : classification === 'UNAUTHENTICATED'
        ? 'sign-in'
        : 'none',
    message: MESSAGE_BY_CLASSIFICATION[classification] ?? MESSAGE_BY_CLASSIFICATION.UNKNOWN,
    correlationId: extensions.correlationId,
  };

  return { ...base, ...SPECIAL_CASES[code] };
}

/** Whether this refusal means the session is over and the user must sign in again. */
export function endsSession(error: GraphQLLikeError | null | undefined): boolean {
  return carrier(error)?.extensions?.errorCode === 'TOKEN_REVOKED';
}

/** What the transport layer must do about a refusal, before any screen sees it. */
export type SessionAction = 'end-session' | 'reauthenticate' | 'none';

/**
 * The auth decision, made from the wire format.
 *
 * <p>Takes the whole error rather than a code string, so that reading the
 * extensions is part of what is under test. `errorCode` is the contract key;
 * graphql-java also offers a generic `code` that this platform never populates,
 * and a caller reading that one gets `undefined` for every refusal — a transport
 * layer that never ends a session, with no symptom until a revoked token is
 * accepted. A helper taking a bare string cannot express that guarantee, because
 * the caller would already have made the choice that matters.</p>
 */
export function sessionAction(error: GraphQLLikeError | null | undefined): SessionAction {
  switch (carrier(error)?.extensions?.errorCode) {
    // Revoked is not expired. The server has fail-closed on this token
    // deliberately, so re-presenting it fails forever and a retry is an infinite
    // loop. Local state must be dropped, not just redirected past.
    case 'TOKEN_REVOKED':
      return 'end-session';
    case 'ACTOR_NOT_AUTHENTICATED':
      return 'reauthenticate';
    default:
      return 'none';
  }
}

/**
 * Copy per constraint name, for field-level display.
 *
 * <p>Keyed on the constraint rather than the field, so a rule added to a new
 * input renders correctly without touching this file. The wording says what to
 * do, not what failed — "Required" beats "must not be blank" beside an empty
 * box, where the user can already see it is empty.</p>
 */
const MESSAGE_BY_CONSTRAINT: Record<string, string> = {
  NotBlank: 'Required',
  NotNull: 'Required',
  NotEmpty: 'Required',
  Size: 'Wrong length',
  Min: 'Too small',
  Max: 'Too large',
  Positive: 'Must be more than zero',
  PositiveOrZero: 'Cannot be negative',
  Email: 'Enter a valid email address',
  Pattern: 'Wrong format',
  Past: 'Must be in the past',
  Future: 'Must be in the future',
  PastOrPresent: 'Cannot be in the future',
  FutureOrPresent: 'Cannot be in the past',
  Digits: 'Enter a number',
  DecimalMin: 'Too small',
  DecimalMax: 'Too large',
};

/** What to show beside a field when nothing more specific is known. */
const FALLBACK_FIELD_MESSAGE = 'Not valid';

/**
 * Field path → message, for rendering errors **on the inputs**.
 *
 * <p>The error contract and the design authority's `error-feedback` rule both put
 * the message next to the problem. A banner saying "3 fields are invalid"
 * makes the user hunt: on a long form the offending field may be off-screen, and
 * nothing marks it when they get there.</p>
 *
 * <p>Returns an empty object for any error that is not a validation refusal, so
 * a form can call this unconditionally and simply render nothing.</p>
 */
export function fieldErrors(
  error: GraphQLLikeError | null | undefined
): Record<string, string> {
  const violations = error?.extensions?.fields;
  if (!Array.isArray(violations)) {
    return {};
  }

  const byPath: Record<string, string> = {};
  for (const violation of violations) {
    if (!violation || typeof violation.path !== 'string') {
      continue;
    }
    // First constraint wins. A field failing both @NotBlank and @Size shows
    // "Required" rather than stacking two messages into one line — the second is
    // a consequence of the first and disappears when it is fixed.
    if (byPath[violation.path] === undefined) {
      byPath[violation.path] =
        MESSAGE_BY_CONSTRAINT[violation.constraint] ?? FALLBACK_FIELD_MESSAGE;
    }
  }
  return byPath;
}
