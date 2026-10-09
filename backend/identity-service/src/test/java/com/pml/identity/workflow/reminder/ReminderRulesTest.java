package com.pml.identity.workflow.reminder;

import com.pml.identity.domain.enums.ReminderStatus;
import com.pml.shared.workflow.Refusal;
import com.pml.identity.workflow.reminder.ReminderRules.Offset;
import com.pml.identity.workflow.reminder.ReminderWorkflow.Reschedule;
import com.pml.identity.workflow.reminder.ReminderWorkflow.View;
import com.pml.shared.error.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-NTF-002")
@DisplayName("ET-NTF-002-R5 · reminder rules: the two moments, the dropped late ones, the key")
class ReminderRulesTest {

    private static final long HOUR = Duration.ofHours(1).toMillis();
    private static final long START = 100 * HOUR;

    @Test
    @DisplayName("R5 · both moments ahead: the 24-hour reminder is next")
    void twentyFourHoursFirst() {
        assertThat(ReminderRules.next(START, START - 30 * HOUR, EnumSet.noneOf(Offset.class))).contains(Offset.T_MINUS_24H);
        assertThat(ReminderRules.fireAt(START, Offset.T_MINUS_24H)).isEqualTo(START - 24 * HOUR);
        assertThat(ReminderRules.fireAt(START, Offset.T_MINUS_1H)).isEqualTo(START - HOUR);
    }

    @Test
    @DisplayName("R5 · a moment already past is dropped, not sent late")
    void pastMomentsAreDropped() {
        assertThat(ReminderRules.next(START, START - 10 * HOUR, EnumSet.noneOf(Offset.class))).contains(Offset.T_MINUS_1H);
        assertThat(ReminderRules.next(START, START - HOUR, EnumSet.noneOf(Offset.class)))
                .as("at the one-hour moment exactly it is no longer ahead").isEmpty();
        assertThat(ReminderRules.next(START, START + HOUR, EnumSet.noneOf(Offset.class))).isEmpty();
    }

    @Test
    @DisplayName("a sent offset is never chosen again")
    void sentOffsetsAreSkipped() {
        assertThat(ReminderRules.next(START, START - 30 * HOUR, EnumSet.of(Offset.T_MINUS_24H))).contains(Offset.T_MINUS_1H);
        assertThat(ReminderRules.next(START, 0, EnumSet.allOf(Offset.class))).isEmpty();
    }

    @Test
    @DisplayName("a reminder known only by its moment implies a start one hour later, so it fires at that moment")
    void impliedStart() {
        long reminderAt = 50 * HOUR;
        assertThat(ReminderRules.fireAt(ReminderRules.impliedEventStart(reminderAt), Offset.T_MINUS_1H)).isEqualTo(reminderAt);
    }

    @Test
    @DisplayName("§4 · reminder:{ticketId}:{reminderType}, distinct per offset")
    void deduplicationKey() {
        assertThat(ReminderRules.deduplicationKey("tk-1", Offset.T_MINUS_24H)).isEqualTo("reminder:tk-1:T_MINUS_24H");
        assertThat(ReminderRules.deduplicationKey("tk-1", Offset.T_MINUS_1H)).isNotEqualTo(ReminderRules.deduplicationKey("tk-1", Offset.T_MINUS_24H));
        assertThat(Offset.T_MINUS_24H.templateKey()).isEqualTo("event.reminder.24h");
        assertThat(Offset.T_MINUS_1H.templateKey()).isEqualTo("event.reminder.1h");
    }

    @Test
    @DisplayName("only the holder cancels or moves their reminder")
    void holderOnly() {
        View view = new View("r-1", "user-1", "tk-1", ReminderStatus.SCHEDULED, START);
        assertThat(ReminderRules.cancelRefusal(view, "user-1")).isEmpty();
        assertThat(ReminderRules.cancelRefusal(view, "user-2")).map(Refusal::code).contains(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(ReminderRules.cancelRefusal(null, "user-1")).map(Refusal::code).contains(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(ReminderRules.rescheduleRefusal(new Reschedule("r-1", "user-2", "tk-1", START), view))
                .map(Refusal::code).contains(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(ReminderRules.rescheduleRefusal(new Reschedule("r-1", "user-1", "tk-1", 0), view))
                .map(Refusal::code).contains(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(ReminderRules.rescheduleRefusal(new Reschedule("r-1", "user-1", "tk-1", START), view)).isEmpty();
    }
}
