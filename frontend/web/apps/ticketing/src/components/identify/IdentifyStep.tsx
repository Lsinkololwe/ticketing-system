'use client';

import React, { useEffect, useRef, useState } from 'react';
import Link from 'next/link';
import { Banner, Button, CircularProgress } from '@pml.tickets/shared/components/m3';
import { useCountryOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { Form, OtpRHF, PhoneRHF, SegmentedRHF, TextFieldRHF, useZodForm } from '@pml.tickets/shared/forms';
import { codeSchema, contactSchema } from './identifyForm';
import {
  ensureAccount,
  requestChallenge,
  verifyCode,
  type ChallengeOk,
  type IdentityFailure,
} from '@/lib/identity/client';
import { regionOf, type ContactMode } from '@/lib/identity/contact';
import { describeError } from '@/lib/identity/messages';
import { formatMMSS, useCountdown, useCountdownUntil } from './useCountdown';

/**
 * Sign in with a one-time code.
 *
 * What the person is doing: they pick how the code should reach them (WhatsApp or email), type that number or
 * address, and we send a six digit code. They type the code and we sign them in, creating a free account the
 * first time. There is no password. The screen always says where the code went.
 *
 * What the server promises: a code lives a few minutes and can be asked for again only after a short wait;
 * a few wrong guesses lock the contact for a while; every refusal is a stable error code that never says
 * whether the contact already has an account. The browser never holds a token: the server keeps the proof
 * and the sign-in handle and only hands the browser a redirect.
 *
 * What happens on each refusal (shown in the one alert line under the field it concerns):
 *  - invalid contact: say so and stay on the entry step;
 *  - wrong code: show the tries left and clear the boxes;
 *  - expired code: say so and offer a new one;
 *  - too soon or too many requests: show how long to wait, counted against the clock so it stays right
 *    after the tab was in the background;
 *  - locked: show when it lifts and keep the code boxes closed until then;
 *  - delivery failed, or the channel is not working for the person: "Send by <other channel> instead"
 *    returns to the entry step with the other channel chosen, so a code is never sent silently to a
 *    different contact;
 *  - account unavailable: a dead end with a pointer to support.
 */
type Step = 'contact' | 'code' | 'provisioning' | 'welcome';

const MAX_PROVISIONING_POLLS = 8;

/** Where a code was (or is about to be) sent, so a resend goes to exactly that contact. */
interface Target {
  value: string;
  mode: ContactMode;
  region?: string;
}

/** A refusal and, when it carries one, the wall-clock moment it stops applying. */
interface Refusal {
  failure: IdentityFailure;
  until: number | null;
}

function refusalEnd(f: IdentityFailure): number | null {
  if (f.lockedUntil) {
    const at = Date.parse(f.lockedUntil);
    if (!Number.isNaN(at)) return at;
  }
  if (f.retryAfterSeconds !== undefined && f.retryAfterSeconds > 0) return Date.now() + f.retryAfterSeconds * 1000;
  return null;
}

export interface IdentifyStepProps {
  /** Where the buyer lands after sign-in (same-origin path). */
  returnTo: string;
  /** Runs before the redirect to sign-in, e.g. to persist the selected tickets server-side. */
  onBeforeRedirect?: () => Promise<void> | void;
  /** Injected for tests; defaults to a full-page navigation. */
  navigate?: (url: string) => void;
  /** Delay before moving on from the welcome step (ms). */
  welcomeDelayMs?: number;
}

export function IdentifyStep({ returnTo, onBeforeRedirect, navigate, welcomeDelayMs = 1500 }: IdentifyStepProps) {
  const [step, setStep] = useState<Step>('contact');
  const { countries } = useCountryOptions();
  const contactForm = useZodForm(contactSchema, { defaultValues: { mode: 'WHATSAPP', phone: '', email: '' } });
  // Validated on submit, not on blur: an empty-code error appearing when focus leaves the boxes pushes
  // "Resend" and "Send by email instead" down between the press and the release, and the click is lost.
  const codeForm = useZodForm(codeSchema, { defaultValues: { code: '' }, mode: 'onSubmit' });
  const code = codeForm.watch('code') ?? '';
  const mode = contactForm.watch('mode') as ContactMode;
  const phone = contactForm.watch('phone') || undefined;
  const email = contactForm.watch('email');
  const [attempt, setAttempt] = useState<Target | null>(null);
  const [challenge, setChallenge] = useState<ChallengeOk | null>(null);
  const [challengeStamp, setChallengeStamp] = useState(0);
  const [busy, setBusy] = useState(false);
  const [refusal, setRefusal] = useState<Refusal | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [polls, setPolls] = useState(0);
  const [verified, setVerified] = useState(false);
  const inFlight = useRef(false);

  const expiresLeft = useCountdown(challenge?.expiresInSeconds ?? 0, challengeStamp);
  const resendLeft = useCountdown(challenge?.resendAfterSeconds ?? 0, challengeStamp);

  const failure = refusal?.failure ?? null;
  const info = failure ? describeError(failure.errorCode) : null;
  const lockCode = failure?.errorCode === 'OTP_LOCKED';
  const waitCode = failure?.errorCode === 'OTP_RATE_LIMITED' || failure?.errorCode === 'OTP_COOLDOWN_ACTIVE';
  const lockLeft = useCountdownUntil(lockCode ? (refusal?.until ?? null) : null);
  const waitLeft = useCountdownUntil(waitCode ? (refusal?.until ?? null) : null);
  // A lock with no known end stays until the person starts again; one with an end lifts by itself.
  const locked = lockCode && (refusal?.until == null || lockLeft > 0);
  const waiting = waitCode && waitLeft > 0;
  const blocked = info?.kind === 'blocked';
  const otherMode: ContactMode = (attempt?.mode ?? mode) === 'EMAIL' ? 'WHATSAPP' : 'EMAIL';

  const go = (url: string) => (navigate ? navigate(url) : window.location.assign(url));

  const refuse = (f: IdentityFailure) => setRefusal({ failure: f, until: refusalEnd(f) });

  // ---- asking for a code ---------------------------------------------------
  const requestCode = async (target: Target): Promise<void> => {
    if (inFlight.current) return;
    inFlight.current = true;
    setRefusal(null);
    setAttempt(target);
    setBusy(true);
    const res = await requestChallenge(target.value, target.mode, target.region);
    setBusy(false);
    inFlight.current = false;
    if (!res.ok) {
      refuse(res);
      return;
    }
    setChallenge(res);
    setChallengeStamp((n) => n + 1);
    codeForm.reset({ code: '' });
    setVerified(false);
    setStep('code');
  };

  const sendCode = async () => {
    const value = (mode === 'EMAIL' ? email.trim() : phone) as string;
    await requestCode({ value, mode, region: mode === 'WHATSAPP' ? regionOf(phone) : undefined });
  };

  const resend = async () => {
    if (attempt) await requestCode(attempt);
  };

  // ---- entering the code ---------------------------------------------------
  const finishEnsure = async (attemptNo: number) => {
    setBusy(true);
    const res = await ensureAccount(true);
    setBusy(false);
    if (!res.ok) {
      refuse(res);
      if (describeError(res.errorCode).kind === 'restart') {
        setVerified(false);
        setStep('contact');
      }
      return;
    }
    if (res.httpStatus === 202 || res.status === 'PROVISIONING') {
      setPolls(attemptNo);
      setStep('provisioning');
      if (attemptNo < MAX_PROVISIONING_POLLS) {
        setTimeout(() => void finishEnsure(attemptNo + 1), Math.max(1, res.retryAfterSeconds ?? 2) * 1000);
      }
      return;
    }
    setIsNew(res.isNew === true);
    setStep('welcome');
  };

  const submitCode = async (value: string) => {
    if (!challenge || inFlight.current || locked || blocked || value.length !== 6) return;
    inFlight.current = true;
    setRefusal(null);
    setBusy(true);
    const res = await verifyCode(challenge.challengeId, value);
    setBusy(false);
    inFlight.current = false;
    if (!res.ok) {
      refuse(res);
      if (res.errorCode === 'OTP_INVALID') codeForm.reset({ code: '' });
      return;
    }
    setVerified(true);
    await finishEnsure(1);
  };

  /** Back to the entry step. `toMode` also switches the channel, for "Send by <other channel> instead". */
  const change = (toMode?: ContactMode) => {
    if (toMode) contactForm.setValue('mode', toMode);
    setStep('contact');
    setRefusal(null);
    setChallenge(null);
    codeForm.reset({ code: '' });
    setVerified(false);
  };

  // ---- welcome -> sign in -------------------------------------------------
  const continueToSignIn = async () => {
    try {
      await onBeforeRedirect?.();
    } catch {
      /* the cart is best-effort; signing in must not be blocked by it */
    }
    go(`/api/auth/start?next=${encodeURIComponent(returnTo)}`);
  };

  const continueRef = useRef(continueToSignIn);
  continueRef.current = continueToSignIn;
  useEffect(() => {
    if (step !== 'welcome') return;
    const t = setTimeout(() => void continueRef.current(), welcomeDelayMs);
    return () => clearTimeout(t);
  }, [step, welcomeDelayMs]);

  // ---- render --------------------------------------------------------------
  /** The refusal, worded for the person, in the one alert line of the step. */
  const errorBox = (() => {
    if (!refusal || !failure || !info) return null;
    let text = info.message;
    if (lockCode && refusal.until !== null) {
      text =
        lockLeft > 0
          ? `${text} Try again at ${new Date(refusal.until).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })} (in ${formatMMSS(lockLeft)}).`
          : `${text} You can try again now.`;
    }
    if (waitCode && refusal.until !== null) {
      text = waitLeft > 0 ? `${text} Try again in ${formatMMSS(waitLeft)}.` : `${text} You can try again now.`;
    }
    return (
      <div role="alert" data-testid={`otp-error-${failure.errorCode}`}>
        <Banner tone="error">{text}</Banner>
      </div>
    );
  })();

  if (step === 'welcome') {
    return (
      <div className="m3-stack buyer-center-col" data-testid="identify-welcome" role="status">
        <h2 className="m3-card__title">{isNew ? 'We created your free account' : 'Welcome back'}</h2>
        <p className="m3-muted">Taking you to your tickets securely…</p>
        <Button variant="accent" onClick={() => void continueToSignIn()} data-testid="identify-continue">
          Continue
        </Button>
      </div>
    );
  }

  if (step === 'provisioning') {
    const exhausted = polls >= MAX_PROVISIONING_POLLS;
    return (
      <div className="m3-stack buyer-center-col" data-testid="identify-provisioning" role="status">
        {!exhausted && <CircularProgress label="Setting up your account" />}
        <p>
          {exhausted
            ? 'Setting up your account is taking longer than usual. Please try again in a moment.'
            : 'Setting up your account is taking longer than usual. Hang tight…'}
        </p>
        {exhausted && (
          <Button variant="accent" onClick={() => void finishEnsure(1)} disabled={busy} data-testid="identify-retry">
            Try again
          </Button>
        )}
        {errorBox}
      </div>
    );
  }

  if (step === 'code' && challenge) {
    const expired = expiresLeft === 0;
    const attemptsLeft = failure?.errorCode === 'OTP_INVALID' ? (failure.attemptsRemaining ?? null) : null;
    return (
      <div className="m3-stack" data-testid="identify-code-step">
        <div className="buyer-split">
          <p data-testid="identify-readback">
            We sent a 6 digit code by {challenge.channel === 'EMAIL' ? 'email' : 'WhatsApp'} to <strong>{challenge.maskedContact}</strong>
          </p>
          <Button variant="text" size="sm" onClick={() => change()} data-testid="identify-change">
            Change
          </Button>
        </div>

        <Form form={codeForm} guardLeave={false} onSubmit={(v) => submitCode(v.code)}>
          <div className="m3-stack">
            <div data-testid="otp-input">
              <OtpRHF
                name="code"
                label="6 digit code"
                disabled={busy || locked || blocked}
                autoFocus
                onComplete={(c) => void submitCode(c)}
              />
            </div>
            <p className="m3-muted" id="identify-code-help" data-testid="otp-countdown">
              {expired ? 'This code has expired.' : `Expires in ${formatMMSS(expiresLeft)}`}
              {attemptsLeft !== null && !locked && (
                <span data-testid="identify-attempts">
                  {' '}· {attemptsLeft === 0 ? 'No tries left' : `${attemptsLeft} ${attemptsLeft === 1 ? 'try' : 'tries'} left`}
                </span>
              )}
            </p>
            {errorBox}
            <Button
              variant="accent"
              fullWidth
              type="submit"
              disabled={busy || locked || blocked || code.length !== 6}
              loading={busy}
              data-testid="otp-submit"
            >
              Verify and sign in
            </Button>
          </div>
        </Form>

        <div className="m3-row">
          {verified && failure && !locked && !blocked && (
            <Button variant="accent" onClick={() => void finishEnsure(1)} disabled={busy} data-testid="identify-ensure-retry">
              Try again
            </Button>
          )}
          <Button
            onClick={() => void resend()}
            disabled={busy || resendLeft > 0 || waiting || locked || blocked}
            data-testid="otp-resend"
          >
            {resendLeft > 0 ? `Resend code in ${resendLeft}s` : 'Resend code'}
          </Button>
          <Button variant="text" onClick={() => change(otherMode)} disabled={busy} data-testid="otp-switch-channel">
            {otherMode === 'EMAIL' ? 'Send by email instead' : 'Send by WhatsApp instead'}
          </Button>
        </div>
      </div>
    );
  }

  return (
    <Form form={contactForm} guardLeave={false} onSubmit={sendCode}>
      <div className="m3-stack" data-testid="identify-contact-step">
        <SegmentedRHF
          name="mode"
          label="How should we reach you?"
          options={[
            { value: 'WHATSAPP', label: 'WhatsApp' },
            { value: 'EMAIL', label: 'Email' },
          ]}
        />

        {mode === 'WHATSAPP' ? (
          <div data-testid="contact-input">
            <PhoneRHF name="phone" countries={countries} id="identify-phone" label="WhatsApp number" defaultCountry="ZM" density="form" />
          </div>
        ) : (
          <div data-testid="contact-input">
            <TextFieldRHF name="email" id="identify-email" label="Email address" type="email" autoComplete="email" inputMode="email" density="form" />
          </div>
        )}

        {errorBox}

        <p className="m3-muted" data-testid="identify-terms">
          By continuing you agree to our <Link className="m3-link" href="/terms">Terms</Link> and{' '}
          <Link className="m3-link" href="/privacy">Privacy Policy</Link>, and consent to us sending a one-time code to{' '}
          {mode === 'EMAIL' ? 'this email' : 'this WhatsApp number'} to verify it.
        </p>

        <Button variant="accent" fullWidth type="submit" disabled={busy || locked || waiting || blocked} loading={busy} data-testid="request-code-submit">
          Send code
        </Button>
      </div>
    </Form>
  );
}
