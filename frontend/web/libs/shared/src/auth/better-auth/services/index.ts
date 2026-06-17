/**
 * Better Auth Services
 *
 * SERVER-ONLY: Class-based service implementations.
 *
 * These services implement the interfaces defined in ../interfaces/
 * and use native features of their respective dependencies.
 *
 * @module libs/shared/src/auth/better-auth/services
 */

import 'server-only';

export { JtiBlacklistService } from './JtiBlacklistService';
export { BackchannelLogoutHandler, extractJtiFromIdToken } from './BackchannelLogoutHandler';
