'use client';

import { Suspense } from 'react';
import { EventEditor } from '@/components/events-editor/EventEditor';

export default function NewEventPage() {
  return (
    <Suspense fallback={null}>
      <EventEditor />
    </Suspense>
  );
}
