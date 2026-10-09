import { redirect } from 'next/navigation';

/** Legacy path kept for existing links. */
export default function StatusRedirect() {
  redirect('/apply/status');
}
