import { GenericContainer, Wait, type StartedTestContainer } from 'testcontainers';

/** A real Redis for the BFF session store. Same image the integration tests use. */
export interface HarnessRedis {
  url: string;
  stop(): Promise<void>;
}

export async function startRedis(): Promise<HarnessRedis> {
  // The reaper container needs a registry pull and a socket mount; the harness stops Redis itself.
  process.env.TESTCONTAINERS_RYUK_DISABLED ??= 'true';
  const c: StartedTestContainer = await new GenericContainer('redis:7-alpine')
    .withExposedPorts(6379)
    .withWaitStrategy(Wait.forLogMessage(/Ready to accept connections/))
    .start();
  return { url: `redis://${c.getHost()}:${c.getMappedPort(6379)}`, stop: () => c.stop().then(() => undefined) };
}
