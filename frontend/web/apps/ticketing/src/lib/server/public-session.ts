import "server-only";

import { cache } from "react";
import { bff } from "@/lib/bff";

/** What the browser is allowed to know about the buyer. No tokens. */
export interface PublicSession {
  authenticated: boolean;
  accountId: string | null;
  displayName: string | null;
}

export const getPublicSession = cache(async (): Promise<PublicSession> => {
  const s = await bff.getSession();
  return s
    ? {
        authenticated: true,
        accountId: s.accountId,
        displayName: s.displayName,
      }
    : { authenticated: false, accountId: null, displayName: null };
});
