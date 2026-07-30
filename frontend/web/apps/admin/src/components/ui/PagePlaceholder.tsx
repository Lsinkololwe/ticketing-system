'use client';

/**
 * PagePlaceholder — the standard "not built yet" screen.
 *
 * A composition of <PageHeader> + <EmptyState>, so an unbuilt page still reads
 * as part of the same system as a finished one. Copy is sentence case and
 * operational: it says what the page will do and that it is not ready, without
 * apologising or over-promising.
 */

import type { ReactNode } from 'react';
import { Box } from '@radix-ui/themes';
import { Hammer } from 'iconoir-react';
import { Badge } from './Badge';
import { EmptyState } from './EmptyState';
import { PageHeader } from './PageHeader';

export interface PagePlaceholderProps {
  title: string;
  description?: string;
  /** Iconoir icon, 14–24px. Defaults to a hammer. */
  icon?: ReactNode;
  comingSoon?: boolean;
}

export function PagePlaceholder({
  title,
  description,
  icon,
  comingSoon = true,
}: PagePlaceholderProps) {
  return (
    <Box>
      <PageHeader
        title={title}
        description={description}
        actions={
          comingSoon ? (
            <Badge color="accent" variant="soft">
              In development
            </Badge>
          ) : undefined
        }
      />

      <EmptyState
        size="lg"
        icon={icon ?? <Hammer style={{ width: 24, height: 24 }} />}
        title="This screen is not ready yet"
        description="It is being built. The data and controls for this area will appear here once the work lands."
      />
    </Box>
  );
}

export default PagePlaceholder;
