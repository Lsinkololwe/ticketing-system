package com.pml.identity.service;

import com.pml.identity.account.AccountService;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.User;
import com.pml.identity.web.graphql.dto.pagination.OffsetPaginationInput;
import com.pml.identity.web.graphql.dto.pagination.PageInfo;
import com.pml.identity.web.graphql.dto.platform.AuditLogEntry;
import com.pml.identity.web.graphql.dto.platform.AuditLogEntryOffsetPage;
import com.pml.identity.web.graphql.dto.platform.AuditLogFilterInput;
import com.pml.identity.web.graphql.dto.platform.GrowthPoint;
import com.pml.shared.constants.PlatformTime;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import lombok.RequiredArgsConstructor;
import org.bson.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationOperation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Administrator reads over accounts and the audit trail. All reads; nothing here changes state. */
@Service
@RequiredArgsConstructor
public class AdminReadService {

    /** Account events the audit trail already carries as audit rows; listing both would double them. */
    private static final Set<String> AUDITED_KINDS = Set.of("SUSPENDED", "UNSUSPENDED", "DELETED");

    private static final long MAX_RANGE_DAYS = 3660;

    private final ReactiveMongoTemplate template;

    // ---- audit trail -------------------------------------------------------------------------------

    public Mono<AuditLogEntryOffsetPage> auditLogs(AuditLogFilterInput filter, OffsetPaginationInput pagination) {
        OffsetPaginationInput p = pagination != null ? pagination : OffsetPaginationInput.defaults();
        AuditLogFilterInput f = filter != null ? filter
                : new AuditLogFilterInput(null, null, null, null, null, null, null, null);
        Mono<Long> total = template.count(new Query(auditCriteria(f)), AuditLog.class);
        Flux<AuditLogEntry> rows = template.find(
                        new Query(auditCriteria(f)).with(Sort.by(Sort.Direction.DESC, "timestamp"))
                                .skip(p.getOffset()).limit(p.getLimit()),
                        AuditLog.class)
                .map(AdminReadService::entry);
        Mono<AuditLogEntryOffsetPage> audits = Mono.zip(rows.collectList(), total).map(t -> page(t.getT1(), t.getT2(), p));
        if (!Boolean.TRUE.equals(f.includeAccountEvents())) {
            return audits;
        }
        // Account events (OTP locks, merges and so on) are merged in by time. They are few and
        // bounded by the same window, so they are read for the page's window rather than paged apart.
        return Mono.zip(audits, accountEvents(f, p.getOffset() + p.getLimit()).collectList())
                .map(t -> {
                    List<AuditLogEntry> merged = new ArrayList<>(t.getT1().content());
                    merged.addAll(t.getT2());
                    merged.sort(Comparator.comparing(AuditLogEntry::at, Comparator.nullsLast(Comparator.reverseOrder())));
                    List<AuditLogEntry> content = merged.stream().limit(p.getLimit()).toList();
                    return page(content, t.getT1().pageInfo().totalElements() + t.getT2().size(), p);
                });
    }

    private Flux<AuditLogEntry> accountEvents(AuditLogFilterInput f, int limit) {
        List<Criteria> all = new ArrayList<>();
        all.add(Criteria.where("kind").nin(AUDITED_KINDS));
        if (f.from() != null) all.add(Criteria.where("at").gte(f.from()));
        if (f.to() != null) all.add(Criteria.where("at").lt(f.to()));
        if (f.action() != null) all.add(Criteria.where("kind").is(f.action()));
        if (f.resourceId() != null) all.add(Criteria.where("accountId").is(f.resourceId()));
        return template.find(Query.query(new Criteria().andOperator(all.toArray(new Criteria[0])))
                        .with(Sort.by(Sort.Direction.DESC, "at")).limit(limit), AccountEvent.class)
                .map(event -> new AuditLogEntry(event.getId(), "ACCOUNT_EVENT", event.getKind(),
                        event.getData() == null ? null : (String) event.getData().get("by"),
                        event.getAccountId(), "User", event.getAccountId(), "SUCCESS", event.getAt(),
                        safe(event.getData())));
    }

    private static Criteria auditCriteria(AuditLogFilterInput f) {
        List<Criteria> all = new ArrayList<>();
        if (f.from() != null) all.add(Criteria.where("timestamp").gte(f.from()));
        if (f.to() != null) all.add(Criteria.where("timestamp").lt(f.to()));
        if (f.action() != null) all.add(Criteria.where("action").is(f.action()));
        if (f.actorId() != null) all.add(Criteria.where("performedBy").is(f.actorId()));
        if (f.resourceType() != null) all.add(Criteria.where("resourceType").is(f.resourceType()));
        if (f.resourceId() != null) all.add(Criteria.where("resourceId").is(f.resourceId()));
        if (f.status() != null) all.add(Criteria.where("status").is(f.status()));
        return all.isEmpty() ? new Criteria() : new Criteria().andOperator(all.toArray(new Criteria[0]));
    }

    private static AuditLogEntry entry(AuditLog row) {
        return new AuditLogEntry(row.getId(), "AUDIT", row.getAction() == null ? null : row.getAction().name(),
                row.getPerformedBy(), row.getUserId(), row.getResourceType(), row.getResourceId(),
                row.getStatus() == null ? null : row.getStatus().name(), row.getTimestamp(),
                row.getMetadata() == null ? Map.of() : new LinkedHashMap<String, Object>(row.getMetadata()));
    }

    /** Account-event data minus anything that is not a plain scalar. */
    private static Map<String, Object> safe(Map<String, Object> data) {
        Map<String, Object> out = new LinkedHashMap<>();
        if (data != null) {
            data.forEach((k, v) -> {
                if (v instanceof String || v instanceof Number || v instanceof Boolean) {
                    out.put(k, v);
                }
            });
        }
        return out;
    }

    private static AuditLogEntryOffsetPage page(List<AuditLogEntry> content, long total, OffsetPaginationInput p) {
        int pages = (int) Math.ceil((double) total / p.getLimit());
        return new AuditLogEntryOffsetPage(content, PageInfo.forOffset((int) total, p.getLimit(), p.page(), pages,
                (long) p.getOffset() + p.getLimit() < total, p.page() > 0));
    }

    // ---- staff -------------------------------------------------------------------------------------

    /** Platform staff: accounts created in the staff realm, by sync or by an administrator. */
    public Mono<List<User>> staff(String search, UserType role) {
        List<Criteria> all = new ArrayList<>();
        all.add(Criteria.where("createdVia").in(AccountService.STAFF_SYNC, AccountService.STAFF_ADMIN));
        if (role != null) {
            all.add(Criteria.where("roles").is(role.name()));
        }
        if (search != null && !search.isBlank()) {
            String pattern = ".*" + java.util.regex.Pattern.quote(search.trim()) + ".*";
            all.add(new Criteria().orOperator(
                    Criteria.where("email").regex(pattern, "i"),
                    Criteria.where("firstName").regex(pattern, "i"),
                    Criteria.where("lastName").regex(pattern, "i"),
                    Criteria.where("displayName").regex(pattern, "i")));
        }
        return template.find(Query.query(new Criteria().andOperator(all.toArray(new Criteria[0])))
                .with(Sort.by("lastName", "firstName")), User.class).collectList();
    }

    // ---- growth ------------------------------------------------------------------------------------

    /** New accounts per bucket across [from, to), with the running total; every bucket present. */
    public Mono<List<GrowthPoint>> userGrowth(Instant from, Instant to, GrowthBuckets.Bucket bucket, UserType role) {
        if (from == null || to == null || !from.isBefore(to)
                || java.time.Duration.between(from, to).toDays() > MAX_RANGE_DAYS) {
            return Mono.error(new TranslatedRefusal(ErrorCode.CONFIGURATION_VALUE_INVALID,
                    "the range must be forward and at most ten years",
                    Map.of("constraint", "from < to and range <= 3660 days")));
        }
        // The first bucket may begin before `from`; count from its start so it is not half a day.
        Instant start = GrowthBuckets.start(from, bucket).toInstant();
        Criteria base = role == null ? new Criteria() : Criteria.where("roles").is(role.name());
        String unit = bucket.name().toLowerCase();
        AggregationOperation match = context -> new Document("$match", new Document("createdAt",
                new Document("$gte", Date.from(start)).append("$lt", Date.from(to))));
        AggregationOperation group = context -> new Document("$group", new Document("_id",
                new Document("$dateTrunc", new Document("date", "$createdAt").append("unit", unit)
                        .append("timezone", PlatformTime.ZONE.getId()).append("startOfWeek", "monday")))
                .append("n", new Document("$sum", 1)));
        List<AggregationOperation> ops = new ArrayList<>();
        if (role != null) {
            ops.add(Aggregation.match(base));
        }
        ops.add(match);
        ops.add(group);
        Mono<Map<Instant, Integer>> counts = template.aggregate(Aggregation.newAggregation(ops), com.pml.identity.persistence.IdentityCollections.USERS, Document.class)
                .collectMap(d -> d.getDate("_id").toInstant(), d -> d.getInteger("n"));
        Mono<Long> before = template.count(Query.query(new Criteria().andOperator(
                Criteria.where("createdAt").lt(start), base)), User.class);
        return Mono.zip(counts, before).map(t -> GrowthBuckets.fill(from, to, bucket, new HashMap<>(t.getT1()), t.getT2()));
    }
}
