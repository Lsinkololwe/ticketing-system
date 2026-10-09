import 'server-only';
import { createBff } from '@pml.tickets/shared/auth/bff';
import { adminBffConfig, adminCsp } from '@/lib/bffConfig';

export { STAFF_ACCESS_ROLES, STEP_UP_SEC } from '@/lib/bffConfig';

// `next build` imports route modules to collect page data without runtime secrets. The placeholder is
// used for that phase only; at runtime every variable must be present or the process refuses to start.
const building = process.env.NEXT_PHASE === 'phase-production-build';
const required = (name: string): string => {
  // The build is not the deployment: a local http APP_URL must not trip the production https rule.
  if (building) return name === 'APP_URL' || name === 'KEYCLOAK_ISSUER' ? 'https://build.invalid' : process.env[name] || 'build-placeholder';
  const v = process.env[name];
  if (v) return v;
  throw new Error(`Missing required environment variable ${name}`);
};

export const bff = createBff(adminBffConfig(process.env, required), { csp: adminCsp(required('KEYCLOAK_ISSUER')) });
