'use client';

import { useState } from 'react';
import { Banner, Button, ConfirmDialog, Icon, ErrorState, Skeleton, type IconName } from '@pml.tickets/shared/components/m3';
import { resolveError, type GraphQLLikeError } from '@pml.tickets/shared';
import { useBuyerAuth } from '@/lib/auth/session-context';
import { useInvitation } from '@pml.tickets/shared';
import { LinkBtn } from '@/components/LinkBtn';
import { SiteShell } from '@/components/shell/SiteShell';
import { fullDate, initials } from '@/lib/format';

const ORGANIZER_URL = process.env.NEXT_PUBLIC_ORGANIZER_URL ?? '';

function Result({ icon, tone, title, children, actions }: { icon: IconName; tone?: 'ok' | 'bad'; title: string; children: React.ReactNode; actions: React.ReactNode }) {
  return (
    <section className="m3-panel buyer-end" aria-labelledby="inv-title">
      <span className="buyer-end__icon" data-tone={tone === 'bad' ? 'bad' : undefined}>
        <Icon name={icon} />
      </span>
      <h2 id="inv-title" className="m3-page-title">{title}</h2>
      <p className="buyer-lead">{children}</p>
      <div className="m3-row buyer-center">{actions}</div>
    </section>
  );
}

/** Team invitation landing page: preview, accept or decline (signed in), expired and withdrawn states. */
export function InvitationClient({ token }: { token: string }) {
  const auth = useBuyerAuth();
  const inv = useInvitation(token);
  const [done, setDone] = useState<null | 'ACCEPTED' | 'DECLINED'>(null);
  const [error, setError] = useState<string | null>(null);
  const [declineOpen, setDeclineOpen] = useState(false);
  const back = <LinkBtn href="/" variant="filled">Back to events</LinkBtn>;

  const act = async (kind: 'ACCEPTED' | 'DECLINED') => {
    setError(null);
    try {
      if (kind === 'ACCEPTED') await inv.accept();
      else await inv.decline();
      setDone(kind);
      setDeclineOpen(false);
    } catch (e) {
      setDeclineOpen(false);
      setError(resolveError(e as GraphQLLikeError).message);
    }
  };

  const org = inv.invitation?.organizationName ?? 'the organization';
  let body: React.ReactNode;
  if (!token) {
    body = <Result icon="error" tone="bad" title="This invitation link is incomplete" actions={back}>Open the link from your invitation message again, or ask {org} to send a new one.</Result>;
  } else if (inv.loading && !inv.invitation) {
    body = <div className="m3-panel" aria-busy="true" aria-label="Loading invitation"><Skeleton width="60%" /><Skeleton width="80%" /></div>;
  } else if (done === 'ACCEPTED') {
    body = (
      <Result icon="check-circle" tone="ok" title={`You've joined ${org}`} actions={<>{ORGANIZER_URL ? <a className="m3-btn m3-state" data-variant="accent" href={ORGANIZER_URL}>Open organizer portal</a> : null}{back}</>}>
        You are now a <b>{inv.invitation?.proposedRole}</b>. Open the organizer portal to start working with the team.
      </Result>
    );
  } else if (done === 'DECLINED') {
    body = <Result icon="close" title="Invitation declined" actions={back}>You declined to join {org}. They have been told.</Result>;
  } else if (inv.error && !inv.invitation) {
    const r = resolveError(inv.error as unknown as GraphQLLikeError);
    body = r.action === 'retry' ? <ErrorState error={inv.error as unknown as GraphQLLikeError} onRetry={() => void inv.refetch()} /> : (
      <Result icon="error" tone="bad" title="This invitation is no longer valid" actions={back}>{r.message} Ask the organization to send a new one.</Result>
    );
  } else if (!inv.invitation) {
    body = <Result icon="error" tone="bad" title="This invitation has expired or was withdrawn" actions={back}>Invitations are valid for a limited time and the organization can withdraw them. Ask them to send a new one.</Result>;
  } else {
    const i = inv.invitation;
    body = (
      <section className="m3-panel m3-stack" aria-labelledby="inv-title">
        <div className="buyer-phead">
          <span className="m3-avatar" data-size="lg" aria-hidden="true">{initials(i.organizationName)}</span>
          <div>
            <span className="m3-eyebrow">Team invitation</span>
            <h2 id="inv-title" className="m3-page-title">{i.organizationName} invited you to join their team</h2>
          </div>
        </div>
        <dl className="m3-kv-grid">
          <div><dt>Organization</dt><dd>{i.organizationName}</dd></div>
          <div><dt>Proposed role</dt><dd>{i.proposedRole}</dd></div>
          <div><dt>Invited by</dt><dd>{i.inviterDisplayName}</dd></div>
          <div><dt>Expires</dt><dd>{fullDate(i.expiresAt)}</dd></div>
        </dl>
        {error ? <div role="alert"><Banner tone="error">{error}</Banner></div> : null}
        {auth.authenticated ? (
          <div className="m3-row">
            <Button variant="accent" loading={inv.busy} onClick={() => void act('ACCEPTED')}>Accept invitation</Button>
            <Button onClick={() => setDeclineOpen(true)}>Decline</Button>
          </div>
        ) : (
          <>
            <Banner tone="info">Sign in with the contact this invitation was sent to, so we can match it.</Banner>
            <div className="m3-row">
              <LinkBtn href={`/auth?next=${encodeURIComponent(`/invitations/accept?token=${token}`)}`} variant="accent">Sign in to accept</LinkBtn>
            </div>
          </>
        )}
      </section>
    );
  }

  return (
    <SiteShell>
      <div className="m3-site-wrap buyer-narrow buyer-page">{body}</div>
      <ConfirmDialog
        open={declineOpen}
        onClose={() => setDeclineOpen(false)}
        onConfirm={() => void act('DECLINED')}
        title="Decline this invitation?"
        description="The organization will be told that you declined. They can send you a new invitation later."
        confirmLabel="Decline invitation"
        cancelLabel="Go back"
        danger
        loading={inv.busy}
      />
    </SiteShell>
  );
}
