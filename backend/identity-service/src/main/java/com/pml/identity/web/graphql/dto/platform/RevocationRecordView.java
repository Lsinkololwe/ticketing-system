package com.pml.identity.web.graphql.dto.platform;

import com.pml.identity.security.revocation.RevocationTrigger;
import com.pml.shared.security.revocation.RevocationType;

import java.time.Instant;

/** One revocation as the administrator's revocation log shows it. */
public record RevocationRecordView(String id, RevocationType type, String value, String reason,
                                   RevocationTrigger trigger, String revokedBy, Instant revokedAt,
                                   Instant expiresAt) {
}
