import type { FieldValues, Path, UseFormReturn } from 'react-hook-form';
import { fieldErrors as violationsToMessages, resolveError, sessionAction } from '../lib/errors';
import type { ErrorAction, ErrorClassification, FieldViolation, GraphQLLikeError } from '../lib/errors';

/**
 * The one place that turns a backend refusal into something a form can show.
 *
 * Wire shapes handled (ET-PLT-005):
 *  - GraphQL: `errors[].extensions.{errorCode, retryable, classification, correlationId, fields[]}`;
 *    Apollo Client 4 surfaces these as `CombinedGraphQLErrors` (`.errors`), older shapes as `.graphQLErrors`.
 *  - REST: RFC 9457 `application/problem+json` with `errorCode`, `retryAfterSeconds`, `retryable`, `fields[]`
 *    (or a `{ path: message }` map in `errors`).
 *  - Anything else (network failure, thrown Error) becomes a neutral UNAVAILABLE refusal.
 *
 * Branching is always on `errorCode`, never on message text. The registry itself is classified in
 * `lib/errors.ts` (parsed against the spec in `errorContract.test.ts`); here we only add the form-specific
 * facts: which field a code belongs to, and copy that reads well beside an input.
 */

/** RFC 9457 problem document as the REST surface sends it. */
export interface ProblemDetails {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  errorCode?: string;
  retryable?: boolean;
  retryAfterSeconds?: number;
  correlationId?: string;
  classification?: ErrorClassification;
  fields?: FieldViolation[];
  /** Alternative field error map, `{ 'input.slug': 'Taken' }`. */
  errors?: Record<string, string>;
}

/** Throw this from a fetch-based REST helper so `applyServerError` can read the body. */
export class RestProblemError extends Error {
  constructor(
    public readonly status: number,
    public readonly problem: ProblemDetails,
    public readonly retryAfterHeader?: string | null
  ) {
    super(problem.detail ?? problem.title ?? `HTTP ${status}`);
    this.name = 'RestProblemError';
  }
}

/** Parse a failed `fetch` Response into a RestProblemError (never throws). */
export async function problemFromResponse(res: Response): Promise<RestProblemError> {
  let body: ProblemDetails = {};
  try {
    body = (await res.json()) as ProblemDetails;
  } catch {
    /* empty or non-JSON body */
  }
  return new RestProblemError(res.status, { status: res.status, ...body }, res.headers.get('retry-after'));
}

export type ErrorPresentation = 'field' | 'form' | 'toast';

export interface NormalisedError {
  code: string;
  classification: ErrorClassification;
  retryable: boolean;
  action: ErrorAction;
  /** Neutral, user-facing sentence. */
  message: string;
  retryAfterSeconds?: number;
  correlationId?: string;
  /** Field path (form-relative) to message. */
  fieldErrors: Record<string, string>;
  /** True when the session is over; the app must sign out rather than show a banner. */
  endsSession: boolean;
  presentation: ErrorPresentation;
  /** Form field that owns this code, if any. */
  field?: string;
}

/**
 * Codes that belong to a specific input, with copy that reads beside it. Field names are the common
 * ones used across the apps; override per form with `fieldMap`.
 */
export const FIELD_FOR_CODE: Readonly<Record<string, { field: string; message: string }>> = {
  PHONE_NUMBER_INVALID: { field: 'phone', message: 'Enter a valid phone number.' },
  CONTACT_INVALID: { field: 'contact', message: 'Enter a valid phone number or email address.' },
  CONTACT_ALREADY_CLAIMED: { field: 'contact', message: 'That contact is already used by another account.' },
  OTP_INVALID: { field: 'code', message: 'That code is not right. Check it and try again.' },
  OTP_EXPIRED: { field: 'code', message: 'That code has expired. Request a new one.' },
  PROOF_INVALID: { field: 'code', message: 'We could not confirm that code. Request a new one.' },
  SLUG_TAKEN: { field: 'slug', message: 'That web address is taken. Try another.' },
  ORGANIZATION_ALREADY_EXISTS: { field: 'name', message: 'An organization with those details already exists.' },
  PROMO_CODE_UNKNOWN: { field: 'promoCode', message: 'That promo code is not valid.' },
  PROMO_CODE_EXHAUSTED: { field: 'promoCode', message: 'That promo code has been fully used.' },
  PROMO_CODE_NOT_APPLICABLE: { field: 'promoCode', message: 'That promo code does not apply to this order.' },
  PAYOUT_BELOW_MINIMUM: { field: 'amount', message: 'That is below the minimum payout amount.' },
  MSISDN_PROVIDER_UNSUPPORTED: { field: 'phone', message: 'That mobile money provider is not supported.' },
  CONFIGURATION_VALUE_INVALID: { field: 'value', message: 'That value is not allowed.' },
  REFERENCE_CODE_DUPLICATE: { field: 'code', message: 'That code is already in use.' },
  CAPACITY_BELOW_COMMITTED: { field: 'capacity', message: 'Capacity cannot be lower than tickets already sold or held.' },
  TRANSFER_TO_SELF: { field: 'contact', message: 'You cannot transfer a ticket to yourself.' },
  LOGIN_HANDLE_INVALID: { field: 'contact', message: 'Enter a valid phone number or email address.' },
};

/** Form-level copy for codes where a person needs more than the classification sentence. */
export const CODE_COPY: Readonly<Record<string, string>> = {
  OTP_COOLDOWN_ACTIVE: 'Please wait before requesting another code.',
  OTP_RATE_LIMITED: 'Too many codes requested. Please wait before trying again.',
  OTP_ATTEMPTS_EXHAUSTED: 'Too many incorrect attempts. Request a new code.',
  OTP_LOCKED: 'This account is temporarily locked. Try again later.',
  OTP_DELIVERY_FAILED: 'We could not send the code. Please try again.',
  ACCOUNT_SUSPENDED: 'This account is suspended. Contact support for help.',
  TIER_SOLD_OUT: 'Those tickets have sold out.',
  TIER_NOT_ON_SALE: 'Those tickets are not on sale right now.',
  PURCHASE_LIMIT_EXCEEDED: 'That is more tickets than one order allows.',
  RESERVATION_EXPIRED: 'Your hold on these tickets has expired. Start again to reserve them.',
  PAYMENT_DECLINED: 'The payment was declined. Try another number or method.',
  PAYMENT_PROVIDER_UNAVAILABLE: 'The payment provider is not responding. Your money has not been taken. Try again shortly.',
  PAYMENT_ALREADY_COMPLETED: 'This order is already paid.',
  EVENT_STATE_INVALID: 'That cannot be done while the event is in its current state.',
  ORGANIZATION_STATE_INVALID: 'That cannot be done while the organization is in its current state.',
  ORGANIZER_NOT_APPROVED: 'Your organization must be approved before you can do that.',
  RESOURCE_CONFLICT: 'Someone else changed this. Reload and try again.',
  IDEMPOTENCY_KEY_REUSED: 'That request was already sent. Reload to see its result.',
  COMMAND_NOT_WELL_FORMED: 'Some details are not valid. Check the highlighted fields.',
  RATE_LIMIT_EXCEEDED: 'Too many requests. Please wait a moment before trying again.',
};

type Rec = Record<string, unknown>;
const isRec = (v: unknown): v is Rec => typeof v === 'object' && v !== null;

/** Pull the first GraphQL-style error out of whatever the transport threw. */
function firstGraphQLError(err: unknown): GraphQLLikeError | undefined {
  if (!isRec(err)) return undefined;
  const list = Array.isArray(err.errors) ? err.errors : Array.isArray(err.graphQLErrors) ? err.graphQLErrors : undefined;
  if (list && list.length > 0 && isRec(list[0])) return list[0] as GraphQLLikeError;
  // Apollo 4 `mutate()` with errorPolicy 'all' resolves with `{ error }`; unwrap one level.
  if (isRec(err.error)) return firstGraphQLError(err.error);
  if (isRec(err.cause)) return firstGraphQLError(err.cause);
  return undefined;
}

function problemOf(err: unknown): { problem: ProblemDetails; retryAfterHeader?: string | null } | undefined {
  if (err instanceof RestProblemError) return { problem: err.problem, retryAfterHeader: err.retryAfterHeader };
  if (!isRec(err)) return undefined;
  // axios-like: err.response.data
  const response = err.response;
  if (isRec(response) && isRec(response.data)) {
    const headers = isRec(response.headers) ? response.headers : {};
    const h = headers['retry-after'];
    return {
      problem: { status: typeof response.status === 'number' ? response.status : undefined, ...(response.data as ProblemDetails) },
      retryAfterHeader: typeof h === 'string' ? h : undefined,
    };
  }
  if (isRec(err.problem)) return { problem: err.problem as ProblemDetails };
  // A bare problem document.
  if (typeof err.errorCode === 'string' || typeof err.type === 'string') return { problem: err as ProblemDetails };
  return undefined;
}

const STATUS_CLASS: Record<number, ErrorClassification> = {
  400: 'BAD_REQUEST',
  401: 'UNAUTHENTICATED',
  403: 'PERMISSION_DENIED',
  404: 'NOT_FOUND',
  409: 'FAILED_PRECONDITION',
  503: 'UNAVAILABLE',
  500: 'INTERNAL',
};

/** Strip the operation argument prefix (`input.slug` -> `slug`) and optionally rename. */
function localPath(path: string, fieldMap?: Record<string, string>, prefixes: string[] = ['input.']): string {
  let p = path.replace(/\[(\d+)\]/g, '.$1');
  for (const prefix of prefixes) if (p.startsWith(prefix)) p = p.slice(prefix.length);
  return fieldMap?.[p] ?? fieldMap?.[path] ?? p;
}

export interface NormaliseOptions {
  /** Server path or code-field -> form field name. */
  fieldMap?: Record<string, string>;
  /** Leading path segments to strip from violation paths. Default `['input.']`. */
  stripPrefixes?: string[];
}

/** Turn any thrown/returned transport error into one shape. Never throws. */
export function normaliseServerError(err: unknown, options: NormaliseOptions = {}): NormalisedError {
  let like: GraphQLLikeError | undefined = firstGraphQLError(err);
  let retryAfterSeconds: number | undefined;
  let violationMessages: Record<string, string> = {};

  const rest = like ? undefined : problemOf(err);
  if (rest) {
    const p = rest.problem;
    like = {
      message: p.detail ?? p.title,
      extensions: {
        errorCode: p.errorCode,
        retryable: p.retryable,
        classification: p.classification ?? (p.status ? STATUS_CLASS[p.status] : undefined),
        correlationId: p.correlationId,
        fields: p.fields,
      },
    };
    retryAfterSeconds =
      typeof p.retryAfterSeconds === 'number'
        ? p.retryAfterSeconds
        : rest.retryAfterHeader && /^\d+$/.test(rest.retryAfterHeader)
          ? Number(rest.retryAfterHeader)
          : undefined;
    if (p.errors && isRec(p.errors)) violationMessages = { ...p.errors };
  } else if (like) {
    const ra = (like.extensions as Rec | undefined)?.retryAfterSeconds;
    if (typeof ra === 'number') retryAfterSeconds = ra;
  }

  if (!like) {
    // Transport failure or thrown Error: the server never answered.
    like = { extensions: { errorCode: 'SERVICE_UNAVAILABLE', classification: 'UNAVAILABLE', retryable: true } };
  }

  const resolved = resolveError(like);
  const fromViolations = violationsToMessages(like);
  const fieldErrors: Record<string, string> = {};
  for (const [k, v] of Object.entries({ ...violationMessages, ...fromViolations })) {
    const key = localPath(k, options.fieldMap, options.stripPrefixes);
    if (fieldErrors[key] === undefined) fieldErrors[key] = v;
  }

  const owned = FIELD_FOR_CODE[resolved.code];
  const field = owned ? (options.fieldMap?.[owned.field] ?? owned.field) : undefined;
  if (field && fieldErrors[field] === undefined && Object.keys(fieldErrors).length === 0) {
    fieldErrors[field] = owned.message;
  }

  let message = CODE_COPY[resolved.code] ?? resolved.message;
  if (retryAfterSeconds && retryAfterSeconds > 0) {
    message = `${message.replace(/\s*$/, '')} Try again in ${retryAfterSeconds} ${retryAfterSeconds === 1 ? 'second' : 'seconds'}.`;
  }

  const hasFields = Object.keys(fieldErrors).length > 0;
  const presentation: ErrorPresentation = hasFields
    ? 'field'
    : resolved.classification === 'UNAVAILABLE' || resolved.classification === 'INTERNAL'
      ? 'toast'
      : 'form';

  return {
    code: resolved.code,
    classification: resolved.classification,
    retryable: resolved.retryable,
    action: resolved.action,
    message,
    retryAfterSeconds,
    correlationId: resolved.correlationId,
    fieldErrors,
    endsSession: sessionAction(like) !== 'none',
    presentation,
    field,
  };
}

export interface ApplyServerErrorOptions extends NormaliseOptions {
  /** Called for toast-class errors (UNAVAILABLE / INTERNAL). When omitted they show as the form banner. */
  notify?: (toast: { message: string; tone: 'error' }) => void;
  /** Called when the session ended (TOKEN_REVOKED etc.) so the app can sign out. */
  onSessionEnded?: (error: NormalisedError) => void;
}

/** Name used for the form-level error (`errors.root.server`). */
export const FORM_ERROR_KEY = 'root.server' as const;

/**
 * Map a server refusal into react-hook-form: field errors on the inputs, otherwise a form-level error
 * at `errors.root.server` (rendered by `<Form>`), and a snackbar for transient failures.
 * Returns the normalised error so callers can branch on `code` for flow decisions.
 */
export function applyServerError<T extends FieldValues>(
  form: Pick<UseFormReturn<T>, 'setError'>,
  err: unknown,
  options: ApplyServerErrorOptions = {}
): NormalisedError {
  const n = normaliseServerError(err, options);
  if (n.endsSession) options.onSessionEnded?.(n);

  for (const [path, message] of Object.entries(n.fieldErrors)) {
    form.setError(path as Path<T>, { type: 'server', message });
  }
  const showBanner = () => form.setError(FORM_ERROR_KEY as Path<T>, { type: 'server', message: n.message });
  if (n.presentation === 'form') showBanner();
  else if (n.presentation === 'toast') {
    if (options.notify) options.notify({ message: n.message, tone: 'error' });
    else showBanner();
  } else if (n.code === 'COMMAND_NOT_WELL_FORMED' || !n.field) {
    // Field errors are on the inputs; the summary region still announces that something was refused.
  }
  return n;
}
