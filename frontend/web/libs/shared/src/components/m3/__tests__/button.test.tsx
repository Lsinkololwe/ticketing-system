// @vitest-environment jsdom
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { Button, Fab, IconButton, LinkButton } from '../Button';

describe('Button', () => {
  it('renders a type=button with the chosen variant and size', () => {
    render(<Button variant="filled" size="sm">Save</Button>);
    const b = screen.getByRole('button', { name: 'Save' });
    expect(b).toHaveAttribute('type', 'button');
    expect(b).toHaveAttribute('data-variant', 'filled');
    expect(b).toHaveAttribute('data-size', 'sm');
  });
  it('fires onClick and is keyboard activatable', async () => {
    const fn = vi.fn();
    render(<Button onClick={fn}>Go</Button>);
    await userEvent.tab();
    await userEvent.keyboard('{Enter}');
    expect(fn).toHaveBeenCalledTimes(1);
  });
  it('blocks activation while loading and announces busy', async () => {
    const fn = vi.fn();
    render(<Button loading onClick={fn}>Pay</Button>);
    const b = screen.getByRole('button', { name: /Pay/ });
    expect(b).toHaveAttribute('aria-busy', 'true');
    await userEvent.click(b);
    expect(fn).not.toHaveBeenCalled();
  });
  it('does not fire when disabled', async () => {
    const fn = vi.fn();
    render(<Button disabled onClick={fn}>No</Button>);
    await userEvent.click(screen.getByRole('button'));
    expect(fn).not.toHaveBeenCalled();
  });
  it('marks danger', () => {
    render(<Button danger>Delete</Button>);
    expect(screen.getByRole('button')).toHaveAttribute('data-danger', 'true');
  });
});

describe('LinkButton / IconButton / Fab', () => {
  it('LinkButton renders a link', () => {
    render(<LinkButton href="/x">Open</LinkButton>);
    expect(screen.getByRole('link', { name: 'Open' })).toHaveAttribute('href', '/x');
  });
  it('IconButton is named by its label', () => {
    render(<IconButton icon="close" label="Close" />);
    expect(screen.getByRole('button', { name: 'Close' })).toBeInTheDocument();
  });
  it('Fab is named by its label', () => {
    render(<Fab icon="add" label="New event" />);
    expect(screen.getByRole('button', { name: 'New event' })).toBeInTheDocument();
  });
});
