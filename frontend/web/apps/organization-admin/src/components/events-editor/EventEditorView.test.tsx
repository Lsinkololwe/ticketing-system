import { fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { useState } from 'react';
import { Form, useZodForm } from '@pml.tickets/shared';
import { describe, expect, it, vi } from 'vitest';
import { EventEditorView, type EventEditorViewProps } from './EventEditorView';
import { emptyForm, newTier, type EditorForm, type TabId } from './model';
import { firstInvalidTab, makeEventSchema } from './schema';
import { RestProblemError } from '@pml.tickets/shared';


// The platform's reference lists, answered from test fixtures instead of Apollo (production code carries none).
vi.mock('@pml.tickets/shared/api/graphql/shared/reference', async (importOriginal) => {
  const actual = await importOriginal<typeof import('@pml.tickets/shared/api/graphql/shared/reference')>();
  const { fakeReferenceModule } = await import('@/test/reference-fixtures');
  return { ...actual, ...fakeReferenceModule() };
});

// Fixtures live in this test only.
const reference = {
  categories: [{ id: 'c1', name: 'Fixture Music' }],
  provinces: [{ id: 'p1', name: 'Fixture Province' }],
  cities: [{ id: 'ci1', name: 'Fixture City', province: 'Fixture Province' }],
};

function filled(): EditorForm {
  const f = emptyForm();
  return {
    ...f,
    title: 'Fixture Fest',
    categoryId: 'c1',
    description: 'A fixture description that is comfortably longer than forty characters.',
    bannerImageUrl: 'https://img.test/a.png',
    start: '2030-01-01T10:00',
    end: '2030-01-01T12:00',
    venue: 'Fixture Hall',
    city: 'Fixture City',
    capacity: '500',
    refundPolicy: 'FLEXIBLE',
    termsAndConditions: 'Entry is subject to venue rules and a bag search.',
    tiers: [newTier({ category: 'GENERAL', name: 'General', price: 10000, quantity: '200' })],
  };
}

type HarnessProps = Partial<EventEditorViewProps> & {
  initial?: EditorForm;
  initialTab?: TabId;
  onSave?: (v: EditorForm) => unknown;
  dirtyStart?: boolean;
};

function Harness({ initial, initialTab, onSave = vi.fn(), dirtyStart, ...over }: HarnessProps) {
  const schema = makeEventSchema({ now: () => new Date('2030-01-01T00:00:00') });
  const form = useZodForm(schema, { defaultValues: initial ?? emptyForm() });
  const [tab, setTab] = useState<TabId>(initialTab ?? 'basics');
  const locked = ['COMPLETED', 'CANCELLED', 'PENDING_APPROVAL', 'REJECTED'].includes(over.status ?? '');
  const props: EventEditorViewProps = {
    mode: 'create',
    eventId: null,
    status: null,
    rejectionReason: null,
    reference,
    tab,
    onTab: setTab,
    saving: false,
    onBack: vi.fn(),
    onDiscard: vi.fn(),
    onSubmit: vi.fn(),
    onPublish: vi.fn(),
    ...over,
  };
  return (
    <Form
      form={form}
      disabled={locked}
      guardLeave={false}
      onSubmit={async (v) => { await onSave(v as EditorForm); }}
      onInvalid={() => {
        const t = firstInvalidTab(schema, form.getValues());
        if (t) setTab(t);
      }}
    >
      {dirtyStart ? <button type="button" onClick={() => form.setValue('title', 'Changed', { shouldDirty: true })}>make dirty</button> : null}
      <EventEditorView {...props} />
    </Form>
  );
}

describe('EventEditorView', () => {
  it('renders the seven step tabs and the sticky step bar', () => {
    render(<Harness />);
    const tabs = screen.getByRole('tablist', { name: 'Event sections' });
    ['Basics', 'Date and schedule', 'Venue and access', 'Ticket tiers', 'Policies and checkout', 'E-ticket design', 'Publish'].forEach((n) =>
      expect(within(tabs).getByRole('tab', { name: new RegExp(n) })).toBeInTheDocument()
    );
    expect(screen.getByTestId('step-bar')).toHaveTextContent('Step 1 of 7');
    expect(screen.getByRole('heading', { level: 1, name: 'New event' })).toBeInTheDocument();
  });

  it('offers categories from reference data only', () => {
    render(<Harness />);
    const select = screen.getByLabelText('Category');
    expect(within(select).getByRole('option', { name: 'Fixture Music' })).toBeInTheDocument();
    expect(within(select).getAllByRole('option')).toHaveLength(2);
  });

  it('Continue and Back move between steps', () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: 'Continue' }));
    expect(screen.getByTestId('step-bar')).toHaveTextContent('Step 2 of 7');
    fireEvent.click(screen.getByRole('button', { name: 'Back' }));
    expect(screen.getByTestId('step-bar')).toHaveTextContent('Step 1 of 7');
  });

  it('adds a tier from a preset and shows commission and net', () => {
    render(<Harness initialTab="tiers" commissionPercent={5} />);
    expect(screen.getByText(/No tiers yet/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Add tier' }));
    fireEvent.click(screen.getByRole('menuitem', { name: /General/ }));
    expect(screen.getAllByText('General').length).toBeGreaterThan(0);
    fireEvent.change(screen.getByLabelText('Price'), { target: { value: '200' } });
    expect(screen.getByText(/commission 5% K 10\.00 · you receive K 190\.00/)).toBeInTheDocument();
  });

  it('shows no commission figure when the platform rate is unavailable', () => {
    const f = filled();
    f.tiers = [newTier({ category: 'GENERAL', name: 'General', price: 20000 })];
    render(<Harness initial={f} initialTab="tiers" />);
    expect(screen.getAllByText(/Not available yet: the commission rate/).length).toBeGreaterThan(0);
  });

  it('edits the running order and FAQs as rows', () => {
    const { unmount } = render(<Harness initialTab="when" />);
    fireEvent.change(screen.getByLabelText('Doors open'), { target: { value: '17:30' } });
    fireEvent.click(screen.getByRole('button', { name: 'Add item' }));
    fireEvent.change(screen.getByLabelText('Item 1'), { target: { value: 'Headline act' } });
    expect(screen.getByLabelText('Item 1')).toHaveValue('Headline act');
    fireEvent.click(screen.getByRole('button', { name: 'Remove item 1' }));
    expect(screen.getByText('No items yet.')).toBeInTheDocument();
    unmount();
    render(<Harness initialTab="policy" rules={{ refundPolicies: [{ value: 'STRICT', label: 'Strict', summary: 'No refunds late.' }], refundCutoffHours: 24, holdMinutes: 10, graceMinutes: 5, maxPerBooking: 8 }} />);
    expect(screen.getByText(/Refunds close 24 hours before the event/)).toBeInTheDocument();
    expect(screen.getByText(/Platform limit: 8 per booking/)).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Add question' }));
    expect(screen.getByLabelText('Question 1')).toBeInTheDocument();
  });

  it('uses only the platform refund policies and says when they are missing', () => {
    render(<Harness initialTab="policy" />);
    expect(screen.getByText(/Not available yet: the platform refund policies/)).toBeInTheDocument();
  });

  it('shows the approval history that the organizer can read', () => {
    render(<Harness initialTab="publish" status="PENDING_APPROVAL" approval={{ submittedAt: '2026-10-01T10:00:00Z', approvedAt: null, rejectedAt: null, deadline: '2026-10-03T10:00:00Z', publishAt: null, publishScheduled: false }} reviewHours={48} />);
    expect(screen.getByRole('list', { name: 'Approval history' })).toHaveTextContent('Submitted for approval');
    expect(screen.getByText(/typical review time 48 hours/)).toBeInTheDocument();
  });

  it('disables deleting a tier that has sales', () => {
    const f = filled();
    f.tiers = [newTier({ category: 'GENERAL', name: 'Sold', sold: 4 })];
    render(<Harness initial={f} initialTab="tiers" />);
    expect(screen.getByRole('button', { name: 'Delete Sold' })).toBeDisabled();
  });

  it('lists the three approval blockers on the publish tab and jumps to the fix', () => {
    render(<Harness initialTab="publish" />);
    const list = screen.getByRole('list', { name: 'Approval blockers' });
    expect(within(list).getByText('At least one active ticket tier')).toBeInTheDocument();
    expect(within(list).getByText('A venue name and city (or a virtual event link)')).toBeInTheDocument();
    expect(within(list).getByText('A total capacity greater than zero')).toBeInTheDocument();
    fireEvent.click(within(list).getAllByRole('button', { name: 'Fix' })[0] as HTMLElement);
    expect(screen.getByTestId('step-bar')).toHaveTextContent('Ticket tiers');
  });

  it('completes the checklist for a ready form and opens the submit dialog with the review time', () => {
    const onSubmit = vi.fn();
    render(<Harness initial={filled()} initialTab="publish" eventId="e1" status="DRAFT" reviewHours={48} onSubmit={onSubmit} />);
    expect(screen.getByText(/of 11 complete/)).toBeInTheDocument();
    fireEvent.click(screen.getAllByRole('button', { name: 'Submit for approval' })[0] as HTMLElement);
    expect(screen.getByRole('dialog')).toHaveTextContent('Typical review time: 48 hours.');
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('button', { name: 'Submit for approval' }));
    expect(onSubmit).toHaveBeenCalledTimes(1);
  });

  it('warns about blockers in the submit dialog and relabels it', () => {
    render(<Harness initialTab="publish" eventId="e1" status="DRAFT" />);
    fireEvent.click(screen.getAllByRole('button', { name: 'Submit for approval' })[0] as HTMLElement);
    expect(screen.getByRole('dialog')).toHaveTextContent('These items will stop an admin approving it');
    expect(within(screen.getByRole('dialog')).getByRole('button', { name: 'Submit anyway' })).toBeInTheDocument();
  });

  it('shows reviewer feedback and Resubmit when changes are requested', () => {
    render(<Harness initial={filled()} initialTab="publish" eventId="e1" status="CHANGES_REQUESTED" rejectionReason="Fixture reviewer comment" />);
    expect(screen.getByText('Changes requested.')).toBeInTheDocument();
    expect(screen.getByText('Fixture reviewer comment')).toBeInTheDocument();
    expect(screen.getAllByRole('button', { name: 'Resubmit for approval' }).length).toBeGreaterThan(0);
  });

  it('is read only while with the reviewer', () => {
    render(<Harness initial={filled()} eventId="e1" status="PENDING_APPROVAL" />);
    expect(screen.getByText(/with a platform reviewer/)).toBeInTheDocument();
    expect(screen.getByLabelText(/Event title/)).toBeDisabled();
  });

  it('locks dates on live events', () => {
    render(<Harness initial={filled()} initialTab="when" eventId="e1" status="PUBLISHED" />);
    expect(screen.getByLabelText(/Starts/)).toBeDisabled();
    expect(screen.getByText(/Dates are locked/, { selector: '.m3-banner *' })).toBeInTheDocument();
  });

  it('shows the save bar when dirty, confirms before leaving and saves through the form', async () => {
    const onBack = vi.fn();
    const onSave = vi.fn();
    render(<Harness dirtyStart initial={filled()} onBack={onBack} onSave={onSave} />);
    expect(screen.queryByRole('region', { name: 'Unsaved changes' })).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'make dirty' }));
    expect(screen.getByRole('region', { name: 'Unsaved changes' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Back to events' }));
    expect(onBack).not.toHaveBeenCalled();
    fireEvent.click(screen.getByRole('button', { name: 'Discard and leave' }));
    expect(onBack).toHaveBeenCalledTimes(1);
    fireEvent.click(within(screen.getByRole('region', { name: 'Unsaved changes' })).getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    expect(onSave.mock.calls[0]?.[0]).toMatchObject({ title: 'Changed', categoryId: 'c1' });
  });

  it('shows validation messages, jumps to the tab of the first error and focuses it', async () => {
    const onSave = vi.fn();
    render(<Harness dirtyStart initial={{ ...filled(), title: 'Fixture Fest', end: '2030-01-01T09:00' }} onSave={onSave} />);
    fireEvent.click(screen.getByRole('button', { name: 'make dirty' }));
    fireEvent.click(within(screen.getByRole('region', { name: 'Unsaved changes' })).getByRole('button', { name: 'Save draft' }));
    await waitFor(() => expect(screen.getByTestId('step-bar')).toHaveTextContent('Date and schedule'));
    expect(await screen.findAllByText('The end must be after the start.')).not.toHaveLength(0);
    await waitFor(() => expect(screen.getByLabelText(/Ends/)).toHaveFocus());
    expect(onSave).not.toHaveBeenCalled();
  });

  it('does not submit twice on a double click', async () => {
    let release: () => void = () => undefined;
    const onSave = vi.fn(() => new Promise<void>((r) => { release = r; }));
    render(<Harness dirtyStart initial={filled()} onSave={onSave} />);
    fireEvent.click(screen.getByRole('button', { name: 'make dirty' }));
    const save = within(screen.getByRole('region', { name: 'Unsaved changes' })).getByRole('button', { name: 'Save draft' });
    fireEvent.click(save);
    fireEvent.click(save);
    await waitFor(() => expect(onSave).toHaveBeenCalledTimes(1));
    release();
  });

  it('maps a server error onto the form banner', async () => {
    const onSave = vi.fn().mockRejectedValue(new RestProblemError(409, { status: 409, errorCode: 'TIER_SOLD_OUT', classification: 'FAILED_PRECONDITION' }));
    render(<Harness dirtyStart initial={filled()} onSave={onSave} />);
    fireEvent.click(screen.getByRole('button', { name: 'make dirty' }));
    fireEvent.click(within(screen.getByRole('region', { name: 'Unsaved changes' })).getByRole('button', { name: 'Save draft' }));
    expect(await screen.findByText('Those tickets have sold out.')).toBeInTheDocument();
  });

  it('toggles the buyer preview', () => {
    render(<Harness initial={filled()} />);
    expect(screen.getByRole('complementary', { name: 'Buyer preview' })).toBeInTheDocument();
    fireEvent.click(screen.getByRole('button', { name: 'Hide preview' }));
    expect(screen.queryByRole('complementary', { name: 'Buyer preview' })).toBeNull();
  });

  it('renders loading and error states', () => {
    const { unmount } = render(<Harness loading mode="edit" />);
    expect(screen.getByTestId('loading')).toBeInTheDocument();
    unmount();
    render(<Harness loadError="Network down" mode="edit" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Network down');
  });

  it('marks not-yet-available regions without inventing values', () => {
    render(<Harness initialTab="design" />);
    expect(screen.getByTestId('not-available')).toHaveTextContent('Not available yet');
  });

  it('uses spacing tokens for the split layout', () => {
    const { container } = render(<Harness initial={filled()} />);
    expect(container.querySelector('.m3-sheet-split')).not.toBeNull();
    expect(container.querySelector('.m3-sheet-split__side')).not.toBeNull();
  });
});
