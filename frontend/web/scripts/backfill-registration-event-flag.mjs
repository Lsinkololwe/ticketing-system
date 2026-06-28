/**
 * One-time backfill for the UserRegisteredEvent decoupling change.
 *
 * Background
 * ----------
 * `UserRegisteredEvent` is now published exactly once per user, guarded by an
 * atomic compare-and-set on the new `registrationEventPublished` flag (see
 * UserSyncServiceImpl.publishRegistrationIfNeeded). Users that already existed
 * before this change have no flag, so their NEXT registration-type sync
 * (e.g. an UPDATE_PROFILE) would wrongly emit a fresh UserRegisteredEvent.
 *
 * This script marks every existing `users` document as already-published so
 * the event only ever fires for genuinely new registrations going forward.
 *
 * Run ONCE per environment, BEFORE real traffic hits the updated service.
 *
 * Usage
 * -----
 *   node --env-file=apps/organization-admin/.env \
 *     scripts/backfill-registration-event-flag.mjs
 *
 *   # Dry run (default): report only, no writes.
 *   DRY_RUN=true node --env-file=... scripts/backfill-registration-event-flag.mjs
 */
import { MongoClient } from 'mongodb';

const uri = process.env.MONGODB_URI;
const dbName = process.env.MONGODB_DATABASE;
const DRY_RUN = process.env.DRY_RUN === 'true';

if (!uri || !dbName) {
  console.error('[backfill] MONGODB_URI and MONGODB_DATABASE are required');
  process.exit(1);
}

const client = new MongoClient(uri);
await client.connect();
try {
  const users = client.db(dbName).collection('users');
  const filter = { registrationEventPublished: { $ne: true } };
  const pending = await users.countDocuments(filter);
  console.log(`[backfill] users needing the flag: ${pending} (dryRun=${DRY_RUN})`);

  if (!DRY_RUN && pending > 0) {
    const res = await users.updateMany(filter, {
      $set: { registrationEventPublished: true },
    });
    console.log(`[backfill] updated ${res.modifiedCount} document(s).`);
  } else if (DRY_RUN) {
    console.log('[backfill] DRY RUN — no changes written.');
  }
} finally {
  await client.close();
}
