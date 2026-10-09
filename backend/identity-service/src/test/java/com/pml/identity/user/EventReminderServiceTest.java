package com.pml.identity.user;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.repository.EventReminderRepository;
import com.pml.identity.service.impl.EventReminderServiceImpl;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The reminder row the reminder workflow's activities write, against a real replica set.
 */
@Tag("L2")
@Tag("ET-NTF-002")
@DisplayName("ET-NTF-002-R5 · reminder writes upsert one row, move it, and never resurrect a cancelled one")
class EventReminderServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
    private static final Instant START = NOW.plus(Duration.ofDays(2));
    private static final String REMINDER = "reminder-service-1";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventReminderRepository repository;
    private static EventReminderServiceImpl reminders;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_event_reminder_service"));
        repository = new ReactiveMongoRepositoryFactory(template).getRepository(EventReminderRepository.class);
        reminders = new EventReminderServiceImpl(repository, TestClock.frozenAt(NOW));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), EventReminder.class).block();
    }

    @Test
    @DisplayName("R5 · scheduling writes one row; scheduling again moves it rather than adding another")
    void scheduleUpsertsAndMoves() {
        reminders.schedule(REMINDER, "user-1", "ticket-1", START, START.minus(Duration.ofHours(24))).block();
        Instant moved = START.plus(Duration.ofDays(1));
        reminders.schedule(REMINDER, "user-1", "ticket-1", moved, moved.minus(Duration.ofHours(24))).block();

        assertThat(template.count(new Query(), EventReminder.class).block()).isEqualTo(1);
        EventReminder row = reminders.findById(REMINDER).block();
        assertThat(row.getStatus()).isEqualTo(ReminderStatus.SCHEDULED);
        assertThat(row.getEventStartsAt()).isEqualTo(moved);
        assertThat(reminders.findByUserIdAndTicketId("user-1", "ticket-1").block().getId()).isEqualTo(REMINDER);
    }

    @Test
    @DisplayName("a cancel is idempotent, and marking sent afterwards leaves it cancelled")
    void cancelIsFinal() {
        reminders.schedule(REMINDER, "user-1", "ticket-1", START, START).block();

        reminders.cancel(REMINDER).block();
        reminders.cancel(REMINDER).block();

        assertThat(reminders.markSent(REMINDER).block().getStatus()).isEqualTo(ReminderStatus.CANCELLED);
    }

    @Test
    @DisplayName("R5 · a scheduled reminder is marked sent once, with the send time")
    void markSentOnce() {
        reminders.schedule(REMINDER, "user-1", "ticket-1", START, START).block();

        reminders.markSent(REMINDER).block();
        EventReminder again = reminders.markSent(REMINDER).block();

        assertThat(again.getStatus()).isEqualTo(ReminderStatus.SENT);
        assertThat(again.getSentAt()).isEqualTo(NOW);
    }

    @Test
    @DisplayName("adoption at boot finds scheduled reminders and nothing else")
    void adoptionFindsOnlyScheduled() {
        reminders.schedule(REMINDER, "user-1", "ticket-1", START, START).block();
        reminders.schedule("reminder-cancelled", "user-1", "ticket-2", START, START).block();
        reminders.cancel("reminder-cancelled").block();

        assertThat(repository.findByStatus(ReminderStatus.SCHEDULED).collectList().block())
                .extracting(EventReminder::getId)
                .containsExactly(REMINDER);
    }
}
