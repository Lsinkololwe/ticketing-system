/**
 * Browser-side wrapper for the identify routes. Sends the CSRF header, never stores or reads
 * any secret (the proof and login handle stay on the server).
 */
export interface IdentityFailure {
  ok: false;
  status: number;
  errorCode: string;
  attemptsRemaining?: number;
  retryAfterSeconds?: number;
  lockedUntil?: string;
}
export type ClientResult<T> =
  ({ ok: true; httpStatus: number } & T) | IdentityFailure;

export async function post<T>(
  path: string,
  body: unknown,
): Promise<ClientResult<T>> {
  let res: Response;
  try {
    res = await fetch(path, {
      method: "POST",
      credentials: "same-origin",
      headers: { "content-type": "application/json", "x-pml-csrf": "1" },
      body: JSON.stringify(body),
    });
  } catch {
    return { ok: false, status: 0, errorCode: "SERVICE_UNAVAILABLE" };
  }
  let json: Record<string, unknown> = {};
  try {
    json = (await res.json()) as Record<string, unknown>;
  } catch {
    /* empty body */
  }
  if (res.ok) return { ok: true, ...(json as T), httpStatus: res.status };
  return {
    ok: false,
    status: res.status,
    errorCode:
      typeof json.errorCode === "string"
        ? json.errorCode
        : "SERVICE_UNAVAILABLE",
    attemptsRemaining:
      typeof json.attemptsRemaining === "number"
        ? json.attemptsRemaining
        : undefined,
    retryAfterSeconds:
      typeof json.retryAfterSeconds === "number"
        ? json.retryAfterSeconds
        : undefined,
    lockedUntil:
      typeof json.lockedUntil === "string" ? json.lockedUntil : undefined,
  };
}

export interface ChallengeOk {
  challengeId: string;
  contactType: string;
  maskedContact: string;
  channel: string;
  expiresInSeconds: number;
  resendAfterSeconds: number;
}
export interface VerifyOk {
  verified: true;
  maskedContact: string;
  expiresInSeconds: number;
}
export interface EnsureOk {
  status: "ACTIVE" | "PROVISIONING";
  isNew?: boolean;
  next?: string;
  retryAfterSeconds?: number;
}

export const requestChallenge = (
  value: string,
  type: "WHATSAPP" | "EMAIL",
  regionHint?: string,
) =>
  post<ChallengeOk>("/api/identity/challenge", {
    contact: { value, type },
    regionHint,
  });
export const verifyCode = (challengeId: string, code: string) =>
  post<VerifyOk>("/api/identity/verify", { challengeId, code });
export const ensureAccount = (consent: boolean) =>
  post<EnsureOk>("/api/identity/ensure", { consent });
export const saveCartIntent = (
  eventId: string,
  quantities: Record<string, number>,
) => post<{ saved: true }>("/api/checkout/intent", { eventId, quantities });
