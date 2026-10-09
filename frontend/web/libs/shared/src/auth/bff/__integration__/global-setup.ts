import type { TestProject } from 'vitest/node';

declare module 'vitest' {
  export interface ProvidedContext {
    redisUrl: string;
  }
}

/** Starts redis:7-alpine with testcontainers (or reuses BFF_TEST_REDIS_URL). */
export default async function setup(project: TestProject) {
  if (process.env.BFF_TEST_REDIS_URL) {
    project.provide('redisUrl', process.env.BFF_TEST_REDIS_URL);
    return;
  }
  const { GenericContainer, Wait } = await import('testcontainers');
  const container = await new GenericContainer('redis:7-alpine')
    .withExposedPorts(6379)
    .withWaitStrategy(Wait.forLogMessage(/Ready to accept connections/))
    .start();
  project.provide('redisUrl', `redis://${container.getHost()}:${container.getMappedPort(6379)}`);
  return async () => {
    await container.stop();
  };
}
