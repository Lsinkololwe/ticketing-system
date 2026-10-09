package com.pml.identity.service;

import com.pml.identity.domain.model.AuditLog;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Writes the immutable audit row for an administrator's action (ET-PLT-009).
 *
 * <p>The row records who did what to which resource and, in {@code metadata}, the non-personal
 * facts of the change. It never carries a contact, a token or a bank number: the audit trail is
 * read by people who are allowed to see that something happened, not what the person's details are.
 */
@Service
public class AdminAuditService {

    private static final int VALUE_LIMIT = 300;

    private final ReactiveMongoTemplate template;
    private final Clock clock;

    public AdminAuditService(ReactiveMongoTemplate template, Clock clock) {
        this.template = template;
        this.clock = clock;
    }

    /** Records a successful administrator action; completes with the saved row. */
    public Mono<AuditLog> record(AuditLog.AuditAction action, String resourceType, String resourceId,
                                 String actorId, Map<String, String> metadata) {
        AuditLog row = AuditLog.success(action, resourceType != null && resourceType.equals("User") ? resourceId : null,
                actorId == null ? "system" : actorId, clock.instant());
        row.setResourceType(resourceType);
        row.setResourceId(resourceId);
        row.setMetadata(clean(metadata));
        return template.insert(row);
    }

    static Map<String, String> clean(Map<String, String> metadata) {
        if (metadata == null || metadata.isEmpty()) {
            return null;
        }
        Map<String, String> cleaned = new LinkedHashMap<>();
        metadata.forEach((key, value) -> {
            if (key != null && value != null) {
                cleaned.put(key, value.length() > VALUE_LIMIT ? value.substring(0, VALUE_LIMIT) : value);
            }
        });
        return cleaned.isEmpty() ? null : cleaned;
    }
}
