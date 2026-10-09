import { defineConfig, devices, test as base, type PlaywrightTestConfig } from '@playwright/test';
import { startFakeUpstream, type FakeUpstream } from './fake-upstream';
import { startRedis, type HarnessRedis } from './redis';
import { createSeeder, type SeedOptions, type Seeder } from './session';
import { APPS, startApp, type RunningApp } from './app-server';
import { signIn, VIEWPORTS } from './helpers';

export * from './fake-upstream';
export * from './helpers';
export type { SeedOptions } from './session';

/**
 * Playwright config for one app. The browser is system Chrome (PW_CHANNEL=chrome, default
 * chrome). Two projects: desktop 1440x900 and phone 390x844. One worker: `next dev` compiles
 * routes on first touch and all tests of a worker share one app + Redis + fake upstream.
 *
 * The app, Redis and the upstream are started by worker fixtures (not `webServer`) because the
 * app's env needs the free port and the upstream URL.
 */
export function defineHarnessConfig(o: { app: keyof typeof APPS; testDir: string; testMatch?: string }): PlaywrightTestConfig {
  const channel = process.env.PW_CHANNEL ?? 'chrome';
  return defineConfig({
    testDir: o.testDir,
    testMatch: o.testMatch ?? '**/*.spec.ts',
    workers: 1,
    fullyParallel: false,
    timeout: 90_000,
    expect: { timeout: 15_000 },
    reporter: [['list']],
    use: { ...devices['Desktop Chrome'], channel, trace: 'off', ignoreHTTPSErrors: true },
    projects: [
      { name: 'desktop', metadata: { app: o.app }, use: { ...devices['Desktop Chrome'], channel, viewport: VIEWPORTS.desktop, ignoreHTTPSErrors: true } },
      { name: 'phone', metadata: { app: o.app }, use: { ...devices['Desktop Chrome'], channel, viewport: VIEWPORTS.phone, hasTouch: true, ignoreHTTPSErrors: true } },
    ],
    // The app target travels in project metadata; see `harnessTest`.
  });
}

interface Stack {
  upstream: FakeUpstream;
  redis: HarnessRedis;
  app: RunningApp;
  seeder: Seeder;
}

/**
 * `test` with:
 *  - `stack` (worker): redis + fake upstream + the app, started once;
 *  - `upstream` (test): the fake upstream, reset before every test;
 *  - `signInAs(opts)` (test): seeds a session and sets its cookie on the page's context;
 *  - `baseURL` pointing at the app.
 */
export const harnessTest = base.extend<
  { upstream: FakeUpstream; signInAs: (o?: SeedOptions) => Promise<void> },
  { stack: Stack; harnessApp: string }
>({
  harnessApp: [async ({}, use, workerInfo) => use(String(workerInfo.project.metadata?.app ?? 'ticketing')), { scope: 'worker' }],
  stack: [
    async ({ harnessApp }, use) => {
      const target = APPS[harnessApp];
      if (!target) throw new Error(`unknown harness app ${harnessApp}`);
      const upstream = await startFakeUpstream();
      const redis = await startRedis();
      const app = await startApp(target, { upstreamUrl: upstream.url, redisUrl: redis.url });
      const seeder = await createSeeder(target.bff, redis.url, app.url);
      try {
        await use({ upstream, redis, app, seeder });
      } finally {
        await seeder.close().catch(() => undefined);
        await app.stop();
        await redis.stop();
        await upstream.close();
      }
    },
    { scope: 'worker', timeout: 360_000 },
  ],
  baseURL: async ({ stack }, use) => use(stack.app.url),
  upstream: async ({ stack }, use) => {
    stack.upstream.reset();
    await use(stack.upstream);
  },
  signInAs: async ({ stack, context }, use) => {
    await use(async (o) => signIn(context, await stack.seeder.seed(o)));
  },
});
export const expect = harnessTest.expect;
