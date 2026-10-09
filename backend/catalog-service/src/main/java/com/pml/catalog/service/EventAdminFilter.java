package com.pml.catalog.service;

import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.repository.ReferenceDataRepository;
import com.pml.catalog.web.graphql.dto.EventFilterInput;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import lombok.RequiredArgsConstructor;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.TextCriteria;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * The query an administrator's event filter describes, with every field of the filter applied.
 *
 * <p>All conditions combine with AND. {@code country} is answered through the reference data: the
 * events whose city lies in one of that country's provinces.
 */
@Component
@RequiredArgsConstructor
public class EventAdminFilter {

    private final ReferenceDataRepository referenceData;
    private final Clock clock;

    public Mono<Query> query(EventFilterInput filter) {
        if (filter == null) {
            return Mono.just(new Query());
        }
        List<FieldViolation> violations = check(filter);
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }
        List<Criteria> conditions = new ArrayList<>();
        Instant now = clock.instant();
        if (filter.getCategoryId() != null) conditions.add(Criteria.where("categoryId").is(filter.getCategoryId()));
        if (filter.getStatus() != null) conditions.add(Criteria.where("status").is(filter.getStatus()));
        if (filter.getStatuses() != null && !filter.getStatuses().isEmpty()) {
            conditions.add(Criteria.where("status").in(filter.getStatuses()));
        }
        if (Boolean.TRUE.equals(filter.getApprovedNotPublished())) {
            conditions.add(Criteria.where("status").is(EventStatus.APPROVED));
        }
        if (filter.getOrganizerId() != null) conditions.add(Criteria.where("organizerId").is(filter.getOrganizerId()));
        if (filter.getPublished() != null) conditions.add(Criteria.where("published").is(filter.getPublished()));
        if (filter.getCityId() != null) conditions.add(Criteria.where("cityId").is(filter.getCityId()));
        if (filter.getOverdue() != null) conditions.add(Criteria.where("isOverdue").is(filter.getOverdue()));
        range(conditions, "eventDateTime", filter.getEventDateAfter(), filter.getEventDateBefore());
        range(conditions, "createdAt", filter.getCreatedAfter(), filter.getCreatedBefore());
        // Approved at least `min` and at most `max` days ago.
        range(conditions, "approvedAt",
                filter.getDaysSinceApprovalMax() == null ? null : now.minus(Duration.ofDays(filter.getDaysSinceApprovalMax())),
                filter.getDaysSinceApprovalMin() == null ? null : now.minus(Duration.ofDays(filter.getDaysSinceApprovalMin())));

        Mono<List<Criteria>> withCountry = filter.getCountry() == null
                ? Mono.just(conditions)
                : citiesOf(filter.getCountry()).map(cities -> {
                    conditions.add(Criteria.where("cityId").in(cities));
                    return conditions;
                });
        return withCountry.map(all -> {
            Query query = all.isEmpty() ? new Query() : new Query(new Criteria().andOperator(all));
            String[] words = words(filter.getSearchQuery());
            if (words.length > 0) {
                query.addCriteria(TextCriteria.forDefaultLanguage().matchingAny(words));
            }
            return query;
        });
    }

    static List<FieldViolation> check(EventFilterInput filter) {
        List<FieldViolation> violations = new ArrayList<>();
        String search = filter.getSearchQuery() == null ? "" : filter.getSearchQuery().trim();
        if (!search.isEmpty() && (search.length() < 3 || search.length() > 100)) {
            violations.add(new FieldViolation("filter.searchQuery", "must be 3 to 100 characters"));
        }
        if (filter.getDaysSinceApprovalMin() != null && filter.getDaysSinceApprovalMin() < 0) {
            violations.add(new FieldViolation("filter.daysSinceApprovalMin", "cannot be negative"));
        }
        if (filter.getDaysSinceApprovalMin() != null && filter.getDaysSinceApprovalMax() != null
                && filter.getDaysSinceApprovalMin() > filter.getDaysSinceApprovalMax()) {
            violations.add(new FieldViolation("filter.daysSinceApprovalMax", "must not be below daysSinceApprovalMin"));
        }
        order(violations, "filter.eventDateBefore", filter.getEventDateAfter(), filter.getEventDateBefore());
        order(violations, "filter.createdBefore", filter.getCreatedAfter(), filter.getCreatedBefore());
        return violations;
    }

    private Mono<List<String>> citiesOf(String country) {
        String typed = country.trim();
        return referenceData.findByTypeOrderByDisplayOrderAscNameAsc(ReferenceType.COUNTRY)
                .filter(row -> row.getCode().equalsIgnoreCase(typed) || row.getName().equalsIgnoreCase(typed))
                .next()
                .flatMapMany(found -> referenceData.findByTypeOrderByDisplayOrderAscNameAsc(ReferenceType.PROVINCE)
                        .filter(province -> found.getCode().equals(province.getParentCode())))
                .map(ReferenceData::getCode)
                .collectList()
                .flatMap(provinces -> referenceData.findByTypeOrderByDisplayOrderAscNameAsc(ReferenceType.CITY)
                        .filter(city -> provinces.contains(city.getParentCode()))
                        .map(ReferenceData::getCode)
                        .collectList());
    }

    private static void range(List<Criteria> conditions, String field, Instant from, Instant to) {
        if (from == null && to == null) {
            return;
        }
        Criteria criteria = Criteria.where(field);
        if (from != null) criteria.gte(from);
        if (to != null) criteria.lte(to);
        conditions.add(criteria);
    }

    private static void order(List<FieldViolation> violations, String path, Instant from, Instant to) {
        if (from != null && to != null && from.isAfter(to)) {
            violations.add(new FieldViolation(path, "must not be before the start of the range"));
        }
    }

    /**
     * Words for a text search. Quotes and a leading minus are MongoDB search operators; they are
     * removed, so a search term is only ever words.
     */
    static String[] words(String search) {
        if (search == null || search.isBlank()) {
            return new String[0];
        }
        return Arrays.stream(search.trim().split("\\s+"))
                .map(word -> word.replace("\"", "").replaceFirst("^-+", ""))
                .filter(word -> !word.isBlank())
                .toArray(String[]::new);
    }
}
