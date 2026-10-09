package com.pml.identity.user;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.enums.NotificationStatus;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.infrastructure.messaging.MessagingService;
import com.pml.identity.security.ContactCrypto;
import com.pml.identity.security.FieldEncryptionService;
import com.pml.identity.workflow.notify.NotificationActivitiesImpl;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import io.temporal.failure.ApplicationFailure;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The notification record and its destination, against a real replica set.
 *
 * <p>The provider is a stub; what is asserted is the one row per key, the destination each subject
 * resolves to on each channel (the WhatsApp number or the email, from the verified contacts), the
 * channels an account can be reached on with its preferred one first, and the failure type the
 * workflow's retry policy acts on.
 */
@Tag("L2")
@Tag("ET-NTF-001")
@DisplayName("ET-NTF-001-R7 · one notification row per key, sent to the destination its subject names")
class NotificationActivitiesTest {

    private static final Instant NOW = Instant.parse("2026-09-13T10:00:00Z");
    private static final String PHONE = "+260971234567";
    private static final String EMAIL = "buyer@example.test";
    private static final String INVITEE_PHONE = "+260961112223";
    private static final String INVITEE_EMAIL = "invitee@example.test";
    private static final ContactCrypto CRYPTO =
            new ContactCrypto(new FieldEncryptionService(FieldEncryptionService.generateKey(), "k7"));

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    private MessagingService messaging;
    private NotificationActivitiesImpl activities;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_notification_activities"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        for (Class<?> type : new Class<?>[]{Notification.class, User.class, TeamInvitation.class,
                VerificationDocument.class, Organization.class, Contact.class}) {
            template.remove(new Query(), type).block();
        }
        // accounts are identified by contacts: a WhatsApp-only buyer, an email-only buyer, both, and neither
        account("user-phone", ContactType.WHATSAPP);
        contact("user-phone", ContactType.WHATSAPP, PHONE);
        account("user-email", ContactType.EMAIL);
        contact("user-email", ContactType.EMAIL, EMAIL);
        account("user-both", ContactType.EMAIL);
        contact("user-both", ContactType.WHATSAPP, PHONE);
        contact("user-both", ContactType.EMAIL, EMAIL);
        account("user-silent", null);
        // an account that predates contacts still carries its number on the document
        template.save(User.builder().id("user-legacy").username("legacy").phoneNumber(PHONE).build()).block();
        template.save(TeamInvitation.builder().id("inv-1").email(INVITEE_EMAIL).phoneNumber(INVITEE_PHONE)
                .organizationId("org-1").build()).block();
        template.save(Organization.builder().id("org-1").name("Org").slug("org").ownerId("user-phone").build()).block();
        VerificationDocument document = new VerificationDocument();
        document.setId("doc-1");
        document.setOrganizationId("org-1");
        template.save(document).block();

        messaging = Mockito.mock(MessagingService.class);
        when(messaging.sendNotification(anyString(), anyString(), anyString())).thenReturn(Mono.just(true));
        activities = new NotificationActivitiesImpl(template, messaging, CRYPTO, TestClock.frozenAt(NOW));
    }

    private static void account(String id, ContactType preferred) {
        template.save(User.builder().id(id).username(id).preferredChannel(preferred).build()).block();
    }

    private static void contact(String accountId, ContactType type, String value) {
        template.insert(Contact.builder().id(accountId + ":" + type).accountId(accountId).type(type)
                .valueHash(accountId + type).valueEncrypted(CRYPTO.encrypt(value).block()).valueMasked("masked")
                .verifiedAt(NOW).primary(true).createdAt(NOW).build()).block();
    }

    @Test
    @DisplayName("R7 · recording one key twice writes one PENDING row with the rendered template")
    void recordingIsIdempotent() {
        Request request = request("team.accepted", "user-phone", NotificationRules.TEAM_INVITATION, "inv-1");

        String first = activities.record(request);
        String second = activities.record(request);

        assertThat(first).isEqualTo(second).isEqualTo(NotificationRules.notificationId(request.deduplicationKey()));
        assertThat(template.count(new Query(), Notification.class).block()).isEqualTo(1);
        Notification row = template.findById(first, Notification.class).block();
        assertThat(row.getStatus()).isEqualTo(NotificationStatus.PENDING);
        assertThat(row.getBody()).isEqualTo(NotificationRules.render("team.accepted").body());
        assertThat(row.getUserId()).isEqualTo("user-phone");
    }

    @Test
    @DisplayName("R5 · a send goes to the recipient's phone, on the channel asked for")
    void sendsToTheRecipient() {
        String id = activities.record(request("ownership.confirmed", "user-phone", NotificationRules.OWNERSHIP_TRANSFER, "t-1"));

        activities.send(id, NotificationChannel.WHATSAPP);

        verify(messaging).sendNotification(PHONE, NotificationRules.render("ownership.confirmed").body(), "whatsapp");
    }

    @Test
    @DisplayName("R6 · a provider that does not accept fails retryably, as CHANNEL_UNAVAILABLE")
    void aProviderRefusalIsRetryable() {
        when(messaging.sendNotification(anyString(), anyString(), anyString())).thenReturn(Mono.just(false));
        String id = activities.record(request("ownership.confirmed", "user-phone", NotificationRules.OWNERSHIP_TRANSFER, "t-1"));

        assertThatThrownBy(() -> activities.send(id, NotificationChannel.WHATSAPP))
                .isInstanceOf(ApplicationFailure.class)
                .satisfies(error -> {
                    assertThat(((ApplicationFailure) error).getType()).isEqualTo(NotificationRules.CHANNEL_UNAVAILABLE);
                    assertThat(((ApplicationFailure) error).isNonRetryable()).isFalse();
                });
    }

    @Test
    @DisplayName("R5 · a recipient with no phone fails at once, as NO_DESTINATION, and no provider is called")
    void noPhoneIsNotRetried() {
        String id = activities.record(request("ownership.confirmed", "user-silent", NotificationRules.OWNERSHIP_TRANSFER, "t-1"));

        assertThatThrownBy(() -> activities.send(id, NotificationChannel.WHATSAPP))
                .isInstanceOf(ApplicationFailure.class)
                .satisfies(error -> {
                    assertThat(((ApplicationFailure) error).getType()).isEqualTo(NotificationRules.NO_DESTINATION);
                    assertThat(((ApplicationFailure) error).isNonRetryable()).isTrue();
                });
        verify(messaging, never()).sendNotification(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("an invitation reaches its addressee, and a document review reaches the organization's owner")
    void subjectsResolveTheirDestination() {
        String invitation = activities.record(request("team.invitation", null, NotificationRules.TEAM_INVITATION, "inv-1"));
        String document = activities.record(request("document.approved", null, NotificationRules.VERIFICATION_DOCUMENT, "doc-1"));

        activities.send(invitation, NotificationChannel.WHATSAPP);
        activities.send(invitation, NotificationChannel.EMAIL);
        activities.send(document, NotificationChannel.WHATSAPP);

        verify(messaging).sendNotification(INVITEE_PHONE, NotificationRules.render("team.invitation").body(), "whatsapp");
        verify(messaging).sendNotification(INVITEE_EMAIL, NotificationRules.render("team.invitation").body(), "email");
        verify(messaging).sendNotification(PHONE, NotificationRules.render("document.approved").body(), "whatsapp");
    }

    @Test
    @DisplayName("an email-only account is reached on email, with no WhatsApp attempt that is certain to fail")
    void anEmailOnlyAccountWorks() {
        String id = activities.record(request("ownership.confirmed", "user-email", NotificationRules.OWNERSHIP_TRANSFER, "t-1"));

        assertThat(activities.channelsFor(id)).containsExactly(NotificationChannel.EMAIL);
        activities.send(id, NotificationChannel.EMAIL);

        verify(messaging).sendNotification(EMAIL, NotificationRules.render("ownership.confirmed").body(), "email");
    }

    @Test
    @DisplayName("an account reachable both ways is tried on its preferred channel first")
    void thePreferredChannelComesFirst() {
        String id = activities.record(request("ownership.confirmed", "user-both", NotificationRules.OWNERSHIP_TRANSFER, "t-1"));

        assertThat(activities.channelsFor(id)).containsExactly(NotificationChannel.EMAIL, NotificationChannel.WHATSAPP);
        String whatsapp = activities.record(request("ownership.confirmed", "user-phone", NotificationRules.OWNERSHIP_TRANSFER, "t-2"));
        assertThat(activities.channelsFor(whatsapp)).containsExactly(NotificationChannel.WHATSAPP);
    }

    @Test
    @DisplayName("an account with a contact nowhere still gets a chain, so the notification fails visibly rather than vanishing")
    void aSilentAccountKeepsTheDefaultChain() {
        String id = activities.record(request("ownership.confirmed", "user-silent", NotificationRules.OWNERSHIP_TRANSFER, "t-1"));

        assertThat(activities.channelsFor(id)).isEqualTo(NotificationRules.CHAIN);
    }

    @Test
    @DisplayName("an account that predates contacts is still reached on the number it carries")
    void aLegacyAccountIsStillReached() {
        String id = activities.record(request("ownership.confirmed", "user-legacy", NotificationRules.OWNERSHIP_TRANSFER, "t-1"));

        assertThat(activities.channelsFor(id)).containsExactly(NotificationChannel.WHATSAPP);
        activities.send(id, NotificationChannel.WHATSAPP);
        verify(messaging).sendNotification(PHONE, NotificationRules.render("ownership.confirmed").body(), "whatsapp");
    }

    @Test
    @DisplayName("R6 · a delivery and a failure are each recorded on the row")
    void outcomesAreRecorded() {
        String delivered = activities.record(request("team.accepted", "user-phone", NotificationRules.TEAM_INVITATION, "inv-1"));
        String failed = activities.record(request("team.accepted", "user-silent", NotificationRules.TEAM_INVITATION, "inv-2"));

        activities.markDelivered(delivered, NotificationChannel.EMAIL);
        activities.markFailed(failed, "every channel failed: WHATSAPP=NO_DESTINATION");

        Notification deliveredRow = template.findById(delivered, Notification.class).block();
        assertThat(deliveredRow.getStatus()).isEqualTo(NotificationStatus.DELIVERED);
        assertThat(deliveredRow.getDeliveredAt()).isEqualTo(NOW);
        assertThat(deliveredRow.getData()).containsEntry("deliveredVia", "EMAIL");
        Notification failedRow = template.findById(failed, Notification.class).block();
        assertThat(failedRow.getStatus()).isEqualTo(NotificationStatus.FAILED);
        assertThat(failedRow.getData()).containsEntry("failureReason", "every channel failed: WHATSAPP=NO_DESTINATION");
    }

    private static Request request(String templateKey, String recipientUserId, String subjectType, String subjectId) {
        return new Request(NotificationRules.key(templateKey, subjectId + ":" + recipientUserId), templateKey,
                recipientUserId, subjectType, subjectId);
    }
}
