'use client';

import { Card, CardHeader } from '@pml.tickets/shared/components/m3';
import { NotAvailable } from '@/components/console/NotAvailable';

export function DesignTab() {
  return (
    <Card>
      <CardHeader title="E-ticket design" subtitle="Branding on the ticket buyers receive." />
      <NotAvailable what="E-ticket design options" />
    </Card>
  );
}
