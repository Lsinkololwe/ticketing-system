import type { ErrorKind } from "@/lib/identity/messages";

/**
 * Copy for the Sign-in contacts area, keyed by stable errorCode. Wording rules:
 *  - a contact that belongs to someone else is never named as such (no account enumeration);
 *  - raw contact values are never echoed.
 */
export interface ContactErrorInfo {
  kind: ErrorKind | "session";
  message: string;
}

const NEUTRAL_UNAVAILABLE =
  "We couldn’t use that contact. Check it is typed correctly, or try a different one.";

export const CONTACT_ERRORS: Record<string, ContactErrorInfo> = {
  CONTACT_INVALID: {
    kind: "field",
    message:
      "That doesn’t look like a valid WhatsApp number or email. Check it and try again.",
  },
  OTP_INVALID: {
    kind: "field",
    message: "That code isn’t right. Check the message and try again.",
  },
  PROOF_INVALID: {
    kind: "restart",
    message: "That step timed out. Start again to get a new code.",
  },
  OTP_EXPIRED: {
    kind: "restart",
    message: "That code has expired. Request a new one.",
  },
  OTP_LOCKED: {
    kind: "locked",
    message:
      "Too many wrong codes. For your security, wait before trying again.",
  },
  OTP_RATE_LIMITED: {
    kind: "locked",
    message:
      "You’ve asked for a lot of codes. Please wait a while and try again.",
  },
  OTP_COOLDOWN_ACTIVE: {
    kind: "retry",
    message: "A code was just sent. You can ask for another shortly.",
  },
  OTP_DELIVERY_FAILED: {
    kind: "retry",
    message: "We couldn’t deliver the code. Try again in a moment.",
  },
  // Same words for "taken by another account", "lost a race" and "quarantined": nothing to learn from them.
  CONTACT_ALREADY_CLAIMED: { kind: "field", message: NEUTRAL_UNAVAILABLE },
  CONTACT_UNAVAILABLE: { kind: "field", message: NEUTRAL_UNAVAILABLE },
  CONTACT_CHANGE_IN_PROGRESS: {
    kind: "blocked",
    message:
      "A change is already waiting for confirmation. Finish it, or wait for it to expire, before starting another.",
  },
  CONTACT_UNKNOWN: {
    kind: "blocked",
    message:
      "That contact or change is no longer on your account. Refresh the list.",
  },
  OTP_ATTEMPTS_EXHAUSTED: {
    kind: "blocked",
    message:
      "Too many wrong codes, so this change was cancelled. Nothing was changed; you can start again.",
  },
  NO_VERIFIED_CONTACT: {
    kind: "blocked",
    message:
      "We have no verified contact that can receive a code. Please contact support.",
  },
  COMMAND_NOT_WELL_FORMED: {
    kind: "field",
    message:
      "Please fill in every field and use the same kind of contact as before.",
  },
  ACCOUNT_NOT_ACTIVE: {
    kind: "blocked",
    message:
      "Contacts can’t be changed right now. Please try again later or contact support.",
  },
  ACTOR_NOT_PERMITTED: {
    kind: "blocked",
    message: "This account can’t manage buyer contacts.",
  },
  LAST_VERIFIED_CONTACT: {
    kind: "blocked",
    message:
      "This is your only verified contact. Add another verified contact first, so you can always sign in.",
  },
  ACCOUNT_MERGING: {
    kind: "retry",
    message:
      "We’re finishing an update on your account. Try again in a minute.",
  },
  ACCOUNT_SUSPENDED: {
    kind: "blocked",
    message: "Contacts can’t be changed right now. Please contact support.",
  },
  UNAUTHENTICATED: {
    kind: "session",
    message: "Your session has ended. Please sign in again.",
  },
  ORIGIN_REJECTED: {
    kind: "blocked",
    message:
      "This request was blocked for your security. Reload the page and try again.",
  },
  SERVICE_UNAVAILABLE: {
    kind: "retry",
    message:
      "We can’t reach our service. Your contacts are unchanged. Try again in a moment.",
  },
};

const FALLBACK: ContactErrorInfo = {
  kind: "retry",
  message: "Something went wrong. Please try again.",
};

export function describeContactError(
  code: string | undefined,
): ContactErrorInfo {
  return (code && CONTACT_ERRORS[code]) || FALLBACK;
}

export const LAST_VERIFIED_EXPLANATION =
  CONTACT_ERRORS.LAST_VERIFIED_CONTACT.message;
