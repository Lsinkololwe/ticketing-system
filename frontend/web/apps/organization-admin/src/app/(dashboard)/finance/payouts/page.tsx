import { redirect } from 'next/navigation';

/** Payouts live at /finance in the console; keep old links working. */
export default function PayoutsRedirect() {
  redirect('/finance');
}
