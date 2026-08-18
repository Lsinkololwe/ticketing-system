'use client';

/**
 * Platform configuration.
 *
 * <h2>Placeholder, deliberately</h2>
 * The design's admin navigation lists this surface, so the nav item exists and
 * must lead somewhere real rather than a 404. The screen itself is built in
 * task #34 from `Admin - Platform Configuration.dc.html` — which is the
 * authority for its layout, not this file.
 *
 * <p>It shows an empty state rather than invented content: a placeholder that
 * fabricates rows is indistinguishable from a finished screen with bad data.
 */

import { Settings } from 'iconoir-react';
import { PagePlaceholder } from '@/components/ui';

export default function Page() {
  return (
    <PagePlaceholder
      title="Platform configuration"
      description="The closed registry of runtime-configurable keys, organization overrides, feature flags and kill switches."
      icon={<Settings width={22} height={22} />}
    />
  );
}
