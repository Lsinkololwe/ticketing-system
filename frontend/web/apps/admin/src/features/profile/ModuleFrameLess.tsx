'use client';

import type { ReactNode } from 'react';
import { PageHeader } from '@pml.tickets/shared/components/m3';
import { TopTools } from '@/components/console/TopTools';

/** Page chrome for routes that belong to no module (profile): header plus content, no role gate. */
export function ModuleFrameLess({ title, subtitle, children }: { title: string; subtitle?: string; children: ReactNode }) {
  return (
    <>
      <PageHeader title={title} subtitle={subtitle} actions={<TopTools />} />
      {children}
    </>
  );
}
