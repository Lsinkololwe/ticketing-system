package com.pml.identity.service.impl;

import com.pml.identity.web.graphql.dto.SendNotificationInput;
import com.pml.identity.web.graphql.dto.SetEventReminderInput;
import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.enums.NotificationType;
import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.identity.repository.EventReminderRepository;
import com.pml.identity.service.EventReminderService;
import com.pml.identity.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Implementation of EventReminderService.
 * Manages event reminder scheduling and automated delivery.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventReminderServiceImpl implements EventReminderService {

    private final EventReminderRepository reminderRepository;
    private final NotificationService notificationService;

    @Override
    public Mono<EventReminder> setReminder(String userId, SetEventReminderInput input) {
        log.debug("Setting reminder for user {} for ticket {}, {} minutes before",
            userId, input.ticketId(), input.minutesBefore());

        return reminderRepository.findByUserIdAndTicketId(userId, input.ticketId())
            .flatMap(existing -> {
                // Update existing reminder
                log.debug("Updating existing reminder {}", existing.getId());
                LocalDateTime reminderTime = calculateReminderTime(input.minutesBefore());
                existing.setReminderAt(reminderTime);
                existing.setStatus(ReminderStatus.SCHEDULED);
                return reminderRepository.save(existing);
            })
            .switchIfEmpty(Mono.defer(() -> {
                // Create new reminder
                log.debug("Creating new reminder for ticket {}", input.ticketId());
                LocalDateTime reminderTime = calculateReminderTime(input.minutesBefore());

                EventReminder reminder = EventReminder.builder()
                    .userId(userId)
                    .ticketId(input.ticketId())
                    .status(ReminderStatus.SCHEDULED)
                    .reminderAt(reminderTime)
                    .createdAt(LocalDateTime.now())
                    .build();

                return reminderRepository.save(reminder);
            }));
    }

    @Override
    public Mono<Boolean> cancelReminder(String reminderId) {
        log.debug("Cancelling reminder {}", reminderId);

        return reminderRepository.findById(reminderId)
            .flatMap(reminder -> {
                reminder.setStatus(ReminderStatus.CANCELLED);
                return reminderRepository.save(reminder);
            })
            .thenReturn(true)
            .onErrorReturn(false);
    }

    @Override
    public Flux<EventReminder> findByUserId(String userId) {
        log.debug("Finding reminders for user {}", userId);
        return reminderRepository.findByUserIdAndStatus(userId, ReminderStatus.SCHEDULED);
    }

    @Override
    public Flux<EventReminder> findByUserIdAndEventId(String userId, String eventId) {
        log.debug("Finding reminders for user {} and event {}", userId, eventId);
        return reminderRepository.findByUserIdAndEventId(userId, eventId);
    }

    @Override
    public Mono<Void> processScheduledReminders() {
        log.debug("Processing scheduled reminders");

        LocalDateTime now = LocalDateTime.now();

        return reminderRepository.findByStatusAndReminderAtBefore(ReminderStatus.SCHEDULED, now)
            .flatMap(this::sendReminder)
            .then();
    }

    /**
     * Send a reminder notification to the user.
     *
     * @param reminder the reminder to send
     * @return Mono containing the updated reminder
     */
    private Mono<EventReminder> sendReminder(EventReminder reminder) {
        log.debug("Sending reminder {} for ticket {}", reminder.getId(), reminder.getTicketId());

        // Create notification for the reminder
        SendNotificationInput notificationInput = new SendNotificationInput(
            reminder.getUserId(),
            NotificationType.EVENT_REMINDER,
            "Event Reminder",
            "Your event is starting soon!",
            Map.of(
                "ticketId", reminder.getTicketId(),
                "eventId", reminder.getEventId() != null ? reminder.getEventId() : "",
                "reminderId", reminder.getId()
            ),
            List.of(
                NotificationChannel.PUSH,
                NotificationChannel.IN_APP,
                NotificationChannel.WHATSAPP
            )
        );

        return notificationService.createNotification(notificationInput)
            .then(Mono.defer(() -> {
                reminder.setStatus(ReminderStatus.SENT);
                reminder.setSentAt(LocalDateTime.now());
                return reminderRepository.save(reminder);
            }))
            .onErrorResume(error -> {
                log.error("Error sending reminder {}: {}", reminder.getId(), error.getMessage());
                return Mono.just(reminder);
            });
    }

    /**
     * Calculate reminder time based on minutes before event.
     * For now, uses a placeholder calculation.
     * TODO: Fetch actual event time from Catalog Service or ticket metadata.
     *
     * @param minutesBefore minutes before event to send reminder
     * @return calculated reminder timestamp
     */
    private LocalDateTime calculateReminderTime(int minutesBefore) {
        // TODO: Fetch event start time from Catalog Service
        // For now, schedule reminder for 'minutesBefore' from now (placeholder)
        return LocalDateTime.now().plusMinutes(minutesBefore);
    }
}
