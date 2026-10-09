import { defineConfig } from "vitest/config";
import base from "./vitest.config";

/** Opt-in integration tests (Docker): `BUYER_IT=1 npx vitest run -c apps/ticketing/vitest.it.config.ts`. */
export default defineConfig({
  ...base,
  test: {
    ...base.test,
    include: ["src/__integration__/**/*.it.ts"],
    setupFiles: [],
    testTimeout: 240_000,
    hookTimeout: 300_000,
    fileParallelism: false,
  },
});
