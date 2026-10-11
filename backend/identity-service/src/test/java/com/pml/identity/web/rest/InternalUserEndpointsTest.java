package com.pml.identity.web.rest;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.config.IdentityLimitsProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.ContactRepository;
import com.pml.identity.repository.NotificationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.service.UserContactLookup;
import com.pml.identity.service.UserNotifier;
import com.pml.identity.workflow.notify.NotificationProcess;
import com.pml.identity.workflow.notify.NotificationWorkflow.Request;
import com.pml.shared.error.PlatformProblemDetailAdvice;
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
import org.springframework.http.MediaType;
import org.springframework.test.web.reactive.server.WebTestClient;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The three internal user endpoints over a MongoDB replica set with the real repositories; only the
 * start of a message is replaced. Authentication and scope for these paths are asserted for every
 * internal endpoint by the internal-surface test, which discovers them from the controllers.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("Internal user notification and contact lookup endpoints answer the contract and leak nothing")
class InternalUserEndpointsTest {

    private static final String PHONE = "+260971234567";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static UserRepository users;
    private static ContactRepository contacts;
    private static NotificationRepository notifications;
    private static final ContactHasher HASHER = new ContactHasher("internal-user-endpoints-hash-key");

    private NotificationProcess process;
    private WebTestClient web;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_internal_user_endpoints"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        users = factory.getRepository(UserRepository.class);
        contacts = factory.getRepository(ContactRepository.class);
        notifications = factory.getRepository(NotificationRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), User.class).block();
        template.remove(new Query(), Contact.class).block();
        template.remove(new Query(), Notification.class).block();
        account("mary", "Mary", "Kabwe", true, ContactType.WHATSAPP, PHONE);
        account("joe", "Joe", "Banda", false, ContactType.WHATSAPP, "+260972000000");
        process = mock(NotificationProcess.class);
        when(process.start(any())).thenReturn(true);
        web = WebTestClient.bindToController(
                        new InternalUserNotificationController(new UserNotifier(users, contacts, notifications, process, java.time.Clock.systemUTC())),
                        new InternalUserLookupController(new UserContactLookup(HASHER, contacts, users, new IdentityLimitsProperties())))
                .controllerAdvice(new PlatformProblemDetailAdvice(List.of()))
                .build();
    }

    private static void account(String id, String first, String last, boolean active, ContactType type, String value) {
        User user = new User();
        user.setId(id);
        user.setFirstName(first);
        user.setLastName(last);
        user.setActive(active);
        users.save(user).block();
        contacts.save(Contact.builder().id("c-" + id).accountId(id).type(type)
                .valueHash(HASHER.hash(type, value)).valueMasked(HASHER.mask(type, value))
                .primary(true).verifiedAt(Instant.now()).build()).block();
    }

    private WebTestClient.ResponseSpec post(String uri, Object body) {
        return web.post().uri(uri).contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange();
    }

    @Test
    @DisplayName("POST /notifications/users answers the receipt with the channel and a masked destination only")
    void notifyUser() {
        String body = new String(post("/api/internal/notifications/users", Map.of(
                "templateKey", "ticket.resend", "discriminator", "t1:5", "userId", "mary",
                "params", Map.of("ticketNumber", "TKT-1", "eventTitle", "Jazz Night")))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("QUEUED")
                .jsonPath("$.channel").isEqualTo("WHATSAPP")
                .jsonPath("$.destination").value(masked -> assertThat((String) masked).contains("*"))
                .jsonPath("$.recipients").isEqualTo(1)
                .returnResult().getResponseBodyContent(), StandardCharsets.UTF_8);

        assertThat(body).doesNotContain(PHONE).doesNotContain("971234567");
        ArgumentCaptor<Request> sent = ArgumentCaptor.forClass(Request.class);
        verify(process).start(sent.capture());
        assertThat(sent.getValue().deduplicationKey()).isEqualTo("ticket.resend:t1:5:mary");
        assertThat(sent.getValue().toString()).as("the workflow request holds ids only").doesNotContain("Jazz Night");
    }

    @Test
    @DisplayName("a repeated call whose message was recorded answers DUPLICATE and starts nothing more")
    void repeatedCallSendsOnce() {
        Map<String, Object> body = Map.of("templateKey", "ticket.resend", "discriminator", "t1:5", "userId", "mary");
        post("/api/internal/notifications/users", body).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("QUEUED");
        // The first call stored the rendered message under the deduplication id before starting the workflow.
        assertThat(template.findById("notify:ticket.resend:t1:5:mary", Notification.class).block()).isNotNull();

        post("/api/internal/notifications/users", body).expectStatus().isOk()
                .expectBody().jsonPath("$.status").isEqualTo("DUPLICATE").jsonPath("$.destination").isEmpty();

        verify(process, times(1)).start(any());
    }

    @Test
    @DisplayName("an unregistered template is a 400 refusal and sends nothing")
    void unknownTemplate() {
        post("/api/internal/notifications/users", Map.of(
                "templateKey", "payout.unheard-of", "discriminator", "d", "userId", "mary"))
                .expectStatus().isBadRequest();

        verify(process, never()).start(any());
    }

    @Test
    @DisplayName("a disabled and an unknown account get the same NO_VERIFIED_CONTACT receipt")
    void unreachableAccountsAreIndistinguishable() {
        for (String id : List.of("joe", "nobody")) {
            post("/api/internal/notifications/users", Map.of(
                    "templateKey", "ticket.resend", "discriminator", "d", "userId", id))
                    .expectStatus().isOk()
                    .expectBody()
                    .jsonPath("$.status").isEqualTo("NO_VERIFIED_CONTACT")
                    .jsonPath("$.recipients").isEqualTo(0);
        }
        verify(process, never()).start(any());
    }

    @Test
    @DisplayName("POST /notifications/users/batch refuses more than 100 ids and counts outcomes for fewer")
    void batch() {
        List<String> tooMany = new ArrayList<>();
        for (int i = 0; i < 101; i++) {
            tooMany.add("u" + i);
        }
        post("/api/internal/notifications/users/batch", Map.of(
                "templateKey", "event.holders.message", "discriminator", "m:0", "userIds", tooMany))
                .expectStatus().isBadRequest();

        post("/api/internal/notifications/users/batch", Map.of(
                "templateKey", "event.holders.message", "discriminator", "m:0",
                "userIds", List.of("mary", "mary", "joe", "nobody"),
                "params", Map.of("subject", "Doors", "message", "Doors open at 7")))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("QUEUED")
                .jsonPath("$.queued").isEqualTo(1)
                .jsonPath("$.noVerifiedContact").isEqualTo(2)
                .jsonPath("$.recipients").isEqualTo(1);
        verify(process, times(1)).start(any());
    }

    @Test
    @DisplayName("POST /users/lookup finds an account by its normalised contact and returns the short name and a mask")
    void lookupFound() {
        String body = new String(post("/api/internal/users/lookup", Map.of("channel", "WHATSAPP", "value", "0971 234 567"))
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.userId").isEqualTo("mary")
                .jsonPath("$.displayName").isEqualTo("Mary K.")
                .jsonPath("$.maskedContact").value(masked -> assertThat((String) masked).contains("*"))
                .returnResult().getResponseBodyContent(), StandardCharsets.UTF_8);

        assertThat(body).doesNotContain("971234567").doesNotContain("0971 234 567");
        assertThat(new HashSet<>(List.of("userId", "displayName", "maskedContact")))
                .isEqualTo(new HashSet<>(fieldNames(body)));
    }

    @Test
    @DisplayName("a contact nobody holds and one held by a disabled account are both 404 with an empty body")
    void lookupNotFoundAndDisabledAreIndistinguishable() {
        for (String value : List.of("+260973999999", "+260972000000")) {
            byte[] body = post("/api/internal/users/lookup", Map.of("channel", "WHATSAPP", "value", value))
                    .expectStatus().isNotFound()
                    .expectBody().returnResult().getResponseBodyContent();
            assertThat(body == null ? 0 : body.length).isZero();
        }
    }

    @Test
    @DisplayName("a channel that is not a contact type is a 400 refusal")
    void lookupRejectsOtherChannels() {
        post("/api/internal/users/lookup", Map.of("channel", "SMS", "value", PHONE)).expectStatus().isBadRequest();
    }

    private static List<String> fieldNames(String json) {
        List<String> names = new ArrayList<>();
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\"([A-Za-z]+)\":").matcher(json);
        while (matcher.find()) {
            names.add(matcher.group(1));
        }
        return names;
    }
}
