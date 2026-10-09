import "server-only";

import { bff } from "@/lib/bff";
import { getServerEnv } from "./env";
import type { AppError } from "./identity";
import type {
  ChangeChallenges,
  ChangeKind,
  CodeChallenge,
  ContactView,
  MyContactsView,
  OpResult,
  PendingChange,
} from "@/lib/contacts/types";

/**
 * Server-side client for the contact operations on the GraphQL gateway. The caller's access
 * token is attached here from the server session; the browser never sees it. Nothing in this
 * module logs variables or responses, because they can hold a raw contact value.
 */

export type GqlResult<T> =
  { ok: true; data: T } | { ok: false; error: AppError };

const CHALLENGE =
  "challengeId contactType maskedContact expiresInSeconds resendAfterSeconds";
const RESULT = "changeId kind status";

export const OPERATIONS = {
  list: `query MyContacts { myContacts { contacts { id type valueMasked verifiedAt primary createdAt } pendingChange { changeId kind newContactMasked expiresAt currentContactVerified attemptsRemaining } } }`,
  requestAdd: `mutation RequestContactAdd($input: RequestContactAddInput!) { requestContactAdd(input: $input) { ${CHALLENGE} } }`,
  confirmAdd: `mutation ConfirmContactAdd($input: ConfirmContactAddInput!) { confirmContactAdd(input: $input) { ${RESULT} } }`,
  requestChange: `mutation RequestContactChange($input: RequestContactChangeInput!) { requestContactChange(input: $input) { changeId expiresAt newContact { ${CHALLENGE} } currentContact { ${CHALLENGE} } } }`,
  confirmChange: `mutation ConfirmContactChange($input: ConfirmContactChangeInput!) { confirmContactChange(input: $input) { ${RESULT} } }`,
  cancelChange: `mutation CancelContactChange($changeId: ID!) { cancelContactChange(changeId: $changeId) }`,
  requestRemoval: `mutation RequestContactRemoval($contactId: ID!) { requestContactRemoval(contactId: $contactId) { ${CHALLENGE} } }`,
  confirmRemoval: `mutation ConfirmContactRemoval($input: ConfirmContactRemovalInput!) { confirmContactRemoval(input: $input) { ${RESULT} } }`,
  requestPrimary: `mutation RequestPrimaryContact($contactId: ID!) { requestPrimaryContact(contactId: $contactId) { ${CHALLENGE} } }`,
  resend: `mutation ResendContactCode($input: ResendContactCodeInput!) { resendContactCode(input: $input) { ${CHALLENGE} } }`,
  setPrimary: `mutation SetPrimaryContact($input: SetPrimaryContactInput!) { setPrimaryContact(input: $input) { ${RESULT} } }`,
} as const;

const STATUS_BY_CODE: Record<string, number> = {
  UNAUTHENTICATED: 401,
  FORBIDDEN: 403,
  CONTACT_INVALID: 400,
  OTP_INVALID: 400,
  PROOF_INVALID: 400,
  CONTACT_UNKNOWN: 404,
  OTP_ATTEMPTS_EXHAUSTED: 409,
  NO_VERIFIED_CONTACT: 409,
  COMMAND_NOT_WELL_FORMED: 400,
  ACCOUNT_NOT_ACTIVE: 409,
  ACTOR_NOT_PERMITTED: 403,
  OTP_EXPIRED: 410,
  OTP_LOCKED: 423,
  OTP_RATE_LIMITED: 429,
  OTP_COOLDOWN_ACTIVE: 429,
  CONTACT_ALREADY_CLAIMED: 409,
  LAST_VERIFIED_CONTACT: 409,
  CONTACT_CHANGE_IN_PROGRESS: 409,
  ACCOUNT_MERGING: 409,
  ACCOUNT_SUSPENDED: 403,
  OTP_DELIVERY_FAILED: 503,
};

interface GqlError {
  message?: string;
  extensions?: Record<string, unknown>;
}

function fromGraphQlError(e: GqlError): AppError {
  const x = e.extensions ?? {};
  const raw = x.errorCode ?? x.code;
  const errorCode = typeof raw === "string" ? raw : "REQUEST_FAILED";
  const err: AppError = { status: STATUS_BY_CODE[errorCode] ?? 400, errorCode };
  if (typeof x.retryAfterSeconds === "number")
    err.retryAfterSeconds = x.retryAfterSeconds;
  if (typeof x.attemptsRemaining === "number")
    err.attemptsRemaining = x.attemptsRemaining;
  if (typeof x.lockedUntil === "string") err.lockedUntil = x.lockedUntil;
  if (typeof x.retryable === "boolean") err.retryable = x.retryable;
  return err;
}

export async function gql<T>(
  query: string,
  variables: Record<string, unknown>,
  correlationId: string,
  field: string,
): Promise<GqlResult<T>> {
  const token = await bff.getAccessToken();
  if (!token)
    return { ok: false, error: { status: 401, errorCode: "UNAUTHENTICATED" } };
  let res: Response;
  try {
    res = await fetch(getServerEnv().graphqlUrl, {
      method: "POST",
      headers: {
        "content-type": "application/json",
        accept: "application/json",
        authorization: `Bearer ${token}`,
        "x-correlation-id": correlationId,
        "apollographql-client-name": "myticketzm-web",
      },
      body: JSON.stringify({ query, variables }),
      cache: "no-store",
      signal: AbortSignal.timeout(15_000),
    });
  } catch {
    return {
      ok: false,
      error: { status: 503, errorCode: "SERVICE_UNAVAILABLE", retryable: true },
    };
  }
  if (res.status === 401)
    return { ok: false, error: { status: 401, errorCode: "UNAUTHENTICATED" } };
  let json: {
    data?: Record<string, unknown> | null;
    errors?: GqlError[];
  } | null = null;
  try {
    json = await res.json();
  } catch {
    json = null;
  }
  if (!json || (!res.ok && !json.errors?.length)) {
    return {
      ok: false,
      error: { status: 503, errorCode: "SERVICE_UNAVAILABLE", retryable: true },
    };
  }
  if (json.errors?.length)
    return { ok: false, error: fromGraphQlError(json.errors[0]) };
  const value = json.data?.[field];
  if (value === undefined || value === null) {
    return {
      ok: false,
      error: { status: 502, errorCode: "SERVICE_UNAVAILABLE", retryable: true },
    };
  }
  return { ok: true, data: value as T };
}

// ---- whitelisting: only the documented fields leave this server ---------------

const str = (v: unknown) => (typeof v === "string" ? v : "");
const num = (v: unknown, d = 0) => (typeof v === "number" ? v : d);

export function toContact(v: unknown): ContactView {
  const c = (v ?? {}) as Record<string, unknown>;
  return {
    id: str(c.id),
    type: c.type === "EMAIL" ? "EMAIL" : "WHATSAPP",
    valueMasked: str(c.valueMasked),
    verifiedAt: typeof c.verifiedAt === "string" ? c.verifiedAt : null,
    primary: c.primary === true,
    createdAt: typeof c.createdAt === "string" ? c.createdAt : null,
  };
}

const KINDS = new Set(["ADD", "CHANGE", "REMOVE", "PRIMARY"]);
const kindOf = (v: unknown): ChangeKind =>
  typeof v === "string" && KINDS.has(v) ? (v as ChangeKind) : "CHANGE";

export function toPending(v: unknown): PendingChange | null {
  if (!v || typeof v !== "object") return null;
  const p = v as Record<string, unknown>;
  return {
    changeId: str(p.changeId),
    kind: kindOf(p.kind),
    newContactMasked:
      typeof p.newContactMasked === "string" ? p.newContactMasked : null,
    expiresAt: str(p.expiresAt),
    currentContactVerified: p.currentContactVerified === true,
    attemptsRemaining: num(p.attemptsRemaining, 0),
  };
}

export function toMyContacts(v: unknown): MyContactsView {
  const m = (v ?? {}) as Record<string, unknown>;
  return {
    contacts: Array.isArray(m.contacts) ? m.contacts.map(toContact) : [],
    pendingChange: toPending(m.pendingChange),
  };
}

export function toChallenge(v: unknown): CodeChallenge {
  const c = (v ?? {}) as Record<string, unknown>;
  return {
    challengeId: str(c.challengeId),
    contactType: c.contactType === "EMAIL" ? "EMAIL" : "WHATSAPP",
    maskedContact: str(c.maskedContact),
    expiresInSeconds: num(c.expiresInSeconds, 300),
    resendAfterSeconds: num(c.resendAfterSeconds, 60),
  };
}

export function toChange(v: unknown): ChangeChallenges {
  const c = (v ?? {}) as Record<string, unknown>;
  return {
    changeId: str(c.changeId),
    newContact: toChallenge(c.newContact),
    currentContact: toChallenge(c.currentContact),
    expiresAt: str(c.expiresAt),
  };
}

export function toOp(v: unknown): OpResult {
  const c = (v ?? {}) as Record<string, unknown>;
  return {
    changeId: str(c.changeId),
    kind: kindOf(c.kind),
    status: c.status === "APPLYING" ? "APPLYING" : "COMPLETED",
  };
}

export const toCancelled = (v: unknown) => ({ cancelled: v === true });
