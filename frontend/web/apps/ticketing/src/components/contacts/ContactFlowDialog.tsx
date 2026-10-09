'use client';

import React, { useCallback, useEffect, useReducer, useRef, useState } from 'react';
import { Banner, Button, CircularProgress, Dialog } from '@pml.tickets/shared/components/m3';
import { useCountryOptions } from '@pml.tickets/shared/api/graphql/shared/reference';
import { Form, OtpRHF, PhoneRHF, TextFieldRHF, useZodForm } from '@pml.tickets/shared/forms';
import { codesSchema, valueSchema } from './contactForms';
import * as api from '@/lib/contacts/client';
import { regionOf } from '@/lib/identity/contact';
import { describeContactError } from '@/lib/contacts/messages';
import type { ContactKind, ContactView, OpResult } from '@/lib/contacts/types';
import type { ClientResult, IdentityFailure } from '@/lib/identity/client';
import { formatMMSS, useCountdown } from '@/components/identify/useCountdown';
import { flowReducer, initialFlow, type FlowKind } from './flow';

type Target = 'new' | 'current';

const MAX_PENDING_POLLS = 6;
const LABEL: Record<ContactKind, string> = { WHATSAPP: 'WhatsApp number', EMAIL: 'email address' };

export interface ContactFlowDialogProps {
  kind: FlowKind;
  /** The contact being changed or removed. */
  contact?: ContactView;
  /** The kind of contact being added. */
  addType?: ContactKind;
  /** True when removing would leave no verified contact. */
  lastVerified?: boolean;
  onClose: () => void;
  /** Called after the server confirms the operation; the caller reloads the list. */
  onDone: (message: string) => void;
}

const TITLES: Record<FlowKind, (t: ContactKind) => string> = {
  primary: () => 'Make this your primary contact',
  add: (t) => `Add a ${LABEL[t]}`,
  change: (t) => `Change your ${LABEL[t]}`,
  remove: (t) => `Remove your ${LABEL[t]}`,
};

const DONE_MESSAGE: Record<FlowKind, string> = {
  primary: 'Primary contact updated.',
  add: 'Contact added.',
  change: 'Contact changed.',
  remove: 'Contact removed.',
};

export function ContactFlowDialog({ kind, contact, addType, lastVerified, onClose, onDone }: ContactFlowDialogProps) {
  const type: ContactKind = kind === 'add' ? (addType ?? 'EMAIL') : (contact?.type ?? 'EMAIL');
  const blockedAtStart = kind === 'remove' && lastVerified ? 'LAST_VERIFIED_CONTACT' : undefined;
  const [state, dispatch] = useReducer(
    (s: ReturnType<typeof initialFlow>, a: Parameters<typeof flowReducer>[1]) => flowReducer(s, a),
    undefined,
    () => initialFlow(kind, blockedAtStart)
  );
  // Values live in component state only: never persisted, logged, or put in a URL.
  const { countries } = useCountryOptions();
  const valueForm = useZodForm(valueSchema, { defaultValues: { type, email: '', phone: '' } });
  const codesForm = useZodForm(codesSchema, { defaultValues: { code: '', primaryCode: '', needPrimary: false } });
  const email = valueForm.watch('email');
  const phone = valueForm.watch('phone') || undefined;
  const newCode = codesForm.watch('code');
  const primaryCode = codesForm.watch('primaryCode');
  const setNewCode = (v: string) => codesForm.setValue('code', v);
  const setPrimaryCode = (v: string) => codesForm.setValue('primaryCode', v);
  const ve = valueForm.formState.errors;
  const ce = codesForm.formState.errors;
  const localError = ve.email?.message ?? ve.phone?.message ?? ce.code?.message ?? ce.primaryCode?.message ?? null;
  const [announce, setAnnounce] = useState('');
  const pollTimer = useRef<ReturnType<typeof setTimeout> | null>(null);
  const focusRef = useRef<HTMLElement | null>(null);
  const titleRef = useRef<HTMLHeadingElement | null>(null);

  // Per-target "send again" timers. Seeded from resendAfterSeconds, re-armed by every send and by
  // OTP_RATE_LIMITED answers (which carry retryAfterSeconds).
  const [timers, setTimers] = useState<Record<Target, { secs: number; stamp: number }>>({
    new: { secs: 0, stamp: 0 },
    current: { secs: 0, stamp: 0 },
  });
  const [resending, setResending] = useState<Record<Target, boolean>>({ new: false, current: false });
  const resendGuard = useRef<Record<Target, boolean>>({ new: false, current: false });
  const [resentNote, setResentNote] = useState('');
  const arm = (target: Target, secs: number) =>
    setTimers((t) => ({ ...t, [target]: { secs, stamp: t[target].stamp + 1 } }));
  const expiresLeft = useCountdown(state.challenge?.expiresInSeconds ?? 0, state.stamp);
  const lockLeft = useCountdown(state.lockedSeconds, state.error);
  const locked = state.lockedSeconds > 0 && lockLeft > 0;

  useEffect(() => () => {
    if (pollTimer.current) clearTimeout(pollTimer.current);
  }, []);

  // Focus management: move focus to the thing the buyer must act on in each phase.
  useEffect(() => {
    const t = setTimeout(() => (focusRef.current ?? titleRef.current)?.focus(), 0);
    return () => clearTimeout(t);
  }, [state.phase, state.stamp]);

  const valueInput = () => {
    const value = type === 'EMAIL' ? email.trim() : phone;
    if (!value) return null;
    return { value, type, ...(type === 'WHATSAPP' && regionOf(phone) ? { regionHint: regionOf(phone) } : {}) };
  };

  useEffect(() => {
    codesForm.setValue('needPrimary', Boolean(state.primary));
  }, [state.primary, codesForm]);

  const fail = useCallback((f: IdentityFailure) => {
    dispatch({
      type: 'failure',
      error: {
        code: f.errorCode,
        attemptsRemaining: f.attemptsRemaining,
        retryAfterSeconds: f.retryAfterSeconds,
        lockedUntil: f.lockedUntil,
      },
    });
  }, []);

  // ---- request a code ---------------------------------------------------------
  const request = async () => {
    setResentNote('');
    valueForm.clearErrors();
    // Add/change need a valid new contact before anything is sent.
    if ((kind === 'add' || kind === 'change') && !(await valueForm.trigger())) return;
    dispatch({ type: 'send' });
    if (kind === 'remove' || kind === 'primary') {
      const res = kind === 'remove' ? await api.requestRemoval(contact?.id ?? '') : await api.requestPrimary(contact?.id ?? '');
      if (!res.ok) return fail(res);
      dispatch({ type: 'challenge', challenge: res });
      arm('new', res.resendAfterSeconds);
      setAnnounce(`Code sent to ${res.maskedContact}`);
    } else {
      const input = valueInput();
      if (!input) return dispatch({ type: 'restart' });
      if (kind === 'add') {
        const res = await api.requestAdd(input);
        if (!res.ok) return fail(res);
        dispatch({ type: 'challenge', challenge: res });
        arm('new', res.resendAfterSeconds);
        setAnnounce(`Code sent to ${res.maskedContact}`);
      } else {
        const res = await api.requestChange(contact?.id ?? '', input);
        if (!res.ok) return fail(res);
        dispatch({ type: 'challenge', challenge: res.newContact, primary: res.currentContact, changeId: res.changeId });
        arm('new', res.newContact.resendAfterSeconds);
        arm('current', res.currentContact.resendAfterSeconds);
        setAnnounce(`Codes sent to ${res.newContact.maskedContact} and ${res.currentContact.maskedContact}`);
      }
    }
    setNewCode('');
    setPrimaryCode('');
  };

  // ---- send the code again -----------------------------------------------------
  const resend = async (target: Target) => {
    const ch = target === 'new' ? state.challenge : state.primary;
    if (!ch || resendGuard.current[target]) return; // never double-submit
    resendGuard.current[target] = true;
    setResending((r) => ({ ...r, [target]: true }));
    setResentNote('');
    codesForm.clearErrors();
    const res = await api.resendCode(
      kind === 'change' ? { changeId: state.changeId ?? '', target: target === 'new' ? 'NEW' : 'CURRENT' } : { challengeId: ch.challengeId }
    );
    resendGuard.current[target] = false;
    setResending((r) => ({ ...r, [target]: false }));
    if (!res.ok) {
      if (res.errorCode === 'OTP_RATE_LIMITED' && res.retryAfterSeconds) {
        // Too early: not a lock. Show when the button comes back and keep everything else usable.
        arm(target, res.retryAfterSeconds);
        setResentNote(`You can ask for another code in a moment. We’ll enable the button when it’s time.`);
        return;
      }
      return fail(res);
    }
    dispatch({ type: 'resent', target, challenge: res });
    arm(target, res.resendAfterSeconds);
    if (target === 'new') {
      setNewCode('');
    } else {
      setPrimaryCode('');
    }
    setResentNote(`Code sent again to ${res.maskedContact}`);
  };

  // ---- confirm ----------------------------------------------------------------
  const callConfirm = (): Promise<ClientResult<OpResult>> => {
    const ch = state.challenge;
    if (!ch) return Promise.resolve({ ok: false, status: 0, errorCode: 'PROOF_INVALID' } as IdentityFailure);
    if (kind === 'add') return api.confirmAdd(ch.challengeId, newCode);
    if (kind === 'remove') return api.confirmRemoval(ch.challengeId, newCode);
    if (kind === 'primary') return api.confirmPrimary(contact?.id ?? '', ch.challengeId, newCode);
    return api.confirmChange(state.changeId ?? '', newCode, state.primary ? primaryCode : undefined);
  };

  // APPLYING: the codes were right and the change is finishing in the background. Never re-send
  // the codes; ask myContacts until no change is pending.
  const pollApplying = async (attempt: number): Promise<void> => {
    const res = await api.listContacts();
    if (res.ok && !res.pendingChange) {
      dispatch({ type: 'done' });
      onDone(DONE_MESSAGE[kind]);
      return;
    }
    if (attempt < MAX_PENDING_POLLS) {
      pollTimer.current = setTimeout(() => void pollApplying(attempt + 1), 2000);
    }
  };

  const finish = (res: ClientResult<OpResult>) => {
    if (!res.ok) return fail(res);
    if (res.httpStatus === 202 || res.status === 'APPLYING') {
      dispatch({ type: 'pending', retryAfterSeconds: 2 });
      pollTimer.current = setTimeout(() => void pollApplying(1), 2000);
      return;
    }
    dispatch({ type: 'done' });
    onDone(DONE_MESSAGE[kind]);
  };

  const confirm = async () => {
    if (!(await codesForm.trigger())) return;
    dispatch({ type: 'verify' });
    finish(await callConfirm());
  };

  const err = state.error ? describeContactError(state.error.code) : null;
  const busy = state.phase === 'sending' || state.phase === 'verifying';
  const wrongCode = state.error?.code === 'OTP_INVALID';

  const errorText = err?.message ?? localError ?? null;
  const errorBox = (
    // Always mounted so assistive tech announces text changes inside it.
    <div role="alert" aria-live="assertive" data-testid="contact-flow-error">
      {errorText && (
        <Banner tone="error">
          {errorText}
          {wrongCode && state.attemptsLeft !== null && (
            <> {state.attemptsLeft} {state.attemptsLeft === 1 ? 'try' : 'tries'} left.</>
          )}
          {locked && <> Try again in {formatMMSS(lockLeft)}.</>}
        </Banner>
      )}
    </div>
  );

  const renderBody = (): { body: React.ReactNode; actions: React.ReactNode } => {
    if (state.phase === 'blocked') {
      return {
        body: (
          <div role="alert" data-testid="contact-flow-blocked">
            <Banner tone="warning">{err?.message}</Banner>
          </div>
        ),
        actions: <Button onClick={onClose} ref={(el) => { focusRef.current = el; }}>Close</Button>,
      };
    }
    if (state.phase === 'done') {
      return {
        body: <p role="status" data-testid="contact-flow-done">{DONE_MESSAGE[kind]}</p>,
        actions: <Button variant="accent" onClick={onClose} ref={(el) => { focusRef.current = el; }}>Done</Button>,
      };
    }
    if (state.phase === 'pending') {
      return {
        body: (
          <div className="m3-row" role="status" data-testid="contact-flow-pending">
            <CircularProgress label="Finishing" size="sm" />
            <p>We’re finishing this on our side. Your codes were right, nothing is lost, and your current contact keeps working meanwhile.</p>
          </div>
        ),
        actions: (
          <>
            <Button variant="text" onClick={onClose}>Close</Button>
            <Button variant="accent" onClick={() => void pollApplying(1)} ref={(el) => { focusRef.current = el; }} data-testid="contact-flow-check">
              Check again
            </Button>
          </>
        ),
      };
    }
    if ((state.phase === 'code' || state.phase === 'verifying') && state.challenge) {
      const expired = expiresLeft === 0;
      return {
        body: (
          <Form form={codesForm} guardLeave={false} onSubmit={() => confirm()}>
            <div className="m3-stack" data-testid="contact-flow-code-step">
              <OtpRHF
                name="code"
                label={
                  kind === 'remove' || kind === 'primary'
                    ? `6 digit code sent to your primary contact ${state.challenge.maskedContact}`
                    : `6 digit code sent to ${state.challenge.maskedContact}`
                }
                disabled={busy || locked}
                autoFocus
              />
              {state.primary && (
                <OtpRHF name="primaryCode" label={`6 digit code sent to your current primary contact ${state.primary.maskedContact}`} disabled={busy || locked} />
              )}
              <p className="m3-muted" data-testid="contact-flow-timer">
                {expired ? 'This code has expired. Send a new one, or start over.' : `Expires in ${formatMMSS(expiresLeft)}`}
              </p>
              {errorBox}
              <div role="status" aria-live="polite" data-testid="contact-flow-resent">
                {resentNote && <p className="m3-muted">{resentNote}</p>}
              </div>
            </div>
          </Form>
        ),
        actions: (
          <>
            <ResendButton
              label={state.primary ? 'Send code again to the new contact' : 'Send code again'}
              secs={timers.new.secs}
              stamp={timers.new.stamp}
              busy={resending.new || busy || locked}
              inFlight={resending.new}
              onClick={() => void resend('new')}
              testId="contact-flow-resend"
              onReady={() => setAnnounce('You can send the code again.')}
            />
            {state.primary && (
              <ResendButton
                label="Send code again to your current contact"
                secs={timers.current.secs}
                stamp={timers.current.stamp}
                busy={resending.current || busy || locked}
                inFlight={resending.current}
                onClick={() => void resend('current')}
                testId="contact-flow-resend-current"
                onReady={() => setAnnounce('You can send the current contact code again.')}
              />
            )}
            {(locked || expired) && (
              <Button type="button" variant="text" onClick={() => dispatch({ type: 'restart' })} disabled={locked} data-testid="contact-flow-restart">
                Start over
              </Button>
            )}
            <Button variant="accent" onClick={() => void confirm()} loading={state.phase === 'verifying'} disabled={busy || locked || expired} data-testid="contact-flow-confirm">
              Confirm
            </Button>
          </>
        ),
      };
    }
    // input / sending
    return {
      body: (
        <>
          {kind === 'remove' || kind === 'primary' ? (
            <p>
              We’ll send a code to your primary contact to confirm you want to {kind === 'remove' ? 'remove' : 'make'}{' '}
              <strong>{contact?.valueMasked}</strong>{kind === 'primary' ? ' your primary contact' : ''}.
            </p>
          ) : (
            <div className="m3-stack">
              {kind === 'change' && (
                <p className="m3-muted">
                  Current: <strong>{contact?.valueMasked}</strong>. We’ll send a code to the new one
                  {contact?.primary ? ' and to the current one' : ' and to your primary contact'} to confirm it’s you. Your current contact keeps working until the new one is confirmed.
                </p>
              )}
              <Form form={valueForm} guardLeave={false} onSubmit={() => request()}>
                {type === 'EMAIL' ? (
                  <TextFieldRHF
                    name="email"
                    id="contact-flow-email"
                    data-testid="contact-flow-email"
                    label={kind === 'change' ? 'New email address' : 'Email address'}
                    density="form"
                    type="email"
                    autoComplete="email"
                    inputMode="email"
                  />
                ) : (
                  <PhoneRHF name="phone" countries={countries} id="contact-flow-phone" label={kind === 'change' ? 'New WhatsApp number' : 'WhatsApp number'} defaultCountry="ZM" />
                )}
              </Form>
            </div>
          )}
          {errorBox}
        </>
      ),
      actions: (
        <>
          <Button variant="text" onClick={onClose} type="button">Cancel</Button>
          <Button
            variant="accent"
            onClick={() => void request()}
            disabled={busy || locked}
            loading={state.phase === 'sending'}
            data-testid="contact-flow-send"
            ref={kind === 'remove' || kind === 'primary' ? (el) => { focusRef.current = el; } : undefined}
          >
            Send code
          </Button>
        </>
      ),
    };
  };

  const { body, actions } = renderBody();
  const desc =
    (kind === 'add' && 'We’ll send a one-time code to confirm it’s yours.') ||
    (kind === 'change' && 'Your account stays reachable on the current contact until the new one is confirmed.') ||
    (kind === 'primary' && 'Switching your primary contact is confirmed with a code, so only you can do it.') ||
    'You can only remove a contact if you keep another verified one.';

  return (
    <Dialog open onClose={onClose} title={TITLES[kind](type)} actions={actions}>
      <div className="m3-stack" data-testid="contact-flow">
        <p className="m3-muted" id="contact-flow-desc">{desc}</p>
        <span className="m3-sr-only" role="status" aria-live="polite" data-testid="contact-flow-announce">
          {announce}
        </span>
        {body}
      </div>
    </Dialog>
  );
}

/** "Send code again": disabled with a live countdown until the server's wait has passed. */
function ResendButton(props: {
  label: string;
  secs: number;
  stamp: number;
  busy: boolean;
  inFlight: boolean;
  onClick: () => void;
  testId: string;
  onReady: () => void;
}) {
  const left = useCountdown(props.secs, props.stamp);
  const prev = useRef(left);
  const { onReady } = props;
  useEffect(() => {
    // Announce once, when the wait ends; not on every tick.
    if (prev.current > 0 && left === 0) onReady();
    prev.current = left;
  }, [left, onReady]);
  const waiting = left > 0;
  return (
    <Button
      type="button"
      disabled={props.busy || waiting}
      loading={props.inFlight}
      onClick={props.onClick}
      aria-label={waiting ? `${props.label}, available in ${formatMMSS(left)}` : props.label}
      data-testid={props.testId}
    >
      {waiting ? `${props.label} (${formatMMSS(left)})` : props.label}
    </Button>
  );
}
