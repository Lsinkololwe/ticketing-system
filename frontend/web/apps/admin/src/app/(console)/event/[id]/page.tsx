import { EventDetailPage } from '@/features/events/EventDetailPage';

export default async function EventPage({ params }: { params: Promise<{ id: string }> }) {
  const { id } = await params;
  return <EventDetailPage eventId={id} />;
}
