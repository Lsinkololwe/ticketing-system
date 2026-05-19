package com.pml.booking.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.JournalLine;
import com.pml.booking.service.JournalService;
import com.pml.booking.web.graphql.dto.CreateJournalEntryInput;
import com.pml.booking.web.graphql.dto.JournalEntryMutationResponse;
import com.pml.booking.web.graphql.dto.JournalLineInput;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

/**
 * GraphQL Mutation Resolver for Journal Entry Operations.
 *
 * <p>Provides mutations for managing double-entry journal entries.
 * Each journal entry must be balanced (debits = credits).</p>
 *
 * <h2>Journal Entry Lifecycle</h2>
 * <ol>
 *   <li>CREATE: Entry created in DRAFT status</li>
 *   <li>POST: Entry finalized, affects account balances</li>
 *   <li>REVERSE (optional): Creates offsetting entry</li>
 * </ol>
 *
 * <h2>Validation Rules</h2>
 * <ul>
 *   <li>Sum of debits MUST equal sum of credits</li>
 *   <li>All account codes must exist in Chart of Accounts</li>
 *   <li>Posted entries cannot be modified (only reversed)</li>
 * </ul>
 *
 * <h2>OWASP Compliance</h2>
 * <ul>
 *   <li>A01:2021 - Broken Access Control: All actor IDs (createdBy, postedBy, reversedBy)
 *       are extracted from JWT, never from client input</li>
 * </ul>
 *
 * @since 1.0.0
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class JournalEntryMutationResolver {

    private final JournalService journalService;

    /**
     * Create a new journal entry.
     * Schema: createJournalEntry(input: CreateJournalEntryInput!): JournalEntryMutationResponse!
     *
     * <p>Creates a journal entry in DRAFT status. The entry must be balanced
     * (sum of debits = sum of credits) before it can be posted.</p>
     *
     * <p>Security: createdBy is extracted from JWT, not from client input.</p>
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<JournalEntryMutationResponse> createJournalEntry(
            @InputArgument CreateJournalEntryInput input
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(createdBy -> log.info("GraphQL mutation: createJournalEntry(correlationId={}, createdBy={})",
                        input.correlationId(), createdBy))
                .flatMap(createdBy -> {
                    // Convert input lines to domain model
                    var lines = input.lines().stream()
                            .map(this::toJournalLine)
                            .collect(Collectors.toList());

                    // Convert metadata to Map<String, String>
                    java.util.Map<String, String> metadata = new java.util.HashMap<>();
                    if (input.metadata() != null) {
                        input.metadata().forEach((k, v) -> metadata.put(k, v != null ? v.toString() : null));
                    }

                    return journalService.createEntry(
                            input.correlationId(),
                            input.entryDate(),
                            input.getEffectiveDateOrDefault(),
                            input.description(),
                            input.type(),
                            lines,
                            createdBy,
                            metadata
                    );
                })
                .map(entry -> JournalEntryMutationResponse.success(
                        "Journal entry " + entry.getEntryNumber() + " created successfully", entry))
                .onErrorResume(e -> {
                    log.error("Failed to create journal entry: {}", e.getMessage());
                    return Mono.just(JournalEntryMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Post a journal entry.
     * Schema: postJournalEntry(id: ID!): JournalEntryMutationResponse!
     *
     * <p>Changes status from DRAFT to POSTED. Once posted, the entry
     * affects account balances and cannot be modified (only reversed).</p>
     *
     * <p>Security: postedBy is extracted from JWT, not from client input.</p>
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<JournalEntryMutationResponse> postJournalEntry(
            @InputArgument String id
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(postedBy -> log.info("GraphQL mutation: postJournalEntry(id={}, postedBy={})", id, postedBy))
                .flatMap(postedBy -> journalService.postEntry(id, postedBy)
                        .map(entry -> JournalEntryMutationResponse.success(
                                "Journal entry " + entry.getEntryNumber() + " posted successfully", entry)))
                .onErrorResume(e -> {
                    log.error("Failed to post journal entry {}: {}", id, e.getMessage());
                    return Mono.just(JournalEntryMutationResponse.error(e.getMessage()));
                });
    }

    /**
     * Reverse a posted journal entry.
     * Schema: reverseJournalEntry(id: ID!, reason: String!): JournalEntryMutationResponse!
     *
     * <p>Creates a new journal entry with opposite debits/credits to
     * effectively cancel the original entry. Both entries are linked
     * for audit trail purposes.</p>
     *
     * <p>Security: reversedBy is extracted from JWT, not from client input.</p>
     */
    @DgsMutation
    @PreAuthorize("hasRole('ADMIN')")
    public Mono<JournalEntryMutationResponse> reverseJournalEntry(
            @InputArgument String id,
            @InputArgument String reason
    ) {
        return SecurityContextUtils.requireCurrentUserId()
                .doOnNext(reversedBy -> log.info("GraphQL mutation: reverseJournalEntry(id={}, reversedBy={}, reason={})",
                        id, reversedBy, reason))
                .flatMap(reversedBy -> journalService.reverseEntry(id, reversedBy, reason)
                        .map(reversalEntry -> JournalEntryMutationResponse.success(
                                "Journal entry reversed. Reversal entry: " + reversalEntry.getEntryNumber(),
                                reversalEntry)))
                .onErrorResume(e -> {
                    log.error("Failed to reverse journal entry {}: {}", id, e.getMessage());
                    return Mono.just(JournalEntryMutationResponse.error(e.getMessage()));
                });
    }

    // ========================================================================
    // HELPER METHODS
    // ========================================================================

    /**
     * Convert JournalLineInput DTO to domain model.
     */
    private JournalLine toJournalLine(JournalLineInput input) {
        return JournalLine.builder()
                .accountCode(input.accountCode())
                .accountName(input.accountName())
                .debit(input.debit())
                .credit(input.credit())
                .description(input.description())
                .referenceType(input.referenceType())
                .referenceId(input.referenceId())
                .build();
    }
}
