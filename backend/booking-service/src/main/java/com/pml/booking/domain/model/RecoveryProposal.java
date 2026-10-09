package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.RecoveryAction;
import com.pml.booking.domain.enums.RecoveryProposalStatus;
import com.pml.booking.persistence.BookingCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * A sensitive action waiting for its second person.
 *
 * <p>The confirmer must differ from the proposer, hold the role the action needs, and act before
 * {@link #expiresAt}. Nothing happens until then: a proposal is only a request. What is confirmed is
 * exactly what is stored here — the subjects, the amount and the parameters — so the confirmer cannot
 * be shown one action and apply another.
 */
@Document(collection = BookingCollections.RECOVERY_PROPOSALS)
@TypeAlias("recovery_proposals")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class RecoveryProposal {

    @Id
    private String id;

    @Version
    private Long version;

    private RecoveryAction action;

    /** PAYMENT_ATTEMPT, CHARGEBACK or PLATFORM_ACCOUNT. */
    private String subjectType;
    private List<String> subjectIds;
    private BigDecimal amount;
    @Builder.Default
    private String currency = "ZMW";
    private Map<String, String> parameters;

    /** PENDING, CONFIRMED, WITHDRAWN or FAILED as stored; EXPIRED is derived. */
    private RecoveryProposalStatus status;

    private String proposedById;
    private Instant proposedAt;
    private String proposalReason;
    private Instant expiresAt;

    private String confirmedById;
    private Instant confirmedAt;
    private String confirmationReason;

    /** What the confirmed action did, in a line. */
    private String outcome;
    private String failureReason;

    @CreatedDate
    private Instant createdAt;

    /** The status as of {@code now}: a pending proposal past its expiry reads {@code EXPIRED}. */
    public RecoveryProposalStatus statusAt(Instant now) {
        return status == RecoveryProposalStatus.PENDING && expiresAt != null && !now.isBefore(expiresAt)
                ? RecoveryProposalStatus.EXPIRED
                : status;
    }
}
