package com.pml.catalog.service.impl;

import com.pml.catalog.domain.model.EventFields;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.repository.TicketTierRepository;
import com.pml.catalog.service.EventCategories;
import com.pml.catalog.service.EventDetails;
import com.pml.catalog.service.EventTierMirror;
import com.pml.catalog.service.TicketTierFactory;
import com.pml.catalog.service.VenueResolver;
import com.pml.catalog.web.graphql.dto.CreateEventInput;
import com.pml.catalog.web.graphql.dto.CreateTicketTierInput;
import com.pml.catalog.web.graphql.dto.UpdateEventInput;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.error.ValidationRefusal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import reactor.core.publisher.Flux;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.exception.EventNotFoundException;
import com.pml.catalog.exception.InvalidEventStateException;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.service.EventService;
import com.pml.catalog.workflow.lifecycle.LifecycleRules;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.event.EventEnvelopes;
import com.pml.shared.event.EventType;
import com.pml.shared.event.Outbox;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.TenantGuard;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;

/**
 * Event lifecycle and discovery.
 *
 * <p>The four transitions other services act on — publish, cancel, complete and reschedule — stage
 * their cross-service envelope in {@code catalog_outbox} inside the same transaction as the status
 * change, so the event document and its announcement commit together.</p>
 *
 * <p>Those four, and unpublishing, are called from {@code EventLifecycleWorkflow}'s activities,
 * which may retry, so each is safe to run twice: a retry that finds the event already where the
 * transition leads returns it unchanged and stages no second envelope.</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventServiceImpl implements EventService {

    private final EventRepository eventRepository;

    /** Every timestamp comes from here, never from the wall clock. */
    private final java.time.Clock clock;

    /** Where the cross-service facts are staged. */
    private final Outbox outbox;
    private final TransactionalOperator transactionalOperator;
    private final VenueResolver venues;
    private final TicketTierFactory tierFactory;
    private final TicketTierRepository tierRepository;
    private final EventTierMirror tierMirror;
    private final EventCategories categories;
    private final IdentityServiceClient identityServiceClient;

    // ==========================================
    // Single Event Operations
    // ==========================================

    @Override
    public Mono<Event> findById(String id) {
        return eventRepository.findById(id);
    }

    /**
     * An event as the caller is entitled to see it.
     * OWASP A01:2021 · CWE-639.
     *
     * <h2>Why a public lookup filters</h2>
     * {@code event(id: ID!)} sits in the schema's PUBLIC block. An unfiltered
     * {@code findById} would let anybody holding an id read a rival's unannounced
     * draft with its capacity and pricing, a rejected event with its
     * {@code rejectionReason}, or a soft-deleted one with {@code deletedBy} and
     * {@code deletionReason} attached — with no account at all.
     *
     * <h2>Published first, and only then tenancy</h2>
     * The order matters for cost as well as correctness. A published event is the
     * overwhelmingly common case and is answered by one indexed lookup, without
     * resolving tenancy at all — so the public path stays free of the membership
     * call {@code CurrentTenantScope} would otherwise trigger. Tenancy is consulted
     * only when the public lookup misses, which is where the question "is this
     * yours?" actually arises.
     *
     * <h2>Empty, not a refusal</h2>
     * {@code event(id)} is nullable and has always answered {@code null} for an id
     * that does not exist. Refusing instead would introduce exactly the distinction
     * {@link com.pml.shared.error.TenantBoundary} exists to remove, so the
     * {@code EVENT_UNKNOWN} refusal is caught and flattened to empty. The value of
     * going through {@link TenantGuard} is undiminished: it still classifies the
     * miss and still logs a confirmed cross-tenant reach as
     * {@code securityIncident=true}, which is the half a bare
     * {@code switchIfEmpty} could never give.
     *
     * <h2>An anonymous miss is not an incident</h2>
     * The guard is skipped for a caller with no subject, and that is a deliberate
     * second decision rather than an optimisation. Somebody following a stale link
     * to an event that was never published is the ordinary traffic of a public
     * catalogue; routing it through {@link TenantGuard} would label every one of those
     * {@code securityIncident=true}, and an incident log that fires on ordinary
     * traffic is one that gets muted, taking the real reaches with it. An
     * anonymous caller also belongs to no organization, so there is no membership
     * under which the event could have been theirs — nothing to classify, and one
     * fewer query on the hottest public path. An <em>authenticated</em> caller with
     * no memberships still goes through the guard: that one is worth seeing.
     */
    @Override
    public Mono<Event> findVisibleById(String id) {
        return eventRepository.findByIdAndPublishedTrueAndIsActiveTrue(id)
                // A caller with no principal is shown an event only while it is PUBLISHED (ET-CAT-004-R13). The flag
                // alone is not enough for them: a COMPLETED event keeps it, and a document whose flag and status
                // disagree is never public. Anyone signed in keeps the broader view (a ticket holder's past event).
                .filterWhen(event -> event.getStatus() == EventStatus.PUBLISHED
                        ? Mono.just(true)
                        : CurrentTenantScope.get().map(scope -> scope.subject() != null).onErrorReturn(false))
                .switchIfEmpty(Mono.defer(() -> CurrentTenantScope.get()
                        .filter(scope -> scope.subject() != null)
                        .flatMap(scope -> TenantGuard.locate(
                                scope,
                                eventRepository.findById(id),
                                organizationIds ->
                                        eventRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                                ErrorCode.EVENT_UNKNOWN,
                                "event " + id))
                        // Only the boundary's own refusal is flattened. A membership lookup
                        // that fails is a different thing and must stay an error — swallowing
                        // it would answer "no such event" during an identity-service outage.
                        .onErrorResume(
                                refused -> refused instanceof DomainRefusal domain
                                        && domain.errorCode() == ErrorCode.EVENT_UNKNOWN,
                                refused -> Mono.empty())));
    }

    @Override
    public Mono<Event> createEvent(CreateEventInput input, String actorId, String organizationId) {
        Instant now = clock.instant();
        Event event = Event.builder()
                .organizerId(actorId)
                .organizationId(organizationId)
                .createdBy(actorId)
                .status(EventStatus.DRAFT)
                .published(false)
                .soldTickets(0)
                .featured(false)
                .isActive(true)
                .build();
        EventDetails.apply(event, input);

        List<CreateTicketTierInput> tiers = input.ticketTiers();
        List<FieldViolation> violations = new ArrayList<>();
        if (!tiers.isEmpty()) {
            int sum = tiers.stream().mapToInt(CreateTicketTierInput::quantity).sum();
            if (input.totalCapacity() != 0 && input.totalCapacity() != sum) {
                violations.add(new FieldViolation("totalCapacity", "must equal the sum of the tier quantities"));
            }
            event.setTotalCapacity(sum);
            event.setLowestTicketPrice(tiers.stream()
                    .filter(tier -> !Boolean.TRUE.equals(tier.isHidden()))
                    .map(CreateTicketTierInput::price)
                    .min(Comparator.naturalOrder())
                    .orElse(null));
        }
        violations.addAll(EventDetails.check(event, input.location() != null));
        violations.addAll(EventDetails.checkStart(event, now));
        violations.addAll(EventDetails.checkPublishAt(input.publishAt(), now));
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < tiers.size(); i++) {
            String path = "ticketTiers[" + i + "]";
            if (!codes.add(tiers.get(i).code().toUpperCase(Locale.ROOT))) {
                violations.add(new FieldViolation(path + ".code", "is already used by another tier of this event"));
            }
            violations.addAll(tierFactory.check(event, tiers.get(i), path));
        }
        return categories.isSelectable(input.categoryId()).flatMap(selectable -> {
            if (!selectable) {
                violations.add(new FieldViolation("categoryId", "is not an active event category"));
            }
            if (!violations.isEmpty()) {
                return Mono.<Event>error(new ValidationRefusal(violations));
            }
            return write(input, event, tiers, organizationId, actorId, now);
        });
    }

    private Mono<Event> write(CreateEventInput input, Event event, List<CreateTicketTierInput> tiers,
                              String organizationId, String actorId, Instant now) {
        Mono<Event> placed = input.location() == null
                ? Mono.just(event)
                : venues.resolve(input.location(), organizationId, actorId).map(venue -> {
                    EventDetails.place(event, venue);
                    return event;
                });
        return placed
                .flatMap(draft -> identityServiceClient.getOrganizationName(organizationId)
                        .defaultIfEmpty("Unknown")
                        .onErrorReturn("Unknown")
                        .map(name -> {
                            // Denormalized once, at creation, for the public discover feed and every
                            // other list that reads many events per request: a federation hop per
                            // event on a hot read path is what this field exists to avoid. Never
                            // refreshed on an organization rename — see F-059.
                            draft.setOrganizerName(name);
                            draft.setCreatedAt(now);
                            draft.setUpdatedAt(now);
                            draft.setAvailableTickets(draft.getTotalCapacity());
                            draft.setDeleted(false);
                            return draft;
                        }))
                .flatMap(eventRepository::save)
                .flatMap(saved -> Flux.range(0, tiers.size())
                        .concatMap(i -> tierRepository.save(tierFactory.build(saved, tiers.get(i),
                                tiers.get(i).sortOrder() != null ? tiers.get(i).sortOrder() : i, now)))
                        .then(tierMirror.refresh(saved.getId())))
                .as(transactionalOperator::transactional)
                .doOnSuccess(created -> log.info("Event {} created with {} tier(s)", created.getId(), tiers.size()));
    }

    @Override
    public Mono<Event> updateEvent(Event existing, UpdateEventInput input, String actorId, boolean platformAdmin) {
        if (input.featured() != null && !platformAdmin) {
            return Mono.error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                    "Only a platform administrator features an event"));
        }
        EventStatus status = existing.getStatus();
        if (status == EventStatus.COMPLETED || status == EventStatus.CANCELLED || existing.isDeleted()) {
            return Mono.error(stateRefusal(existing, "a " + status + (existing.isDeleted() ? " deleted" : "")
                    + " event cannot be edited"));
        }
        Instant now = clock.instant();
        Event before = existing.toBuilder().build();
        boolean hasTiers = existing.getTicketCategories() != null && !existing.getTicketCategories().isEmpty();
        EventDetails.apply(existing, input);

        List<FieldViolation> violations = new ArrayList<>();
        if (input.totalCapacity() != null && hasTiers && input.totalCapacity() != before.getTotalCapacity()) {
            violations.add(new FieldViolation("totalCapacity",
                    "is the sum of the tiers; change a tier's quantity instead"));
        }
        Mono<Event> placed = input.location() == null
                ? Mono.just(existing)
                : venues.resolve(input.location(), existing.getOrganizationId(), actorId).map(venue -> {
                    EventDetails.place(existing, venue);
                    return existing;
                });
        Mono<Boolean> category = input.categoryId() == null || input.categoryId().equals(before.getCategoryId())
                ? Mono.just(true)
                : categories.isSelectable(input.categoryId());
        return category.flatMap(selectable -> placed.map(edited -> {
                    if (!selectable) {
                        violations.add(new FieldViolation("categoryId", "is not an active event category"));
                    }
                    return edited;
                }))
                .flatMap(edited -> {
                    violations.addAll(EventDetails.check(edited, edited.getLocationId() != null));
                    if (input.eventDateTime() != null) {
                        violations.addAll(EventDetails.checkStart(edited, now));
                    }
                    violations.addAll(EventDetails.checkPublishAt(input.publishAt(), now));
                    if (!violations.isEmpty()) {
                        return Mono.error(new ValidationRefusal(violations));
                    }
                    Set<String> material = EventFields.materialChanges(before, edited);
                    if (!material.isEmpty()) {
                        if (status == EventStatus.PUBLISHED) {
                            return Mono.error(stateRefusal(edited, "a published event's " + material
                                    + " change only by rescheduling or after unpublishing"));
                        }
                        if (status == EventStatus.PENDING_APPROVAL) {
                            return Mono.error(stateRefusal(edited, "an event under review cannot change " + material));
                        }
                        if (status == EventStatus.APPROVED) {
                            edited.setStatus(EventStatus.DRAFT);
                            edited.setApprovedAt(null);
                            edited.setApprovedBy(null);
                            edited.setPublishScheduled(false);
                        }
                    }
                    if (status == EventStatus.REJECTED) {
                        edited.setStatus(EventStatus.DRAFT);
                    }
                    edited.setUpdatedAt(now);
                    edited.setUpdatedBy(actorId);
                    return eventRepository.save(edited);
                })
                .as(transactionalOperator::transactional);
    }

    @Override
    public Mono<Event> duplicateEvent(Event original, String newTitle, String actorId, String organizationId) {
        Instant now = clock.instant();
        Event copy = original.toBuilder()
                .id(null)
                .version(null)
                .title(newTitle)
                .organizerId(actorId)
                .organizationId(organizationId)
                .createdBy(actorId)
                .updatedBy(null)
                .status(EventStatus.DRAFT)
                .published(false)
                .publishedAt(null)
                .soldTickets(0)
                .availableTickets(original.getTotalCapacity())
                .ticketCategories(List.of())
                .tags(null)
                .featured(false)
                .isActive(true)
                .isDeleted(false)
                .deletedAt(null)
                .deletedBy(null)
                .deletionReason(null)
                .submittedForApprovalAt(null)
                .approvalDeadline(null)
                .isOverdue(false)
                .assignedReviewerId(null)
                .assignedReviewerName(null)
                .approvedAt(null)
                .approvedBy(null)
                .rejectedAt(null)
                .rejectedBy(null)
                .rejectionReason(null)
                .changesRequestedAt(null)
                .changesRequestedBy(null)
                .changesRequestedComments(null)
                .submissionCount(0)
                .previousStartsAt(null)
                .rescheduleCount(0)
                .publishAt(null)
                .publishScheduled(false)
                .cancellationReason(null)
                .cancelledAt(null)
                .grossSales(java.math.BigDecimal.ZERO)
                .commissionAmount(java.math.BigDecimal.ZERO)
                .createdAt(now)
                .updatedAt(now)
                .build();
        return eventRepository.save(copy)
                .flatMap(saved -> tierRepository.findByEventIdOrderBySortOrderAsc(original.getId())
                        .concatMap(tier -> tierRepository.save(tier.toBuilder()
                                .id(null)
                                .version(null)
                                .eventId(saved.getId())
                                .organizationId(organizationId)
                                .availableQuantity(tier.getQuantity())
                                .reservedQuantity(0)
                                .soldQuantity(0)
                                .movements(new ArrayList<>())
                                .createdAt(now)
                                .updatedAt(now)
                                .build()))
                        .then(tierMirror.refresh(saved.getId())))
                .as(transactionalOperator::transactional);
    }

    private static TranslatedRefusal stateRefusal(Event event, String why) {
        return new TranslatedRefusal(ErrorCode.EVENT_STATE_INVALID, "Event " + event.getId() + ": " + why,
                Map.of("currentStatus", event.getStatus().name()));
    }

    @Override
    public Mono<Event> publishEvent(String id) {
        // Reached only from EventLifecycleActivitiesImpl, a Temporal activity with no HTTP
        // request and so no TenantScope to read: the caller is the lifecycle workflow itself,
        // acting on a transition the resolver already authorized when it started the workflow.
        return TenantGuard.locate(
                        PlatformWideAccess.system(PlatformWideAccess.Reason.LIFECYCLE_WORKFLOW, clock),
                        eventRepository.findById(id),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + id + " (publish)")
                .flatMap(event -> {
                    if (event.getStatus() == EventStatus.PUBLISHED) {
                        // A retried publication finds its own committed write, envelope included.
                        return Mono.just(event);
                    }
                    // STATE VALIDATION: Only APPROVED events can be published
                    if (event.getStatus() != EventStatus.APPROVED) {
                        return Mono.error(new InvalidEventStateException(
                                id,
                                event.getStatus().name(),
                                EventStatus.APPROVED.name()
                        ));
                    }
                    if (event.getEventDateTime() == null) {
                        return Mono.error(new InvalidEventStateException(
                                "Event " + id + " has no start time and cannot be published"));
                    }
                    event.setStatus(EventStatus.PUBLISHED);
                    event.setPublished(true);
                    event.setPublishScheduled(false);
                    event.setPublishedAt(clock.instant());
                    event.setUpdatedAt(clock.instant());
                    return eventRepository.save(event)
                            .flatMap(saved -> stage(EventType.CATALOG_EVENT_PUBLISHED, saved, Map.of(
                                    "eventId", saved.getId(),
                                    "organizationId", tenantOf(saved),
                                    "startsAt", saved.getEventDateTime().toString())));
                })
                .as(transactionalOperator::transactional)
                .doOnSuccess(event -> log.info("Event published: {}", id));
    }

    @Override
    public Mono<Event> scheduleEventPublish(String id) {
        // Reached only after EventWriteGuard.forWrite has located the event for the caller, or from the
        // publish workflow acting as the system: neither has a tenant scope to filter by here.
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new EventNotFoundException(id)))
                .flatMap(event -> {
                    if (event.getStatus() != EventStatus.APPROVED) {
                        return Mono.<Event>error(stateRefusal(event, "only an approved event is scheduled for publication"));
                    }
                    if (event.getPublishAt() == null || !event.getPublishAt().isAfter(clock.instant())) {
                        return Mono.<Event>error(new ValidationRefusal(List.of(
                                new FieldViolation("publishAt", "must be in the future to schedule a publication"))));
                    }
                    if (event.isPublishScheduled()) {
                        return Mono.just(event);
                    }
                    event.setPublishScheduled(true);
                    event.setUpdatedAt(clock.instant());
                    return eventRepository.save(event);
                });
    }

    @Override
    public Mono<Event> clearPublishSchedule(String id) {
        // Reached only after EventWriteGuard.forWrite has located the event for the caller, or from the
        // publish workflow acting as the system: neither has a tenant scope to filter by here.
        return eventRepository.findById(id)
                .switchIfEmpty(Mono.error(new EventNotFoundException(id)))
                .flatMap(event -> {
                    if (!event.isPublishScheduled() && event.getPublishAt() == null) {
                        return Mono.just(event);
                    }
                    event.setPublishScheduled(false);
                    event.setPublishAt(null);
                    event.setUpdatedAt(clock.instant());
                    return eventRepository.save(event);
                });
    }

    /**
     * Stages an outbox envelope about {@code event}. Called inside the transaction that saved the
     * event, so the document and its envelope commit together.
     */
    private Mono<Event> stage(EventType type, Event event, Map<String, Object> payload) {
        return outbox.stage(EventEnvelopes.of(type, clock.instant(), event.getId(), payload))
                .thenReturn(event);
    }

    /**
     * The tenant an event's money belongs to. An event without an organization id is attributed to
     * its organizer, so publication is never blocked on a missing tenant field.
     */
    private static String tenantOf(Event event) {
        String organizationId = event.getOrganizationId();
        return organizationId != null && !organizationId.isBlank() ? organizationId : event.getOrganizerId();
    }

    @Override
    @Transactional
    public Mono<Event> cancelEvent(String id) {
        return cancelEventWithReason(id, "Event cancelled by organizer");
    }

    /**
     * Cancel event with a specific reason.
     * Publishes EventCancelledEvent for automatic refund processing.
     */
    @Override
    @Transactional
    public Mono<Event> cancelEventWithReason(String id, String reason) {
        return cancelEventWithDetails(id, reason, true, true);
    }

    @Override
    public Mono<Event> cancelEventWithDetails(String id, String reason, boolean notifyAttendees, boolean triggerRefunds) {
        // Reached only from EventLifecycleActivitiesImpl: a Temporal activity, no HTTP request,
        // the resolver already authorized the cancellation when it started the workflow.
        return TenantGuard.locate(
                        PlatformWideAccess.system(PlatformWideAccess.Reason.LIFECYCLE_WORKFLOW, clock),
                        eventRepository.findById(id),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + id + " (cancel)")
                .flatMap(event -> {
                    if (event.getStatus() == EventStatus.CANCELLED) {
                        // A retried cancellation finds its own committed write, envelope included.
                        return Mono.just(event);
                    }
                    // Cancellation is legal from APPROVED and PUBLISHED only
                    if (!LifecycleRules.CANCELLABLE.contains(event.getStatus())) {
                        return Mono.error(new InvalidEventStateException(
                                String.format("Event %s cannot be cancelled. Current status: %s. Cancellation only allowed from: %s",
                                        id, event.getStatus(), LifecycleRules.CANCELLABLE)
                        ));
                    }
                    event.setStatus(EventStatus.CANCELLED);
                    event.setActive(false);
                    event.setPublishScheduled(false);
                    // The collection's validator holds the reason to 1,000 characters.
                    event.setCancellationReason(reason == null || reason.isBlank() ? null
                            : reason.trim().substring(0, Math.min(1_000, reason.trim().length())));
                    event.setCancelledAt(clock.instant());
                    event.setUpdatedAt(clock.instant());
                    return eventRepository.save(event)
                            .flatMap(saved -> stage(EventType.CATALOG_EVENT_CANCELLED, saved, Map.of(
                                    "eventId", saved.getId(),
                                    "reason", reason == null || reason.isBlank() ? "UNSPECIFIED" : reason)));
                })
                .as(transactionalOperator::transactional)
                .doOnSuccess(event -> log.info("Event cancelled: {}", id));
    }

    /**
     * Mark event as completed.
     * Publishes EventCompletedEvent to lock escrow and start hold period.
     *
     * STATE VALIDATION: Only PUBLISHED events can be completed.
     */
    @Override
    public Mono<Event> completeEvent(String id) {
        // Reached from the ADMIN/internal-write-only completeEvent mutation and from the
        // lifecycle workflow's activity — every caller is already platform-level or a system
        // actor, with no organization of its own to filter by.
        return TenantGuard.locate(
                        PlatformWideAccess.system(PlatformWideAccess.Reason.EVENT_COMPLETION, clock),
                        eventRepository.findById(id),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + id + " (complete)")
                .flatMap(event -> {
                    if (event.getStatus() == EventStatus.COMPLETED) {
                        // A retried completion finds its own committed write, envelope included.
                        return Mono.just(event);
                    }
                    // STATE VALIDATION: Only PUBLISHED events can be completed
                    if (event.getStatus() != EventStatus.PUBLISHED) {
                        return Mono.error(new InvalidEventStateException(
                                id,
                                event.getStatus().name(),
                                EventStatus.PUBLISHED.name()
                        ));
                    }
                    event.setStatus(EventStatus.COMPLETED);
                    event.setUpdatedAt(clock.instant());
                    return eventRepository.save(event)
                            .flatMap(saved -> stage(EventType.CATALOG_EVENT_COMPLETED, saved, Map.of(
                                    "eventId", saved.getId(),
                                    "completedAt", clock.instant().toString())));
                })
                .as(transactionalOperator::transactional)
                .doOnSuccess(event -> log.info("Event completed: {}", id));
    }

    @Override
    @Transactional
    public Mono<Event> setEventFeatured(String id, boolean featured) {
        // Reached only from the ADMIN-only featureEvent mutation, so this reads the real
        // request scope rather than assuming platform authority: defense in depth if a future
        // caller is ever added here without the same guarantee.
        return CurrentTenantScope.get()
                .flatMap(scope -> TenantGuard.locate(
                        scope,
                        eventRepository.findById(id),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + id + " (feature)"))
                .flatMap(event -> {
                    event.setFeatured(featured);
                    event.setUpdatedAt(clock.instant());
                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> {
                    log.info("Event {} featured status set to: {}", id, featured);
                });
    }

    @Override
    @Transactional
    public Mono<Event> sendPublishReminder(String eventId, String triggeredBy) {
        log.info("Sending publish reminder for event: {} by: {}", eventId, triggeredBy);
        // Reached only from the ADMIN-only sendEventPublishReminder mutation: read the real
        // request scope rather than assuming platform authority.
        return CurrentTenantScope.get()
                .flatMap(scope -> TenantGuard.locate(
                        scope,
                        eventRepository.findById(eventId),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(eventId, organizationIds),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + eventId + " (publish reminder)"))
                .flatMap(event -> {
                    // Validate event is in a state that allows publish reminders (approved but not published)
                    if (event.getStatus() != EventStatus.APPROVED) {
                        return Mono.error(new IllegalStateException(
                                "Can only send publish reminders for approved events. Current status: " + event.getStatus()));
                    }
                    if (event.isPublished()) {
                        return Mono.error(new IllegalStateException("Event is already published"));
                    }

                    // Update the event with reminder tracking
                    event.setUpdatedAt(clock.instant());

                    // The reminder is recorded in the log only; sending it to the organizer belongs to
                    // the notification service's lifecycle triggers.
                    log.info("Publish reminder sent for event: {} to organizer: {}", eventId, event.getOrganizerId());

                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> log.info("Publish reminder processed for event: {}", eventId));
    }

    /**
     * A PUBLISHED event moves to a new start, keeping its duration.
     *
     * <p>{@code previousStartsAt} and {@code rescheduleCount} are kept on the event, the count is
     * capped at {@link LifecycleRules#MAX_RESCHEDULES}, and {@code catalog.EventRescheduled} is staged
     * with the status change, from which booking opens the holders' refund window. A retry that finds
     * the event already at {@code newDateTime} changes nothing and stages nothing.</p>
     */
    @Override
    public Mono<Event> rescheduleEvent(String id, Instant newDateTime, String reason) {
        // Reached only from EventLifecycleActivitiesImpl: a Temporal activity, no HTTP request,
        // the resolver already authorized the reschedule when it started the workflow.
        return TenantGuard.locate(
                        PlatformWideAccess.system(PlatformWideAccess.Reason.LIFECYCLE_WORKFLOW, clock),
                        eventRepository.findById(id),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + id + " (reschedule)")
                .flatMap(event -> {
                    if (event.getStatus() != EventStatus.PUBLISHED) {
                        return Mono.error(new InvalidEventStateException(
                                id, String.valueOf(event.getStatus()), EventStatus.PUBLISHED.name()));
                    }
                    if (newDateTime.equals(event.getEventDateTime())) {
                        return Mono.just(event);
                    }
                    if (!LifecycleRules.canReschedule(event.getRescheduleCount())) {
                        return Mono.error(new InvalidEventStateException(String.format(
                                "Event %s has been rescheduled %d times; the limit is %d",
                                id, event.getRescheduleCount(), LifecycleRules.MAX_RESCHEDULES)));
                    }
                    Instant originalDateTime = event.getEventDateTime();
                    event.setPreviousStartsAt(originalDateTime);
                    event.setEndDateTime(LifecycleRules.shiftedEnd(originalDateTime, event.getEndDateTime(), newDateTime));
                    event.setEventDateTime(newDateTime);
                    event.setRescheduleCount(event.getRescheduleCount() + 1);
                    event.setUpdatedAt(clock.instant());
                    return eventRepository.save(event)
                            .flatMap(saved -> stage(EventType.CATALOG_EVENT_RESCHEDULED, saved, Map.of(
                                    "eventId", saved.getId(),
                                    "previousStartsAt", (originalDateTime != null ? originalDateTime : newDateTime).toString(),
                                    "newStartsAt", newDateTime.toString())));
                })
                .as(transactionalOperator::transactional)
                .doOnSuccess(event -> log.info("Event {} rescheduled to {} ({})", id, newDateTime, reason));
    }

    /**
     * PUBLISHED → APPROVED while nothing is sold.
     *
     * <p>The refusal carries the sold count. An event already back in APPROVED and unpublished is
     * returned unchanged, so a retried activity is harmless.</p>
     */
    @Override
    public Mono<Event> unpublishEvent(String id, long soldCount) {
        // Reached only from EventLifecycleActivitiesImpl: a Temporal activity, no HTTP request,
        // the resolver already authorized the unpublish when it started the workflow.
        return TenantGuard.locate(
                        PlatformWideAccess.system(PlatformWideAccess.Reason.LIFECYCLE_WORKFLOW, clock),
                        eventRepository.findById(id),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + id + " (unpublish)")
                .flatMap(event -> {
                    if (event.getStatus() == EventStatus.APPROVED && !event.isPublished()) {
                        return Mono.just(event);
                    }
                    if (event.getStatus() != EventStatus.PUBLISHED) {
                        return Mono.error(new InvalidEventStateException(
                                id, String.valueOf(event.getStatus()), EventStatus.PUBLISHED.name()));
                    }
                    if (soldCount > 0) {
                        return Mono.error(new TranslatedRefusal(ErrorCode.EVENT_STATE_INVALID,
                                "an event with sold tickets is cancelled, not unpublished",
                                Map.of("soldTickets", soldCount)));
                    }
                    event.setStatus(EventStatus.APPROVED);
                    event.setPublished(false);
                    event.setPublishedAt(null);
                    event.setUpdatedAt(clock.instant());
                    return eventRepository.save(event);
                })
                .as(transactionalOperator::transactional)
                .doOnSuccess(event -> log.info("Event unpublished: {}", id));
    }

    /**
     * Soft delete an event.
     *
     * <p>Instead of physically deleting, sets isDeleted=true with audit trail.
     * Validates that no tickets have been sold before allowing deletion.</p>
     *
     * OWASP A09:2021 Compliance: Maintains audit trail for security logging.
     *
     * @param id Event ID to delete
     * @param deletedBy User ID who is deleting
     * @param reason Reason for deletion
     * @return Empty Mono on success
     */
    @Override
    @Transactional
    public Mono<Void> deleteEvent(String id) {
        return deleteEventWithReason(id, null, "Deleted by user");
    }

    /**
     * Soft delete an event with reason and audit trail.
     */
    @Override
    @Transactional
    public Mono<Void> deleteEventWithReason(String id, String deletedBy, String reason) {
        // The resolver's EventWriteGuard already proved ownership before calling this; reading
        // the scope again here is defense in depth, not the only check — a future caller that
        // skips the guard is refused here too, instead of silently deleting across tenants.
        return CurrentTenantScope.get()
                .flatMap(scope -> TenantGuard.locate(
                        scope,
                        eventRepository.findById(id),
                        organizationIds -> eventRepository.findByIdAndOrganizationIdIn(id, organizationIds),
                        ErrorCode.EVENT_UNKNOWN,
                        "event " + id + " (delete)"))
                .flatMap(event -> {
                    // BUSINESS RULE: Cannot delete events that have sold tickets
                    if (event.getSoldTickets() > 0) {
                        return Mono.error(new IllegalStateException(
                                String.format("Cannot delete event %s with %d sold tickets. Cancel the event instead.",
                                        id, event.getSoldTickets())
                        ));
                    }

                    // BUSINESS RULE: Cannot delete already deleted events
                    if (event.isDeleted()) {
                        return Mono.error(new IllegalStateException("Event is already deleted: " + id));
                    }

                    // Soft delete with audit trail
                    event.setDeleted(true);
                    event.setDeletedAt(clock.instant());
                    event.setDeletedBy(deletedBy);
                    event.setDeletionReason(reason);
                    event.setActive(false);
                    event.setUpdatedAt(clock.instant());

                    return eventRepository.save(event);
                })
                .doOnSuccess(event -> log.info("Event soft deleted: {}", id))
                .then();
    }

    // ==========================================
    // Flux-based Queries (for resolver pagination helpers)
    // ==========================================

    @Override
    public Flux<Event> findAllEvents() {
        return eventRepository.findAll();
    }

    @Override
    public Flux<Event> searchEvents(String query) {
        return eventRepository.searchEvents(query);
    }

    @Override
    public Flux<Event> findEventsByCategory(String categoryId) {
        return eventRepository.findByCategoryIdAndPublishedTrueAndIsActiveTrue(categoryId);
    }

    @Override
    public Flux<Event> findEventsByCity(String city) {
        return eventRepository.findByCityAndPublishedTrueAndIsActiveTrue(city);
    }

    @Override
    public Flux<Event> findEventsByOrganizer(String organizerId) {
        return eventRepository.findByOrganizerId(organizerId);
    }

    @Override
    public Flux<Event> findEventsByStatus(EventStatus status) {
        return eventRepository.findByStatus(status);
    }

    @Override
    public Flux<Event> findDraftEventsByOrganizer(String organizerId) {
        return eventRepository.findByOrganizerIdAndStatus(organizerId, EventStatus.DRAFT);
    }

    @Override
    public Flux<Event> findPendingApprovalEvents() {
        return eventRepository.findByStatus(EventStatus.PENDING_APPROVAL);
    }

    @Override
    public Flux<Event> findOverdueApprovalEvents() {
        return eventRepository.findOverdueApprovalEvents(clock.instant());
    }

    @Override
    public Flux<Event> findApprovedNotPublishedEvents() {
        return eventRepository.findApprovedNotPublishedEvents();
    }

    @Override
    public Flux<Event> findCancelledEvents() {
        return eventRepository.findByStatus(EventStatus.CANCELLED);
    }

    @Override
    public Flux<Event> findCompletedEvents() {
        return eventRepository.findByStatus(EventStatus.COMPLETED);
    }

    // ==========================================
    // Count Operations
    // ==========================================

    @Override
    public Mono<Long> countAll() {
        return eventRepository.count();
    }

    @Override
    public Mono<Long> countByOrganizer(String organizerId) {
        return eventRepository.countByOrganizerId(organizerId);
    }

    @Override
    public Mono<Long> countByCategory(String categoryId) {
        return eventRepository.countByCategoryId(categoryId);
    }

    @Override
    public Mono<Long> countByCity(String city) {
        return eventRepository.countByCity(city);
    }

    @Override
    public Mono<Long> countByStatus(EventStatus status) {
        return eventRepository.countByStatus(status);
    }
}
