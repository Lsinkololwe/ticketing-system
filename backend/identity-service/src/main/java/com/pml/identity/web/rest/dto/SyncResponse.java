package com.pml.identity.web.rest.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Response DTO for sync operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SyncResponse {

    /**
     * Whether the sync operation was successful.
     */
    private boolean success;

    /**
     * A message describing the result.
     */
    private String message;

    /**
     * The user ID that was synced (if applicable).
     */
    private String userId;

    /**
     * The action taken: CREATED, UPDATED, DELETED, SKIPPED, ERROR
     */
    private String action;

    /**
     * Timestamp of the sync operation.
     */
    /**
     * Set by the caller from the injected clock, not defaulted from the wall clock.
     *
     * <p>A {@code @Builder.Default} of {@code Instant.now()} stamps the moment the object was
     * <em>constructed</em>, which for a sync response is close enough to be plausible and never
     * exactly right — and impossible to assert in a test.</p>
     */
    private Instant timestamp;

    /**
     * Create a success response.
     */
    public static SyncResponse success(String userId, String action, String message) {
        return SyncResponse.builder()
                .success(true)
                .userId(userId)
                .action(action)
                .message(message)
                .build();
    }

    /**
     * Create an error response.
     */
    public static SyncResponse error(String userId, String message) {
        return SyncResponse.builder()
                .success(false)
                .userId(userId)
                .action("ERROR")
                .message(message)
                .build();
    }

    /**
     * Create a skipped response.
     */
    public static SyncResponse skipped(String userId, String reason) {
        return SyncResponse.builder()
                .success(true)
                .userId(userId)
                .action("SKIPPED")
                .message(reason)
                .build();
    }
}
