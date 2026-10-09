'use client';

import Link from 'next/link';
import type { ReactNode } from 'react';
import { Card, Icon, SectionHeader, type IconName } from '@pml.tickets/shared/components/m3';
import { PublicLayout, SiteFooter, SiteHeader, SiteTool } from '@pml.tickets/shared/layouts';
import { LinkBtn } from '@/components/console/LinkBtn';

export const FEATURES: Array<{ icon: IconName; title: string; text: string }> = [
  { icon: 'calendar', title: 'Create and publish events', text: 'Build an event with tiers, schedule and policies, then submit it for approval and go live.' },
  { icon: 'ticket', title: 'Sell tickets with mobile money', text: 'Buyers pay with MTN, Airtel or Zamtel. Every booking is tracked in real time.' },
  { icon: 'qr', title: 'Check guests in at the gate', text: 'Scan or enter ticket codes, admit manually with a reason and review conflicts.' },
  { icon: 'wallet', title: 'Get paid from escrow', text: 'Request payouts to a verified bank account or mobile wallet after the hold period.' },
  { icon: 'users', title: 'Work as a team', text: 'Invite managers, marketers and gate staff with roles that match their job.' },
  { icon: 'chart', title: 'Know how sales are going', text: 'Revenue by month, ticket mix by tier and check-in rate on one overview.' },
];

const STEPS = [
  { title: 'Apply', text: 'Tell us about your organization and upload your KYB documents.' },
  { title: 'Get approved', text: 'Our team reviews your application, usually within two working days.' },
  { title: 'Sell and get paid', text: 'Publish events, sell tickets and withdraw your earnings.' },
];

export function PublicFrame({ current, children }: { current?: 'features'; children: ReactNode }) {
  return (
    <PublicLayout
      mesh={false}
      header={
        <SiteHeader
          name="MyTicketZM Organizer"
          linkAs={Link}
          links={[
            { id: 'features', label: 'Features', href: '/features', current: current === 'features' },
            { id: 'signin', label: 'Sign in', href: '/login' },
          ]}
          tools={
            <SiteTool label="Apply to become an organizer" href="/login" tone="accent" linkAs={Link}>
              Apply
            </SiteTool>
          }
        />
      }
      footer={
        <SiteFooter
          linkAs={Link}
          columns={[
            { heading: 'Product', links: [{ label: 'Features', href: '/features' }, { label: 'Sign in', href: '/login' }] },
            { heading: 'Help', links: [{ label: 'Contact support', href: 'mailto:support@myticket.zm' }] },
          ]}
          legal="MyTicketZM. Event ticketing for Zambia."
        />
      }
    >
      <div className="m3-site-wrap">{children}</div>
    </PublicLayout>
  );
}

export function FeatureGrid() {
  return (
    <div className="oc-cols" role="list" aria-label="Features">
      {FEATURES.map((f) => (
        <Card key={f.title}>
          <div role="listitem" className="m3-stack">
            <Icon name={f.icon} />
            <h3 className="m3-card__title">{f.title}</h3>
            <p className="m3-muted">{f.text}</p>
          </div>
        </Card>
      ))}
    </div>
  );
}

export function LandingView() {
  return (
    <PublicFrame>
      <section className="oc-section" aria-labelledby="hero-title">
        <div className="m3-eyebrow">For event organizers</div>
        <h1 id="hero-title" className="m3-page-title">Sell out your next event</h1>
        <p className="m3-page-sub">
          Create events, sell tickets with mobile money, check guests in and get paid, all from one console.
        </p>
        <div className="m3-row oc-section">
          <LinkBtn href="/login" variant="filled" data-testid="landing-apply">Apply to become an organizer</LinkBtn>
          <LinkBtn href="/login" variant="outlined" data-testid="landing-signin">Sign in</LinkBtn>
        </div>
      </section>
      <section className="oc-section" aria-labelledby="features-title">
        <SectionHeader title="Everything you need to run an event" level={2} />
        <span id="features-title" className="m3-sr-only">Features</span>
        <FeatureGrid />
      </section>
      <section className="oc-section">
        <SectionHeader title="How it works" level={2} />
        <ol className="oc-cols" aria-label="How it works">
          {STEPS.map((s, i) => (
            <li key={s.title}>
              <Card>
                <div className="m3-eyebrow">Step {i + 1}</div>
                <h3 className="m3-card__title">{s.title}</h3>
                <p className="m3-muted">{s.text}</p>
              </Card>
            </li>
          ))}
        </ol>
      </section>
      <section className="oc-section">
        <Card>
          <SectionHeader title="Ready to get started?" level={3} actions={<LinkBtn href="/login" variant="filled">Start your application</LinkBtn>} />
        </Card>
      </section>
    </PublicFrame>
  );
}

export function FeaturesView() {
  return (
    <PublicFrame current="features">
      <section className="oc-section">
        <h1 className="m3-page-title">Features</h1>
        <p className="m3-page-sub">What the organizer console gives you.</p>
        <div className="oc-section"><FeatureGrid /></div>
        <div className="oc-section">
          <LinkBtn href="/login" variant="filled" data-testid="features-apply">Apply to become an organizer</LinkBtn>
        </div>
      </section>
    </PublicFrame>
  );
}
