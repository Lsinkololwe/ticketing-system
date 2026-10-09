'use client';

import { useMediaAssets } from '@pml.tickets/shared/api/admin/modules/media-ops';
import { ModuleFrame } from '@/components/console';
import { AllEventsTab } from './AllEventsTab';
import { CategoriesTab } from './CategoriesTab';
import { LocationsTab } from './LocationsTab';
import { MediaModerationTab, StockImagesTab } from './MediaTabs';

export type EventsTab = 'all' | 'categories' | 'locations' | 'media' | 'stock';

/** /events/[tab]: all events, categories, provinces and cities, media moderation, stock images. */
export function EventsPage({ tab }: { tab: EventsTab }) {
  const flagged = useMediaAssets({ status: 'FLAGGED', size: 1 });
  return (
    <ModuleFrame module="events" tab={tab} title="Events" subtitle="All events on the platform, plus categories and locations" tabCounts={{ media: flagged.error ? undefined : flagged.totalElements }}>
      {tab === 'all' ? <AllEventsTab /> : tab === 'categories' ? <CategoriesTab /> : tab === 'locations' ? <LocationsTab /> : tab === 'media' ? <MediaModerationTab /> : <StockImagesTab />}
    </ModuleFrame>
  );
}
