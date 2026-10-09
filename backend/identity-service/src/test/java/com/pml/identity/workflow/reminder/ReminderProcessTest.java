package com.pml.identity.workflow.reminder;

import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.identity.domain.model.EventReminder;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.identity.service.EventReminderService;
import com.pml.identity.web.graphql.dto.SetEventReminderInput;
import com.pml.identity.workflow.notify.NotificationProcess;
import io.temporal.testing.TestWorkflowEnvironment;
import io.temporal.worker.Worker;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * {@code setEventReminder} carries the event's real start on the mutation input,
 * end to end, through {@link ReminderProcess#set} into the workflow's {@code Reschedule} command —
 * so the two fixed offsets fire against the event, never against the moment the mutation is called.
 */
@Tag("L3")
@Tag("ET-NTF-002")
@DisplayName("setEventReminder schedules against the event's real start, not the moment it is called")
class ReminderProcessTest {

    private static final String TICKET = "ticket-1";
    private static final String USER = "user-1";

    private TestWorkflowEnvironment env;
    private ReminderProcess process;
    private FakeEventReminderService reminders;

    @BeforeEach
    void start() {
        env = com.pml.identity.workflow.TemporalTestEnvironments.newInstance();
        reminders = new FakeEventReminderService();
        Worker worker = env.newWorker(TaskQueues.NOTIFY);
        worker.registerWorkflowImplementationTypes(ReminderWorkflowImpl.class);
        worker.registerActivitiesImplementations(
                new ReminderActivitiesImpl(reminders, mock(NotificationProcess.class), Clock.systemUTC()));
        env.start();
        process = new ReminderProcess(new TemporalGateway(env.getWorkflowClient()), reminders);
    }

    @AfterEach
    void stop() {
        env.close();
    }

    @Test
    @DisplayName("a reminder set ten days out is scheduled against that real start, not now")
    void schedulesAgainstTheRealEventStart() {
        Instant eventStart = Instant.ofEpochMilli(env.currentTimeMillis()).plus(Duration.ofDays(10));
        SetEventReminderInput input = new SetEventReminderInput(TICKET, eventStart);

        EventReminder result = process.set(USER, input).block(Duration.ofSeconds(10));

        assertThat(result).isNotNull();
        assertThat(result.getEventStartsAt()).isEqualTo(eventStart);
        assertThat(result.getStatus()).isEqualTo(ReminderStatus.SCHEDULED);
        // one hour after the call, which is where an answer computed from "now" would land
        Instant answerFromNow = Instant.ofEpochMilli(env.currentTimeMillis()).plus(Duration.ofHours(1));
        assertThat(result.getEventStartsAt()).isNotEqualTo(answerFromNow);
    }

    /** Enough of {@link EventReminderService} for the workflow's activities and the process's read-back. */
    static final class FakeEventReminderService implements EventReminderService {
        private final Map<String, EventReminder> byId = new LinkedHashMap<>();
        private final AtomicInteger sequence = new AtomicInteger();

        @Override
        public Flux<EventReminder> findByUserId(String userId) {
            return Flux.fromIterable(byId.values());
        }

        @Override
        public Flux<EventReminder> findByUserIdAndEventId(String userId, String eventId) {
            return Flux.empty();
        }

        @Override
        public Mono<EventReminder> findById(String reminderId) {
            return Mono.justOrEmpty(byId.get(reminderId));
        }

        @Override
        public Mono<EventReminder> findByUserIdAndTicketId(String userId, String ticketId) {
            return byId.values().stream()
                    .filter(r -> userId.equals(r.getUserId()) && ticketId.equals(r.getTicketId()))
                    .findFirst()
                    .map(Mono::just)
                    .orElse(Mono.empty());
        }

        @Override
        public synchronized Mono<EventReminder> schedule(String reminderId, String userId, String ticketId,
                                                          Instant eventStartsAt, Instant reminderAt) {
            EventReminder row = EventReminder.builder()
                    .id(reminderId)
                    .userId(userId)
                    .ticketId(ticketId)
                    .eventStartsAt(eventStartsAt)
                    .reminderAt(reminderAt)
                    .status(ReminderStatus.SCHEDULED)
                    .build();
            byId.put(reminderId, row);
            return Mono.just(row);
        }

        @Override
        public Mono<EventReminder> cancel(String reminderId) {
            EventReminder row = byId.get(reminderId);
            if (row == null) {
                return Mono.empty();
            }
            row.setStatus(ReminderStatus.CANCELLED);
            return Mono.just(row);
        }

        @Override
        public Mono<EventReminder> markSent(String reminderId) {
            EventReminder row = byId.get(reminderId);
            if (row != null && row.getStatus() == ReminderStatus.SCHEDULED) {
                row.setStatus(ReminderStatus.SENT);
            }
            return Mono.justOrEmpty(row);
        }
    }
}
