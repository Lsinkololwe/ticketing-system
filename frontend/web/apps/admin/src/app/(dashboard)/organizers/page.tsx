/**
 * organizers — one of the three views of `Admin - Users & Organizations.dc.html`.
 *
 * The design is a single surface whose rail switches between All users,
 * Organizers and Organizations; the navigation it declares has three separate
 * entries. Rendering the same workbench at each route satisfies both, and makes
 * the rail real navigation rather than local state.
 */

import { PeopleWorkbench } from '@/components/people/PeopleWorkbench';

export default function Page() {
  return <PeopleWorkbench view="organizers" />;
}
