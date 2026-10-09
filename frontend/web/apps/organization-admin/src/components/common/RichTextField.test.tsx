import { fireEvent, render, screen } from '@testing-library/react';
import { FormProvider, useForm } from 'react-hook-form';
import { describe, expect, it } from 'vitest';
import { RichTextField } from './RichTextField';

let latest = '';
function Harness({ initial = '', disabled = false }: { initial?: string; disabled?: boolean }) {
  const form = useForm({ defaultValues: { description: initial } });
  latest = form.watch('description');
  return (
    <FormProvider {...form}>
      <RichTextField name="description" label="Full description" maxLength={500} disabled={disabled} />
    </FormProvider>
  );
}

describe('RichTextField', () => {
  it('renders the design toolbar and the stored description', () => {
    render(<Harness initial="<p>Hello <b>world</b></p>" />);
    for (const t of ['Bold', 'Italic', 'Underline', 'Heading', 'Bulleted list', 'Numbered list', 'Quote', 'Clear formatting']) expect(screen.getByRole('button', { name: t })).toBeInTheDocument();
    expect(screen.getByRole('textbox', { name: 'Full description' })).toHaveTextContent('Hello world');
    expect(screen.getByText('11/500')).toBeInTheDocument();
  });
  it('stores only the sanitised subset, whatever lands in the surface', () => {
    render(<Harness />);
    const box = screen.getByRole('textbox', { name: 'Full description' });
    box.innerHTML = '<p onclick="x()">Hi</p><script>alert(1)</script><a href="javascript:1">link</a>';
    fireEvent.input(box);
    expect(latest).toBe('<p>Hi</p>link');
  });
  it('previews the formatted text and goes back to editing', () => {
    render(<Harness initial="<ul><li>one</li></ul>" />);
    fireEvent.click(screen.getByRole('button', { name: 'Preview' }));
    expect(screen.getByRole('listitem')).toHaveTextContent('one');
    expect(screen.queryByRole('textbox')).toBeNull();
    fireEvent.click(screen.getByRole('button', { name: 'Edit' }));
    expect(screen.getByRole('textbox', { name: 'Full description' })).toBeInTheDocument();
  });
  it('is read-only without toolbar buttons when disabled', () => {
    render(<Harness initial="<p>x</p>" disabled />);
    expect(screen.queryByRole('button', { name: 'Bold' })).toBeNull();
    expect(screen.getByRole('textbox')).toHaveAttribute('contenteditable', 'false');
  });
});
