package com.pml.booking.service.impl;

import com.pml.booking.domain.enums.JournalEntryStatus;
import com.pml.booking.domain.enums.JournalEntryType;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.event.domain.JournalEntryPostedEvent;
import com.pml.booking.exception.UnbalancedJournalEntryException;
import com.pml.booking.repository.JournalEntryRepository;
import com.pml.booking.service.ChartOfAccountsService;
import com.pml.booking.service.JournalService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Journal Service Implementation
 *
 * <p>Manages journal entries - the core of double-entry bookkeeping.
 * Every financial transaction is recorded as a balanced journal entry.</p>
 *
 * <h2>Double-Entry Principle</h2>
 * <p>Every entry MUST satisfy: SUM(debits) = SUM(credits)</p>
 *
 * <h2>Entry Lifecycle</h2>
 * <pre>
 * DRAFT → POSTED → (optional) REVERSED
 * </pre>
 *
 * <h2>Entry Number Format</h2>
 * <p>JE-YYYY-MM-NNNNN (e.g., JE-2024-03-00001)</p>
 *
 * @see JournalService
 * @see JournalEntry
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class JournalServiceImpl implements JournalService {

    private final JournalEntryRepository journalRepository;
    private final ChartOfAccountsService chartOfAccountsService;
    private final ApplicationEventPublisher eventPublisher;

    private static final DateTimeFormatter ENTRY_NUMBER_FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM");

    // ========================================================================
    // ENTRY CREATION
    // ========================================================================

    @Override
    @Transactional
    public Mono<JournalEntry> createEntry(
            String correlationId,
            LocalDateTime entryDate,
            LocalDateTime effectiveDate,
            String description,
            JournalEntryType type,
            List<JournalLine> lines,
            String createdBy,
            Map<String, String> metadata
    ) {
        log.info("Creating journal entry for correlation: {}", correlationId);

        // Validate all account codes first
        List<String> accountCodes = lines.stream()
                .map(JournalLine::getAccountCode)
                .collect(Collectors.toList());

        return chartOfAccountsService.validateAccountCodes(accountCodes)
                .then(generateEntryNumber(entryDate))
                .flatMap(entryNumber -> {
                    JournalEntry entry = JournalEntry.builder()
                            .entryNumber(entryNumber)
                            .correlationId(correlationId)
                            .entryDate(entryDate.toLocalDate())
                            .effectiveDate(effectiveDate != null ? effectiveDate.toLocalDate() : entryDate.toLocalDate())
                            .description(description)
                            .type(type)
                            .lines(new ArrayList<>(lines))
                            .createdBy(createdBy)
                            .metadata(metadata != null ? metadata : new java.util.HashMap<>())
                            .build();

                    // Validate balance
                    if (!entry.isBalanced()) {
                        return Mono.error(new UnbalancedJournalEntryException(
                                entryNumber,
                                entry.getTotalDebits(),
                                entry.getTotalCredits()
                        ));
                    }

                    return journalRepository.save(entry)
                            .doOnSuccess(saved -> log.info("Journal entry created: {} ({})",
                                    saved.getEntryNumber(), saved.getStatus()));
                });
    }

    @Override
    @Transactional
    public Mono<JournalEntry> createAndPostEntry(
            String correlationId,
            LocalDateTime entryDate,
            String description,
            JournalEntryType type,
            List<JournalLine> lines,
            String createdBy,
            Map<String, String> metadata
    ) {
        log.info("Creating and posting journal entry for correlation: {}", correlationId);

        return createEntry(correlationId, entryDate, entryDate, description, type, lines, createdBy, metadata)
                .flatMap(entry -> postEntry(entry.getId(), createdBy));
    }

    // ========================================================================
    // ENTRY POSTING
    // ========================================================================

    @Override
    @Transactional
    public Mono<JournalEntry> postEntry(String entryId, String postedBy) {
        log.info("Posting journal entry: {}", entryId);

        return journalRepository.findById(entryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Journal entry not found: " + entryId)))
                .flatMap(entry -> {
                    // Validate entry can be posted
                    return validateEntry(entry)
                            .then(Mono.defer(() -> {
                                entry.post(postedBy);
                                return journalRepository.save(entry)
                                        .doOnSuccess(posted -> {
                                            log.info("Journal entry posted: {} at {}",
                                                    posted.getEntryNumber(), posted.getPostedAt());
                                            // Publish event for downstream consumers
                                            eventPublisher.publishEvent(JournalEntryPostedEvent.of(posted));
                                        });
                            }));
                });
    }

    @Override
    @Transactional
    public Mono<JournalEntry> postEntryByNumber(String entryNumber, String postedBy) {
        return journalRepository.findByEntryNumber(entryNumber)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Journal entry not found: " + entryNumber)))
                .flatMap(entry -> postEntry(entry.getId(), postedBy));
    }

    // ========================================================================
    // ENTRY REVERSAL
    // ========================================================================

    @Override
    @Transactional
    public Mono<JournalEntry> reverseEntry(String entryId, String reason, String reversedBy) {
        log.info("Reversing journal entry: {}", entryId);

        return journalRepository.findById(entryId)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Journal entry not found: " + entryId)))
                .flatMap(originalEntry -> {
                    if (originalEntry.getStatus() != JournalEntryStatus.POSTED) {
                        return Mono.error(new IllegalStateException(
                                "Only POSTED entries can be reversed. Current status: " +
                                        originalEntry.getStatus()));
                    }

                    if (originalEntry.getReversedByEntryId() != null) {
                        return Mono.error(new IllegalStateException(
                                "Entry already reversed by: " + originalEntry.getReversedByEntryId()));
                    }

                    return generateEntryNumber(LocalDateTime.now())
                            .flatMap(reversalNumber -> {
                                // Create reversal entry with generated entry number
                                JournalEntry reversalEntry = originalEntry.createReversal(
                                        reversalNumber,
                                        reason,
                                        reversedBy
                                );

                                // Save reversal entry
                                return journalRepository.save(reversalEntry)
                                        .flatMap(savedReversal -> {
                                            // Mark original as reversed
                                            originalEntry.markReversed(
                                                    savedReversal.getId(),
                                                    reversedBy
                                            );

                                            // Save original with reversed status
                                            return journalRepository.save(originalEntry)
                                                    .thenReturn(savedReversal)
                                                    .doOnSuccess(reversal -> log.info(
                                                            "Journal entry {} reversed by {}",
                                                            originalEntry.getEntryNumber(),
                                                            reversal.getEntryNumber()
                                                    ));
                                        });
                            });
                });
    }

    @Override
    @Transactional
    public Mono<JournalEntry> reverseEntryByNumber(String entryNumber, String reason, String reversedBy) {
        return journalRepository.findByEntryNumber(entryNumber)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(
                        "Journal entry not found: " + entryNumber)))
                .flatMap(entry -> reverseEntry(entry.getId(), reason, reversedBy));
    }

    // ========================================================================
    // ENTRY QUERIES
    // ========================================================================

    @Override
    public Mono<JournalEntry> findById(String id) {
        return journalRepository.findById(id);
    }

    @Override
    public Flux<JournalEntry> findAll() {
        return journalRepository.findAll();
    }

    @Override
    public Mono<JournalEntry> findByEntryNumber(String entryNumber) {
        return journalRepository.findByEntryNumber(entryNumber);
    }

    @Override
    public Flux<JournalEntry> findByCorrelationId(String correlationId) {
        return journalRepository.findByCorrelationId(correlationId);
    }

    @Override
    public Flux<JournalEntry> findByDateRange(LocalDate startDate, LocalDate endDate) {
        return journalRepository.findByEntryDateBetween(startDate, endDate);
    }

    @Override
    public Flux<JournalEntry> findByStatus(JournalEntryStatus status) {
        return journalRepository.findByStatus(status);
    }

    @Override
    public Flux<JournalEntry> findByAccountCode(String accountCode) {
        return journalRepository.findByAccountCode(accountCode);
    }

    @Override
    public Flux<JournalEntry> findByAccountCodeAndDateRange(
            String accountCode,
            LocalDate startDate,
            LocalDate endDate
    ) {
        return journalRepository.findByAccountCodeAndEntryDateBetween(
                accountCode, startDate, endDate);
    }

    // ========================================================================
    // ENTRY NUMBER GENERATION
    // ========================================================================

    @Override
    public Mono<String> generateEntryNumber(LocalDateTime entryDate) {
        String prefix = "JE-" + entryDate.format(ENTRY_NUMBER_FORMAT) + "-";

        // Find the highest entry number for this month
        return journalRepository.findFirstByEntryNumberStartingWithOrderByEntryNumberDesc(prefix)
                .map(entry -> {
                    // Extract sequence number and increment
                    String currentNumber = entry.getEntryNumber();
                    String sequencePart = currentNumber.substring(prefix.length());
                    int sequence = Integer.parseInt(sequencePart);
                    return prefix + String.format("%05d", sequence + 1);
                })
                .defaultIfEmpty(prefix + "00001");
    }

    // ========================================================================
    // VALIDATION
    // ========================================================================

    @Override
    public Mono<Void> validateEntry(JournalEntry entry) {
        // 1. Check balance
        if (!entry.isBalanced()) {
            return Mono.error(new UnbalancedJournalEntryException(
                    entry.getEntryNumber(),
                    entry.getTotalDebits(),
                    entry.getTotalCredits()
            ));
        }

        // 2. Validate all lines have valid debit XOR credit
        for (JournalLine line : entry.getLines()) {
            if (!line.isValid()) {
                return Mono.error(new IllegalArgumentException(
                        "Invalid journal line for account " + line.getAccountCode() +
                                ": must have exactly one of debit or credit > 0"
                ));
            }
        }

        // 3. Validate all account codes exist and are active
        List<String> accountCodes = entry.getLines().stream()
                .map(JournalLine::getAccountCode)
                .collect(Collectors.toList());

        return chartOfAccountsService.validateAccountCodes(accountCodes);
    }

    /**
     * Calculates the running balance impact for each line.
     * Used for account statement generation.
     *
     * @param lines Journal lines
     * @return Lines with balance impact calculated
     */
    private List<JournalLine> calculateBalanceImpacts(List<JournalLine> lines) {
        // This would be used for statement generation
        // Each line's impact depends on the account's normal balance
        return lines;
    }
}
