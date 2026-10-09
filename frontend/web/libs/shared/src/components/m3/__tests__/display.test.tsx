// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import {
  Avatar, Badge, Banner, BulkBar, Card, CardHeader, Chip, CircularProgress, Divider, EmptyState, ErrorState, ExpansionItem,
  FieldError, KeyValue, LinearProgress, List, ListItem, SaveBar, SegmentedButton, Skeleton, Stepper, StatusPill, SummaryLine, Tabs, Timeline, Toolbar,
} from '../Display';

describe('status and chips', () => {
  it('StatusPill humanises and keeps text', () => {
    render(<StatusPill status="PENDING_APPROVAL" />);
    expect(screen.getByText('Pending approval')).toBeInTheDocument();
  });
  it('StatusPill derives a tone', () => {
    render(<StatusPill status="PUBLISHED" />);
    expect(screen.getByText('Live')).toHaveAttribute('data-tone', 'success');
  });
  it('filter chip toggles aria-pressed', async () => {
    render(<Chip kind="filter" selected={false}>Music</Chip>);
    const c = screen.getByRole('button', { name: 'Music' });
    expect(c).toHaveAttribute('aria-pressed', 'false');
  });
  it('badge caps at max', () => {
    render(<Badge count={120} />);
    expect(screen.getByText('99+')).toBeInTheDocument();
  });
  it('avatar shows initials', () => {
    render(<Avatar name="Ann Banda" />);
    expect(screen.getByText('AB')).toBeInTheDocument();
  });
});

describe('SegmentedButton', () => {
  it('is a radiogroup with arrow-key selection', async () => {
    function H() {
      const [v, setV] = useState<'a' | 'b'>('a');
      return <SegmentedButton label="View" options={[{ value: 'a', label: 'Grid' }, { value: 'b', label: 'List' }]} value={v} onChange={setV} />;
    }
    render(<H />);
    expect(screen.getByRole('radiogroup', { name: 'View' })).toBeInTheDocument();
    screen.getByRole('radio', { name: 'Grid' }).focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('radio', { name: 'List' })).toBeChecked();
  });
});

describe('Tabs', () => {
  const tabs = [{ id: 'a', label: 'Overview' }, { id: 'b', label: 'Tiers', warning: true }, { id: 'c', label: 'Off', disabled: true }];
  it('roving tabindex, arrow navigation and labelled panel', async () => {
    render(<Tabs label="Event" tabs={tabs}>{(id) => <p>panel {id}</p>}</Tabs>);
    expect(screen.getByRole('tablist', { name: 'Event' })).toBeInTheDocument();
    const t1 = screen.getByRole('tab', { name: 'Overview' });
    expect(t1).toHaveAttribute('aria-selected', 'true');
    expect(t1).toHaveAttribute('tabindex', '0');
    expect(screen.getByRole('tab', { name: /Tiers/ })).toHaveAttribute('tabindex', '-1');
    t1.focus();
    await userEvent.keyboard('{ArrowRight}');
    expect(screen.getByRole('tab', { name: /Tiers/ })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('tabpanel')).toHaveTextContent('panel b');
  });
  it('calls onChange', async () => {
    const fn = vi.fn();
    render(<Tabs label="Event" tabs={tabs} onChange={fn} />);
    await userEvent.click(screen.getByRole('tab', { name: /Tiers/ }));
    expect(fn).toHaveBeenCalledWith('b');
  });
});

describe('surfaces and lists', () => {
  it('Card with header', () => {
    render(<Card><CardHeader title="Sales" subtitle="This month" /></Card>);
    expect(screen.getByRole('heading', { name: 'Sales' })).toBeInTheDocument();
  });
  it('List items are not interactive by themselves', () => {
    render(<List><ListItem headline="Row" support="sub" trailing={<button>Act</button>} /></List>);
    expect(screen.getAllByRole('listitem')).toHaveLength(1);
    expect(screen.getByRole('button', { name: 'Act' })).toBeInTheDocument();
  });
  it('KeyValue, SummaryLine, Timeline, Divider', () => {
    render(<><KeyValue items={[{ label: 'City', value: 'Lusaka' }]} /><SummaryLine label="Total" value="K 10" strong /><Timeline label="History" items={[{ id: '1', title: 'Created', time: 'now' }]} /><Divider /></>);
    expect(screen.getByText('Lusaka')).toBeInTheDocument();
    expect(screen.getByText('K 10')).toBeInTheDocument();
    expect(screen.getByRole('list', { name: 'History' })).toBeInTheDocument();
  });
  it('ExpansionItem toggles natively', async () => {
    const { container } = render(<ExpansionItem title="More">hidden body</ExpansionItem>);
    const d = container.querySelector('details') as HTMLDetailsElement;
    expect(d.open).toBe(false);
    await userEvent.click(screen.getByText('More'));
    expect(d.open).toBe(true);
  });
  it('Toolbar and BulkBar', () => {
    render(<><Toolbar label="Filters"><button>x</button></Toolbar><BulkBar count={3}><button>Publish</button></BulkBar></>);
    expect(screen.getByRole('group', { name: 'Filters' })).toBeInTheDocument();
    expect(screen.getByText(/3/)).toBeInTheDocument();
  });
});

describe('feedback', () => {
  it('Banner role depends on urgency', () => {
    const { rerender } = render(<Banner title="Heads up">text</Banner>);
    expect(screen.getByRole('status')).toBeInTheDocument();
    rerender(<Banner title="Down" tone="error" urgent>text</Banner>);
    expect(screen.getByRole('alert')).toBeInTheDocument();
  });
  it('EmptyState shows title and action', () => {
    render(<EmptyState title="No events" description="Create one" action={<button>Create</button>} />);
    expect(screen.getByText('No events')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Create' })).toBeInTheDocument();
  });
  it('ErrorState: retry only when the server says retryable', async () => {
    const retry = vi.fn();
    const { rerender } = render(<ErrorState error={{ message: 'x', extensions: { errorCode: 'TIER_SOLD_OUT', retryable: false } }} onRetry={retry} />);
    expect(screen.queryByRole('button', { name: 'Try again' })).toBeNull();
    rerender(<ErrorState error={{ message: 'x', extensions: { errorCode: 'PAYMENT_DECLINED', retryable: true } }} onRetry={retry} />);
    await userEvent.click(screen.getByRole('button', { name: 'Try again' }));
    expect(retry).toHaveBeenCalled();
  });
  it('ErrorState renders nothing without an error', () => {
    const { container } = render(<ErrorState error={null} />);
    expect(container).toBeEmptyDOMElement();
  });
  it('FieldError is an alert and hides when empty', () => {
    const { rerender, container } = render(<FieldError message="Bad" fieldPath="a" />);
    expect(screen.getByRole('alert')).toHaveTextContent('Bad');
    rerender(<FieldError message={undefined} fieldPath="a" />);
    expect(container).toBeEmptyDOMElement();
  });
  it('Skeleton is hidden from AT', () => {
    const { container } = render(<Skeleton />);
    expect(container.firstChild).toHaveAttribute('aria-hidden', 'true');
  });
});

describe('progress', () => {
  it('LinearProgress exposes progressbar values', () => {
    render(<LinearProgress value={40} label="Sold" />);
    const p = screen.getByRole('progressbar', { name: 'Sold' });
    expect(p).toHaveAttribute('aria-valuenow', '40');
  });
  it('CircularProgress exposes progressbar', () => {
    render(<CircularProgress value={70} label="Check-in" showValue />);
    expect(screen.getByRole('progressbar', { name: 'Check-in' })).toHaveAttribute('aria-valuenow', '70');
  });
  it('Stepper marks current step', () => {
    render(<Stepper steps={[{ id: 'a', label: 'Tickets' }, { id: 'b', label: 'Pay' }]} current={1} />);
    expect(screen.getByText('Pay').closest('li')).toHaveAttribute('aria-current', 'step');
  });
});

describe('SaveBar', () => {
  it('saves and discards; hidden renders nothing', async () => {
    const save = vi.fn();
    const discard = vi.fn();
    const { rerender, container } = render(<SaveBar message="Unsaved" onSave={save} onDiscard={discard} />);
    await userEvent.click(screen.getByRole('button', { name: 'Save changes' }));
    await userEvent.click(screen.getByRole('button', { name: /Discard/ }));
    expect(save).toHaveBeenCalled();
    expect(discard).toHaveBeenCalled();
    rerender(<SaveBar message="Unsaved" onSave={save} hidden />);
    expect(container).toBeEmptyDOMElement();
  });
});
