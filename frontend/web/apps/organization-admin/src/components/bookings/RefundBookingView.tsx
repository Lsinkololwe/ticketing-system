'use client';

import { useEffect, useMemo } from 'react';
import { Banner, Card, CardHeader, DataTable, KeyValue, PageHeader, Skeleton } from '@pml.tickets/shared/components/m3';
import { Form, FormActions, TextAreaRHF, TextFieldRHF, useZodForm } from '@pml.tickets/shared';
import { refundBookingSchema } from './schemas';
import type { BookingGroup } from '@/lib/bookings/group';
import { isRefundable } from '@/lib/bookings/group';
import { kwacha } from '@/lib/bookings/format';
import { LinkBtn } from '@/components/console/LinkBtn';
import { Status } from '@/components/console/Status';

export interface RefundBookingViewProps {
  id: string;
  booking: BookingGroup | null;
  loading: boolean;
  /** Called with the chosen ticket numbers and the reason. */
  onSubmit: (ticketNumbers: string[], reason: string, amount: string | null) => void | Promise<void>;
  resultMessage?: string | null;
}

/** Process-refund page: choose tickets, give a reason. The policy amount is computed by the server. */
export function RefundBookingView({ id, booking: b, loading, onSubmit, resultMessage }: RefundBookingViewProps) {
  const eligible = b?.tickets.filter(isRefundable) ?? [];
  const maxRefundable = (b?.tickets ?? []).filter(isRefundable).reduce((a, t) => a + Number(t.price), 0);
  const schema = useMemo(() => refundBookingSchema(maxRefundable, (n) => kwacha(n, b?.currency)), [maxRefundable, b?.currency]);
  const form = useZodForm(schema, { defaultValues: { ticketNumbers: eligible.map((t) => t.ticketNumber), reason: '', amount: '' } });
  const eligibleKey = eligible.map((t) => t.ticketNumber).join(',');
  useEffect(() => {
    form.reset({ ticketNumbers: eligibleKey ? eligibleKey.split(',') : [], reason: form.getValues('reason'), amount: form.getValues('amount') });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eligibleKey]);
  const picked = form.watch('ticketNumbers');
  const chosen = new Set(picked);
  const chosenTickets = eligible.filter((t) => chosen.has(t.ticketNumber));
  const max = chosenTickets.reduce((a, t) => a + Number(t.price), 0);
  const enc = encodeURIComponent(id);

  return (
    <>
      <PageHeader
        title="Process refund"
        subtitle={b ? `${b.bookingNumber} · ${b.eventTitle}` : id}
        breadcrumbs={[{ label: 'Bookings', href: '/bookings' }, { label: b?.bookingNumber ?? id, href: `/bookings/${enc}` }, { label: 'Refund' }]}
      />
      {loading && !b ? (
        <div className="m3-stack" role="status" aria-label="Loading booking" data-testid="loading">
          <Skeleton />
          <Skeleton />
        </div>
      ) : (
        <div className="oc-cols--2-1">
          <Card>
            <CardHeader title="Refund details" />
            <Form form={form} onSubmit={(v) => onSubmit(v.ticketNumbers, v.reason, v.amount || null)} guardLeave={false} aria-label="Process refund">
              <div className="m3-stack">
                {resultMessage ? <Banner tone="success">{resultMessage}</Banner> : null}
                {b && eligible.length === 0 ? <Banner tone="warning">Nothing on this booking can be refunded.</Banner> : null}
                <KeyValue
                  columns
                  items={[
                    { label: 'Buyer', value: b?.buyerName ?? '—' },
                    { label: 'Maximum refundable', value: kwacha(max, b?.currency) },
                    { label: 'Tickets selected', value: String(chosenTickets.length) },
                  ]}
                />
                <p className="m3-muted">
                  The refund amount follows the event refund policy and the platform refund cutoff; the commission on refunded tickets is taken back. The
                  server applies the policy when you confirm.
                </p>
                <TextFieldRHF
                  name="amount"
                  label="Partial amount (optional)"
                  inputMode="decimal"
                  helperText="Leave empty to refund the policy amount. To refund part of a ticket, select that one ticket and enter the kwacha amount."
                />
                <TextAreaRHF name="reason" label="Reason" rows={3} />
                {form.formState.errors.ticketNumbers?.message ? <p role="alert">{form.formState.errors.ticketNumbers.message}</p> : null}
                <FormActions
                  submitLabel="Refund"
                  submittingLabel="Refunding…"
                  align="start"
                  leading={<LinkBtn href={`/bookings/${enc}`}>Back</LinkBtn>}
                />
              </div>
            </Form>
          </Card>
          <Card>
            <CardHeader title="Tickets on this booking" />
            <DataTable
              caption="Tickets on this booking"
              rows={b?.tickets ?? []}
              getRowId={(t) => t.ticketNumber}
              selectable
              selectedIds={chosen}
              onSelectionChange={(ids) =>
                form.setValue('ticketNumbers', [...ids].filter((i) => eligible.some((t) => t.ticketNumber === i)), { shouldValidate: true, shouldDirty: true })
              }
              columns={[
                { id: 'no', header: 'Ticket', rowHeader: true, cell: (t) => <span className="m3-mono">{t.ticketNumber}</span> },
                { id: 'price', header: 'Price', align: 'end', cell: (t) => kwacha(t.price, t.currency) },
                { id: 'st', header: 'Status', cell: (t) => <Status status={t.status} /> },
              ]}
            />
          </Card>
        </div>
      )}
    </>
  );
}
