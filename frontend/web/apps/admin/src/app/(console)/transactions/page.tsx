import { redirect } from 'next/navigation';

export default function TransactionsIndex() {
  redirect('/transactions/payments');
}
