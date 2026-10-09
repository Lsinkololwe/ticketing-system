'use client';

import { ExpansionItem, Tabs, Timeline } from '@pml.tickets/shared/components/m3';
import { effectivePrice, hoursText, richView, tierState, type BuyerPlatformRules, type EventPageRow, type EventTierRow } from '@pml.tickets/shared';
import { clock, initials, money } from '@/lib/format';
import { DEFAULT_CANCELLATION, DEFAULT_TERMS, GENERAL_FAQ } from './copy';
import { policyView } from './policy';

/** Shown where the organizer left a detail out. This is a content gap, not a missing feature. */
function Unshared({ what }: { what: string }) {
  return <p className="m3-muted">{what}</p>;
}

function mapsHref(e: EventPageRow) {
  const q = [e.locationName, e.locationAddress, e.cityName].filter(Boolean).join(', ');
  return `https://www.google.com/maps/search/?api=1&query=${encodeURIComponent(q)}`;
}

function Overview({ event, tiers, now }: { event: EventPageRow; tiers: EventTierRow[]; now: number }) {
  return (
    <div className="m3-stack">
      <h3 className="m3-card__title">About this event</h3>
      <div className="buyer-lead" dangerouslySetInnerHTML={{ __html: richView(event.description) }} />
      <div className="buyer-chips2">
        {event.category ? <span>{event.category.name}</span> : null}
        <span>Pay with mobile money</span>
        <span>Tickets by SMS and QR</span>
      </div>
      <div className="buyer-org">
        <span className="m3-avatar" data-size="lg" aria-hidden="true">
          {initials(event.organizerName)}
        </span>
        <div>
          <b>{event.organizerName}</b>
          {event.organization?.verified ? (
            <>
              {' '}
              <span className="buyer-pill" data-tone="ok">
                Verified organizer
              </span>
            </>
          ) : null}
          <div className="m3-muted">
            {event.organization
              ? `${event.organization.publishedEventCount} event${event.organization.publishedEventCount === 1 ? '' : 's'} on Showstop`
              : 'Event organizer'}
          </div>
        </div>
      </div>
      <h3 className="m3-card__title">Ticket categories at a glance</h3>
      {tiers.length ? (
        <div className="m3-table-wrap" role="region" aria-label="Ticket categories" tabIndex={0}>
          <table className="m3-table">
            <thead>
              <tr>
                <th scope="col">Category</th>
                <th scope="col">Price</th>
                <th scope="col">Includes</th>
                <th scope="col">Status</th>
              </tr>
            </thead>
            <tbody>
              {tiers.map((t) => {
                const s = tierState(t, now);
                return (
                  <tr key={t.id}>
                    <td>
                      <b>{t.name}</b>
                    </td>
                    <td className="m3-num">{money(effectivePrice(t, now))}</td>
                    <td>{t.benefits?.join(', ') || t.description || '—'}</td>
                    <td>
                      {s === 'SOLD' ? 'Sold out' : s === 'SOON' ? 'Not on sale yet' : s === 'ENDED' ? 'Sales closed' : t.availableQuantity < 20 ? 'Low stock' : 'Available'}
                    </td>
                  </tr>
                );
              })}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="m3-muted">No ticket tiers are on sale yet.</p>
      )}
    </div>
  );
}

function Yes({ on, label }: { on: boolean; label: string }) {
  return (
    <li data-on={on ? 'true' : 'false'}>
      <span aria-hidden="true">{on ? '✓' : '✕'}</span> {label}
      {on ? '' : <span className="m3-muted"> (not available)</span>}
    </li>
  );
}

function Venue({ event }: { event: EventPageRow }) {
  const a = event.accessibility;
  const doors = event.doorsOpenAt ? Date.parse(event.doorsOpenAt) : null;
  const lead = doors === null ? 0 : Math.round((Date.parse(event.eventDateTime) - doors) / 60_000);
  return (
    <div className="m3-stack">
      <h3 className="m3-card__title">{event.locationName ?? 'Venue'}</h3>
      <p className="buyer-lead">
        {[event.locationAddress, event.cityName].filter(Boolean).join(', ')}.{' '}
        <a className="m3-link" href={mapsHref(event)} target="_blank" rel="noopener noreferrer">
          Open in maps
        </a>
      </p>
      <div className="buyer-info3">
        <div>
          <h4>Getting there</h4>
          {event.gettingThere ? <p className="m3-muted">{event.gettingThere}</p> : <Unshared what="The organizer has not shared directions." />}
        </div>
        <div>
          <h4>Parking</h4>
          {event.parkingInfo ? <p className="m3-muted">{event.parkingInfo}</p> : <Unshared what="The organizer has not shared parking details." />}
        </div>
        <div>
          <h4>Doors</h4>
          {event.doorsOpenAt ? (
            <p className="m3-muted">
              Doors open at {clock(event.doorsOpenAt)}, {lead > 0 ? `${lead} minutes before the start` : 'at the start'}. Allow time for security checks.
            </p>
          ) : (
            <Unshared what="The organizer has not shared the door time." />
          )}
        </div>
      </div>
      <h3 className="m3-card__title">Accessibility</h3>
      {a ? (
        <>
          <ul className="buyer-acc">
            <Yes on={a.wheelchairAccessible} label={`Wheelchair accessible${a.wheelchairAccessible && a.wheelchairSeatsAvailable ? `, ${a.wheelchairSeatsAvailable} wheelchair spaces` : ''}`} />
            <Yes on={a.signLanguageInterpreter} label="Sign language interpreter" />
            <Yes on={a.hearingLoopAvailable} label="Hearing loop" />
            <Yes on={a.accessibleParking} label="Accessible parking" />
            <Yes on={a.accessibleRestrooms} label="Accessible restrooms" />
            <Yes on={a.assistanceDogsAllowed} label="Assistance dogs allowed" />
          </ul>
          {a.additionalNotes ? <p className="m3-muted">{a.additionalNotes}</p> : null}
        </>
      ) : (
        <Unshared what="The organizer has not shared accessibility details." />
      )}
    </div>
  );
}

function Know({ event, rules }: { event: EventPageRow; rules: BuyerPlatformRules | null }) {
  const pol = policyView(rules, event.refundPolicy);
  const faqs = [...(event.faqs ?? []).map((f) => [f.question, f.answer] as const), ...GENERAL_FAQ];
  return (
    <div className="m3-stack">
      <h3 className="m3-card__title">Good to know</h3>
      <h4 className="buyer-sub">Frequently asked questions</h4>
      {faqs.map(([q, a]) => (
        <ExpansionItem key={q} title={q}>
          <p>{a}</p>
        </ExpansionItem>
      ))}
      <h4 className="buyer-sub">Policies</h4>
      <ExpansionItem title="Age restriction">{event.ageRestriction ? <p>{event.ageRestriction}</p> : <Unshared what="The organizer has not set an age restriction." />}</ExpansionItem>
      <ExpansionItem title="Bag policy">{event.bagPolicy ? <p>{event.bagPolicy}</p> : <Unshared what="The organizer has not shared a bag policy." />}</ExpansionItem>
      <ExpansionItem title={`Refund policy: ${pol.name}`} defaultOpen>
        {pol.summary ? <p>{pol.summary}</p> : <Unshared what="The refund rules for this event are not available right now." />}
        {pol.lines.length ? (
          <ul className="buyer-plain">
            {pol.lines.map((l) => (
              <li key={l}>{l}</li>
            ))}
          </ul>
        ) : null}
        <p>
          {rules ? `Refund requests close ${hoursText(rules.refundCutoffHours)} before the event and are not possible after it. ` : 'Refund requests close before the event and are not possible after it. '}A person reviews every request. See{' '}
          <a className="m3-link" href="/help#refund-policies">
            all refund policies
          </a>
          .
        </p>
      </ExpansionItem>
      <ExpansionItem title="Cancellation policy">
        <p>{event.cancellationPolicy || DEFAULT_CANCELLATION}</p>
      </ExpansionItem>
      <ExpansionItem title="Terms and conditions">
        <p>{event.termsAndConditions || DEFAULT_TERMS}</p>
      </ExpansionItem>
    </div>
  );
}

/** Overview / Schedule / Venue and access / Good to know. */
export function EventTabs({ event, tiers, now, rules }: { event: EventPageRow; tiers: EventTierRow[]; now: number; rules: BuyerPlatformRules | null }) {
  return (
    <Tabs
      label="Event information"
      variant="segmented"
      tabs={[
        { id: 'overview', label: 'Overview' },
        { id: 'schedule', label: 'Schedule' },
        { id: 'venue', label: 'Venue and access' },
        { id: 'know', label: 'Good to know' },
      ]}
    >
      {(id) =>
        id === 'overview' ? (
          <Overview event={event} tiers={tiers} now={now} />
        ) : id === 'schedule' ? (
          <div className="m3-stack">
            <h3 className="m3-card__title">Running order</h3>
            {event.runningOrder?.length ? (
              <>
                <Timeline label="Running order" items={event.runningOrder.map((r, i) => ({ id: `${i}-${r.time}`, title: <b>{r.title}</b>, time: r.time }))} />
                <p className="m3-muted">Times are approximate and may change on the day.</p>
              </>
            ) : (
              <Unshared what="The organizer has not published a running order." />
            )}
          </div>
        ) : id === 'venue' ? (
          <Venue event={event} />
        ) : (
          <Know event={event} rules={rules} />
        )
      }
    </Tabs>
  );
}
