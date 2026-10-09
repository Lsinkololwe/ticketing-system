'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
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
import { formatMMSS, useCountdown } from './useCountdown';

type Step = 'contact' | 'code' | 'provisioning' | 'welcome';

const MAX_PROVISIONING_POLLS = 8;

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
  const codeForm = useZodForm(codeSchema, { defaultValues: { code: '' } });
  const mode = contactForm.watch('mode') as ContactMode;
  const phone = contactForm.watch('phone') || undefined;
  const email = contactForm.watch('email');
  const [challenge, setChallenge] = useState<ChallengeOk | null>(null);
  const [challengeStamp, setChallengeStamp] = useState(0);
  const [busy, setBusy] = useState(false);
  const [failure, setFailure] = useState<IdentityFailure | null>(null);
  const [attemptsLeft, setAttemptsLeft] = useState<number | null>(null);
  const [isNew, setIsNew] = useState(false);
  const [polls, setPolls] = useState(0);
  const [locked, setLocked] = useState(false);
  const [verified, setVerified] = useState(false);
  const submittedCode = useRef<string | null>(null);

  const expiresLeft = useCountdown(challenge?.expiresInSeconds ?? 0, challengeStamp);
  const resendLeft = useCountdown(challenge?.resendAfterSeconds ?? 0, challengeStamp);

  const go = useCallback(
    (url: string) => (navigate ? navigate(url) : window.location.assign(url)),
    [navigate]
  );

  const info = failure ? describeError(failure.errorCode) : null;

  // ---- contact -------------------------------------------------------------
  const sendCode = async () => {
    setFailure(null);
    const value = (mode === 'EMAIL' ? email.trim() : phone) as string;
    setBusy(true);
    const res = await requestChallenge(value, mode, mode === 'WHATSAPP' ? regionOf(phone) : undefined);
    setBusy(false);
    if (!res.ok) {
      setFailure(res);
      setLocked(describeError(res.errorCode).kind === 'locked');
      return;
    }
    setChallenge(res);
    setChallengeStamp((n) => n + 1);
    codeForm.reset({ code: '' });
    submittedCode.current = null;
    setAttemptsLeft(null);
    setLocked(false);
    setVerified(false);
    setStep('code');
  };

  // ---- code ----------------------------------------------------------------
  const finishEnsure = useCallback(
    async (attempt: number) => {
      setBusy(true);
      const res = await ensureAccount(true);
      setBusy(false);
      if (!res.ok) {
        setFailure(res);
        const kind = describeError(res.errorCode).kind;
        if (kind === 'restart') {
          setVerified(false);
          setStep('contact');
        } else if (kind === 'blocked') {
          setLocked(true);
        }
        return;
      }
      if (res.httpStatus === 202 || res.status === 'PROVISIONING') {
        setPolls(attempt);
        setStep('provisioning');
        if (attempt < MAX_PROVISIONING_POLLS) {
          setTimeout(() => void finishEnsure(attempt + 1), Math.max(1, res.retryAfterSeconds ?? 2) * 1000);
        }
        return;
      }
      setIsNew(res.isNew === true);
      setStep('welcome');
    },
    []
  );

  const submitCode = useCallback(
    async (value: string) => {
      if (!challenge || submittedCode.current === value) return;
      submittedCode.current = value;
      setFailure(null);
      setBusy(true);
      const res = await verifyCode(challenge.challengeId, value);
      setBusy(false);
      if (!res.ok) {
        setFailure(res);
        const kind = describeError(res.errorCode).kind;
        if (res.errorCode === 'OTP_INVALID') {
          setAttemptsLeft(res.attemptsRemaining ?? null);
          codeForm.reset({ code: '' });
          submittedCode.current = null;
        }
        if (kind === 'locked') setLocked(true);
        return;
      }
      setVerified(true);
      await finishEnsure(1);
    },
    [challenge, finishEnsure]
  );

  const resend = async () => {
    if (!challenge) return;
    setFailure(null);
    setBusy(true);
    const res = await requestChallenge(
      mode === 'EMAIL' ? email.trim() : (phone as string),
      mode,
      mode === 'WHATSAPP' ? regionOf(phone) : undefined
    );
    setBusy(false);
    if (!res.ok) {
      setFailure(res);
      setLocked(describeError(res.errorCode).kind === 'locked');
      return;
    }
    setChallenge(res);
    setChallengeStamp((n) => n + 1);
    codeForm.reset({ code: '' });
    submittedCode.current = null;
    setAttemptsLeft(null);
  };

  const change = () => {
    setStep('contact');
    setFailure(null);
    setChallenge(null);
    codeForm.reset({ code: '' });
    setLocked(false);
    setVerified(false);
    submittedCode.current = null;
  };

  // ---- welcome -> sign in -------------------------------------------------
  const continueToSignIn = useCallback(async () => {
    try {
      await onBeforeRedirect?.();
    } catch {
      /* the cart is best-effort; signing in must not be blocked by it */
    }
    go(`/api/auth/start?next=${encodeURIComponent(returnTo)}`);
  }, [go, onBeforeRedirect, returnTo]);

  useEffect(() => {
    if (step !== 'welcome') return;
    const t = setTimeout(() => void continueToSignIn(), welcomeDelayMs);
    return () => clearTimeout(t);
  }, [step, continueToSignIn, welcomeDelayMs]);

  // ---- render --------------------------------------------------------------
  const errorBox = (text: string, testId = 'identify-error') => (
    <div role="alert" data-testid={testId}>
      <Banner tone="error">{text}</Banner>
    </div>
  );

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
        {failure && errorBox(describeError(failure.errorCode).message)}
      </div>
    );
  }

  if (step === 'code' && challenge) {
    const expired = expiresLeft === 0;
    return (
      <div className="m3-stack" data-testid="identify-code-step">
        <div className="buyer-split">
          <p data-testid="identify-readback">
            Code sent to <strong>{challenge.maskedContact}</strong>
          </p>
          <Button variant="text" size="sm" onClick={change} data-testid="identify-change">
            Change
          </Button>
        </div>

        <Form form={codeForm} guardLeave={false} onSubmit={(v) => submitCode(v.code)}>
          <OtpRHF
            name="code"
            label="6 digit code"
            disabled={busy || locked}
            autoFocus
            onComplete={(c) => {
              if (!busy && !locked) void submitCode(c);
            }}
          />
        </Form>
        <p className="m3-muted" id="identify-code-help" data-testid="identify-timer">
          {expired ? 'This code has expired.' : `Expires in ${formatMMSS(expiresLeft)}`}
          {attemptsLeft !== null && !locked && (
            <span data-testid="identify-attempts">
              {' '}· {attemptsLeft} {attemptsLeft === 1 ? 'try' : 'tries'} left
            </span>
          )}
        </p>

        {info && errorBox(info.message)}
        {busy && <CircularProgress label="Checking" size="sm" />}

        <div className="m3-row">
          {verified && failure && !locked && (
            <Button variant="accent" onClick={() => void finishEnsure(1)} disabled={busy} data-testid="identify-ensure-retry">
              Try again
            </Button>
          )}
          <Button onClick={() => void resend()} disabled={busy || resendLeft > 0 || locked} data-testid="identify-resend">
            {resendLeft > 0 ? `Resend code in ${resendLeft}s` : 'Resend code'}
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
          <PhoneRHF name="phone" countries={countries} id="identify-phone" label="WhatsApp number" defaultCountry="ZM" density="form" />
        ) : (
          <TextFieldRHF name="email" id="identify-email" data-testid="identify-email" label="Email address" type="email" autoComplete="email" inputMode="email" density="form" />
        )}

        {info && errorBox(info.message)}

        <p className="m3-muted" data-testid="identify-terms">
          By continuing you agree to our <Link className="m3-link" href="/terms">Terms</Link> and{' '}
          <Link className="m3-link" href="/privacy">Privacy Policy</Link>, and consent to us sending a one-time code to{' '}
          {mode === 'EMAIL' ? 'this email' : 'this WhatsApp number'} to verify it.
        </p>

        <Button variant="accent" fullWidth type="submit" disabled={busy || locked} loading={busy} data-testid="identify-send">
          Send code
        </Button>
      </div>
    </Form>
  );
}
