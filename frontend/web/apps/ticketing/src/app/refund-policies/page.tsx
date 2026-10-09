import { redirect } from 'next/navigation';

/** The refund policies live on the help page. */
export default function RefundPolicies() {
  redirect('/help#refund-policies');
}
