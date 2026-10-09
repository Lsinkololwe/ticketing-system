'use client';

import { useRouter, useSearchParams } from 'next/navigation';
import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import { Form, useZodForm } from '@pml.tickets/shared';
import { usePublishEvent } from '@pml.tickets/shared/api/organization-admin/modules/events';
import { useSnackbar } from '@pml.tickets/shared/components/m3';
import { useEditorEvent, useEditorMutations, useEditorReferenceData } from '@/lib/api/event-editor';
import { useReferenceList, usePlatformRules } from '@/lib/api/platform';
import { EventEditorView } from './EventEditorView';
import type { ApprovalInfo, EditorRules } from './types';
import {
  TAB_IDS,
  accessibilityInput,
  createInput,
  emptyForm,
  formFromEvent,
  isDateLocked,
  isEditLocked,
  updateInput,
  type EditorForm,
  type TabId,
} from './model';
import { firstInvalidTab, makeEventSchema } from './schema';
import { syncTiers } from './saveTiers';

const isTab = (v: string | null): v is TabId => !!v && (TAB_IDS as readonly string[]).includes(v);

/** Container: loads data, owns the react-hook-form instance and talks to the API. Rendering lives in EventEditorView. */
export function EventEditor({ eventId }: { eventId?: string }) {
  const router = useRouter();
  const params = useSearchParams();
  const snackbar = useSnackbar();
  const { event, loading, error, refetch } = useEditorEvent(eventId);
  const ref = useEditorReferenceData();
  const ops = useEditorMutations();
  const { publish } = usePublishEvent();
  const { rules } = usePlatformRules();
  const ages = useReferenceList('AGE_RESTRICTION').items.map((a) => ({ code: a.code, name: a.name }));
  const tierCategories = useReferenceList('TICKET_TIER_CATEGORY').items.map((c) => c.code);
  const tierCategoryKey = tierCategories.join('|');
  const maxPerOrder = rules?.maxTicketsPerBooking;
  const editorRules: EditorRules = {
    refundPolicies: (rules?.refundPolicies ?? []).map((r) => ({ value: r.code, label: r.label, summary: r.summary })),
    refundCutoffHours: rules?.refundCutoffHours ?? null,
    holdMinutes: rules?.reservationHoldMinutes ?? null,
    graceMinutes: rules?.reservationGraceMinutes ?? null,
    maxPerBooking: maxPerOrder ?? null,
  };
  const approval: ApprovalInfo | null = event
    ? {
        submittedAt: event.submittedForApprovalAt,
        deadline: event.approvalDeadline,
        approvedAt: event.approvedAt,
        rejectedAt: event.rejectedAt,
        publishAt: event.publishAt,
        publishScheduled: event.publishScheduled,
      }
    : null;

  const status = event?.status ?? null;
  const dateLocked = isDateLocked(status);
  const schema = useMemo(() => makeEventSchema({ dateLocked, maxPerOrder, tierCategories }), [dateLocked, maxPerOrder, tierCategoryKey]); // eslint-disable-line react-hooks/exhaustive-deps
  const form = useZodForm(schema, { defaultValues: emptyForm() });
  const [tab, setTab] = useState<TabId>(() => (isTab(params?.get('tab') ?? null) ? (params?.get('tab') as TabId) : 'basics'));
  const [submitting, setSubmitting] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const loadedFor = useRef<string | null>(null);

  // A new event starts on the platform's first refund policy; there is no built-in default.
  const firstPolicy = rules?.refundPolicies[0]?.code;
  useEffect(() => {
    if (!eventId && firstPolicy && !form.getValues('refundPolicy')) form.setValue('refundPolicy', firstPolicy, { shouldDirty: false });
  }, [eventId, firstPolicy, form]);

  // Load the server state into the form once per event (and after each save, via reset()).
  useEffect(() => {
    if (event && loadedFor.current !== event.id) {
      loadedFor.current = event.id;
      form.reset(formFromEvent(event));
    }
  }, [event, form]);

  /** An invalid submit jumps to the step that holds the first invalid field. */
  const onInvalid = useCallback(() => {
    const target = firstInvalidTab(schema, form.getValues());
    if (target) setTab(target);
  }, [form, schema]);

  const save = useCallback(
    async (values: EditorForm): Promise<void> => {
      setSaveError(null);
      if (!eventId) {
        const res = await ops.createEvent(createInput(values, maxPerOrder));
        const id = res.data?.createEvent.id;
        if (!id) throw new Error('The event could not be created.');
        form.reset(values);
        snackbar.show('Draft saved');
        router.replace(`/events/${id}/edit?tab=${tab}`);
        return;
      }
      await ops.updateEvent(eventId, updateInput(values, { dateLocked, maxPerOrderLimit: maxPerOrder }));
      await ops.updateAccessibility(eventId, accessibilityInput(values.accessibility));
      await syncTiers(eventId, event?.ticketTiers ?? [], values.tiers, ops);
      const fresh = await refetch();
      const next = fresh.data?.event;
      form.reset(next ? formFromEvent(next) : values);
      snackbar.show('Changes saved');
    },
    [dateLocked, event, eventId, form, ops, refetch, router, snackbar, tab, maxPerOrder]
  );

  const submit = useCallback(async () => {
    if (!eventId) return;
    setSubmitting(true);
    try {
      await ops.submitForApproval(eventId);
      snackbar.show(rules ? `Submitted for approval. Typical review time: ${rules.approval.slaHours} hours.` : 'Submitted for approval.');
      await refetch();
    } catch (e) {
      setSaveError(e instanceof Error ? e.message : 'The event could not be submitted.');
    } finally {
      setSubmitting(false);
    }
  }, [eventId, ops, refetch, snackbar, rules]);

  const doPublish = useCallback(async () => {
    if (!eventId) return;
    const r = await publish(eventId);
    if (r.success) {
      snackbar.show('Event is live');
      await refetch();
    } else {
      setSaveError(r.message ?? r.errors[0] ?? 'The event could not be published.');
    }
  }, [eventId, publish, refetch, snackbar]);

  return (
    <Form
      form={form}
      onSubmit={(values) => save(values as EditorForm)}
      onInvalid={onInvalid}
      notify={(toast) => snackbar.show(toast)}
      disabled={isEditLocked(status)}
      aria-label="Event editor"
    >
      <EventEditorView
        mode={eventId ? 'edit' : 'create'}
        eventId={eventId ?? null}
        status={status}
        rejectionReason={event?.rejectionReason ?? null}
        reference={{ categories: ref.categories, provinces: ref.provinces, cities: ref.cities }}
        tab={tab}
        onTab={setTab}
        saving={form.formState.isSubmitting}
        submitting={submitting}
        saveError={saveError}
        loading={!!eventId && loading && !event}
        loadError={eventId && !event && error ? error.message : eventId && !loading && !event ? 'This event could not be found.' : null}
        commissionPercent={rules ? rules.commissionRate ?? rules.commissionDefault : undefined}
        reviewHours={rules?.approval.slaHours}
        requireCommentsOnChanges={rules?.approval.requireCommentsOnChangesRequested ?? false}
        maxPerOrder={maxPerOrder}
        rules={editorRules}
        ageRestrictions={ages}
        approval={approval}
        onCancelSchedule={() => {
          if (eventId) void ops.cancelScheduledPublish(eventId).then(() => { snackbar.show('Scheduled publish cancelled'); return refetch(); });
        }}
        onBack={() => router.push(eventId ? `/events/${eventId}` : '/events')}
        onDiscard={() => form.reset(event ? formFromEvent(event) : emptyForm())}
        onSubmit={() => void submit()}
        onPublish={() => void doPublish()}
      />
    </Form>
  );
}
