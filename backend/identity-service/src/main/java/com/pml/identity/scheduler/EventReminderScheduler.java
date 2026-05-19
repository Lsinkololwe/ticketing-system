package com.pml.identity.scheduler;

import com.pml.identity.service.EventReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Scheduler for processing event reminders.
 * Runs periodically to check for reminders that are due and sends notifications.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class EventReminderScheduler {

    private final EventReminderService reminderService;

    /**
     * Process scheduled event reminders.
     * Runs every minute to check for reminders that need to be sent.
     */
    @Scheduled(fixedRate = 60000) // Every minute
    public void processReminders() {
        log.debug("Processing scheduled event reminders");

        reminderService.processScheduledReminders()
            .subscribe(
                null,
                error -> log.error("Error processing reminders", error),
                () -> log.debug("Reminder processing complete")
            );
    }
}
