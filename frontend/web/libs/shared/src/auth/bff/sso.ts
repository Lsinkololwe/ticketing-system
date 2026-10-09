import type { BffDeps } from './context';
import type { SessionRecord } from './session';

/**
 * Ends the Keycloak SSO session behind an app session, so the next sign-in starts a new one.
 *
 * <p>Keycloak keeps one session per browser and its id (`sid`) is shared by every app signed in through it.
 * Revoking that id at identity-service when someone signs out, while leaving the SSO session alive, turns every
 * later sign-in into a refused one: Keycloak reissues tokens carrying the same revoked id. So whenever a session
 * is revoked or ended, the SSO session has to end with it.</p>
 *
 * <p>This is a server-to-server call with the ID token as the hint, deliberately not a browser redirect: a
 * redirect only works if the browser follows it and the app still holds a session, and the revoked-token path
 * has neither guarantee. The refresh token is revoked afterwards (best effort) because revoking it first can
 * remove the session the end-session call needs to find.</p>
 *
 * @returns whether Keycloak accepted the end-session request
 */
export async function endSso(deps: BffDeps, record: Pick<SessionRecord, 'idToken' | 'refreshToken'>, cid: string): Promise<boolean> {
  const log = deps.cfg.logger;
  let ended = false;
  if (record.idToken) {
    ended = await deps.oidc.endSessionServerSide(record.idToken).catch((err) => {
      log.warn('auth.sso_end_failed', { cid, err });
      return false;
    });
  }
  if (record.refreshToken) {
    await deps.oidc.revokeRefreshToken(record.refreshToken).catch((err) => log.warn('auth.logout.kc_revoke_failed', { cid, err }));
  }
  return ended;
}
