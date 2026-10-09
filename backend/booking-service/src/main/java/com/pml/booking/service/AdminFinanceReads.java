package com.pml.booking.service;

import com.pml.booking.domain.model.ChargebackRecord;
import com.pml.booking.domain.model.CommissionRecord;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.web.graphql.dto.ChargebackFilterInput;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.shared.constants.ChargebackStatus;
import com.pml.shared.constants.PlatformTime;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Finance lists read in the database: commission records, gateway settlements and chargebacks. */
@Service
public class AdminFinanceReads {

    private static final Set<String> COMMISSION_SORT = Set.of("createdAt", "amount", "status", "pendingAt");
    private static final Set<String> SETTLEMENT_SORT = Set.of("entryDate", "createdAt");
    private static final Set<String> CHARGEBACK_SORT = Set.of("receivedAt", "responseDeadline", "chargebackAmount", "status", "createdAt");

    private final ReactiveMongoTemplate template;

    public AdminFinanceReads(ReactiveMongoTemplate template) {
        this.template = template;
    }

    // ---- commission records --------------------------------------------------------------------

    public record CommissionFilter(CommissionRecord.CommissionStatus status, String eventId, String organizationId,
                                   String ticketId, Instant createdAfter, Instant createdBefore) {
    }

    public record CommissionTotals(BigDecimal pending, BigDecimal earned, BigDecimal cancelled, BigDecimal clawedBack) {
    }

    public record CommissionPage(List<CommissionRecord> data, com.pml.booking.web.graphql.dto.PaginationInfo pagination,
                                 CommissionTotals totals) {
    }

    public Mono<CommissionPage> commissions(CommissionFilter filter, OffsetPaginationInput pagination) {
        List<Criteria> all = new ArrayList<>();
        if (filter != null) {
            if (filter.status() != null) all.add(Criteria.where("status").is(filter.status()));
            if (filter.eventId() != null) all.add(Criteria.where("eventId").is(filter.eventId()));
            if (filter.organizationId() != null) all.add(Criteria.where("organizationId").is(filter.organizationId()));
            if (filter.ticketId() != null) all.add(Criteria.where("ticketId").is(filter.ticketId()));
            if (filter.createdAfter() != null || filter.createdBefore() != null) {
                if (filter.createdAfter() != null && filter.createdBefore() != null && filter.createdAfter().isAfter(filter.createdBefore())) {
                    return Mono.error(new ValidationRefusal(List.of(new FieldViolation("filter.createdBefore", "must not be before createdAfter"))));
                }
                Criteria created = Criteria.where("createdAt");
                if (filter.createdAfter() != null) created.gte(filter.createdAfter());
                if (filter.createdBefore() != null) created.lte(filter.createdBefore());
                all.add(created);
            }
        }
        Criteria criteria = all.isEmpty() ? new Criteria() : new Criteria().andOperator(all);
        return Mono.zip(Pages.offset(template, criteria, pagination, CommissionRecord.class, COMMISSION_SORT, "createdAt"),
                        totals(criteria))
                .map(parts -> new CommissionPage(parts.getT1().data(), parts.getT1().pagination(), parts.getT2()));
    }

    private Mono<CommissionTotals> totals(Criteria criteria) {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.match(criteria),
                Aggregation.group("status").sum("amount").as("amount"));
        return template.aggregate(aggregation, CommissionRecord.class, org.bson.Document.class).collectList().map(rows -> {
            Map<String, BigDecimal> byStatus = new java.util.HashMap<>();
            for (org.bson.Document row : rows) {
                Object amount = row.get("amount");
                byStatus.put(String.valueOf(row.get("_id")), amount instanceof org.bson.types.Decimal128 d ? d.bigDecimalValue()
                        : amount == null ? BigDecimal.ZERO : new BigDecimal(amount.toString()));
            }
            return new CommissionTotals(
                    byStatus.getOrDefault("PENDING", BigDecimal.ZERO), byStatus.getOrDefault("EARNED", BigDecimal.ZERO),
                    byStatus.getOrDefault("CANCELLED", BigDecimal.ZERO), byStatus.getOrDefault("CLAWED_BACK", BigDecimal.ZERO));
        });
    }

    // ---- gateway settlements -------------------------------------------------------------------

    public record SettlementFilter(Instant from, Instant to, String settlementId) {
    }

    public record GatewaySettlement(String settlementId, String journalEntryId, String entryNumber, Instant settlementDate,
                                    BigDecimal grossAmount, BigDecimal feeAmount, BigDecimal netAmount, String bankReference,
                                    String currency, Instant postedAt) {
    }

    public record SettlementPage(List<GatewaySettlement> data, com.pml.booking.web.graphql.dto.PaginationInfo pagination) {
    }

    private static final String RECEIVABLE = "1021";
    private static final String BANK = "1011";
    private static final String FEES = "5010";

    /** The settlements recorded in the ledger, newest first. */
    public Mono<SettlementPage> settlements(SettlementFilter filter, OffsetPaginationInput pagination) {
        List<Criteria> all = new ArrayList<>();
        all.add(Criteria.where("metadata.transactionType").is("GATEWAY_SETTLEMENT"));
        if (filter != null) {
            if (filter.settlementId() != null && !filter.settlementId().isBlank()) {
                all.add(Criteria.where("correlationId").is(filter.settlementId().trim()));
            }
            if (filter.from() != null) all.add(Criteria.where("entryDate").gte(PlatformTime.dateAt(filter.from())));
            if (filter.to() != null) all.add(Criteria.where("entryDate").lte(PlatformTime.dateAt(filter.to())));
        }
        return Pages.offset(template, new Criteria().andOperator(all), pagination, JournalEntry.class, SETTLEMENT_SORT, "entryDate")
                .map(slice -> new SettlementPage(slice.data().stream().map(AdminFinanceReads::settlementOf).toList(), slice.pagination()));
    }

    static GatewaySettlement settlementOf(JournalEntry entry) {
        BigDecimal gross = BigDecimal.ZERO, fee = BigDecimal.ZERO, net = BigDecimal.ZERO;
        for (JournalLine line : entry.getLines()) {
            if (RECEIVABLE.equals(line.getAccountCode())) gross = gross.add(nz(line.getCredit()));
            else if (FEES.equals(line.getAccountCode())) fee = fee.add(nz(line.getDebit()));
            else if (BANK.equals(line.getAccountCode())) net = net.add(nz(line.getDebit()));
        }
        Map<String, String> metadata = entry.getMetadata() == null ? Map.of() : entry.getMetadata();
        String date = metadata.get("settlementDate");
        return new GatewaySettlement(entry.getCorrelationId(), entry.getId(), entry.getEntryNumber(),
                date == null ? null : Instant.parse(date), gross, fee, net, metadata.get("bankReference"),
                entry.getCurrency(), entry.getPostedAt());
    }

    private static BigDecimal nz(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    // ---- chargebacks ---------------------------------------------------------------------------

    public Mono<Pages.Slice<ChargebackRecord>> chargebacks(ChargebackFilterInput filter, OffsetPaginationInput pagination) {
        List<Criteria> all = new ArrayList<>();
        if (filter != null) {
            if (filter.status() != null) all.add(Criteria.where("status").is(filter.status()));
            if (filter.recoveryStatus() != null) all.add(Criteria.where("recoveryStatus").is(filter.recoveryStatus()));
            if (filter.eventId() != null) all.add(Criteria.where("eventId").is(filter.eventId()));
            if (filter.organizerId() != null) all.add(Criteria.where("organizerId").is(filter.organizerId()));
            if (filter.startDate() != null || filter.endDate() != null) {
                Criteria received = Criteria.where("receivedAt");
                if (filter.startDate() != null) received.gte(filter.startDate());
                if (filter.endDate() != null) received.lte(filter.endDate());
                all.add(received);
            }
            if (filter.deadlineBefore() != null || filter.deadlineAfter() != null) {
                Criteria deadline = Criteria.where("responseDeadline");
                if (filter.deadlineAfter() != null) deadline.gte(LocalDate.ofInstant(filter.deadlineAfter(), PlatformTime.ZONE));
                if (filter.deadlineBefore() != null) deadline.lte(LocalDate.ofInstant(filter.deadlineBefore(), PlatformTime.ZONE));
                all.add(deadline);
            }
            if (Boolean.TRUE.equals(filter.awaitingResponse())) {
                all.add(Criteria.where("status").in(ChargebackStatus.RECEIVED, ChargebackStatus.UNDER_REVIEW));
            }
        }
        Criteria criteria = all.isEmpty() ? new Criteria() : new Criteria().andOperator(all);
        return Pages.offset(template, criteria, pagination, ChargebackRecord.class, CHARGEBACK_SORT, "receivedAt");
    }
}
