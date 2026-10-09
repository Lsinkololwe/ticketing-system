package com.pml.identity.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.shared.constants.UserType;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;

import java.util.EnumSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Who hears about an event's review, against a MongoDB replica set with the real user queries; only
 * the start of each message is replaced, since sending is the notification workflow's job.
 */
@Tag("L2")
@Tag("ET-NTF-002")
@DisplayName("An event joining the queue reaches every active administrator; a decision reaches its organizer")
class ApprovalNotifierTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static UserRepository users;

    private NotificationProcess notifications;
    private ApprovalNotifier notifier;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_approval_notifier"));
        users = new ReactiveMongoRepositoryFactory(template).getRepository(UserRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), User.class).block();
        user("admin-1", EnumSet.of(UserType.ADMIN), true);
        user("admin-2", EnumSet.of(UserType.ADMIN, UserType.FINANCE), true);
        user("admin-away", EnumSet.of(UserType.ADMIN), false);
        user("super-1", EnumSet.of(UserType.SUPER_ADMIN), true);
        user("organizer-1", EnumSet.of(UserType.ORGANIZER), true);
        notifications = mock(NotificationProcess.class);
        notifier = new ApprovalNotifier(users, notifications);
    }

    @Test
    @DisplayName("A submitted event reaches each active ADMIN once, keyed by the submission and the admin")
    void pendingEventReachesActiveAdmins() {
        Integer addressed = notifier.notify(NotificationRules.EVENT_PENDING, "event-1:1", "event-1", "organizer-1").block();

        ArgumentCaptor<Request> sent = ArgumentCaptor.forClass(Request.class);
        verify(notifications, times(2)).startNow(sent.capture());
        assertThat(addressed).isEqualTo(2);
        assertThat(sent.getAllValues()).extracting(Request::recipientUserId).containsExactlyInAnyOrder("admin-1", "admin-2");
        assertThat(sent.getAllValues()).extracting(Request::deduplicationKey).containsExactlyInAnyOrder(
                "admin.event-pending:event-1:1:admin-1", "admin.event-pending:event-1:1:admin-2");
        assertThat(sent.getAllValues()).allSatisfy(request -> {
            assertThat(request.subjectType()).isEqualTo(NotificationRules.EVENT_REVIEW);
            assertThat(request.subjectId()).isEqualTo("event-1");
        });
    }

    @Test
    @DisplayName("A decision reaches the organizer alone")
    void decisionReachesTheOrganizer() {
        Integer addressed = notifier.notify("event.rejected", "event-1:1:REJECTED", "event-1", "organizer-1").block();

        ArgumentCaptor<Request> sent = ArgumentCaptor.forClass(Request.class);
        verify(notifications).startNow(sent.capture());
        assertThat(addressed).isEqualTo(1);
        assertThat(sent.getValue().recipientUserId()).isEqualTo("organizer-1");
        assertThat(sent.getValue().templateKey()).isEqualTo("event.rejected");
    }

    @Test
    @DisplayName("A decision for an organizer who is gone, or unnamed, addresses nobody")
    void missingOrganizerAddressesNobody() {
        assertThat(notifier.notify("event.approved", "event-1:1:APPROVED", "event-1", "organizer-gone").block()).isZero();
        assertThat(notifier.notify("event.approved", "event-1:1:APPROVED", "event-1", null).block()).isZero();
        verify(notifications, never()).startNow(any());
    }

    @Test
    @DisplayName("A template that is not an event review is refused and nothing is sent")
    void otherTemplatesAreRefused() {
        assertThatThrownBy(() -> notifier.notify("finance.refund-waiting", "x", "event-1", "organizer-1").block())
                .isInstanceOf(IllegalArgumentException.class);
        verify(notifications, never()).startNow(any());
    }

    private static void user(String id, EnumSet<UserType> roles, boolean active) {
        User user = new User();
        user.setId(id);
        user.setRoles(roles);
        user.setActive(active);
        users.save(user).block();
    }
}
