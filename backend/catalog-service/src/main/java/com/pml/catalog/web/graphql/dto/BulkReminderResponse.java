package com.pml.catalog.web.graphql.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * Response for bulk event publish reminder operations.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BulkReminderResponse {
    private boolean success;
    private String message;
    private int sentCount;
    private int failedCount;
    @Builder.Default
    private List<String> errors = new ArrayList<>();

    public static BulkReminderResponse success(int sentCount, int failedCount) {
        return BulkReminderResponse.builder()
                .success(failedCount == 0)
                .message(sentCount + " reminders sent successfully" + (failedCount > 0 ? ", " + failedCount + " failed" : ""))
                .sentCount(sentCount)
                .failedCount(failedCount)
                .build();
    }

    public static BulkReminderResponse success(int sentCount, int failedCount, List<String> errors) {
        return BulkReminderResponse.builder()
                .success(failedCount == 0)
                .message(sentCount + " reminders sent successfully" + (failedCount > 0 ? ", " + failedCount + " failed" : ""))
                .sentCount(sentCount)
                .failedCount(failedCount)
                .errors(errors)
                .build();
    }

    public static BulkReminderResponse error(String errorMessage) {
        return BulkReminderResponse.builder()
                .success(false)
                .message(errorMessage)
                .sentCount(0)
                .failedCount(0)
                .errors(List.of(errorMessage))
                .build();
    }
}
