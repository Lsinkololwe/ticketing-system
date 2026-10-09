package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.TicketTier;
import com.pml.catalog.infrastructure.client.IdentityServiceClient;
import com.pml.catalog.repository.ApprovalTimelineRepository;
import com.pml.catalog.workflow.approval.ApprovalRules.ApprovalBlocker;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * An event review's announcements and its approval blockers, against a MongoDB replica set with the
 * real event and tier documents; only the HTTP client to identity is replaced.
 */
@Tag("L2")
@Tag("ET-ADM-001")
@DisplayName("Review announcements carry the organizer and submission number; blockers read the stored event")
class ApprovalAnnouncementTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventReviewService reviews;

    private IdentityServiceClient identity;
    private ApprovalAnnouncer announcer;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "catalog_approval_announcement"));
        reviews = new EventReviewService(template,
                new ReactiveMongoRepositoryFactory(template).getRepository(ApprovalTimelineRepository.class),
                TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())),
                TestClock.frozenAt(Instant.parse("2026-09-18T10:00:00Z")));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), Event.class).block();
        template.remove(new Query(), TicketTier.class).block();
        Event event = Event.builder().id("event-1").title("Lusaka Jazz Night").organizerId("organizer-1")
                .organizationId("org-1").status(EventStatus.PENDING_APPROVAL).build();
        event.setSubmissionCount(2);
        template.save(event).block();
        identity = mock(IdentityServiceClient.class);
        when(identity.notifyApproval(any(), any(), any(), any())).thenReturn(Mono.empty());
        announcer = new ApprovalAnnouncer(template, identity);
    }

    @Test
    @DisplayName("Joining the queue asks identity to tell administrators, keyed by the submission number")
    void pendingIsAnnouncedToAdministrators() {
        announcer.announce("event-1", EventStatus.PENDING_APPROVAL).block();

        verify(identity).notifyApproval("admin.event-pending", "event-1:2:PENDING_APPROVAL", "event-1", "organizer-1");
    }

    @Test
    @DisplayName("A decision names the event's organizer")
    void decisionNamesTheOrganizer() {
        announcer.announce("event-1", EventStatus.REJECTED).block();

        verify(identity).notifyApproval("event.rejected", "event-1:2:REJECTED", "event-1", "organizer-1");
    }

    @Test
    @DisplayName("A status nobody is told about, or an event that no longer exists, sends nothing")
    void quietStatusesAndMissingEventsSendNothing() {
        announcer.announce("event-1", EventStatus.PUBLISHED).block();
        announcer.announce("event-gone", EventStatus.APPROVED).block();

        verify(identity, never()).notifyApproval(any(), any(), any(), any());
    }

    @Test
    @DisplayName("An unreachable identity service fails the announcement so the activity retries")
    void identityFailureIsReturned() {
        when(identity.notifyApproval(any(), any(), any(), any())).thenReturn(Mono.error(new IllegalStateException("down")));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> announcer.announce("event-1", EventStatus.APPROVED).block())
                .hasMessageContaining("down");
    }

    @Test
    @DisplayName("Blockers follow the stored event: an active tier, a location and capacity clear them one by one")
    void blockersFollowTheStoredEvent() {
        Event event = template.findById("event-1", Event.class).block();
        assertThat(reviews.approvalBlockers(event).block())
                .containsExactly(ApprovalBlocker.NO_PUBLISHED_TIER, ApprovalBlocker.NO_LOCATION, ApprovalBlocker.NO_CAPACITY);

        TicketTier tier = new TicketTier();
        tier.setId("tier-1");
        tier.setEventId("event-1");
        tier.setOrganizationId("org-1");
        tier.setCode("GA");
        tier.setName("General");
        tier.setPrice(new BigDecimal("150.00"));
        tier.setQuantity(100);
        tier.setAvailableQuantity(100);
        tier.setActive(true);
        template.save(tier).block();
        event.setLocationId("venue-1");
        event.setTotalCapacity(100);

        assertThat(reviews.approvalBlockers(event).block()).isEmpty();

        tier.setActive(false);
        template.save(tier).block();
        assertThat(reviews.approvalBlockers(event).block()).as("an inactive tier is not a published one")
                .containsExactly(ApprovalBlocker.NO_PUBLISHED_TIER);
    }
}
