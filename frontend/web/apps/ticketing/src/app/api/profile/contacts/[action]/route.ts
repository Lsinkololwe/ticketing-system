import { NextResponse } from "next/server";
import { bff } from "@/lib/bff";
import { guardMutation, problem } from "@/lib/server/http";
import {
  gql,
  OPERATIONS,
  toCancelled,
  toChallenge,
  toChange,
  toOp,
} from "@/lib/server/contacts";

export const dynamic = "force-dynamic";

/**
 * POST /api/profile/contacts/{action} — contact add / change / removal / primary.
 *
 * Origin + x-pml-csrf guard from the shared BFF, session required, shared limiter policies. Raw contact values
 * pass through in the request body only: they are never logged, stored, or put in a URL, and
 * responses carry masked values only.
 */

type Body = Record<string, unknown>;
type Handler = {
  operation: string;
  field: string;
  /** Returns the GraphQL variables, or null when the body is invalid. */
  variables: (b: Body) => Record<string, unknown> | null;
  shape: (data: unknown) => unknown;
};

const ID = /^[A-Za-z0-9_-]{8,64}$/;
const CODE = /^\d{6}$/;
const KIND = new Set(["WHATSAPP", "EMAIL"]);
const s = (v: unknown) => (typeof v === "string" ? v : "");

function contactInput(b: Body) {
  const value = s(b.value).trim();
  if (!value || value.length > 254 || !KIND.has(s(b.type))) return null;
  const region = s(b.regionHint);
  return {
    value,
    type: b.type,
    ...(/^[A-Z]{2}$/.test(region) ? { regionHint: region } : {}),
  };
}

const HANDLERS: Record<string, Handler> = {
  "add-request": {
    operation: OPERATIONS.requestAdd,
    field: "requestContactAdd",
    variables: (b) => {
      const c = contactInput(b);
      return c ? { input: c } : null;
    },
    shape: toChallenge,
  },
  "add-confirm": {
    operation: OPERATIONS.confirmAdd,
    field: "confirmContactAdd",
    variables: (b) =>
      ID.test(s(b.challengeId)) && CODE.test(s(b.code))
        ? { input: { challengeId: b.challengeId, code: b.code } }
        : null,
    shape: toOp,
  },
  "change-request": {
    operation: OPERATIONS.requestChange,
    field: "requestContactChange",
    variables: (b) => {
      const c = contactInput(b);
      return c && ID.test(s(b.contactId))
        ? { input: { contactId: b.contactId, ...c } }
        : null;
    },
    shape: toChange,
  },
  "change-confirm": {
    operation: OPERATIONS.confirmChange,
    field: "confirmContactChange",
    variables: (b) => {
      if (!ID.test(s(b.changeId)) || !CODE.test(s(b.newCode))) return null;
      if (
        b.currentCode !== undefined &&
        b.currentCode !== null &&
        !CODE.test(s(b.currentCode))
      )
        return null;
      return {
        input: {
          changeId: b.changeId,
          newContactCode: b.newCode,
          ...(b.currentCode ? { currentContactCode: b.currentCode } : {}),
        },
      };
    },
    shape: toOp,
  },
  "change-cancel": {
    operation: OPERATIONS.cancelChange,
    field: "cancelContactChange",
    variables: (b) =>
      ID.test(s(b.changeId)) ? { changeId: b.changeId } : null,
    shape: toCancelled,
  },
  "remove-request": {
    operation: OPERATIONS.requestRemoval,
    field: "requestContactRemoval",
    variables: (b) =>
      ID.test(s(b.contactId)) ? { contactId: b.contactId } : null,
    shape: toChallenge,
  },
  "remove-confirm": {
    operation: OPERATIONS.confirmRemoval,
    field: "confirmContactRemoval",
    variables: (b) =>
      ID.test(s(b.challengeId)) && CODE.test(s(b.code))
        ? { input: { challengeId: b.challengeId, code: b.code } }
        : null,
    shape: toOp,
  },
  resend: {
    operation: OPERATIONS.resend,
    field: "resendContactCode",
    variables: (b) => {
      if (
        ID.test(s(b.changeId)) &&
        (b.target === "NEW" || b.target === "CURRENT")
      ) {
        return { input: { changeId: b.changeId, target: b.target } };
      }
      return ID.test(s(b.challengeId))
        ? { input: { challengeId: b.challengeId } }
        : null;
    },
    shape: toChallenge,
  },
  "primary-request": {
    operation: OPERATIONS.requestPrimary,
    field: "requestPrimaryContact",
    variables: (b) =>
      ID.test(s(b.contactId)) ? { contactId: b.contactId } : null,
    shape: toChallenge,
  },
  "primary-confirm": {
    operation: OPERATIONS.setPrimary,
    field: "setPrimaryContact",
    variables: (b) =>
      ID.test(s(b.contactId)) &&
      ID.test(s(b.challengeId)) &&
      CODE.test(s(b.code))
        ? {
            input: {
              contactId: b.contactId,
              challengeId: b.challengeId,
              code: b.code,
            },
          }
        : null,
    shape: toOp,
  },
};

export async function POST(
  req: Request,
  { params }: { params: Promise<{ action: string }> },
) {
  const { action } = await params;
  const handler = Object.hasOwn(HANDLERS, action)
    ? HANDLERS[action]
    : undefined;
  if (!handler) return problem(404, "REQUEST_FAILED");

  const session = await bff.getSession();
  if (!session) return problem(401, "UNAUTHENTICATED");
  const policy =
    action.endsWith("request") || action === "resend"
      ? "contacts_challenge"
      : "contacts";
  const ctx = await guardMutation(req, policy, { account: session.accountId });
  if (ctx instanceof NextResponse) return ctx;

  let body: Body;
  try {
    body = (await req.json()) as Body;
  } catch {
    body = {};
  }
  const variables = handler.variables(
    body && typeof body === "object" ? body : {},
  );
  if (!variables) {
    return problem(
      400,
      action.endsWith("confirm") ? "OTP_INVALID" : "CONTACT_INVALID",
    );
  }

  const res = await gql<unknown>(
    handler.operation,
    variables,
    ctx.correlationId,
    handler.field,
  );
  if (!res.ok) {
    const { status, ...rest } = res.error;
    return problem(status, rest.errorCode, rest);
  }
  const shaped = handler.shape(res.data) as { status?: string };
  // APPLYING: codes were right and the change is finishing in the background; look again shortly.
  const pending = shaped.status === "APPLYING";
  return NextResponse.json(shaped, {
    status: pending ? 202 : 200,
    headers: {
      "cache-control": "no-store",
      "x-correlation-id": ctx.correlationId,
      ...(pending ? { "retry-after": "2" } : {}),
    },
  });
}
