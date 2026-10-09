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

/** Consent granted by an account for a purpose at a version (for example TERMS 2026-10). */
@Document(collection = IdentityCollections.CONSENTS)
@TypeAlias("consents")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class Consent {

    @Id
    private String id;

    private String accountId;

    private String purpose;

    private String version;

    private Instant grantedAt;

    private String source;

    private Instant withdrawnAt;
}
