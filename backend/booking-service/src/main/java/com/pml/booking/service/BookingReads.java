package com.pml.booking.service;

import com.pml.booking.domain.BookingRules;
import com.pml.booking.domain.enums.BookingStatus;
import com.pml.booking.domain.model.Booking;
import com.pml.booking.security.OrganizerAccess;
import com.pml.booking.web.graphql.dto.BookingFilterInput;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.security.Permission;
import com.pml.shared.security.SecurityContextUtils;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Bookings as a caller is allowed to see them.
 *
 * <p>Three kinds of reader: the buyer who paid (their own bookings, everything including contact);
 * an organizer holding {@code attendee:view} on the event or organization (their organization's
 * bookings, contact masked); platform staff (all). Every refusal on a single booking is
 * {@code BOOKING_UNKNOWN} — the same answer an invented id gets — so a guessed id confirms nothing.
 */
@Service
public class BookingReads {

    private static final Set<String> SORTABLE = Set.of("createdAt", "totalAmount", "bookingNumber", "confirmedAt");

    private final BookingStore store;
    private final ReactiveMongoTemplate template;
    private final OrganizerAccess access;

    public BookingReads(BookingStore store, ReactiveMongoTemplate template, OrganizerAccess access) {
        this.store = store;
        this.template = template;
        this.access = access;
    }

    // ---- one booking ---------------------------------------------------------------------------

    public Mono<Booking> byId(String id) {
        return authorize(store.byId(id), id);
    }

    public Mono<Booking> byNumber(String bookingNumber) {
        return authorize(store.byNumber(bookingNumber), bookingNumber);
    }

    private Mono<Booking> authorize(Mono<Booking> located, String what) {
        return located
                .flatMap(booking -> SecurityContextUtils.requireCurrentUserId().flatMap(caller -> {
                    if (caller.equals(booking.getBuyerId())) {
                        return Mono.just(booking);
                    }
                    return OrganizerAccess.platformGrants(Permission.ATTENDEE_VIEW).flatMap(platform -> platform
                            ? Mono.just(booking)
                            : access.requireEvent(booking.getEventId(), Permission.ATTENDEE_VIEW).thenReturn(booking));
                }))
                .onErrorMap(DomainRefusal.class, refusal -> TenantBoundary.refuse(ErrorCode.BOOKING_UNKNOWN, "booking " + what))
                .switchIfEmpty(Mono.error(() -> TenantBoundary.refuse(ErrorCode.BOOKING_UNKNOWN, "booking " + what)));
    }

    /** Whether the caller may see the order's contact in full: the buyer, or platform staff. */
    public static Mono<Boolean> seesFullContact(Booking booking) {
        return SecurityContextUtils.getCurrentUserId()
                .map(caller -> caller.equals(booking.getBuyerId()))
                .defaultIfEmpty(false)
                .flatMap(owner -> owner ? Mono.just(true) : OrganizerAccess.isPlatformStaff());
    }

    // ---- lists ---------------------------------------------------------------------------------

    /** An organization's bookings; platform staff may omit the organization and read across all. */
    public Mono<Pages.Slice<Booking>> byOrganizer(String organizationId, BookingFilterInput filter,
                                                  OffsetPaginationInput pagination) {
        Mono<Criteria> scoped = filter != null && filter.eventId() != null && !filter.eventId().isBlank()
                // A grant on one event is enough to list that event's bookings.
                ? access.requireEvent(filter.eventId(), Permission.ATTENDEE_VIEW)
                        .map(event -> Criteria.where("organizationId").is(event.getOrganizationId()))
                : access.requireOrganizations(organizationId, Permission.ATTENDEE_VIEW)
                        .map(organizations -> organizations.isEmpty()
                                ? new Criteria()
                                : Criteria.where("organizationId").in(organizations));
        return scoped.flatMap(scope -> page(scope, filter, pagination));
    }

    /** A buyer's bookings: the buyer's own, or any buyer's for platform staff. */
    public Mono<Pages.Slice<Booking>> byBuyer(String buyerId, BookingFilterInput filter, OffsetPaginationInput pagination) {
        return SecurityContextUtils.requireCurrentUserId().flatMap(caller -> caller.equals(buyerId)
                        ? Mono.just(true)
                        : OrganizerAccess.isPlatformStaff())
                .flatMap(allowed -> allowed
                        ? page(Criteria.where("buyerId").is(buyerId), filter, pagination)
                        : Mono.error(TenantBoundary.refuse(ErrorCode.BOOKING_UNKNOWN, "bookings of " + buyerId)));
    }

    public Mono<Pages.Slice<Booking>> mine(BookingFilterInput filter, OffsetPaginationInput pagination) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(caller -> page(Criteria.where("buyerId").is(caller), filter, pagination));
    }

    private Mono<Pages.Slice<Booking>> page(Criteria scope, BookingFilterInput filter, OffsetPaginationInput pagination) {
        List<FieldViolation> violations = check(filter);
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }
        List<Criteria> all = new ArrayList<>();
        all.add(scope);
        all.addAll(conditions(filter));
        return Pages.offset(template, new Criteria().andOperator(all), pagination, Booking.class, SORTABLE, "createdAt");
    }

    static List<FieldViolation> check(BookingFilterInput filter) {
        List<FieldViolation> violations = new ArrayList<>();
        if (filter == null) {
            return violations;
        }
        String search = filter.search() == null ? "" : filter.search().trim();
        if (!search.isEmpty() && search.length() < 3) {
            violations.add(new FieldViolation("filter.search", "must be at least 3 characters"));
        }
        if (filter.createdAfter() != null && filter.createdBefore() != null
                && filter.createdAfter().isAfter(filter.createdBefore())) {
            violations.add(new FieldViolation("filter.createdBefore", "must not be before createdAfter"));
        }
        return violations;
    }

    static List<Criteria> conditions(BookingFilterInput filter) {
        List<Criteria> conditions = new ArrayList<>();
        if (filter == null) {
            return conditions;
        }
        if (filter.eventId() != null && !filter.eventId().isBlank()) {
            conditions.add(Criteria.where("eventId").is(filter.eventId()));
        }
        List<BookingStatus> statuses = new ArrayList<>();
        if (filter.status() != null) {
            statuses.add(filter.status());
        }
        if (filter.statuses() != null) {
            statuses.addAll(filter.statuses());
        }
        if (!statuses.isEmpty()) {
            conditions.add(new Criteria().orOperator(statuses.stream().distinct().map(BookingRules::criteriaFor).toList()));
        }
        if (filter.createdAfter() != null || filter.createdBefore() != null) {
            Criteria created = Criteria.where("createdAt");
            if (filter.createdAfter() != null) created.gte(filter.createdAfter());
            if (filter.createdBefore() != null) created.lte(filter.createdBefore());
            conditions.add(created);
        }
        if (filter.search() != null && !filter.search().isBlank()) {
            Pattern literal = Pattern.compile(Pattern.quote(filter.search().trim()), Pattern.CASE_INSENSITIVE);
            conditions.add(new Criteria().orOperator(
                    Criteria.where("bookingNumber").regex(literal),
                    Criteria.where("contactName").regex(literal)));
        }
        return conditions;
    }
}
