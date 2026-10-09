import { QueueClient } from '@/components/checkout/QueueClient';

export default async function QueuePage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <QueueClient eventId={id} />;
}
