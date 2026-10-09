// @vitest-environment jsdom
import React from "react";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/__tests__/referenceMock')).referenceModule());
vi.mock("@/lib/identity/client", () => ({
  requestChallenge: vi.fn(),
  verifyCode: vi.fn(),
  ensureAccount: vi.fn(),
}));

import * as api from "@/lib/identity/client";
import { IdentifyStep } from "@/components/identify/IdentifyStep";
import { IDENTITY_ERROR_MESSAGES } from "@/lib/identity/messages";

const requestChallenge = vi.mocked(api.requestChallenge);
const verifyCode = vi.mocked(api.verifyCode);
const ensureAccount = vi.mocked(api.ensureAccount);

const challengeOk = (over: Record<string, unknown> = {}) =>
  ({
    ok: true,
    httpStatus: 202,
    challengeId: "cid",
    contactType: "EMAIL",
    maskedContact: "j***@gmail.com",
    channel: "EMAIL",
    expiresInSeconds: 300,
    resendAfterSeconds: 60,
    ...over,
  }) as never;

const fail = (
  errorCode: string,
  extra: Record<string, unknown> = {},
  status = 400,
) => ({ ok: false, status, errorCode, ...extra }) as never;

async function toCodeStep(
  user: ReturnType<typeof userEvent.setup>,
  over: Record<string, unknown> = {},
) {
  requestChallenge.mockResolvedValueOnce(challengeOk(over));
  await user.click(screen.getByRole("radio", { name: "Email" }));
  await user.type(screen.getByTestId("identify-email"), "j@gmail.com");
  await user.click(screen.getByTestId("identify-send"));
  await screen.findByTestId("identify-code-step");
}

const boxes = () =>
  within(screen.getByRole("group", { name: "6 digit code" })).getAllByRole(
    "textbox",
  ) as HTMLInputElement[];
async function typeCode(
  user: ReturnType<typeof userEvent.setup>,
  digits: string,
) {
  await user.click(boxes()[0]);
  await user.keyboard(digits);
}

beforeEach(() => {
  vi.clearAllMocks();
});

describe("IdentifyStep contact step", () => {
  it("validates before calling the server and shows CONTACT_INVALID copy", async () => {
    const user = userEvent.setup();
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await user.click(screen.getByTestId("identify-send"));
    expect(requestChallenge).not.toHaveBeenCalled();
    expect(
      (
        await screen.findAllByText(
          IDENTITY_ERROR_MESSAGES.CONTACT_INVALID.message,
        )
      ).length,
    ).toBeGreaterThan(0);
  });

  it("shows the terms and consent line", () => {
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    expect(screen.getByTestId("identify-terms")).toHaveTextContent(
      /agree to our Terms and Privacy Policy/,
    );
  });

  it("accepts a WhatsApp number in E.164", async () => {
    const user = userEvent.setup();
    requestChallenge.mockResolvedValueOnce(
      challengeOk({
        contactType: "WHATSAPP",
        maskedContact: "+260 97* ***567",
      }),
    );
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await user.type(
      screen.getByRole("textbox", { name: /WhatsApp number/ }),
      "0971234567",
    );
    await user.click(screen.getByTestId("identify-send"));
    await screen.findByTestId("identify-code-step");
    expect(requestChallenge).toHaveBeenCalledWith(
      "+260971234567",
      "WHATSAPP",
      "ZM",
    );
  });

  it.each([
    ["CONTACT_INVALID"],
    ["OTP_RATE_LIMITED"],
    ["OTP_COOLDOWN_ACTIVE"],
    ["OTP_LOCKED"],
    ["OTP_DELIVERY_FAILED"],
  ])("maps challenge error %s to its message", async (code) => {
    const user = userEvent.setup();
    requestChallenge.mockResolvedValueOnce(fail(code, {}, 429));
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await user.click(screen.getByRole("radio", { name: "Email" }));
    await user.type(screen.getByTestId("identify-email"), "j@gmail.com");
    await user.click(screen.getByTestId("identify-send"));
    expect(await screen.findByTestId("identify-error")).toHaveTextContent(
      IDENTITY_ERROR_MESSAGES[code].message,
    );
  });
});

describe("IdentifyStep code step", () => {
  it("reads back the masked contact, uses one-time-code autofill, and Change returns to contact", async () => {
    const user = userEvent.setup();
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await toCodeStep(user);
    expect(screen.getByTestId("identify-readback")).toHaveTextContent(
      "j***@gmail.com",
    );
    const input = boxes()[0];
    expect(boxes()).toHaveLength(6);
    expect(input.getAttribute("autocomplete")).toBe("one-time-code");
    expect(input.getAttribute("inputmode")).toBe("numeric");
    await user.click(screen.getByTestId("identify-change"));
    expect(screen.getByTestId("identify-contact-step")).toBeInTheDocument();
  });

  it("counts down expiry and disables resend until resendAfterSeconds", async () => {
    const user = userEvent.setup();
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await toCodeStep(user, { expiresInSeconds: 300, resendAfterSeconds: 60 });
    expect(screen.getByTestId("identify-timer")).toHaveTextContent(
      "Expires in 05:00",
    );
    expect(screen.getByTestId("identify-resend")).toBeDisabled();
    expect(screen.getByTestId("identify-resend")).toHaveTextContent(
      /Resend code in 6\d?s|Resend code in 5\ds/,
    );
  });

  it("enables resend once the wait is over and requests a new code", async () => {
    const user = userEvent.setup();
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await toCodeStep(user, { resendAfterSeconds: 0 });
    requestChallenge.mockResolvedValueOnce(
      challengeOk({ challengeId: "cid2" }),
    );
    await user.click(screen.getByTestId("identify-resend"));
    await waitFor(() => expect(requestChallenge).toHaveBeenCalledTimes(2));
  });

  it("shows attempts remaining after OTP_INVALID and clears the field", async () => {
    const user = userEvent.setup();
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await toCodeStep(user);
    verifyCode.mockResolvedValueOnce(
      fail("OTP_INVALID", { attemptsRemaining: 3 }),
    );
    await typeCode(user, "000000");
    expect(await screen.findByTestId("identify-attempts")).toHaveTextContent(
      "3 tries left",
    );
    expect(screen.getByTestId("identify-error")).toHaveTextContent(
      IDENTITY_ERROR_MESSAGES.OTP_INVALID.message,
    );
    expect(boxes().every((b) => b.value === "")).toBe(true);
  });

  it("locks the input on OTP_LOCKED", async () => {
    const user = userEvent.setup();
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await toCodeStep(user);
    verifyCode.mockResolvedValueOnce(
      fail("OTP_LOCKED", { lockedUntil: "2026-10-04T10:00:00Z" }, 423),
    );
    await typeCode(user, "111111");
    expect(await screen.findByTestId("identify-error")).toHaveTextContent(
      IDENTITY_ERROR_MESSAGES.OTP_LOCKED.message,
    );
    expect(boxes().every((b) => b.disabled)).toBe(true);
  });

  it("shows OTP_EXPIRED copy and keeps the resend path", async () => {
    const user = userEvent.setup();
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await toCodeStep(user);
    verifyCode.mockResolvedValueOnce(fail("OTP_EXPIRED", {}, 410));
    await typeCode(user, "222222");
    expect(await screen.findByTestId("identify-error")).toHaveTextContent(
      IDENTITY_ERROR_MESSAGES.OTP_EXPIRED.message,
    );
    expect(screen.getByTestId("identify-resend")).toBeInTheDocument();
  });

  it.each(["PROOF_INVALID", "LOGIN_HANDLE_INVALID"])(
    "%s after verify sends the buyer back to the contact step",
    async (code) => {
      const user = userEvent.setup();
      render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
      await toCodeStep(user);
      verifyCode.mockResolvedValueOnce({
        ok: true,
        httpStatus: 200,
        verified: true,
        maskedContact: "x",
        expiresInSeconds: 120,
      } as never);
      ensureAccount.mockResolvedValueOnce(fail(code));
      await typeCode(user, "333333");
      await screen.findByTestId("identify-contact-step");
      expect(screen.getByTestId("identify-error")).toHaveTextContent(
        IDENTITY_ERROR_MESSAGES[code].message,
      );
    },
  );

  it.each(["ACCOUNT_SUSPENDED", "ACCOUNT_MERGING"])(
    "%s is shown with its own copy",
    async (code) => {
      const user = userEvent.setup();
      render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
      await toCodeStep(user);
      verifyCode.mockResolvedValueOnce({
        ok: true,
        httpStatus: 200,
        verified: true,
        maskedContact: "x",
        expiresInSeconds: 120,
      } as never);
      ensureAccount.mockResolvedValueOnce(fail(code, {}, 409));
      await typeCode(user, "444444");
      expect(await screen.findByTestId("identify-error")).toHaveTextContent(
        IDENTITY_ERROR_MESSAGES[code].message,
      );
    },
  );
});

describe("IdentifyStep welcome and provisioning", () => {
  const verifiedOk = {
    ok: true,
    httpStatus: 200,
    verified: true,
    maskedContact: "x",
    expiresInSeconds: 120,
  } as never;

  it.each([
    [true, "We created your free account"],
    [false, "Welcome back"],
  ])(
    "isNew=%s shows %s then saves the cart and signs in",
    async (isNew, text) => {
      const user = userEvent.setup();
      const navigate = vi.fn();
      const order: string[] = [];
      const before = vi.fn(async () => void order.push("cart"));
      navigate.mockImplementation(() => order.push("navigate"));
      render(
        <IdentifyStep
          returnTo="/events/e1/book"
          navigate={navigate}
          onBeforeRedirect={before}
          welcomeDelayMs={60_000}
        />,
      );
      await toCodeStep(user);
      verifyCode.mockResolvedValueOnce(verifiedOk);
      ensureAccount.mockResolvedValueOnce({
        ok: true,
        httpStatus: 200,
        status: "ACTIVE",
        isNew,
      } as never);
      await typeCode(user, "555555");
      expect(await screen.findByTestId("identify-welcome")).toHaveTextContent(
        text,
      );
      await user.click(screen.getByTestId("identify-continue"));
      await waitFor(() =>
        expect(navigate).toHaveBeenCalledWith(
          "/api/auth/start?next=%2Fevents%2Fe1%2Fbook",
        ),
      );
      expect(order).toEqual(["cart", "navigate"]);
    },
  );

  it("202 PROVISIONING says it is taking longer and retries politely after retryAfterSeconds", async () => {
    const user = userEvent.setup();
    render(
      <IdentifyStep returnTo="/" navigate={vi.fn()} welcomeDelayMs={60_000} />,
    );
    await toCodeStep(user);
    verifyCode.mockResolvedValueOnce(verifiedOk);
    ensureAccount.mockResolvedValueOnce({
      ok: true,
      httpStatus: 202,
      status: "PROVISIONING",
      retryAfterSeconds: 1,
    } as never);
    ensureAccount.mockResolvedValueOnce({
      ok: true,
      httpStatus: 200,
      status: "ACTIVE",
      isNew: true,
    } as never);
    await typeCode(user, "666666");
    expect(
      await screen.findByTestId("identify-provisioning"),
    ).toHaveTextContent(/taking longer than usual/);
    expect(
      await screen.findByTestId("identify-welcome", undefined, {
        timeout: 3000,
      }),
    ).toBeInTheDocument();
    expect(ensureAccount).toHaveBeenCalledTimes(2);
  });
});

describe("code input digits only", () => {
  it("strips non digits", async () => {
    const user = userEvent.setup();
    render(<IdentifyStep returnTo="/" navigate={vi.fn()} />);
    await toCodeStep(user);
    await user.click(boxes()[0]);
    await user.paste("12ab3");
    expect(
      boxes()
        .map((b) => b.value)
        .join(""),
    ).toBe("123");
  });
});
