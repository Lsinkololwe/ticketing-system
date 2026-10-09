// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it, vi } from 'vitest';
import { Button } from '../Button';
import { ConfirmDialog, Dialog, Menu, RowMenu, SideSheet, SnackbarProvider, Tooltip, useSnackbar } from '../Overlays';

function DialogHarness({ side }: { side?: boolean }) {
  const [open, setOpen] = useState(false);
  return (
    <>
      <Button onClick={() => setOpen(true)}>Open</Button>
      {side ? (
        <SideSheet open={open} onClose={() => setOpen(false)} title="Details" actions={<Button>Done</Button>}>
          <input aria-label="inner" />
        </SideSheet>
      ) : (
        <Dialog open={open} onClose={() => setOpen(false)} title="Hello" actions={<Button>OK</Button>}>
          <input aria-label="inner" />
        </Dialog>
      )}
    </>
  );
}

describe.each([
  ['Dialog', false],
  ['SideSheet', true],
])('%s', (_n, side) => {
  it('is a labelled modal dialog', async () => {
    render(<DialogHarness side={side} />);
    expect(screen.queryByRole('dialog')).toBeNull();
    await userEvent.click(screen.getByRole('button', { name: 'Open' }));
    const d = screen.getByRole('dialog');
    expect(d).toHaveAttribute('aria-modal', 'true');
    expect(d).toHaveAccessibleName(side ? 'Details' : 'Hello');
  });
  it('closes on Escape and returns focus to the opener', async () => {
    render(<DialogHarness side={side} />);
    const opener = screen.getByRole('button', { name: 'Open' });
    await userEvent.click(opener);
    await userEvent.keyboard('{Escape}');
    expect(screen.queryByRole('dialog')).toBeNull();
    expect(opener).toHaveFocus();
  });
  it('traps Tab inside', async () => {
    render(<DialogHarness side={side} />);
    await userEvent.click(screen.getByRole('button', { name: 'Open' }));
    for (let i = 0; i < 6; i++) await userEvent.tab();
    expect(screen.getByRole('dialog')).toContainElement(document.activeElement as HTMLElement);
  });
});

describe('ConfirmDialog', () => {
  it('is an alertdialog and reports the choice', async () => {
    const onConfirm = vi.fn();
    const onClose = vi.fn();
    render(<ConfirmDialog open onClose={onClose} onConfirm={onConfirm} title="Delete event?" description="This cannot be undone." confirmLabel="Delete" />);
    expect(screen.getByRole('alertdialog')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Delete' }));
    expect(onConfirm).toHaveBeenCalled();
  });
});

describe('Menu', () => {
  it('opens, selects with keyboard and closes', async () => {
    const onSelect = vi.fn();
    render(<Menu label="Actions" trigger={(p) => <button {...p}>Menu</button>} items={[{ id: 'a', label: 'Edit', onSelect }]} />);
    await userEvent.click(screen.getByRole('button', { name: 'Menu' }));
    expect(screen.getByRole('menu')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('menuitem', { name: 'Edit' }));
    expect(onSelect).toHaveBeenCalled();
    expect(screen.queryByRole('menu')).toBeNull();
  });
});

describe('RowMenu', () => {
  it('has an accessible trigger', () => {
    render(<RowMenu label="Row actions" items={[{ id: 'a', label: 'Edit', onSelect: () => undefined }]} />);
    expect(screen.getByRole('button', { name: 'Row actions' })).toBeInTheDocument();
  });
});

describe('Tooltip', () => {
  it('shows content on focus', async () => {
    render(<Tooltip content="Helpful"><button>Hover</button></Tooltip>);
    await userEvent.tab();
    expect(await screen.findByRole('tooltip')).toHaveTextContent('Helpful');
  });
});

describe('Snackbar', () => {
  it('announces a message with an action', async () => {
    const onAction = vi.fn();
    function Trigger() {
      const sb = useSnackbar();
      return <button onClick={() => sb.show({ message: 'Saved', actionLabel: 'Undo', onAction })}>go</button>;
    }
    render(<SnackbarProvider><Trigger /></SnackbarProvider>);
    await userEvent.click(screen.getByRole('button', { name: 'go' }));
    expect(await screen.findByText('Saved')).toBeInTheDocument();
    await userEvent.click(screen.getByRole('button', { name: 'Undo' }));
    expect(onAction).toHaveBeenCalled();
  });
});
