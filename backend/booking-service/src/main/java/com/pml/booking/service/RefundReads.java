package com.pml.booking.service;

import com.pml.booking.domain.model.RefundRequest;
import com.pml.booking.security.OrganizerAccess;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.RefundRequestFilterInput;
import com.pml.shared.security.Permission;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * An organization's refund inbox: the refund requests raised against its events, read in the
 * database, narrowed to the organizations the caller belongs to and may refund for.
 */
@Service
public class RefundReads {

    private static final Set<String> SORTABLE = Set.of("createdAt", "requestedAt", "refundAmount", "status");

    private final ReactiveMongoTemplate template;
    private final OrganizerAccess access;

    public RefundReads(ReactiveMongoTemplate template, OrganizerAccess access) {
        this.template = template;
        this.access = access;
    }

    public Mono<Pages.Slice<RefundRequest>> byOrganizer(String organizationId, RefundRequestFilterInput filter,
                                                        OffsetPaginationInput pagination) {
        return access.requireOrganizations(organizationId, Permission.TICKET_REFUND).flatMap(organizations -> {
            List<Criteria> all = new ArrayList<>();
            all.add(organizations.isEmpty() ? new Criteria() : Criteria.where("organizationId").in(organizations));
            all.addAll(conditions(filter));
            return Pages.offset(template, new Criteria().andOperator(all), pagination, RefundRequest.class,
                    SORTABLE, "createdAt");
        });
    }

    static List<Criteria> conditions(RefundRequestFilterInput filter) {
        List<Criteria> conditions = new ArrayList<>();
        if (filter == null) {
            return conditions;
        }
        if (filter.ticketId() != null) conditions.add(Criteria.where("ticketId").is(filter.ticketId()));
        if (filter.buyerId() != null) conditions.add(Criteria.where("buyerId").is(filter.buyerId()));
        if (filter.eventId() != null) conditions.add(Criteria.where("eventId").is(filter.eventId()));
        if (filter.organizerId() != null) conditions.add(Criteria.where("organizerId").is(filter.organizerId()));
        if (filter.status() != null) conditions.add(Criteria.where("status").is(filter.status()));
        if (filter.requestType() != null) conditions.add(Criteria.where("requestType").is(filter.requestType()));
        if (filter.startDate() != null || filter.endDate() != null) {
            Criteria requested = Criteria.where("requestedAt");
            if (filter.startDate() != null) requested.gte(filter.startDate());
            if (filter.endDate() != null) requested.lte(filter.endDate());
            conditions.add(requested);
        }
        return conditions;
    }
}
