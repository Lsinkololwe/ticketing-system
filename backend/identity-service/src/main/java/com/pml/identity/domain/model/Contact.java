package com.pml.identity.domain.model;

import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.persistence.IdentityCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * A verified WhatsApp number or email owned by exactly one account (CONTRACT 8).
 *
 * <p>The normalised value is stored only in {@link #valueEncrypted}. {@link #valueHash} is the
 * contact key (HMAC-SHA256), used for uniqueness and lookups; {@link #valueMasked} is for display.
 * Uniqueness of a verified, unreleased contact is the partial unique index {@code uniq_verified_contact}.</p>
 */
@Document(collection = IdentityCollections.CONTACTS)
@TypeAlias("contacts")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Contact {

    @Id
    private String id;

    private String accountId;

    private ContactType type;

    /** The contact key: HMAC-SHA256 of {@code TYPE:normalized}, lower-case hex. */
    private String valueHash;

    /** AES-GCM ciphertext of the normalised value, prefixed with the key id. */
    private String valueEncrypted;

    /** For example {@code +260 97* ***456} or {@code j***@gmail.com}. */
    private String valueMasked;

    /** Absent until the owner proved control of the contact. */
    private Instant verifiedAt;

    private boolean primary;

    /** Where the contact came from: OTP, MIGRATION, KEYCLOAK_SYNC ... */
    private String source;

    private Instant createdAt;

    /** Set when the contact stops belonging to the account (merge, change, erasure). */
    private Instant releasedAt;
}
