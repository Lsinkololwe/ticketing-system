package com.pml.identity.dto.sync;

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
    @Builder.Default
    private Instant timestamp = Instant.now();

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
