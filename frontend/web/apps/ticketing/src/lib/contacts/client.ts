import { post, type ClientResult } from "@/lib/identity/client";
import type {
  ChangeChallenges,
  CodeChallenge,
  ContactKind,
  MyContactsView,
  OpResult,
} from "./types";

/**
 * Browser-side wrapper for /api/profile/contacts. Sends the CSRF header; holds no token
 * (the session is an httpOnly cookie) and persists nothing.
 */
const BASE = "/api/profile/contacts";

export async function listContacts(): Promise<ClientResult<MyContactsView>> {
  try {
    const res = await fetch(BASE, {
      credentials: "same-origin",
      headers: { accept: "application/json" },
      cache: "no-store",
    });
    let json: Record<string, unknown> = {};
    try {
      json = (await res.json()) as Record<string, unknown>;
    } catch {
      /* empty */
    }
    if (res.ok)
      return {
        ok: true,
        httpStatus: res.status,
        contacts: (json.contacts as MyContactsView["contacts"]) ?? [],
        pendingChange:
          (json.pendingChange as MyContactsView["pendingChange"]) ?? null,
      };
    return {
      ok: false,
      status: res.status,
      errorCode:
        typeof json.errorCode === "string"
          ? json.errorCode
          : "SERVICE_UNAVAILABLE",
    };
  } catch {
    return { ok: false, status: 0, errorCode: "SERVICE_UNAVAILABLE" };
  }
}

export interface ContactValueInput {
  value: string;
  type: ContactKind;
  regionHint?: string;
}

export const requestAdd = (c: ContactValueInput) =>
  post<CodeChallenge>(`${BASE}/add-request`, c);
export const confirmAdd = (challengeId: string, code: string) =>
  post<OpResult>(`${BASE}/add-confirm`, { challengeId, code });
export const requestChange = (contactId: string, c: ContactValueInput) =>
  post<ChangeChallenges>(`${BASE}/change-request`, { contactId, ...c });
export const confirmChange = (
  changeId: string,
  newCode: string,
  currentCode?: string,
) =>
  post<OpResult>(`${BASE}/change-confirm`, {
    changeId,
    newCode,
    ...(currentCode ? { currentCode } : {}),
  });
/** Send the code again: a single-code flow passes `challengeId`; a change passes `changeId` and a target. */
export const resendCode = (
  ref:
    { challengeId: string } | { changeId: string; target: "NEW" | "CURRENT" },
) => post<CodeChallenge>(`${BASE}/resend`, ref);
export const cancelChange = (changeId: string) =>
  post<{ cancelled: boolean }>(`${BASE}/change-cancel`, { changeId });
export const requestRemoval = (contactId: string) =>
  post<CodeChallenge>(`${BASE}/remove-request`, { contactId });
export const confirmRemoval = (challengeId: string, code: string) =>
  post<OpResult>(`${BASE}/remove-confirm`, { challengeId, code });
export const requestPrimary = (contactId: string) =>
  post<CodeChallenge>(`${BASE}/primary-request`, { contactId });
export const confirmPrimary = (
  contactId: string,
  challengeId: string,
  code: string,
) =>
  post<OpResult>(`${BASE}/primary-confirm`, { contactId, challengeId, code });
