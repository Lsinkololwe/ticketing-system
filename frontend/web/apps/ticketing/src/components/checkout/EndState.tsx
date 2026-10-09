import type { ReactNode } from 'react';
import { Icon, type IconName } from '@pml.tickets/shared/components/m3';

/** Full-width result panel for hold expired, released, failed and similar terminal states. */
export function EndState({ icon, tone, title, children, actions }: { icon: IconName; tone?: 'ok' | 'warn' | 'bad'; title: string; children: ReactNode; actions: ReactNode }) {
  return (
    <div className="m3-site-wrap buyer-narrow">
      <section className="m3-panel buyer-end" aria-labelledby="end-title">
        <span className="buyer-end__icon" data-tone={tone}>
          <Icon name={icon} />
        </span>
        <h2 id="end-title" className="m3-page-title">
          {title}
        </h2>
        <p className="buyer-lead">{children}</p>
        <div className="m3-row buyer-center">{actions}</div>
      </section>
    </div>
  );
}
