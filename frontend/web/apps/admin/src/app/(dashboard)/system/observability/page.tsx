'use client';

/**
 * Observability.
 *
 * <h2>Placeholder, deliberately</h2>
 * The design's admin navigation lists this surface, so the nav item exists and
 * must lead somewhere real rather than a 404. The screen itself is built in
 * task #34 from `Admin - Observability & Health.dc.html` — which is the
 * authority for its layout, not this file.
 *
 * <p>It shows an empty state rather than invented content: a placeholder that
 * fabricates rows is indistinguishable from a finished screen with bad data.
 */

import { Activity } from 'iconoir-react';
import { PagePlaceholder } from '@/components/ui';

export default function Page() {
  return (
    <PagePlaceholder
      title="Observability"
      description="Service health, queue depth and the signals that say whether the platform is actually working."
      icon={<Activity width={22} height={22} />}
    />
  );
}
