package com.pml.booking.domain.model;

import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.persistence.BookingCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.math.BigDecimal;
import java.time.Instant;

/** A move of the platform's own money between two of its accounts, with the journal entry that records it. */
@Document(collection = BookingCollections.PLATFORM_TRANSFERS)
@TypeAlias("platform_transfers")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class PlatformTransfer {

    @Id
    private String id;

    /** One transfer per key: repeating the request returns this one instead of moving the money twice. */
    private String idempotencyKey;

    private PlatformAccountType fromAccount;
    private PlatformAccountType toAccount;
    private BigDecimal amount;
    private String currency;
    private String reason;
    private String executedBy;
    /** The proposal that authorised it when it needed a second person. */
    private String proposalId;
    private String journalEntryId;

    @CreatedDate
    private Instant createdAt;
}
