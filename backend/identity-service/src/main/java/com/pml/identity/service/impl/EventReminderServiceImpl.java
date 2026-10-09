package com.pml.identity.service.impl;

import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.repository.EventReminderRepository;
import com.pml.identity.service.EventReminderService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Implementation of EventReminderService.
 *
 * <p>Each write is idempotent, because each is an activity a worker may run twice.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EventReminderServiceImpl implements EventReminderService {

    private final EventReminderRepository reminderRepository;

    /** Every timestamp comes from here. */
    private final java.time.Clock clock;

    @Override
    public Flux<EventReminder> findByUserId(String userId) {
        return reminderRepository.findByUserIdAndStatus(userId, ReminderStatus.SCHEDULED);
    }

    @Override
    public Flux<EventReminder> findByUserIdAndEventId(String userId, String eventId) {
        return reminderRepository.findByUserIdAndEventId(userId, eventId);
    }

    @Override
    public Mono<EventReminder> findById(String reminderId) {
        return reminderRepository.findById(reminderId);
    }

    @Override
    public Mono<EventReminder> findByUserIdAndTicketId(String userId, String ticketId) {
        return reminderRepository.findByUserIdAndTicketId(userId, ticketId);
    }

    @Override
    public Mono<EventReminder> schedule(String reminderId, String userId, String ticketId,
                                        Instant eventStartsAt, Instant reminderAt) {
        return reminderRepository.findById(reminderId)
                .defaultIfEmpty(EventReminder.builder()
                        .id(reminderId)
                        .userId(userId)
                        .ticketId(ticketId)
                        .createdAt(clock.instant())
                        .build())
                .flatMap(reminder -> {
                    if (reminder.getStatus() == ReminderStatus.SCHEDULED
                            && eventStartsAt.equals(reminder.getEventStartsAt())
                            && reminderAt.equals(reminder.getReminderAt())) {
                        return Mono.just(reminder);
                    }
                    reminder.setEventStartsAt(eventStartsAt);
                    reminder.setReminderAt(reminderAt);
                    reminder.setStatus(ReminderStatus.SCHEDULED);
                    reminder.setSentAt(null);
                    return reminderRepository.save(reminder);
                });
    }

    @Override
    public Mono<EventReminder> cancel(String reminderId) {
        return reminderRepository.findById(reminderId)
                .flatMap(reminder -> {
                    if (reminder.getStatus() == ReminderStatus.CANCELLED) {
                        return Mono.just(reminder);
                    }
                    reminder.setStatus(ReminderStatus.CANCELLED);
                    return reminderRepository.save(reminder);
                });
    }

    @Override
    public Mono<EventReminder> markSent(String reminderId) {
        return reminderRepository.findById(reminderId)
                .flatMap(reminder -> {
                    if (reminder.getStatus() != ReminderStatus.SCHEDULED) {
                        return Mono.just(reminder);
                    }
                    reminder.setStatus(ReminderStatus.SENT);
                    reminder.setSentAt(clock.instant());
                    return reminderRepository.save(reminder);
                });
    }
}
