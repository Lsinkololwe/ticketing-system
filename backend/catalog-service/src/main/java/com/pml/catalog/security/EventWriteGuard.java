package com.pml.catalog.security;

import com.pml.shared.security.Permission;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.repository.EventRepository;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

/**
 * The only way to load an event in order to change it. OWASP A01:2021 — Broken Access Control.
 *
 * <h2>Why this exists as a component rather than a pattern to follow</h2>
 * Written out by hand, every event mutation would open the same way: load the event by id with
 * no filter, read its {@code organizationId} off the loaded document, then ask identity-service
 * whether the caller holds the permission. That is correct, but it is correct by repetition,
 * and the next mutation is the one that gets it wrong. The shape gives no help: a mutation that
 * skips the check compiles, reads naturally, and passes every test that does not specifically
 * probe another tenant.
 *
 * <p>So the composition lives here once. {@link #forWrite} cannot be called without producing
 * both locks, because {@code TenantGuard.locateAndPermit} takes both as arguments.
 *
 * <h2>The filter joins the check, it does not replace it</h2>
 * The two answer different questions and the platform needs both answers:
 *
 * <ul>
 *   <li><b>the filter</b> — {@code findByIdAndOrganizationIdIn} — asks whether the event belongs
 *       to an organization the caller is in. A database predicate; it knows nothing else;</li>
 *   <li><b>the check</b> — {@code checkEventAccess} — asks whether the caller may perform this
 *       particular action, resolved by identity-service with event-level roles taking
 *       precedence over the organization role.</li>
 * </ul>
 *
 * A MARKETER cannot edit events and a CONTRIBUTOR is view-only; event-level roles override the
 * organization role for one event. Dropping the check in favour of the filter would give all of
 * them owner-level power over everything the organization runs. Dropping the filter in favour of
 * the check leaves an unfiltered load, where a forgotten check is invisible.
 *
 * <h2>The cost this accepts</h2>
 * The availability cost is taken knowingly: an event mutation depends on identity-service
 * answering, so an identity outage means organizers cannot edit, publish or cancel. The decision
 * is not cached — caching trades a security property for a performance gain nobody has
 * measured, and a cached grant outlives a demotion. Nor does the guard degrade to
 * membership-only during an outage: that would convert an availability incident into a
 * privilege-escalation window at the moment nobody is watching.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventWriteGuard {

    private final EventRepository eventRepository;
    private final IdentityServiceClient identityServiceClient;

    /**
     * The event, if the caller's organization owns it and the caller holds {@code permission}.
     *
     * @param eventId    caller-supplied and therefore untrusted
     * @param permission what this mutation requires, e.g. {@link Permission#EVENT_EDIT}
     * @return the event, or {@code EVENT_UNKNOWN} when it is not the caller's, or
     *         {@link AccessDeniedException} when it is theirs and they may not do this to it
     */
    public Mono<Event> forWrite(String eventId, Permission permission) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(userId ->
                CurrentTenantScope.get().flatMap(scope -> TenantGuard.locateAndPermit(
                        scope,
                        eventRepository.findById(eventId),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(eventId, organizationIds),
                        event -> permits(userId, eventId, event, permission),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + eventId)));
    }

    /**
     * The second lock. Errors when identity-service refuses.
     *
     * <p>Deliberately not silent about the reason. By the time this runs the caller has proved
     * membership of the owning organization, so the event's existence is not news to them and
     * {@code TenantBoundary}'s disguise has nothing left to hide. "You do not have event:edit on
     * this event" is the answer that lets somebody ask the right colleague for access, rather
     * than filing a bug about an event that has vanished.
     */
    private Mono<Void> permits(String userId, String eventId, Event event, Permission permission) {
        return identityServiceClient
                .checkEventAccess(userId, eventId, event.getOrganizationId(), permission.code())
                .flatMap(result -> {
                    if (result.isAuthorized()) {
                        return Mono.empty();
                    }
                    log.info("Refused {} on event {} for {}: {}",
                            permission.code(), eventId, userId, result.getReason());
                    return Mono.error(new AccessDeniedException(result.getReason()));
                });
    }
}
