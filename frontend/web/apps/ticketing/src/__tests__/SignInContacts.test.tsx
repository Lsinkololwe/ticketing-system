// @vitest-environment jsdom
import React from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { act, render, screen, waitFor, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";

vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async () => (await import('@/__tests__/referenceMock')).referenceModule());
vi.mock("@/lib/contacts/client", () => ({
  listContacts: vi.fn(),
  requestAdd: vi.fn(),
  confirmAdd: vi.fn(),
  requestChange: vi.fn(),
  confirmChange: vi.fn(),
  requestRemoval: vi.fn(),
  confirmRemoval: vi.fn(),
  requestPrimary: vi.fn(),
  confirmPrimary: vi.fn(),
  cancelChange: vi.fn(),
  resendCode: vi.fn(),
}));

import * as api from "@/lib/contacts/client";
import { SignInContacts } from "@/components/contacts/SignInContacts";
import { CONTACT_ERRORS } from "@/lib/contacts/messages";

const m = vi.mocked(api);
const wa = {
  id: "c-wa",
  type: "WHATSAPP",
  valueMasked: "+260 97* ***123",
  verifiedAt: "2026-10-01T00:00:00Z",
  primary: true,
};
const em = {
  id: "c-em",
  type: "EMAIL",
  valueMasked: "j***@gmail.com",
  verifiedAt: "2026-10-02T00:00:00Z",
  primary: false,
};
const ok = (contacts: unknown[], pendingChange: unknown = null) =>
  ({ ok: true, httpStatus: 200, contacts, pendingChange }) as never;
const fail = (
  errorCode: string,
  extra: Record<string, unknown> = {},
  status = 400,
) => ({ ok: false, status, errorCode, ...extra }) as never;
const challenge = (over: Record<string, unknown> = {}) =>
  ({
    ok: true,
    httpStatus: 200,
    challengeId: "cid",
    maskedContact: "n***@x.com",
    contactType: "EMAIL",
    expiresInSeconds: 300,
    resendAfterSeconds: 60,
    ...over,
  }) as never;
const done = {
  ok: true,
  httpStatus: 200,
  changeId: "c",
  kind: "ADD",
  status: "COMPLETED",
} as never;

beforeEach(() => vi.clearAllMocks());

/** The 6-digit code widgets in the open dialog: index 0 is the main code, 1 the current-primary code. */
const otpBoxes = (i = 0) => {
  const groups = screen
    .getAllByRole("group")
    .filter((g) => /6 digit code/.test(g.getAttribute("aria-label") ?? ""));
  return within(groups[i]).getAllByRole("textbox") as HTMLInputElement[];
};
async function typeOtp(
  user: ReturnType<typeof userEvent.setup>,
  digits: string,
  i = 0,
) {
  await waitFor(() => expect(otpBoxes(i).length).toBe(6));
  await user.click(otpBoxes(i)[0]);
  await user.keyboard(digits);
}

async function openAddEmail(
  user: ReturnType<typeof userEvent.setup>,
  over: Record<string, unknown> = {},
) {
  m.listContacts.mockResolvedValue(ok([wa]));
  render(<SignInContacts navigate={vi.fn()} />);
  await user.click(await screen.findByTestId("contact-add-email"));
  await user.type(screen.getByTestId("contact-flow-email"), "n@x.com");
  m.requestAdd.mockResolvedValueOnce(challenge(over));
  await user.click(screen.getByTestId("contact-flow-send"));
  await screen.findByTestId("contact-flow-code-step");
}

describe("SignInContacts list", () => {
  it("shows masked value, kind, verified, primary and pending change", async () => {
    m.listContacts.mockResolvedValue(
      ok([wa, em], {
        changeId: "chg-1",
        kind: "CHANGE",
        newContactMasked: "+260 96* ***999",
        expiresAt: "2026-10-06T10:00:00Z",
        currentContactVerified: true,
        attemptsRemaining: 4,
      }),
    );
    render(<SignInContacts navigate={vi.fn()} />);
    const row = await screen.findByTestId("contact-c-wa");
    expect(within(row).getByText("+260 97* ***123")).toBeInTheDocument();
    expect(within(row).getByText("WhatsApp")).toBeInTheDocument();
    expect(within(row).getByText("Primary")).toBeInTheDocument();
    expect(within(row).getByText("Verified")).toBeInTheDocument();
    const pending = screen.getByTestId("contacts-pending");
    expect(pending).toHaveTextContent("+260 96* ***999");
    expect(pending).toHaveTextContent(/expires/);
    expect(pending).toHaveTextContent("4 tries left");
    expect(screen.queryByTestId("contact-add-email")).toBeNull();
  });

  it("offers only the other kind and labels buttons per contact", async () => {
    m.listContacts.mockResolvedValue(ok([wa]));
    render(<SignInContacts navigate={vi.fn()} />);
    expect(await screen.findByTestId("contact-add-email")).toBeInTheDocument();
    expect(screen.queryByTestId("contact-add-whatsapp")).toBeNull();
    expect(
      screen.getByRole("button", { name: "Change WhatsApp +260 97* ***123" }),
    ).toBeInTheDocument();
  });

  it("redirects to sign-in when the session is gone", async () => {
    const navigate = vi.fn();
    m.listContacts.mockResolvedValue(fail("UNAUTHENTICATED", {}, 401));
    render(<SignInContacts navigate={navigate} />);
    await waitFor(() =>
      expect(navigate).toHaveBeenCalledWith("/auth?next=%2Fprofile"),
    );
  });

  it("shows a load error with retry", async () => {
    const user = userEvent.setup();
    m.listContacts.mockResolvedValueOnce(fail("SERVICE_UNAVAILABLE", {}, 503));
    render(<SignInContacts navigate={vi.fn()} />);
    expect(await screen.findByTestId("contacts-load-error")).toHaveTextContent(
      CONTACT_ERRORS.SERVICE_UNAVAILABLE.message,
    );
    m.listContacts.mockResolvedValueOnce(ok([wa]));
    await user.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByTestId("contact-c-wa")).toBeInTheDocument();
  });

  it("cancels a stuck pending change", async () => {
    const user = userEvent.setup();
    m.listContacts.mockResolvedValueOnce(
      ok([wa, em], {
        changeId: "chg-1",
        kind: "CHANGE",
        newContactMasked: "n***@x.com",
        expiresAt: "2026-10-06T10:00:00Z",
        currentContactVerified: false,
        attemptsRemaining: 1,
      }),
    );
    m.listContacts.mockResolvedValue(ok([wa, em]));
    m.cancelChange.mockResolvedValue({
      ok: true,
      httpStatus: 200,
      cancelled: true,
    } as never);
    render(<SignInContacts navigate={vi.fn()} />);
    expect(await screen.findByTestId("contacts-pending")).toHaveTextContent(
      "1 try left",
    );
    await user.click(screen.getByTestId("contacts-cancel-change"));
    expect(m.cancelChange).toHaveBeenCalledWith("chg-1");
    await waitFor(() =>
      expect(screen.queryByTestId("contacts-pending")).toBeNull(),
    );
    expect(screen.getByTestId("contacts-status")).toHaveTextContent(
      /cancelled/,
    );
  });

  it("sets primary with a code to the current primary contact", async () => {
    const user = userEvent.setup();
    m.listContacts.mockResolvedValue(ok([wa, em]));
    m.requestPrimary.mockResolvedValueOnce(
      challenge({ maskedContact: "+260 97* ***123", contactType: "WHATSAPP" }),
    );
    m.confirmPrimary.mockResolvedValueOnce({
      ok: true,
      httpStatus: 200,
      changeId: "c",
      kind: "PRIMARY",
      status: "COMPLETED",
    } as never);
    render(<SignInContacts navigate={vi.fn()} />);
    await user.click(
      await screen.findByRole("button", {
        name: "Make Email j***@gmail.com primary",
      }),
    );
    await user.click(screen.getByTestId("contact-flow-send"));
    expect(m.requestPrimary).toHaveBeenCalledWith("c-em");
    await typeOtp(user, "123456");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    await screen.findByTestId("contact-flow-done");
    expect(m.confirmPrimary).toHaveBeenCalledWith("c-em", "cid", "123456");
  });
});

describe("add contact", () => {
  it("completes with a correct code and refreshes the list", async () => {
    const user = userEvent.setup();
    await openAddEmail(user);
    expect(m.requestAdd).toHaveBeenCalledWith({
      value: "n@x.com",
      type: "EMAIL",
    });
    expect(screen.getByTestId("contact-flow-announce")).toHaveTextContent(
      "n***@x.com",
    );
    m.confirmAdd.mockResolvedValueOnce(done);
    await typeOtp(user, "123456");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    expect(await screen.findByTestId("contact-flow-done")).toBeInTheDocument();
    expect(m.confirmAdd).toHaveBeenCalledWith("cid", "123456");
    expect(m.listContacts).toHaveBeenCalledTimes(2);
  });

  it("wrong code shows attempts left in an alert and keeps the step", async () => {
    const user = userEvent.setup();
    await openAddEmail(user);
    m.confirmAdd.mockResolvedValueOnce(
      fail("OTP_INVALID", { attemptsRemaining: 2 }),
    );
    await typeOtp(user, "000000");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    const alert = await screen.findByText(CONTACT_ERRORS.OTP_INVALID.message, {
      exact: false,
    });
    expect(alert.closest('[role="alert"]')).not.toBeNull();
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-error")).toHaveTextContent(
        "2 tries left",
      ),
    );
    expect(screen.getByTestId("contact-flow-code-step")).toBeInTheDocument();
  });

  it("locked: disables the input and shows when to retry", async () => {
    const user = userEvent.setup();
    await openAddEmail(user);
    m.confirmAdd.mockResolvedValueOnce(
      fail("OTP_LOCKED", { retryAfterSeconds: 600 }, 423),
    );
    await typeOtp(user, "111111");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    expect(await screen.findByText(/Try again in 10:00/)).toBeInTheDocument();
    expect(otpBoxes().every((b) => b.disabled)).toBe(true);
  });

  it("expired code returns to the input step with a message", async () => {
    const user = userEvent.setup();
    await openAddEmail(user);
    m.confirmAdd.mockResolvedValueOnce(fail("OTP_EXPIRED", {}, 410));
    await typeOtp(user, "222222");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-error")).toHaveTextContent(
        CONTACT_ERRORS.OTP_EXPIRED.message,
      ),
    );
    expect(screen.getByTestId("contact-flow-email")).toBeInTheDocument();
  });

  it("claimed by another account uses neutral wording", async () => {
    const user = userEvent.setup();
    await openAddEmail(user);
    m.confirmAdd.mockResolvedValueOnce(
      fail("CONTACT_ALREADY_CLAIMED", {}, 409),
    );
    await typeOtp(user, "333333");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    const box = await screen.findByTestId("contact-flow-error");
    await waitFor(() =>
      expect(box).toHaveTextContent(
        CONTACT_ERRORS.CONTACT_ALREADY_CLAIMED.message,
      ),
    );
    expect(box.textContent).not.toMatch(
      /another account|already (registered|taken|belongs)|someone/i,
    );
  });

  it("APPLYING (202) shows a calm finishing state, polls myContacts and never re-sends the code", async () => {
    const user = userEvent.setup();
    await openAddEmail(user);
    m.confirmAdd.mockResolvedValueOnce({
      ok: true,
      httpStatus: 202,
      changeId: "c",
      kind: "ADD",
      status: "APPLYING",
    } as never);
    m.listContacts
      .mockResolvedValueOnce(
        ok([wa], {
          changeId: "c",
          kind: "ADD",
          newContactMasked: "n***@x.com",
          expiresAt: "2026-10-06T10:00:00Z",
          currentContactVerified: true,
          attemptsRemaining: 5,
        }),
      )
      .mockResolvedValue(ok([wa, em]));
    await typeOtp(user, "123456");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    expect(await screen.findByTestId("contact-flow-pending")).toHaveTextContent(
      /nothing is lost/,
    );
    expect(
      await screen.findByTestId("contact-flow-done", undefined, {
        timeout: 8000,
      }),
    ).toBeInTheDocument();
    expect(m.confirmAdd).toHaveBeenCalledTimes(1);
  }, 12000);

  it("network failure keeps the code step and says contacts are unchanged", async () => {
    const user = userEvent.setup();
    await openAddEmail(user);
    m.confirmAdd.mockResolvedValueOnce(fail("SERVICE_UNAVAILABLE", {}, 0));
    await typeOtp(user, "123456");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-error")).toHaveTextContent(
        /unchanged/,
      ),
    );
    expect(screen.getByTestId("contact-flow-code-step")).toBeInTheDocument();
  });

  it("rejects an invalid email locally", async () => {
    const user = userEvent.setup();
    m.listContacts.mockResolvedValue(ok([wa]));
    render(<SignInContacts navigate={vi.fn()} />);
    await user.click(await screen.findByTestId("contact-add-email"));
    await user.type(screen.getByTestId("contact-flow-email"), "nope");
    await user.click(screen.getByTestId("contact-flow-send"));
    expect(m.requestAdd).not.toHaveBeenCalled();
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-error")).toHaveTextContent(
        CONTACT_ERRORS.CONTACT_INVALID.message,
      ),
    );
  });
});

describe("change contact", () => {
  it("asks for a code for the new contact and one for the current primary", async () => {
    const user = userEvent.setup();
    m.listContacts.mockResolvedValue(ok([wa, em]));
    render(<SignInContacts navigate={vi.fn()} />);
    await user.click(
      await screen.findByRole("button", {
        name: "Change Email j***@gmail.com",
      }),
    );
    await user.type(screen.getByTestId("contact-flow-email"), "n@x.com");
    m.requestChange.mockResolvedValueOnce({
      ok: true,
      httpStatus: 200,
      changeId: "change-12345",
      newContact: {
        challengeId: "cn",
        maskedContact: "n***@x.com",
        contactType: "EMAIL",
        expiresInSeconds: 300,
        resendAfterSeconds: 60,
      },
      currentContact: {
        challengeId: "cp",
        maskedContact: "+260 97* ***123",
        contactType: "WHATSAPP",
        expiresInSeconds: 300,
        resendAfterSeconds: 60,
      },
      expiresAt: "2026-10-06T10:00:00Z",
    } as never);
    await user.click(screen.getByTestId("contact-flow-send"));
    expect(m.requestChange).toHaveBeenCalledWith("c-em", {
      value: "n@x.com",
      type: "EMAIL",
    });
    expect(screen.getByLabelText(/sent to n\*\*\*@x\.com/)).toBeInTheDocument();
    expect(
      screen.getByLabelText(/current primary contact \+260 97\* \*\*\*123/),
    ).toBeInTheDocument();
    m.confirmChange.mockResolvedValueOnce(done);
    await typeOtp(user, "111111");
    await typeOtp(user, "222222", 1);
    await user.click(screen.getByTestId("contact-flow-confirm"));
    await screen.findByTestId("contact-flow-done");
    expect(m.confirmChange).toHaveBeenCalledWith(
      "change-12345",
      "111111",
      "222222",
    );
  });
});

describe("remove contact", () => {
  it("is blocked with an explanation when it is the last verified contact", async () => {
    const user = userEvent.setup();
    m.listContacts.mockResolvedValue(ok([wa, { ...em, verifiedAt: null }]));
    render(<SignInContacts navigate={vi.fn()} />);
    await user.click(
      await screen.findByRole("button", {
        name: "Remove WhatsApp +260 97* ***123",
      }),
    );
    expect(await screen.findByTestId("contact-flow-blocked")).toHaveTextContent(
      CONTACT_ERRORS.LAST_VERIFIED_CONTACT.message,
    );
    expect(m.requestRemoval).not.toHaveBeenCalled();
  });

  it("server refusal of the last verified contact is explained", async () => {
    const user = userEvent.setup();
    m.listContacts.mockResolvedValue(ok([wa, em]));
    render(<SignInContacts navigate={vi.fn()} />);
    await user.click(
      await screen.findByRole("button", {
        name: "Remove Email j***@gmail.com",
      }),
    );
    m.requestRemoval.mockResolvedValueOnce(
      fail("LAST_VERIFIED_CONTACT", {}, 409),
    );
    await user.click(screen.getByTestId("contact-flow-send"));
    expect(await screen.findByTestId("contact-flow-blocked")).toHaveTextContent(
      CONTACT_ERRORS.LAST_VERIFIED_CONTACT.message,
    );
  });

  it("removes after a correct code", async () => {
    const user = userEvent.setup();
    m.listContacts.mockResolvedValue(ok([wa, em]));
    render(<SignInContacts navigate={vi.fn()} />);
    await user.click(
      await screen.findByRole("button", {
        name: "Remove Email j***@gmail.com",
      }),
    );
    m.requestRemoval.mockResolvedValueOnce(
      challenge({ maskedContact: "j***@gmail.com" }),
    );
    await user.click(screen.getByTestId("contact-flow-send"));
    m.confirmRemoval.mockResolvedValueOnce(done);
    await typeOtp(user, "123456");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    await screen.findByTestId("contact-flow-done");
    expect(m.confirmRemoval).toHaveBeenCalledWith("cid", "123456");
  });
});

describe("storage hygiene", () => {
  it("never writes the raw contact to localStorage, sessionStorage or the URL", async () => {
    const user = userEvent.setup();
    await openAddEmail(user);
    expect(JSON.stringify({ ...localStorage })).not.toContain("n@x.com");
    expect(JSON.stringify({ ...sessionStorage })).not.toContain("n@x.com");
    expect(window.location.href).not.toContain("n@x.com");
    expect(document.cookie).not.toContain("n@x.com");
  });
});

describe("send code again", () => {
  const tick = (ms: number) =>
    act(() => {
      vi.advanceTimersByTime(ms);
    });
  const setup = () =>
    userEvent.setup({ advanceTimers: (ms) => vi.advanceTimersByTime(ms) });
  // Fake only interval + Date (the countdown); real setTimeout keeps findBy/waitFor working.
  beforeEach(() => {
    vi.useFakeTimers({ toFake: ["setInterval", "clearInterval", "Date"] });
  });
  afterEach(() => {
    vi.useRealTimers();
  });

  it("is disabled with a live countdown, then enabled and announced", async () => {
    const user = setup();
    await openAddEmail(user, { resendAfterSeconds: 300 });
    const btn = screen.getByTestId("contact-flow-resend");
    expect(btn).toBeDisabled();
    expect(btn).toHaveTextContent("05:00");
    expect(btn).toHaveAccessibleName(/available in 05:00/);
    tick(120_000);
    expect(screen.getByTestId("contact-flow-resend")).toHaveTextContent(
      "03:00",
    );
    tick(181_000);
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-resend")).toBeEnabled(),
    );
    expect(screen.getByTestId("contact-flow-announce")).toHaveTextContent(
      "You can send the code again.",
    );
  });

  it("sends again once, shows the masked target, resets the countdown and clears the field", async () => {
    const user = setup();
    await openAddEmail(user, { resendAfterSeconds: 0 });
    await typeOtp(user, "123");
    m.resendCode.mockResolvedValueOnce(
      challenge({ challengeId: "cid2", resendAfterSeconds: 300 }),
    );
    await user.click(screen.getByTestId("contact-flow-resend"));
    expect(m.resendCode).toHaveBeenCalledWith({ challengeId: "cid" });
    expect(await screen.findByTestId("contact-flow-resent")).toHaveTextContent(
      "Code sent again to n***@x.com",
    );
    expect(screen.getByTestId("contact-flow-resend")).toBeDisabled();
    expect(screen.getByTestId("contact-flow-resend")).toHaveTextContent(
      "05:00",
    );
    expect(otpBoxes().every((b) => b.value === "")).toBe(true);
    // the next confirm uses the new challenge id
    m.confirmAdd.mockResolvedValueOnce(done);
    await typeOtp(user, "123456");
    await user.click(screen.getByTestId("contact-flow-confirm"));
    await screen.findByTestId("contact-flow-done");
    expect(m.confirmAdd).toHaveBeenCalledWith("cid2", "123456");
  });

  it("never double-submits while a resend is in flight", async () => {
    const user = setup();
    await openAddEmail(user, { resendAfterSeconds: 0 });
    let release: (v: unknown) => void = () => {};
    m.resendCode.mockReturnValueOnce(
      new Promise((r) => {
        release = r;
      }) as never,
    );
    const btn = screen.getByTestId("contact-flow-resend");
    await user.click(btn);
    await user.click(btn);
    expect(m.resendCode).toHaveBeenCalledTimes(1);
    expect(btn).toBeDisabled();
    await act(async () => {
      release(challenge());
    });
  });

  it("OTP_RATE_LIMITED re-arms the countdown from retryAfterSeconds without locking the input", async () => {
    const user = setup();
    await openAddEmail(user, { resendAfterSeconds: 0 });
    m.resendCode.mockResolvedValueOnce(
      fail("OTP_RATE_LIMITED", { retryAfterSeconds: 90 }, 429),
    );
    await user.click(screen.getByTestId("contact-flow-resend"));
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-resend")).toHaveTextContent(
        "01:30",
      ),
    );
    expect(screen.getByTestId("contact-flow-resend")).toBeDisabled();
    expect(otpBoxes().every((b) => !b.disabled)).toBe(true);
    tick(91_000);
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-resend")).toBeEnabled(),
    );
  });

  it("OTP_LOCKED locks the step", async () => {
    const user = setup();
    await openAddEmail(user, { resendAfterSeconds: 0 });
    m.resendCode.mockResolvedValueOnce(
      fail("OTP_LOCKED", { retryAfterSeconds: 600 }, 423),
    );
    await user.click(screen.getByTestId("contact-flow-resend"));
    expect(await screen.findByText(/Try again in 10:00/)).toBeInTheDocument();
    expect(otpBoxes().every((b) => b.disabled)).toBe(true);
    expect(screen.getByTestId("contact-flow-resend")).toBeDisabled();
  });

  it("an expired change on resend is explained (CONTACT_UNKNOWN) and an expired code restarts (OTP_EXPIRED)", async () => {
    const user = setup();
    await openAddEmail(user, { resendAfterSeconds: 0 });
    m.resendCode.mockResolvedValueOnce(fail("OTP_EXPIRED", {}, 410));
    await user.click(screen.getByTestId("contact-flow-resend"));
    expect(await screen.findByTestId("contact-flow-email")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-error")).toHaveTextContent(
        CONTACT_ERRORS.OTP_EXPIRED.message,
      ),
    );
  });

  it("CONTACT_UNKNOWN on resend blocks with the refresh explanation", async () => {
    const user = setup();
    await openAddEmail(user, { resendAfterSeconds: 0 });
    m.resendCode.mockResolvedValueOnce(fail("CONTACT_UNKNOWN", {}, 404));
    await user.click(screen.getByTestId("contact-flow-resend"));
    expect(await screen.findByTestId("contact-flow-blocked")).toHaveTextContent(
      CONTACT_ERRORS.CONTACT_UNKNOWN.message,
    );
  });

  it("a network error keeps the step and the button usable", async () => {
    const user = setup();
    await openAddEmail(user, { resendAfterSeconds: 0 });
    m.resendCode.mockResolvedValueOnce(fail("SERVICE_UNAVAILABLE", {}, 0));
    await user.click(screen.getByTestId("contact-flow-resend"));
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-error")).toHaveTextContent(
        /unchanged/,
      ),
    );
    expect(screen.getByTestId("contact-flow-code-step")).toBeInTheDocument();
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-resend")).toBeEnabled(),
    );
  });

  it("when the code has expired it offers both send again and start over", async () => {
    const user = setup();
    await openAddEmail(user, { resendAfterSeconds: 0, expiresInSeconds: 3 });
    tick(4000);
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-timer")).toHaveTextContent(
        /expired/,
      ),
    );
    expect(screen.getByTestId("contact-flow-resend")).toBeEnabled();
    expect(screen.getByTestId("contact-flow-confirm")).toBeDisabled();
    await user.click(screen.getByTestId("contact-flow-restart"));
    expect(screen.getByTestId("contact-flow-email")).toBeInTheDocument();
  });

  it("change flow has a separate resend per code", async () => {
    const user = setup();
    m.listContacts.mockResolvedValue(ok([wa, em]));
    render(<SignInContacts navigate={vi.fn()} />);
    await user.click(
      await screen.findByRole("button", {
        name: "Change Email j***@gmail.com",
      }),
    );
    await user.type(screen.getByTestId("contact-flow-email"), "n@x.com");
    const sent = (
      id: string,
      masked: string,
      type: string,
      resend: number,
    ) => ({
      challengeId: id,
      maskedContact: masked,
      contactType: type,
      expiresInSeconds: 300,
      resendAfterSeconds: resend,
    });
    m.requestChange.mockResolvedValueOnce({
      ok: true,
      httpStatus: 200,
      changeId: "change-12345",
      expiresAt: "2026-10-06T10:00:00Z",
      newContact: sent("cn", "n***@x.com", "EMAIL", 0),
      currentContact: sent("cp", "+260 97* ***123", "WHATSAPP", 300),
    } as never);
    await user.click(screen.getByTestId("contact-flow-send"));
    expect(screen.getByTestId("contact-flow-resend")).toBeEnabled();
    expect(screen.getByTestId("contact-flow-resend-current")).toBeDisabled();
    m.resendCode.mockResolvedValueOnce(
      challenge({
        challengeId: "cn2",
        maskedContact: "n***@x.com",
        resendAfterSeconds: 300,
      }),
    );
    await user.click(screen.getByTestId("contact-flow-resend"));
    expect(m.resendCode).toHaveBeenCalledWith({
      changeId: "change-12345",
      target: "NEW",
    });
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-resend")).toBeDisabled(),
    );
    tick(301_000);
    await waitFor(() =>
      expect(screen.getByTestId("contact-flow-resend-current")).toBeEnabled(),
    );
    m.resendCode.mockResolvedValueOnce(
      challenge({
        challengeId: "cp2",
        maskedContact: "+260 97* ***123",
        contactType: "WHATSAPP",
      }),
    );
    await user.click(screen.getByTestId("contact-flow-resend-current"));
    expect(m.resendCode).toHaveBeenLastCalledWith({
      changeId: "change-12345",
      target: "CURRENT",
    });
    expect(await screen.findByTestId("contact-flow-resent")).toHaveTextContent(
      "+260 97* ***123",
    );
  });
});
