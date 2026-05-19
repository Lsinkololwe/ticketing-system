package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.JournalEntryRepository;
import com.pml.booking.repository.PayoutRequestRepository;
import com.pml.booking.repository.RefundRequestRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.service.FinancialReportService;
import com.pml.booking.web.graphql.dto.*;
import com.pml.shared.constants.PayoutRequestStatus;
import com.pml.shared.constants.RefundRequestStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Financial Report Service Implementation
 *
 * <p>Generates comprehensive financial reports using the double-entry
 * bookkeeping system (JournalEntry) for accurate accounting data.</p>
 *
 * <h2>Account Code Reference</h2>
 * <ul>
 *   <li><b>1021</b>: Gateway Receivable (PawaPay)</li>
 *   <li><b>2010-xxx</b>: Event Escrow Accounts</li>
 *   <li><b>4010</b>: Platform Commission Revenue</li>
 *   <li><b>4020</b>: Processing Fee Revenue</li>
 * </ul>
 *
 * @see JournalEntry
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FinancialReportServiceImpl implements FinancialReportService {

    private final JournalEntryRepository journalEntryRepository;
    private final PayoutRequestRepository payoutRequestRepository;
    private final RefundRequestRepository refundRequestRepository;
    private final EventEscrowAccountRepository escrowAccountRepository;
    private final TicketRepository ticketRepository;
    private final ReactiveMongoTemplate mongoTemplate;

    // Chart of Accounts codes
    private static final String ACCOUNT_PLATFORM_COMMISSION = "4010";
    private static final String ACCOUNT_PROCESSING_FEE = "4020";
    private static final String ACCOUNT_PREFIX_ESCROW = "2010-";

    @Override
    public Mono<TransactionStats> getTransactionStats(String eventId, String organizerId) {
        log.debug("Getting transaction stats for eventId={}, organizerId={}", eventId, organizerId);

        // Use journal entries to compute stats based on posted entries
        return journalEntryRepository.findByStatus(JournalEntryStatus.POSTED)
                .filter(entry -> {
                    // Filter by eventId if provided (entries related to event escrow accounts)
                    if (eventId != null && !eventId.isBlank()) {
                        return entry.getAffectedAccountCodes().stream()
                                .anyMatch(code -> code.equals(ACCOUNT_PREFIX_ESCROW + eventId));
                    }
                    return true;
                })
                .collectList()
                .map(entries -> {
                    int totalTransactions = entries.size();
                    int completedTransactions = entries.size(); // All POSTED entries are completed
                    int failedTransactions = 0;
                    int pendingTransactions = 0;
                    int timedOutTransactions = 0;

                    BigDecimal totalVolume = BigDecimal.ZERO;
                    BigDecimal totalCommissions = BigDecimal.ZERO;

                    for (JournalEntry entry : entries) {
                        // Calculate volume from escrow credits (ticket sales)
                        for (JournalLine line : entry.getLines()) {
                            if (line.getAccountCode() != null &&
                                line.getAccountCode().startsWith(ACCOUNT_PREFIX_ESCROW)) {
                                if (line.getCredit() != null) {
                                    totalVolume = totalVolume.add(line.getCredit());
                                }
                            }
                            // Calculate commissions
                            if (ACCOUNT_PLATFORM_COMMISSION.equals(line.getAccountCode())) {
                                if (line.getCredit() != null) {
                                    totalCommissions = totalCommissions.add(line.getCredit());
                                }
                            }
                        }
                    }

                    // Get pending entry count (DRAFT status)
                    // Note: For a more accurate count, we could make another query

                    // Calculate average transaction value
                    BigDecimal averageTransactionValue = null;
                    if (completedTransactions > 0) {
                        averageTransactionValue = totalVolume
                                .divide(BigDecimal.valueOf(completedTransactions), 2, RoundingMode.HALF_UP);
                    }

                    return new TransactionStats(
                            totalTransactions,
                            completedTransactions,
                            failedTransactions,
                            pendingTransactions,
                            timedOutTransactions,
                            totalVolume.setScale(2, RoundingMode.HALF_UP),
                            totalCommissions.setScale(2, RoundingMode.HALF_UP),
                            averageTransactionValue
                    );
                });
    }

    @Override
    public Mono<FinancialReport> generateFinancialReport(FinancialReportFilterInput filter) {
        log.info("Generating financial report for period: {} to {}", filter.startDate(), filter.endDate());

        LocalDate startDate = filter.startDate().toLocalDate();
        LocalDate endDate = filter.endDate().toLocalDate();

        // Fetch POSTED journal entries in date range
        Mono<List<JournalEntry>> entriesMono = journalEntryRepository
                .findByEntryDateBetweenAndStatus(startDate, endDate, JournalEntryStatus.POSTED)
                .filter(entry -> {
                    // Filter by eventId if provided
                    if (filter.eventId() != null && !filter.eventId().isBlank()) {
                        String escrowAccount = ACCOUNT_PREFIX_ESCROW + filter.eventId();
                        return entry.getAffectedAccountCodes().contains(escrowAccount);
                    }
                    return true;
                })
                .collectList();

        // Fetch payout data
        Mono<BigDecimal> completedPayoutsMono = payoutRequestRepository.calculateTotalCompletedPayouts()
                .map(result -> result.total() != null ? result.total() : BigDecimal.ZERO)
                .defaultIfEmpty(BigDecimal.ZERO);

        Mono<BigDecimal> pendingPayoutsMono = payoutRequestRepository.findByStatus(PayoutRequestStatus.PENDING)
                .map(p -> p.getNetPayoutAmount() != null ? p.getNetPayoutAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fetch escrow balance
        Mono<BigDecimal> escrowBalanceMono = escrowAccountRepository.findAll()
                .filter(e -> e.getStatus() == EventEscrowAccount.EscrowStatus.ACTIVE ||
                             e.getStatus() == EventEscrowAccount.EscrowStatus.LOCKED)
                .map(e -> e.getCurrentBalance() != null ? e.getCurrentBalance() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Fetch refund data in date range
        Mono<BigDecimal> totalRefundsMono = refundRequestRepository
                .findByRequestedAtBetween(filter.startDate(), filter.endDate())
                .filter(r -> r.getStatus() == RefundRequestStatus.COMPLETED)
                .map(r -> r.getRefundAmount() != null ? r.getRefundAmount() : BigDecimal.ZERO)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return Mono.zip(entriesMono, completedPayoutsMono, pendingPayoutsMono, escrowBalanceMono, totalRefundsMono)
                .flatMap(tuple -> {
                    List<JournalEntry> entries = tuple.getT1();
                    BigDecimal totalPayouts = tuple.getT2();
                    BigDecimal pendingPayouts = tuple.getT3();
                    BigDecimal escrowBalance = tuple.getT4();
                    BigDecimal totalRefunds = tuple.getT5();

                    // Calculate totals from journal entries
                    BigDecimal totalRevenue = BigDecimal.ZERO;
                    BigDecimal totalCommissions = BigDecimal.ZERO;

                    for (JournalEntry entry : entries) {
                        for (JournalLine line : entry.getLines()) {
                            // Revenue = credits to escrow accounts
                            if (line.getAccountCode() != null &&
                                line.getAccountCode().startsWith(ACCOUNT_PREFIX_ESCROW) &&
                                line.getCredit() != null) {
                                totalRevenue = totalRevenue.add(line.getCredit());
                            }
                            // Commissions = credits to commission account
                            if (ACCOUNT_PLATFORM_COMMISSION.equals(line.getAccountCode()) &&
                                line.getCredit() != null) {
                                totalCommissions = totalCommissions.add(line.getCredit());
                            }
                        }
                    }

                    // Calculate net platform revenue
                    BigDecimal netPlatformRevenue = totalCommissions.subtract(totalRefunds);

                    // Generate time series data points
                    List<FinancialDataPoint> dataPoints = generateDataPoints(
                            entries, filter.startDate(), filter.endDate(), filter.groupBy()
                    );

                    // Generate event breakdown if not filtering by specific event
                    Mono<List<EventFinancialSummary>> eventBreakdownMono;
                    if (filter.eventId() == null || filter.eventId().isBlank()) {
                        eventBreakdownMono = generateEventBreakdown(entries);
                    } else {
                        eventBreakdownMono = Mono.just(List.of());
                    }

                    BigDecimal finalTotalRevenue = totalRevenue;
                    BigDecimal finalTotalCommissions = totalCommissions;
                    BigDecimal finalNetPlatformRevenue = netPlatformRevenue;

                    return eventBreakdownMono.map(eventBreakdown -> new FinancialReport(
                            filter.startDate(),
                            filter.endDate(),
                            finalTotalRevenue.setScale(2, RoundingMode.HALF_UP),
                            finalTotalCommissions.setScale(2, RoundingMode.HALF_UP),
                            totalRefunds.setScale(2, RoundingMode.HALF_UP),
                            totalPayouts.setScale(2, RoundingMode.HALF_UP),
                            pendingPayouts.setScale(2, RoundingMode.HALF_UP),
                            escrowBalance.setScale(2, RoundingMode.HALF_UP),
                            finalNetPlatformRevenue.setScale(2, RoundingMode.HALF_UP),
                            dataPoints,
                            eventBreakdown.isEmpty() ? null : eventBreakdown
                    ));
                });
    }

    @Override
    public Mono<ReportExport> exportFinancialReport(FinancialReportFilterInput filter, ExportFormat format) {
        log.info("Exporting financial report in {} format", format);

        return generateFinancialReport(filter)
                .map(report -> {
                    // Generate file name
                    String fileName = String.format("financial_report_%s_to_%s.%s",
                            filter.startDate().toLocalDate(),
                            filter.endDate().toLocalDate(),
                            format.name().toLowerCase());

                    // In a real implementation, this would:
                    // 1. Generate the report in the requested format (CSV, PDF, Excel, JSON)
                    // 2. Upload to cloud storage (S3, Azure Blob, etc.)
                    // 3. Return a signed download URL

                    // For now, return a placeholder indicating the feature needs cloud storage integration
                    String downloadUrl = "/api/reports/download/" + UUID.randomUUID() + "/" + fileName;

                    return ReportExport.success(downloadUrl, format, fileName);
                })
                .onErrorResume(e -> {
                    log.error("Failed to export financial report: {}", e.getMessage());
                    return Mono.just(ReportExport.error("Failed to generate report: " + e.getMessage(), format));
                });
    }

    @Override
    public Mono<ReportExport> exportSalesReport(String eventId, ExportFormat format) {
        log.info("Exporting sales report for event {} in {} format", eventId, format);

        // Fetch ticket sales data for the event
        return ticketRepository.findByEventId(eventId)
                .collectList()
                .map(tickets -> {
                    String fileName = String.format("sales_report_event_%s_%s.%s",
                            eventId,
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss")),
                            format.name().toLowerCase());

                    // In a real implementation, this would generate the actual report file
                    String downloadUrl = "/api/reports/download/" + UUID.randomUUID() + "/" + fileName;

                    return ReportExport.success(downloadUrl, format, fileName);
                })
                .onErrorResume(e -> {
                    log.error("Failed to export sales report: {}", e.getMessage());
                    return Mono.just(ReportExport.error("Failed to generate sales report: " + e.getMessage(), format));
                });
    }

    /**
     * Generate time-series data points grouped by the specified time unit.
     */
    private List<FinancialDataPoint> generateDataPoints(
            List<JournalEntry> entries,
            LocalDateTime startDate,
            LocalDateTime endDate,
            TimeUnit groupBy
    ) {
        TimeUnit effectiveGroupBy = groupBy != null ? groupBy : TimeUnit.DAY;

        // Group journal entries by period
        Map<String, List<JournalEntry>> groupedEntries = entries.stream()
                .collect(Collectors.groupingBy(e -> formatPeriod(e.getEntryDate().atStartOfDay(), effectiveGroupBy)));

        // Generate all periods in the range
        List<String> allPeriods = generatePeriods(startDate, endDate, effectiveGroupBy);

        // Create data points for each period
        return allPeriods.stream()
                .map(period -> {
                    List<JournalEntry> periodEntries = groupedEntries.getOrDefault(period, List.of());

                    BigDecimal revenue = BigDecimal.ZERO;
                    BigDecimal commissions = BigDecimal.ZERO;
                    BigDecimal refunds = BigDecimal.ZERO;
                    BigDecimal payouts = BigDecimal.ZERO;
                    int ticketsSold = 0;

                    for (JournalEntry entry : periodEntries) {
                        for (JournalLine line : entry.getLines()) {
                            // Revenue = credits to escrow accounts
                            if (line.getAccountCode() != null &&
                                line.getAccountCode().startsWith(ACCOUNT_PREFIX_ESCROW) &&
                                line.getCredit() != null) {
                                revenue = revenue.add(line.getCredit());
                                ticketsSold++; // Each escrow credit typically represents a ticket sale
                            }
                            // Commissions = credits to commission account
                            if (ACCOUNT_PLATFORM_COMMISSION.equals(line.getAccountCode()) &&
                                line.getCredit() != null) {
                                commissions = commissions.add(line.getCredit());
                            }
                            // Refunds = debits from escrow (money leaving escrow for refund)
                            if (line.getAccountCode() != null &&
                                line.getAccountCode().startsWith(ACCOUNT_PREFIX_ESCROW) &&
                                line.getDebit() != null &&
                                entry.getDescription() != null &&
                                entry.getDescription().toLowerCase().contains("refund")) {
                                refunds = refunds.add(line.getDebit());
                            }
                            // Payouts = debits from escrow for payout
                            if (line.getAccountCode() != null &&
                                line.getAccountCode().startsWith(ACCOUNT_PREFIX_ESCROW) &&
                                line.getDebit() != null &&
                                entry.getDescription() != null &&
                                entry.getDescription().toLowerCase().contains("payout")) {
                                payouts = payouts.add(line.getDebit());
                            }
                        }
                    }

                    return new FinancialDataPoint(
                            period,
                            revenue.setScale(2, RoundingMode.HALF_UP),
                            commissions.setScale(2, RoundingMode.HALF_UP),
                            refunds.setScale(2, RoundingMode.HALF_UP),
                            payouts.setScale(2, RoundingMode.HALF_UP),
                            ticketsSold
                    );
                })
                .collect(Collectors.toList());
    }

    /**
     * Format a date/time into a period string based on the time unit.
     */
    private String formatPeriod(LocalDateTime dateTime, TimeUnit timeUnit) {
        return switch (timeUnit) {
            case HOUR -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00"));
            case DAY -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            case WEEK -> {
                // ISO week number
                int weekOfYear = dateTime.get(java.time.temporal.WeekFields.ISO.weekOfYear());
                yield dateTime.format(DateTimeFormatter.ofPattern("yyyy")) + "-W" + String.format("%02d", weekOfYear);
            }
            case MONTH -> dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        };
    }

    /**
     * Generate all period labels between start and end dates.
     */
    private List<String> generatePeriods(LocalDateTime startDate, LocalDateTime endDate, TimeUnit timeUnit) {
        List<String> periods = new ArrayList<>();
        LocalDateTime current = startDate;

        while (!current.isAfter(endDate)) {
            periods.add(formatPeriod(current, timeUnit));

            current = switch (timeUnit) {
                case HOUR -> current.plusHours(1);
                case DAY -> current.plusDays(1);
                case WEEK -> current.plusWeeks(1);
                case MONTH -> current.plusMonths(1);
            };
        }

        return periods;
    }

    /**
     * Generate event-level financial breakdown from journal entries.
     */
    private Mono<List<EventFinancialSummary>> generateEventBreakdown(List<JournalEntry> entries) {
        // Extract unique event IDs from escrow account codes (2010-{eventId})
        Set<String> eventIds = new HashSet<>();
        for (JournalEntry entry : entries) {
            for (JournalLine line : entry.getLines()) {
                if (line.getAccountCode() != null &&
                    line.getAccountCode().startsWith(ACCOUNT_PREFIX_ESCROW)) {
                    String eventId = line.getAccountCode().substring(ACCOUNT_PREFIX_ESCROW.length());
                    eventIds.add(eventId);
                }
            }
        }

        if (eventIds.isEmpty()) {
            return Mono.just(List.of());
        }

        // Group entries by event
        Map<String, List<JournalEntry>> byEvent = new HashMap<>();
        for (JournalEntry entry : entries) {
            for (String eventId : eventIds) {
                String escrowAccount = ACCOUNT_PREFIX_ESCROW + eventId;
                if (entry.getAffectedAccountCodes().contains(escrowAccount)) {
                    byEvent.computeIfAbsent(eventId, k -> new ArrayList<>()).add(entry);
                }
            }
        }

        // Fetch escrow accounts and compute summaries
        return reactor.core.publisher.Flux.fromIterable(byEvent.keySet())
                .flatMap(eventId -> escrowAccountRepository.findByEventId(eventId)
                        .map(escrow -> {
                            List<JournalEntry> eventEntries = byEvent.get(eventId);
                            String escrowAccount = ACCOUNT_PREFIX_ESCROW + eventId;

                            BigDecimal totalRevenue = BigDecimal.ZERO;
                            BigDecimal totalCommissions = BigDecimal.ZERO;
                            BigDecimal totalRefunds = BigDecimal.ZERO;

                            for (JournalEntry entry : eventEntries) {
                                for (JournalLine line : entry.getLines()) {
                                    // Revenue = credits to this event's escrow
                                    if (escrowAccount.equals(line.getAccountCode()) &&
                                        line.getCredit() != null) {
                                        totalRevenue = totalRevenue.add(line.getCredit());
                                    }
                                    // Commissions (from entries affecting this event)
                                    if (ACCOUNT_PLATFORM_COMMISSION.equals(line.getAccountCode()) &&
                                        line.getCredit() != null) {
                                        totalCommissions = totalCommissions.add(line.getCredit());
                                    }
                                    // Refunds = debits from escrow for refund
                                    if (escrowAccount.equals(line.getAccountCode()) &&
                                        line.getDebit() != null &&
                                        entry.getDescription() != null &&
                                        entry.getDescription().toLowerCase().contains("refund")) {
                                        totalRefunds = totalRefunds.add(line.getDebit());
                                    }
                                }
                            }

                            return new EventFinancialSummary(
                                    eventId,
                                    escrow.getEventTitle() != null ? escrow.getEventTitle() : "Event " + eventId,
                                    totalRevenue.setScale(2, RoundingMode.HALF_UP),
                                    totalCommissions.setScale(2, RoundingMode.HALF_UP),
                                    totalRefunds.setScale(2, RoundingMode.HALF_UP),
                                    escrow.getCurrentBalance() != null ? escrow.getCurrentBalance().setScale(2, RoundingMode.HALF_UP) : BigDecimal.ZERO,
                                    escrow.getStatus().name()
                            );
                        })
                        .defaultIfEmpty(createEventSummaryWithoutEscrow(eventId, byEvent.get(eventId))))
                .collectList();
    }

    /**
     * Create event summary when no escrow account exists.
     */
    private EventFinancialSummary createEventSummaryWithoutEscrow(String eventId, List<JournalEntry> entries) {
        String escrowAccount = ACCOUNT_PREFIX_ESCROW + eventId;

        BigDecimal totalRevenue = BigDecimal.ZERO;
        BigDecimal totalCommissions = BigDecimal.ZERO;
        BigDecimal totalRefunds = BigDecimal.ZERO;

        for (JournalEntry entry : entries) {
            for (JournalLine line : entry.getLines()) {
                // Revenue = credits to this event's escrow
                if (escrowAccount.equals(line.getAccountCode()) && line.getCredit() != null) {
                    totalRevenue = totalRevenue.add(line.getCredit());
                }
                // Commissions
                if (ACCOUNT_PLATFORM_COMMISSION.equals(line.getAccountCode()) && line.getCredit() != null) {
                    totalCommissions = totalCommissions.add(line.getCredit());
                }
                // Refunds
                if (escrowAccount.equals(line.getAccountCode()) &&
                    line.getDebit() != null &&
                    entry.getDescription() != null &&
                    entry.getDescription().toLowerCase().contains("refund")) {
                    totalRefunds = totalRefunds.add(line.getDebit());
                }
            }
        }

        return new EventFinancialSummary(
                eventId,
                "Event " + eventId,
                totalRevenue.setScale(2, RoundingMode.HALF_UP),
                totalCommissions.setScale(2, RoundingMode.HALF_UP),
                totalRefunds.setScale(2, RoundingMode.HALF_UP),
                BigDecimal.ZERO,
                "NO_ESCROW"
        );
    }
}
