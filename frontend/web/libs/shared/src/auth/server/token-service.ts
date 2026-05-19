/**
 * Token Service for Next.js Apps
 *
 * Apps register their token getter (e.g., from Keycloak's useKeycloak().getToken)
 * Shared code uses it via getAuthHeader()
 */

type TokenGetter = () => Promise<string | null>;

let tokenGetter: TokenGetter | null = null;

/**
 * Register token getter from app (e.g., getToken from useKeycloak())
 * Should be called once at app initialization
 */
export const registerTokenGetter = (getter: TokenGetter) => {
  tokenGetter = getter;
};

/**
 * Get auth token from registered getter
 */
const getToken = async (): Promise<string | null> => {
  if (!tokenGetter) {
    console.warn('No token getter registered. Call registerTokenGetter() at app initialization.');
    return null;
  }
  try {
    return await tokenGetter();
  } catch (error) {
    console.error('Failed to get token:', error);
    return null;
  }
};

/**
 * Redirect to logout page
 */
export const redirectToLogout = () => {
  if (typeof globalThis !== 'undefined' && typeof (globalThis as any).window !== 'undefined') {
    try { ((globalThis as any).window as any).location.href = '/logout'; } catch { /* noop */ }
  }
};

/**
 * Get auth header with Bearer token
 * Redirects to /logout if token is missing or invalid
 */
export const getAuthHeader = async (base?: Record<string, string>): Promise<Record<string, string>> => {
  const token = await getToken();
  if (!token) {
    redirectToLogout();
    throw new Error('No valid token available');
  }
  return { ...(base || {}), Authorization: `Bearer ${token}` };
};
