package com.pml.identity.domain.model;

import com.pml.identity.persistence.IdentityCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.Map;

/**
 * An account lifecycle or operational fact. {@link #id} is the event id, so writing the same
 * event twice is a duplicate-key, not a second row. {@link #data} carries no personal data.
 */
@Document(collection = IdentityCollections.ACCOUNT_EVENTS)
@TypeAlias("account_events")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class AccountEvent {

    @Id
    private String id;

    private String accountId;

    /** For example PROVISIONED, ACTIVATED, OTP_LOCK, CONTACT_DUPLICATE_REPORT. */
    private String kind;

    private Instant at;

    private Map<String, Object> data;
}
