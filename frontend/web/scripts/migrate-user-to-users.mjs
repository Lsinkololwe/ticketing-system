/**
 * One-time migration: collapse Better Auth's legacy `user` collection into the
 * single shared `users` collection keyed by the Keycloak `sub`.
 *
 * Background
 * ----------
 * Better Auth previously wrote to its default `user` collection with a
 * Better-Auth-generated random `_id`, while the backend business logic uses the
 * `users` collection keyed by the Keycloak `sub`. After the AuthContainer change,
 * Better Auth now writes to `users` with `_id = sub`. This script migrates the
 * pre-existing `user` documents so nothing is orphaned.
 *
 * What it does (idempotent)
 * -------------------------
 *  1. For each legacy `user` doc, resolve the Keycloak `sub` from its linked
 *     `account` (providerId = 'keycloak', accountId = sub).
 *  2. Upsert into `users` with `_id = sub`, copying Better-Auth-owned fields
 *     (email, name, emailVerified, image, createdAt, updatedAt) and keycloakId.
 *     Business fields already present on an existing `users` doc are preserved.
 *  3. Re-point `account.userId` and `session.userId` from the legacy id to `sub`.
 *  4. Optionally drop the legacy `user` collection (only when DROP_LEGACY_USER=true).
 *
 * Usage
 * -----
 *   MONGODB_URI=... MONGODB_DATABASE=dev_ticketing \
 *     node scripts/migrate-user-to-users.mjs
 *
 *   # Dry run (default): logs intended changes without writing.
 *   DRY_RUN=true MONGODB_URI=... MONGODB_DATABASE=... node scripts/migrate-user-to-users.mjs
 *
 *   # After verifying, drop the legacy collection:
 *   DROP_LEGACY_USER=true MONGODB_URI=... MONGODB_DATABASE=... node scripts/migrate-user-to-users.mjs
 *
 * SAFETY: take a backup / run against a copy first. `_id` is immutable, so this
 * migration inserts new `users` docs and rewrites references rather than mutating
 * `_id` in place.
 */

import { MongoClient } from 'mongodb';

const MONGODB_URI = process.env.MONGODB_URI;
const MONGODB_DATABASE = process.env.MONGODB_DATABASE;
const KEYCLOAK_PROVIDER_ID = process.env.KEYCLOAK_PROVIDER_ID || 'keycloak';
const DRY_RUN = process.env.DRY_RUN !== 'false'; // default to dry run for safety
const DROP_LEGACY_USER = process.env.DROP_LEGACY_USER === 'true';

// Better-Auth-owned core fields to copy from the legacy `user` doc onto the
// shared `users` doc. Business fields (username, firstName, ...) are owned by the
// keycloak-extensions sync and are intentionally NOT overwritten here.
const BETTER_AUTH_FIELDS = [
  'email',
  'name',
  'emailVerified',
  'image',
  'createdAt',
  'updatedAt',
  'keycloakId',
];

function requireEnv(name, value) {
  if (!value) {
    console.error(`[migrate] Missing required env var: ${name}`);
    process.exit(1);
  }
}

async function main() {
  requireEnv('MONGODB_URI', MONGODB_URI);
  requireEnv('MONGODB_DATABASE', MONGODB_DATABASE);

  console.log(
    `[migrate] db=${MONGODB_DATABASE} provider=${KEYCLOAK_PROVIDER_ID} ` +
      `dryRun=${DRY_RUN} dropLegacy=${DROP_LEGACY_USER}`,
  );

  const client = new MongoClient(MONGODB_URI);
  await client.connect();
  try {
    const db = client.db(MONGODB_DATABASE);
    const legacyUsers = db.collection('user');
    const users = db.collection('users');
    const accounts = db.collection('account');
    const sessions = db.collection('session');

    const legacyCount = await legacyUsers.countDocuments();
    console.log(`[migrate] legacy 'user' documents: ${legacyCount}`);

    let migrated = 0;
    let skippedNoSub = 0;
    let reaccountedAccounts = 0;
    let reaccountedSessions = 0;

    const cursor = legacyUsers.find({});
    for await (const legacy of cursor) {
      const legacyId = legacy._id;

      // 1. Resolve the Keycloak sub from the linked account.
      const kcAccount = await accounts.findOne({
        userId: legacyId,
        providerId: KEYCLOAK_PROVIDER_ID,
      });
      const sub = kcAccount?.accountId || legacy.keycloakId;

      if (!sub) {
        console.warn(
          `[migrate] SKIP user ${String(legacyId)} (email=${legacy.email}) — no Keycloak sub found`,
        );
        skippedNoSub++;
        continue;
      }

      if (String(sub) === String(legacyId)) {
        // Already keyed by sub (re-run / already migrated).
        continue;
      }

      // 2. Build the $set of Better-Auth-owned fields for the shared `users` doc.
      const setFields = { keycloakId: sub };
      for (const f of BETTER_AUTH_FIELDS) {
        if (legacy[f] !== undefined) setFields[f] = legacy[f];
      }

      console.log(
        `[migrate] user ${String(legacyId)} -> users/_id=${sub} (email=${legacy.email})`,
      );

      if (!DRY_RUN) {
        // Upsert into `users`, preserving any business fields already populated
        // there by the keycloak-extensions sync ($set only the BA-owned fields).
        await users.updateOne(
          { _id: sub },
          { $set: setFields },
          { upsert: true },
        );

        // 3a. Re-point account.userId for this user's accounts.
        const accRes = await accounts.updateMany(
          { userId: legacyId },
          { $set: { userId: sub } },
        );
        reaccountedAccounts += accRes.modifiedCount;

        // 3b. Re-point session.userId for this user's sessions.
        const sesRes = await sessions.updateMany(
          { userId: legacyId },
          { $set: { userId: sub } },
        );
        reaccountedSessions += sesRes.modifiedCount;

        // Remove the legacy user doc now that it has been migrated.
        await legacyUsers.deleteOne({ _id: legacyId });
      }
      migrated++;
    }

    console.log(
      `[migrate] done. migrated=${migrated} skippedNoSub=${skippedNoSub} ` +
        `accountsRepointed=${reaccountedAccounts} sessionsRepointed=${reaccountedSessions}`,
    );

    if (DROP_LEGACY_USER) {
      const remaining = await legacyUsers.countDocuments();
      if (remaining > 0) {
        console.warn(
          `[migrate] NOT dropping 'user' — ${remaining} doc(s) still present (resolve skips first).`,
        );
      } else if (DRY_RUN) {
        console.log(`[migrate] (dry run) would drop empty 'user' collection.`);
      } else {
        await legacyUsers.drop().catch((e) => {
          console.warn(`[migrate] drop 'user' failed: ${e.message}`);
        });
        console.log(`[migrate] dropped legacy 'user' collection.`);
      }
    }

    if (DRY_RUN) {
      console.log(`[migrate] DRY RUN — no changes were written. Set DRY_RUN=false to apply.`);
    }
  } finally {
    await client.close();
  }
}

main().catch((err) => {
  console.error('[migrate] fatal:', err);
  process.exit(1);
});
