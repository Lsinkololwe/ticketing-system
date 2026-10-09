package com.pml.identity.service;

import com.pml.identity.domain.model.User;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.shared.constants.UserType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@Tag("L1")
@Tag("ET-NTF-002")
@DisplayName("D-32 · a booking escalation reaches every active finance lead, once per lead and escalation")
class FinanceLeadNotifierTest {

    private final UserRepository users = mock(UserRepository.class);
    private final NotificationProcess notifications = mock(NotificationProcess.class);
    private final FinanceLeadNotifier notifier = new FinanceLeadNotifier(users, notifications);

    @Test
    @DisplayName("contacts are the active leads with an email address")
    void contactsAreActiveLeadsWithEmail() {
        User active = user("lead-1", "lead-1@example.test", true);
        User inactive = user("lead-2", "lead-2@example.test", false);
        User noEmail = user("lead-3", " ", true);
        when(users.findByRole(UserType.FINANCE_LEAD)).thenReturn(Flux.just(active, inactive, noEmail));

        StepVerifier.create(notifier.contacts())
                .expectNext(new FinanceLeadNotifier.Contact("lead-1", "lead-1@example.test"))
                .verifyComplete();
    }

    @Test
    @DisplayName("each active lead gets one notification keyed by the escalation and the lead")
    void eachLeadIsNotified() {
        User first = user("lead-1", "lead-1@example.test", true);
        User withoutEmail = user("lead-2", null, true);
        User inactive = user("lead-3", "lead-3@example.test", false);
        when(users.findByRole(UserType.FINANCE_LEAD)).thenReturn(Flux.just(first, withoutEmail, inactive));

        StepVerifier.create(notifier.notifyLeads("finance.refund-waiting", "refund-9:2", "refund-9"))
                .expectNext(2)
                .verifyComplete();

        ArgumentCaptor<Request> requests = ArgumentCaptor.forClass(Request.class);
        verify(notifications, times(2)).startNow(requests.capture());
        assertThat(requests.getAllValues()).extracting(Request::deduplicationKey)
                .containsExactly("finance.refund-waiting:refund-9:2:lead-1", "finance.refund-waiting:refund-9:2:lead-2");
        assertThat(requests.getAllValues()).allSatisfy(request -> {
            assertThat(request.subjectType()).isEqualTo(NotificationRules.FINANCE_ESCALATION);
            assertThat(request.subjectId()).isEqualTo("refund-9");
        });
    }

    @Test
    @DisplayName("a template that is not a finance escalation is refused, and nobody is notified")
    void otherTemplatesAreRefused() {
        StepVerifier.create(notifier.notifyLeads("team.invitation", "x", "y"))
                .expectError(IllegalArgumentException.class)
                .verify();

        verify(notifications, never()).startNow(any());
    }

    private static User user(String id, String email, boolean active) {
        User user = mock(User.class);
        when(user.getId()).thenReturn(id);
        when(user.getEmail()).thenReturn(email);
        when(user.isActive()).thenReturn(active);
        return user;
    }
}
