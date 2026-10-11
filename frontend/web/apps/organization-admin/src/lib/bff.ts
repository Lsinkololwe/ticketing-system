import 'server-only';
import { createBff } from '@pml.tickets/shared/auth/bff';
import { CSP_OPTIONS, organizerBffConfig } from './bff.config';

export { FRESH_AUTH_SEC } from './bff.config';

/**
 * `next build` imports route modules to collect page data without any runtime secrets. Config is
 * validated eagerly, so during that phase only (never at runtime) inert placeholders stand in.
 * Redis is connected lazily on first request, so nothing here is contacted.
 */
const building = process.env.NEXT_PHASE === 'phase-production-build';
const env = building
  ? {
      APP_URL: 'https://build.invalid',
      KEYCLOAK_ISSUER: 'https://build.invalid/realms/build',
      KEYCLOAK_CLIENT_ID: 'build',
      KEYCLOAK_CLIENT_SECRET: 'build',
      BFF_ENC_KEYS: `build:${Buffer.alloc(32).toString('base64')}`,
      ...Object.fromEntries(Object.entries(process.env).filter(([, v]) => v)),
    }
  : process.env;
if (building && !env.APP_URL?.startsWith('https://')) env.APP_URL = 'https://build.invalid';

/** Organizer BFF: sessions, tokens and refresh live server-side; the browser only holds an opaque cookie. */
export const bff = createBff(organizerBffConfig(env), { csp: CSP_OPTIONS });
