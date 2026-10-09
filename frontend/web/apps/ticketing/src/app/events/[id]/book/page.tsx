import type { Metadata } from 'next';
import { CheckoutClient } from '@/components/checkout/CheckoutClient';
import { getCartFor } from '@/lib/server/cart';

export const metadata: Metadata = { title: 'Checkout | Showstop Tickets', robots: { index: false } };

/**
 * Server wrapper: resolves any tickets parked before sign-in. The purchase mutations are
 * additionally authorised server-side by /api/graphql and the gateway.
 */
export default async function BookPage({
  params,
  searchParams,
}: {
  params: Promise<{ id: string }>;
  searchParams: Promise<{ r?: string }>;
}) {
  const [{ id }, sp] = await Promise.all([params, searchParams]);
  const initialQuantities = await getCartFor(id);
  return <CheckoutClient eventId={id} initialQuantities={initialQuantities} reservationId={typeof sp.r === 'string' && sp.r ? sp.r : null} />;
}
