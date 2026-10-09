"use client";

import React, { createContext, useCallback, useContext, useMemo } from "react";

/**
 * What the browser knows about the buyer. Seeded by the server layout from the server-side
 * session; contains no tokens.
 */
export interface BuyerSession {
  authenticated: boolean;
  accountId: string | null;
  displayName: string | null;
}

const Ctx = createContext<BuyerSession>({
  authenticated: false,
  accountId: null,
  displayName: null,
});

export function BuyerSessionProvider({
  session,
  children,
}: {
  session: BuyerSession;
  children: React.ReactNode;
}) {
  return <Ctx.Provider value={session}>{children}</Ctx.Provider>;
}

export function useBuyerAuth() {
  const s = useContext(Ctx);
  // The BFF answers the POST with a 303 chain through Keycloak's end-session endpoint, so it
  // must be a real navigation (a fetch cannot follow it cross-origin).
  const logout = useCallback(async () => {
    const form = document.createElement("form");
    form.method = "POST";
    form.action = "/api/auth/logout";
    document.body.appendChild(form);
    form.submit();
  }, []);

  return useMemo(() => {
    const [givenName = "", ...rest] = (s.displayName ?? "").split(" ");
    return {
      authenticated: s.authenticated,
      loading: false,
      user: s.authenticated
        ? {
            id: s.accountId as string,
            givenName,
            familyName: rest.join(" "),
            email: "",
          }
        : null,
      logout,
    };
  }, [s, logout]);
}
