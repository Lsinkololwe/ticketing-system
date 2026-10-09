/**
 * A minimal cookie-keeping "browser" for driving a real Keycloak login form from a test: follows
 * redirects by hand, keeps the Keycloak SSO cookies, and stops at the first redirect that matches `stopAt`
 * (usually the app's callback URL, which the test then hands to the BFF handler).
 */
export class Browser {
  jar = new Map<string, string>();

  private store(res: Response) {
    for (const c of res.headers.getSetCookie()) {
      const [pair] = c.split(';');
      const i = pair.indexOf('=');
      if (/Max-Age=0|expires=Thu, 01 Jan 1970/i.test(c) && pair.slice(i + 1) === '') this.jar.delete(pair.slice(0, i));
      else this.jar.set(pair.slice(0, i), pair.slice(i + 1));
    }
  }

  get cookieHeader() {
    return [...this.jar].map(([k, v]) => `${k}=${v}`).join('; ');
  }

  async go(url: string, init: RequestInit = {}, stopAt?: (loc: string) => boolean): Promise<{ res: Response; location?: string; body: string }> {
    let current = url;
    let method = init.method ?? 'GET';
    let body = init.body;
    for (let hop = 0; hop < 15; hop++) {
      const res = await fetch(current, { method, body, redirect: 'manual', headers: { cookie: this.cookieHeader, ...(init.headers as Record<string, string>) } });
      this.store(res);
      const loc = res.headers.get('location');
      if (res.status >= 300 && res.status < 400 && loc) {
        const abs = new URL(loc, current).toString();
        if (stopAt?.(abs)) return { res, location: abs, body: '' };
        current = abs;
        method = 'GET';
        body = undefined;
        continue;
      }
      return { res, body: await res.text() };
    }
    throw new Error('too many redirects');
  }
}
