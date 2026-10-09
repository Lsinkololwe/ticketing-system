import { redirect } from 'next/navigation';

/** The event listing is the "Upcoming events" section of the home page. */
export default function EventsIndex() {
  redirect('/#events');
}
