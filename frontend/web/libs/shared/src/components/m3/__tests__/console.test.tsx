// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Amount, EditableValue, QueueItem, ReadinessList, SettingRow, SplitLayout } from '../Console';
import { Icon, iconNames } from '../icons';
import { cx } from '../utils';

describe('Console', () => {
  it('Amount states the sign in text', () => {
    const { rerender } = render(<Amount value={5} formatted="K 5" />);
    expect(screen.getByText(/\+K 5/)).toBeInTheDocument();
    rerender(<Amount value={-5} formatted="K 5" />);
    expect(screen.getByText(/−K 5/)).toBeInTheDocument();
  });
  it('SettingRow renders label, description and control', () => {
    render(<SettingRow label="Require approval" description="Events need review" control={<button>Toggle</button>} />);
    expect(screen.getByText('Require approval')).toBeInTheDocument();
    expect(screen.getByRole('button', { name: 'Toggle' })).toBeInTheDocument();
  });
  it('QueueItem is a labelled article with actions', () => {
    render(<QueueItem title="Acme Ltd" status="PENDING_REVIEW" submitted="2d ago" actions={<button>Review</button>} />);
    expect(screen.getByRole('article', { name: 'Acme Ltd' })).toBeInTheDocument();
    expect(screen.getByText('Pending review')).toBeInTheDocument();
  });
  it('ReadinessList marks done and todo items', () => {
    render(<ReadinessList items={[{ id: 'a', label: 'Has tier', done: true }, { id: 'b', label: 'Has venue', done: false, hint: 'Add a venue' }]} />);
    expect(screen.getByRole('list', { name: 'Readiness checklist' })).toBeInTheDocument();
    expect(screen.getByText('Add a venue')).toBeInTheDocument();
  });
  it('EditableValue validates and saves', async () => {
    const save = vi.fn();
    render(<EditableValue label="Slug" value="acme" onSave={save} validate={(d) => (d.length < 3 ? 'Too short' : undefined)} />);
    await userEvent.click(screen.getByRole('button', { name: /edit/i }));
    const input = screen.getByRole('textbox', { name: 'Slug' });
    await userEvent.clear(input);
    await userEvent.type(input, 'ab');
    expect(screen.getByRole('alert')).toHaveTextContent('Too short');
    await userEvent.type(input, 'c');
    await userEvent.click(screen.getByRole('button', { name: /save/i }));
    expect(save).toHaveBeenCalledWith('abc');
  });
  it('SplitLayout labels the aside and can hide it', () => {
    const { rerender } = render(<SplitLayout main={<p>main</p>} aside={<p>preview</p>} asideLabel="Live preview" />);
    expect(screen.getByLabelText('Live preview')).toBeInTheDocument();
    rerender(<SplitLayout main={<p>main</p>} aside={<p>preview</p>} asideLabel="Live preview" asideHidden />);
    expect(screen.queryByText('preview')).toBeNull();
  });
});

describe('icons and utils', () => {
  it('icons are decorative unless labelled', () => {
    const { container, rerender } = render(<Icon name="add" />);
    expect(container.querySelector('svg')).toHaveAttribute('aria-hidden', 'true');
    rerender(<Icon name="add" label="Add" />);
    expect(screen.getByRole('img', { name: 'Add' })).toBeInTheDocument();
  });
  it('every named icon renders a path', () => {
    for (const n of iconNames) {
      const { container, unmount } = render(<Icon name={n} />);
      expect(container.querySelector('svg')?.innerHTML.length, n).toBeGreaterThan(0);
      unmount();
    }
  });
  it('cx joins truthy parts', () => {
    expect(cx('a', false, null, undefined, 'b')).toBe('a b');
  });
});
