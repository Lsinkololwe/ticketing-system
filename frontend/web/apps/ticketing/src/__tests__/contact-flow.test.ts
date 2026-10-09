import { describe, expect, it } from "vitest";
import {
  flowReducer,
  initialFlow,
  type FlowState,
} from "@/components/contacts/flow";

const ch = {
  challengeId: "c",
  maskedContact: "j***@x.com",
  contactType: "EMAIL" as const,
  expiresInSeconds: 300,
  resendAfterSeconds: 60,
};
const inCode = (): FlowState =>
  flowReducer(flowReducer(initialFlow("add"), { type: "send" }), {
    type: "challenge",
    challenge: ch,
  });
const NOW = Date.parse("2026-10-04T10:00:00Z");

describe("contact flow state machine", () => {
  it("goes input -> sending -> code -> verifying -> done", () => {
    let s = initialFlow("add");
    expect(s.phase).toBe("input");
    s = flowReducer(s, { type: "send" });
    expect(s.phase).toBe("sending");
    s = flowReducer(s, { type: "challenge", challenge: ch });
    expect(s.phase).toBe("code");
    s = flowReducer(s, { type: "verify" });
    expect(s.phase).toBe("verifying");
    expect(flowReducer(s, { type: "done" }).phase).toBe("done");
  });

  it("wrong code keeps the challenge and reports attempts left", () => {
    const s = flowReducer(flowReducer(inCode(), { type: "verify" }), {
      type: "failure",
      error: { code: "OTP_INVALID", attemptsRemaining: 3 },
    });
    expect(s).toMatchObject({ phase: "code", attemptsLeft: 3 });
    expect(s.challenge).toBe(ch);
  });

  it("lock computes the retry time from lockedUntil and keeps the code step", () => {
    const s = flowReducer(
      flowReducer(inCode(), { type: "verify" }),
      {
        type: "failure",
        error: { code: "OTP_LOCKED", lockedUntil: "2026-10-04T10:15:00Z" },
      },
      NOW,
    );
    expect(s.phase).toBe("code");
    expect(s.lockedSeconds).toBe(900);
  });

  it("expired code sends the buyer back to input with no challenge", () => {
    const s = flowReducer(flowReducer(inCode(), { type: "verify" }), {
      type: "failure",
      error: { code: "OTP_EXPIRED" },
    });
    expect(s).toMatchObject({ phase: "input", challenge: null });
  });

  it("a lost claim at confirm returns to input (neutral error code kept)", () => {
    const s = flowReducer(flowReducer(inCode(), { type: "verify" }), {
      type: "failure",
      error: { code: "CONTACT_ALREADY_CLAIMED" },
    });
    expect(s).toMatchObject({
      phase: "input",
      challenge: null,
      error: { code: "CONTACT_ALREADY_CLAIMED" },
    });
  });

  it("a claimed contact at send time stays on input", () => {
    const s = flowReducer(flowReducer(initialFlow("add"), { type: "send" }), {
      type: "failure",
      error: { code: "CONTACT_ALREADY_CLAIMED" },
    });
    expect(s.phase).toBe("input");
  });

  it("last verified contact blocks removal from the start and after a server refusal", () => {
    expect(initialFlow("remove", "LAST_VERIFIED_CONTACT").phase).toBe(
      "blocked",
    );
    const s = flowReducer(
      flowReducer(initialFlow("remove"), { type: "send" }),
      { type: "failure", error: { code: "LAST_VERIFIED_CONTACT" } },
    );
    expect(s.phase).toBe("blocked");
  });

  it("pending (202) enters the pending phase with a minimum 1s retry", () => {
    const s = flowReducer(flowReducer(inCode(), { type: "verify" }), {
      type: "pending",
      retryAfterSeconds: 0,
    });
    expect(s).toMatchObject({ phase: "pending", pendingRetry: 1 });
  });

  it("network failure while verifying stays on the code step", () => {
    const s = flowReducer(flowReducer(inCode(), { type: "verify" }), {
      type: "failure",
      error: { code: "SERVICE_UNAVAILABLE" },
    });
    expect(s.phase).toBe("code");
  });
});
