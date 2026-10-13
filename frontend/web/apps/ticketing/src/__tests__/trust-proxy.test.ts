import { afterEach, beforeEach, describe, expect, it } from "vitest";
import { createBff } from "@pml.tickets/shared/auth/bff";
import { MemoryStore } from "@pml.tickets/shared/auth/bff/store/memory";
import { setupServerEnv } from "@/test/server";
import { buyerBffConfig } from "@/lib/bff";

/**
 * Behind a proxy every visitor would share one 'unknown' rate-limit bucket unless the app is told how many
 * proxy hops to trust. Production must say so; local runs and tests default to 0.
 */
const production = (over: Record<string, unknown> = {}) => ({
  ...buyerBffConfig({ production: true, appUrl: "https://tickets.example.com" }),
  redis: { store: new MemoryStore(Date.now) },
  ...over,
});

describe("TRUST_PROXY_HOPS", () => {
  beforeEach(() => {
    setupServerEnv();
  });
  afterEach(() => {
    process.env.TRUST_PROXY_HOPS = "1";
  });

  it("the app refuses to start in production when it is unset", () => {
    delete process.env.TRUST_PROXY_HOPS;
    expect(() => createBff(production())).toThrow(/TRUST_PROXY_HOPS/);
  });

  it("a blank value counts as unset", () => {
    process.env.TRUST_PROXY_HOPS = "  ";
    expect(() => createBff(production())).toThrow(/TRUST_PROXY_HOPS/);
  });

  it("starts in production once it is set, including an explicit 0", () => {
    process.env.TRUST_PROXY_HOPS = "0";
    expect(createBff(production()).config.trustProxyHops).toBe(0);
    process.env.TRUST_PROXY_HOPS = "2";
    expect(createBff(production()).config.trustProxyHops).toBe(2);
  });

  it("defaults to 0 outside production", () => {
    delete process.env.TRUST_PROXY_HOPS;
    const cfg = buyerBffConfig({ production: false });
    expect(createBff({ ...cfg, redis: { store: new MemoryStore(Date.now) } }).config.trustProxyHops).toBe(0);
  });

  it("rejects a value that is not a whole number of hops", () => {
    process.env.TRUST_PROXY_HOPS = "lots";
    expect(() => buyerBffConfig()).toThrow(/TRUST_PROXY_HOPS/);
  });
});
