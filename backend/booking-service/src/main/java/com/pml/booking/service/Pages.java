package com.pml.booking.service;

import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.web.graphql.dto.PaginationInfo;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Offset pages read in the database: one count and one bounded find, rather than the whole
 * collection loaded and sliced in memory.
 */
public final class Pages {

    private Pages() {
    }

    public record Slice<T>(List<T> data, PaginationInfo pagination) {
    }

    /**
     * @param sortable the document fields a caller may sort by; anything else falls back to
     *                 {@code defaultField}, so a client-supplied sort name can never reach the query
     */
    public static <T> Mono<Slice<T>> offset(ReactiveMongoTemplate template, Criteria criteria,
                                            OffsetPaginationInput pagination, Class<T> type,
                                            Set<String> sortable, String defaultField) {
        OffsetPaginationInput page = pagination != null ? pagination : OffsetPaginationInput.defaults();
        int limit = page.getLimit();
        int offset = page.getOffset();
        String field = page.sortBy() != null && sortable.contains(page.sortBy()) ? page.sortBy() : defaultField;
        Sort.Direction direction = page.sortDirection() == OffsetPaginationInput.SortDirection.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        Query base = new Query(criteria);
        Query pageQuery = new Query(criteria).with(Sort.by(new Sort.Order(direction, field), Sort.Order.asc("_id")))
                .skip(offset).limit(limit);
        return Mono.zip(template.count(base, type), template.find(pageQuery, type).collectList())
                .map(result -> {
                    long total = result.getT1();
                    int totalPages = (int) Math.ceil((double) total / limit);
                    return new Slice<>(result.getT2(), new PaginationInfo((int) total, limit, page.page(), totalPages,
                            offset + limit < total, page.page() > 0));
                });
    }
}
