/**
 * errorCode -> UI state + copy for the identify step. Copy is keyed by the stable
 * `errorCode`, never by server message text.
 *
 * kind:
 *  - 'field'  stay on the same step, show inline under the input
 *  - 'retry'  transient; offer try again / resend
 *  - 'locked' the step is blocked until `lockedUntil`/countdown
 *  - 'restart' go back to the contact step
 *  - 'blocked' dead end, show support text (no retry)
 */
export type ErrorKind = "field" | "retry" | "locked" | "restart" | "blocked";

export interface IdentityErrorInfo {
  kind: ErrorKind;
  message: string;
}

export const IDENTITY_ERROR_MESSAGES: Record<string, IdentityErrorInfo> = {
  CONTACT_INVALID: {
    kind: "field",
    message:
      "That doesn’t look like a valid WhatsApp number or email. Check it and try again.",
  },
  OTP_INVALID: {
    kind: "field",
    message: "That code isn’t right. Check the message and try again.",
  },
  OTP_EXPIRED: {
    kind: "restart",
    message: "That code has expired. We can send you a new one.",
  },
  OTP_LOCKED: {
    kind: "locked",
    message:
      "Too many wrong codes. For your security, wait a few minutes before trying again.",
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
    message:
      "We couldn’t deliver your code. Try again, or use a different WhatsApp number or email.",
  },
  PROOF_INVALID: {
    kind: "restart",
    message: "Your verification timed out. Please request a new code.",
  },
  ACCOUNT_SUSPENDED: {
    kind: "blocked",
    message: "This account can’t be used right now. Please contact support.",
  },
  ACCOUNT_MERGING: {
    kind: "retry",
    message:
      "We’re finishing an update on your account. Try again in a minute.",
  },
  LOGIN_HANDLE_INVALID: {
    kind: "restart",
    message: "Your sign-in link expired. Please verify your contact again.",
  },
  CONSENT_REQUIRED: {
    kind: "field",
    message: "Please accept the terms to continue.",
  },
  CONTACT_ALREADY_CLAIMED: {
    kind: "retry",
    message: "Something changed while we set things up. Try again.",
  },
  ORIGIN_REJECTED: {
    kind: "blocked",
    message:
      "This request was blocked for your security. Reload the page and try again.",
  },
  SERVICE_UNAVAILABLE: {
    kind: "retry",
    message:
      "We can’t reach our sign-in service. Please try again in a moment.",
  },
};

const FALLBACK: IdentityErrorInfo = {
  kind: "retry",
  message: "Something went wrong. Please try again.",
};

export function describeError(
  errorCode: string | undefined,
): IdentityErrorInfo {
  return (errorCode && IDENTITY_ERROR_MESSAGES[errorCode]) || FALLBACK;
}

/** Page-level errors passed back by /api/auth/* redirects as ?error=. */
export const AUTH_PAGE_ERRORS: Record<string, string> = {
  SIGN_IN_FAILED: "We couldn’t finish signing you in. Please try again.",
  ACCOUNT_SUSPENDED: IDENTITY_ERROR_MESSAGES.ACCOUNT_SUSPENDED.message,
  LOGIN_HANDLE_INVALID: IDENTITY_ERROR_MESSAGES.LOGIN_HANDLE_INVALID.message,
  SERVICE_UNAVAILABLE: IDENTITY_ERROR_MESSAGES.SERVICE_UNAVAILABLE.message,
};
