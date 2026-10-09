import Link from 'next/link';
import { Banner } from '@pml.tickets/shared/components/m3';
import { SiteShell } from '@/components/shell/SiteShell';

export interface LegalSection {
  heading: string;
  body: string[];
}

/** Shared layout for the draft legal pages. The draft banner is part of the page, not optional. */
export function LegalPage({ title, updated, sections }: { title: string; updated: string; sections: LegalSection[] }) {
  return (
    <SiteShell>
      <article className="m3-site-wrap buyer-narrow buyer-page m3-stack">
        <Link className="buyer-back" href="/">
          ← Back to Showstop
        </Link>
        <h1 className="m3-page-title">{title}</h1>
        <div data-testid="legal-draft-banner" role="note">
          <Banner tone="warning" title="Draft for legal review.">
            This text is a placeholder and is not yet legal advice or a binding agreement. It will be replaced by the reviewed version before launch.
          </Banner>
        </div>
        <p className="m3-muted">Draft last updated {updated}</p>
        {sections.map((s) => {
          const id = `h-${s.heading.replace(/\W+/g, '-').toLowerCase()}`;
          return (
            <section key={s.heading} aria-labelledby={id} className="m3-stack">
              <h2 id={id} className="m3-card__title">
                {s.heading}
              </h2>
              {s.body.map((p) => (
                <p key={p} className="buyer-lead">
                  {p}
                </p>
              ))}
            </section>
          );
        })}
      </article>
    </SiteShell>
  );
}
