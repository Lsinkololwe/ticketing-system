package com.pml.identity.web.graphql.dto.platform;

import java.time.Instant;
import java.util.Map;

/**
 * One row of the audit trail. It names who acted and on what, never a contact detail, token or
 * account number: the trail is read by people entitled to see that something happened.
 *
 * <p>{@code at} is when the action happened. {@code receivedAt} is when identity stored the row; for a
 * reach delivered late by another service the two differ, and for an account event there is no receipt.
 */
public record AuditLogEntry(
        String id,
        String source,
        String action,
        String actorId,
        String subjectId,
        String resourceType,
        String resourceId,
        String status,
        Instant at,
        Instant receivedAt,
        Map<String, Object> metadata
) {}
