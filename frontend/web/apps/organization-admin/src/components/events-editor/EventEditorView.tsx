'use client';

import { useMemo, useRef, useState, type ReactNode } from 'react';
import { useFormState } from 'react-hook-form';
import {
  Banner,
  Button,
  ConfirmDialog,
  PageHeader,
  SaveBar,
  Skeleton,
  Tabs,
} from '@pml.tickets/shared/components/m3';
import { Status } from '@/components/console/Status';
import { BasicsTab } from './BasicsTab';
import { DesignTab } from './DesignTab';
import { PolicyTab } from './PolicyTab';
import { PreviewPane } from './PreviewPane';
import { PublishTab } from './PublishTab';
import { SubmitDialog } from './SubmitDialog';
import { TiersTab } from './TiersTab';
import { VenueTab } from './VenueTab';
import { WhenTab } from './WhenTab';
import {
  TAB_IDS,
  TAB_LABELS,
  blockers as computeBlockers,
  checklist,
  isDateLocked,
  isEditLocked,
  lockReason,
  tabsNeedingAttention,
  type TabId,
} from './model';
import { tabsWithErrors, tiersAreValid } from './schema';
import { useEditorValues } from './useEditorValues';
import type { ApprovalInfo, EditorRules, ReferenceOptions } from './types';

export interface EventEditorViewProps {
  mode: 'create' | 'edit';
  eventId: string | null;
  status: string | null;
  rejectionReason: string | null;
  reference: ReferenceOptions;
  tab: TabId;
  onTab: (tab: TabId) => void;
  saving: boolean;
  submitting?: boolean;
  /** Error from the last save, shown above the form. */
  saveError?: string | null;
  loading?: boolean;
  loadError?: string | null;
  commissionPercent?: number;
  reviewHours?: number;
  rules?: EditorRules;
  ageRestrictions?: Array<{ code: string; name: string }>;
  approval?: ApprovalInfo | null;
  onCancelSchedule?: () => void;
  requireCommentsOnChanges?: boolean;
  maxPerOrder?: number;
  onBack: () => void;
  onDiscard: () => void;
  onSubmit: () => void;
  onPublish: () => void;
}

/** The full editor: header, step tabs, form, buyer preview and the sticky step bar. */
export function EventEditorView(p: EventEditorViewProps) {
  const form = useEditorValues();
  const { isDirty: dirty, errors } = useFormState();
  const anchor = useRef<HTMLDivElement>(null);
  /** Saving is a normal form submit, so validation, focus and server errors go through <Form>. */
  const onSave = () => anchor.current?.closest('form')?.requestSubmit();
  const [previewOpen, setPreviewOpen] = useState(true);
  const [leaveOpen, setLeaveOpen] = useState(false);
  const [submitOpen, setSubmitOpen] = useState(false);

  const locked = isEditLocked(p.status);
  const dateLocked = isDateLocked(p.status);
  const tiersValid = useMemo(() => tiersAreValid(form.tiers, p.maxPerOrder), [form.tiers, p.maxPerOrder]);
  const attention = useMemo(() => {
    const set = tabsNeedingAttention(form, tiersValid);
    tabsWithErrors(errors).forEach((t) => set.add(t));
    return set;
  }, [form, tiersValid, errors]);
  const items = checklist(form, tiersValid);
  const done = items.filter((i) => i.ok).length;
  const index = TAB_IDS.indexOf(p.tab);
  const last = index === TAB_IDS.length - 1;
  const categoryName = p.reference.categories.find((c) => c.id === form.categoryId)?.name;
  const bl = computeBlockers(form);
  const title = form.title.trim() || (p.mode === 'create' ? 'New event' : 'Untitled draft');
  const isResubmit = p.status === 'CHANGES_REQUESTED';

  const back = () => (dirty ? setLeaveOpen(true) : p.onBack());

  let body: ReactNode;
  switch (p.tab) {
    case 'basics':
      body = <BasicsTab categories={p.reference.categories} ageRestrictions={p.ageRestrictions ?? []} />;
      break;
    case 'when':
      body = <WhenTab dateLocked={dateLocked} />;
      break;
    case 'venue':
      body = <VenueTab provinces={p.reference.provinces} cities={p.reference.cities} />;
      break;
    case 'tiers':
      body = <TiersTab commissionPercent={p.commissionPercent} maxPerOrder={p.maxPerOrder} />;
      break;
    case 'policy':
      body = (
        <PolicyTab
          refundPolicies={p.rules?.refundPolicies ?? []}
          refundCutoffHours={p.rules?.refundCutoffHours}
          holdMinutes={p.rules?.holdMinutes}
          graceMinutes={p.rules?.graceMinutes}
          maxPerBooking={p.rules?.maxPerBooking}
        />
      );
      break;
    case 'design':
      body = <DesignTab />;
      break;
    default:
      body = (
        <PublishTab
          form={form}
          eventId={p.eventId}
          status={p.status}
          rejectionReason={p.rejectionReason}
          approval={p.approval ?? null}
          reviewHours={p.reviewHours}
          onCancelSchedule={p.onCancelSchedule}
          dirty={dirty}
          onGoToTab={p.onTab}
          onSubmit={() => setSubmitOpen(true)}
          onPublish={p.onPublish}
        />
      );
  }

  if (p.loadError) {
    return (
      <div data-testid="event-editor">
        <PageHeader title="Event" onBack={p.onBack} />
        <Banner tone="error" urgent title="Could not load this event.">
          {p.loadError}
        </Banner>
      </div>
    );
  }

  if (p.loading) {
    return (
      <div data-testid="event-editor">
        <PageHeader title="Event" onBack={p.onBack} />
        <div className="m3-stack" role="status" aria-label="Loading event" data-testid="loading">
          <Skeleton />
          <Skeleton />
          <Skeleton />
        </div>
      </div>
    );
  }

  return (
    <div data-testid="event-editor" ref={anchor}>
      <PageHeader
        title={title}
        subtitle={
          <>
            {p.status ? <Status status={p.status} /> : null}{' '}
            {[categoryName, form.city].filter(Boolean).join(' · ') || (p.mode === 'create' ? 'Fill in the steps, then save the draft.' : '')}
          </>
        }
        onBack={back}
        backLabel="Back to events"
        actions={
          <Button variant="tonal" icon="eye" onClick={() => setPreviewOpen((o) => !o)} aria-pressed={previewOpen}>
            {previewOpen ? 'Hide preview' : 'Show preview'}
          </Button>
        }
      />

      {locked ? (
        <Banner tone="info" title="View only.">
          {lockReason(p.status)}
        </Banner>
      ) : null}
      {p.saveError ? (
        <Banner tone="error" urgent>
          {p.saveError}
        </Banner>
      ) : null}

      <Tabs
        label="Event sections"
        sticky
        value={p.tab}
        onChange={(id) => p.onTab(id as TabId)}
        tabs={TAB_IDS.map((id, i) => ({ id, label: TAB_LABELS[id], number: i + 1, warning: attention.has(id) }))}
      />

      <div className={previewOpen ? 'm3-sheet-split oc-section' : 'oc-section'}>
        <div className="m3-stack" role="tabpanel" aria-label={TAB_LABELS[p.tab]}>
          {body}
        </div>
        {previewOpen ? (
          <aside className="m3-sheet-split__side" aria-label="Buyer preview">
            <PreviewPane form={form} categoryName={categoryName} refundPolicies={p.rules?.refundPolicies} />
          </aside>
        ) : null}
      </div>

      <div className="oc-stepbar" data-testid="step-bar">
        <div>
          <b>
            Step {index + 1} of {TAB_IDS.length}
          </b>{' '}
          · {TAB_LABELS[p.tab]}
          <div className="m3-muted">
            {done} of {items.length} setup items done
          </div>
        </div>
        <Button variant="outlined" disabled={index === 0} onClick={() => p.onTab(TAB_IDS[index - 1] as TabId)}>
          Back
        </Button>
        {last ? (
          <Button variant="filled" loading={p.submitting} disabled={p.saving || locked} onClick={dirty || !p.eventId ? onSave : () => setSubmitOpen(true)}>
            {dirty || !p.eventId ? 'Save draft' : isResubmit ? 'Resubmit for approval' : 'Submit for approval'}
          </Button>
        ) : (
          <Button variant="filled" onClick={() => p.onTab(TAB_IDS[index + 1] as TabId)}>
            Continue
          </Button>
        )}
      </div>

      <SaveBar
        hidden={!dirty || locked}
        message="You have unsaved changes"
        saving={p.saving}
        saveLabel={p.eventId ? 'Save changes' : 'Save draft'}
        onSave={onSave}
        onDiscard={p.onDiscard}
      />

      <ConfirmDialog
        open={leaveOpen}
        onClose={() => setLeaveOpen(false)}
        onConfirm={() => {
          setLeaveOpen(false);
          p.onBack();
        }}
        title="Discard unsaved changes?"
        description="You have edits that are not saved. If you leave now they will be lost."
        confirmLabel="Discard and leave"
        cancelLabel="Keep editing"
        danger
      />
      <SubmitDialog
        open={submitOpen}
        title={form.title}
        resubmit={isResubmit}
        blockers={bl}
        reviewHours={p.reviewHours}
        requireComments={p.requireCommentsOnChanges}
        submitting={p.submitting}
        onClose={() => setSubmitOpen(false)}
        onConfirm={() => {
          setSubmitOpen(false);
          p.onSubmit();
        }}
      />
    </div>
  );
}
