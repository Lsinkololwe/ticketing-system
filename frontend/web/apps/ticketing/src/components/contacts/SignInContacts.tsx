'use client';

import React, { useCallback, useEffect, useRef, useState } from 'react';
import { Banner, Button, CircularProgress, StatusPill } from '@pml.tickets/shared/components/m3';
import { cancelChange, listContacts } from '@/lib/contacts/client';
import { describeContactError, LAST_VERIFIED_EXPLANATION } from '@/lib/contacts/messages';
import type { ChangeKind, ContactKind, ContactView, PendingChange } from '@/lib/contacts/types';
import { ContactFlowDialog } from './ContactFlowDialog';
import type { FlowKind } from './flow';

type Load = 'loading' | 'ready' | 'error';
type Active = { kind: FlowKind; contact?: ContactView; addType?: ContactKind } | null;

const KIND_LABEL: Record<ContactKind, string> = { WHATSAPP: 'WhatsApp', EMAIL: 'Email' };
const CHANGE_LABEL: Record<ChangeKind, string> = {
  ADD: 'Adding a contact',
  CHANGE: 'Changing a contact',
  REMOVE: 'Removing a contact',
  PRIMARY: 'Switching your primary contact',
};

function formatWhen(iso: string): string {
  const d = new Date(iso);
  return Number.isNaN(d.getTime()) ? '' : d.toLocaleString('en-GB', { dateStyle: 'medium', timeStyle: 'short' });
}

export interface SignInContactsProps {
  /** Injected for tests; defaults to a full-page navigation. */
  navigate?: (url: string) => void;
}

export function SignInContacts({ navigate }: SignInContactsProps) {
  const [load, setLoad] = useState<Load>('loading');
  const [contacts, setContacts] = useState<ContactView[]>([]);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [active, setActive] = useState<Active>(null);
  const [status, setStatus] = useState('');
  const [actionError, setActionError] = useState<string | null>(null);
  const [pending, setPending] = useState<PendingChange | null>(null);
  const [cancelling, setCancelling] = useState(false);
  const headingRef = useRef<HTMLHeadingElement | null>(null);

  const toSignIn = useCallback(() => {
    const url = '/auth?next=%2Fprofile';
    if (navigate) navigate(url);
    else window.location.assign(url);
  }, [navigate]);

  const refresh = useCallback(async () => {
    const res = await listContacts();
    if (!res.ok) {
      if (res.errorCode === 'UNAUTHENTICATED' || res.status === 401) return toSignIn();
      setLoadError(describeContactError(res.errorCode).message);
      setLoad('error');
      return;
    }
    setContacts(res.contacts);
    setPending(res.pendingChange ?? null);
    setLoadError(null);
    setLoad('ready');
  }, [toSignIn]);

  useEffect(() => {
    void refresh();
  }, [refresh]);

  const verified = contacts.filter((c) => c.verifiedAt);
  const heldTypes = new Set(contacts.map((c) => c.type));
  const missing = (['EMAIL', 'WHATSAPP'] as ContactKind[]).filter((t) => !heldTypes.has(t));

  const closeDialog = () => {
    setActive(null);
    // The trigger may have disappeared (removal); the heading is a stable place to land.
    setTimeout(() => headingRef.current?.focus(), 0);
  };

  const onDone = (message: string) => {
    setStatus(message);
    setActionError(null);
    void refresh();
  };

  const cancelPending = async () => {
    if (!pending) return;
    setActionError(null);
    setCancelling(true);
    const res = await cancelChange(pending.changeId);
    setCancelling(false);
    if (!res.ok) {
      if (res.errorCode === 'UNAUTHENTICATED') return toSignIn();
      setActionError(describeContactError(res.errorCode).message);
      void refresh();
      return;
    }
    setStatus('The waiting change was cancelled. Your contacts are unchanged.');
    void refresh();
  };

  return (
    <section className="m3-panel m3-stack" aria-labelledby="contacts-heading" data-testid="sign-in-contacts">
      <h3 className="m3-card__title" id="contacts-heading" ref={headingRef} tabIndex={-1}>
        Sign-in contacts
      </h3>
      <p className="m3-muted">
        You sign in with a code sent to one of these. Keep at least one verified contact so you can always get in.
      </p>

      <div role="status" aria-live="polite" data-testid="contacts-status">
        {status && <p className="buyer-ok">{status}</p>}
      </div>
      {pending && (
        <div data-testid="contacts-pending">
          <Banner
            tone="warning"
            actions={
              <Button size="sm" disabled={cancelling} loading={cancelling} onClick={() => void cancelPending()} data-testid="contacts-cancel-change">
                Cancel change
              </Button>
            }
          >
            <strong>{CHANGE_LABEL[pending.kind]}</strong>
            {pending.newContactMasked && <> to <strong>{pending.newContactMasked}</strong></>} is waiting for confirmation
            {formatWhen(pending.expiresAt) && <>, expires {formatWhen(pending.expiresAt)}</>}.
            {pending.currentContactVerified && ' Your current contact code was accepted.'}
            {' '}{pending.attemptsRemaining} {pending.attemptsRemaining === 1 ? 'try' : 'tries'} left.
          </Banner>
        </div>
      )}
      <div role="alert" data-testid="contacts-action-error">
        {actionError && <Banner tone="error">{actionError}</Banner>}
      </div>

      {load === 'loading' && (
        <div className="m3-row" role="status" data-testid="contacts-loading" aria-busy="true">
          <CircularProgress label="Loading your contacts" size="sm" />
          <span>Loading your contacts…</span>
        </div>
      )}

      {load === 'error' && (
        <div role="alert" data-testid="contacts-load-error">
          <Banner tone="error" actions={<Button size="sm" onClick={() => { setLoad('loading'); void refresh(); }}>Try again</Button>}>
            {loadError}
          </Banner>
        </div>
      )}

      {load === 'ready' && (
        <>
          <ul className="buyer-contacts" data-testid="contacts-list">
            {contacts.map((c) => {
              const label = `${KIND_LABEL[c.type]} ${c.valueMasked}`;
              const isLastVerified = !!c.verifiedAt && verified.length <= 1;
              return (
                <li key={c.id} data-testid={`contact-${c.id}`} className="buyer-contact">
                  <div className="m3-row">
                    <StatusPill tone="info">{KIND_LABEL[c.type]}</StatusPill>
                    <b data-testid="contact-value">{c.valueMasked}</b>
                    {c.primary && <StatusPill tone="info">Primary</StatusPill>}
                    {c.verifiedAt ? <StatusPill tone="success">Verified</StatusPill> : <StatusPill tone="warning">Not verified</StatusPill>}
                  </div>
                  {isLastVerified && (
                    <p className="m3-muted" data-testid="contact-last-note">Your only verified contact.</p>
                  )}
                  <div className="m3-row">
                    {c.verifiedAt && !c.primary && (
                      <Button size="sm" onClick={() => setActive({ kind: 'primary', contact: c })} aria-label={`Make ${label} primary`}>
                        Make primary
                      </Button>
                    )}
                    <Button size="sm" onClick={() => setActive({ kind: 'change', contact: c })} aria-label={`Change ${label}`}>
                      Change
                    </Button>
                    <Button size="sm" danger onClick={() => setActive({ kind: 'remove', contact: c })} aria-label={`Remove ${label}`}>
                      Remove
                    </Button>
                  </div>
                </li>
              );
            })}
          </ul>

          {contacts.length === 0 && <p className="m3-muted">You have no contacts yet.</p>}

          {missing.length > 0 ? (
            <div className="m3-row">
              {missing.map((t) => (
                <Button key={t} variant="filled" onClick={() => setActive({ kind: 'add', addType: t })} data-testid={`contact-add-${t.toLowerCase()}`}>
                  {t === 'EMAIL' ? 'Add an email' : 'Add a WhatsApp number'}
                </Button>
              ))}
            </div>
          ) : (
            <p className="m3-muted">You have a WhatsApp number and an email. Change one if you need a different one.</p>
          )}
          <p className="m3-muted" data-testid="contacts-remove-rule">{LAST_VERIFIED_EXPLANATION}</p>
        </>
      )}

      {active && (
        <ContactFlowDialog
          key={`${active.kind}-${active.contact?.id ?? active.addType}`}
          kind={active.kind}
          contact={active.contact}
          addType={active.addType}
          lastVerified={
            active.kind === 'remove' && !!active.contact?.verifiedAt && verified.length <= 1
          }
          onClose={closeDialog}
          onDone={onDone}
        />
      )}
    </section>
  );
}
