package com.pml.identity.notification;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.netflix.graphql.dgs.internal.DefaultInputObjectMapper;
import com.pml.identity.config.MongoSchemaValidationConfig;
import com.pml.identity.domain.enums.NotificationChannel;
import com.pml.identity.domain.enums.NotificationStatus;
import com.pml.identity.domain.enums.NotificationType;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.model.NotificationPreferences;
import com.pml.identity.infrastructure.messaging.MessagingService;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.identity.repository.NotificationPreferencesRepository;
import com.pml.identity.service.NotificationService;
import com.pml.identity.service.impl.NotificationPreferencesServiceImpl;
import com.pml.identity.web.graphql.dto.UpdateNotificationPreferencesInput;
import com.pml.identity.web.graphql.mutation.NotificationMutationResolver;
import com.pml.identity.workflow.notify.NotificationActivitiesImpl;
import com.pml.identity.workflow.notify.NotificationRules;
import com.pml.identity.workflow.notify.NotificationWorkflow;
import com.pml.identity.workflow.notify.PreferenceGate;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.testing.MongoReplicaSet;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.index.Index;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The notification settings page, end to end against a MongoDB replica set with identity's own
 * collection validators: what it saves is stored and read back, the essential categories cannot be
 * switched off, and an optional message honours what the recipient chose.
 */
@Tag("L2")
@Tag("ET-NTF-001")
@DisplayName("Notification settings are saved whole, essential messages stay on, and reminders honour them")
class NotificationPreferencesTest {

    private static final String ALICE = "user-alice";
    private static final String BOB = "user-bob";
    private static final Validator VALIDATOR = Validation.buildDefaultValidatorFactory().getValidator();

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static NotificationPreferencesRepository preferences;

    private NotificationMutationResolver resolver;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_notification_preferences_it"));
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties()).run(null);
        template.indexOps(IdentityCollections.NOTIFICATION_PREFERENCES)
                .ensureIndex(new Index().on("userId", org.springframework.data.domain.Sort.Direction.ASC).unique()).block();
        preferences = new ReactiveMongoRepositoryFactory(template).getRepository(NotificationPreferencesRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), IdentityCollections.NOTIFICATION_PREFERENCES).block();
        template.remove(new Query(), IdentityCollections.NOTIFICATIONS).block();
        resolver = new NotificationMutationResolver(Mockito.mock(NotificationService.class),
                new NotificationPreferencesServiceImpl(preferences));
    }

    private NotificationPreferences save(String userId, Map<String, Object> payload) {
        return saving(userId, payload).block();
    }

    private Mono<NotificationPreferences> saving(String userId, Map<String, Object> payload) {
        UpdateNotificationPreferencesInput input =
                new DefaultInputObjectMapper().mapToJavaObject(payload, UpdateNotificationPreferencesInput.class);
        return resolver.updateNotificationPreferences(input)
                .contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(
                        Jwt.withTokenValue("t").header("alg", "none").subject(userId).build(), List.of())));
    }

    private List<String> refusedPaths(Map<String, Object> payload) {
        try {
            save(ALICE, payload);
        } catch (ValidationRefusal refused) {
            return refused.violations().stream().map(FieldViolation::path).toList();
        }
        throw new AssertionError("expected a refusal");
    }

    private static Map<String, Object> payload(Object... pairs) {
        Map<String, Object> map = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put((String) pairs[i], pairs[i + 1]);
        }
        return map;
    }

    @Nested
    @DisplayName("saving")
    class Saving {

        @Test
        @DisplayName("every switch the settings page shows is stored and reads back")
        void storesThePage() {
            save(ALICE, payload(
                    "emailEnabled", false, "smsEnabled", true, "whatsappEnabled", true, "pushEnabled", false,
                    "inAppEnabled", false, "eventReminders", false, "marketingEmails", true,
                    "ticketNotifications", true, "eventUpdates", true, "paymentNotifications", true,
                    "teamNotifications", true, "systemAnnouncements", true,
                    "reminderHoursBefore", 6, "quietHoursStart", "22:00", "quietHoursEnd", "07:00",
                    "timezone", "Africa/Lusaka"));

            NotificationPreferences stored = preferences.findByUserId(ALICE).block();
            assertThat(stored.isEmailEnabled()).isFalse();
            assertThat(stored.isPushEnabled()).isFalse();
            assertThat(stored.isInAppEnabled()).isFalse();
            assertThat(stored.isEventReminders()).isFalse();
            assertThat(stored.isMarketingEmails()).isTrue();
            assertThat(stored.getReminderHoursBefore()).isEqualTo(6);
            assertThat(stored.getQuietHoursStart()).isEqualTo("22:00");
            assertThat(stored.getQuietHoursEnd()).isEqualTo("07:00");
            assertThat(stored.getTimezone()).isEqualTo("Africa/Lusaka");
            assertThat(stored.isTicketNotifications()).isTrue();
            assertThat(stored.isPaymentNotifications()).isTrue();
        }

        @Test
        @DisplayName("a save names only what changed and leaves the rest alone")
        void partialSave() {
            save(ALICE, payload("marketingEmails", true));
            save(ALICE, payload("smsEnabled", false));

            NotificationPreferences stored = preferences.findByUserId(ALICE).block();
            assertThat(stored.isMarketingEmails()).isTrue();
            assertThat(stored.isSmsEnabled()).isFalse();
        }

        @Test
        @DisplayName("each user changes their own settings and nobody else's")
        void ownSettingsOnly() {
            save(BOB, payload("marketingEmails", false));
            save(ALICE, payload("marketingEmails", true));

            assertThat(preferences.findByUserId(BOB).block().isMarketingEmails()).isFalse();
            assertThat(preferences.findByUserId(ALICE).block().isMarketingEmails()).isTrue();
        }

        @Test
        @DisplayName("two first saves at once create one row between them")
        void firstSavesRace() {
            Flux.range(0, 8)
                    .flatMap(i -> saving(ALICE, payload("marketingEmails", i % 2 == 0)))
                    .collectList()
                    .block();

            assertThat(template.count(Query.query(Criteria.where("userId").is(ALICE)),
                    IdentityCollections.NOTIFICATION_PREFERENCES).block()).isEqualTo(1);
        }
    }

    @Nested
    @DisplayName("essential messages")
    class Essential {

        @ParameterizedTest
        @ValueSource(strings = {"ticketNotifications", "paymentNotifications", "eventUpdates",
                "teamNotifications", "systemAnnouncements"})
        @DisplayName("cannot be switched off: the save is refused and names the field")
        void cannotBeSwitchedOff(String field) {
            assertThat(refusedPaths(payload(field, false))).containsExactly(field);
            assertThat(preferences.findByUserId(ALICE).block()).as("nothing stored").isNull();
        }
    }

    @Nested
    @DisplayName("quiet hours and time zone")
    class QuietHours {

        @Test
        @DisplayName("a time that is not HH:mm is refused by input validation")
        void timeFormat() {
            UpdateNotificationPreferencesInput input = new UpdateNotificationPreferencesInput(null, null, null, null,
                    null, null, null, null, null, null, null, null, 0, "25:00", "7am", null);

            assertThat(VALIDATOR.validate(input)).extracting(v -> v.getPropertyPath().toString())
                    .contains("quietHoursStart", "quietHoursEnd", "reminderHoursBefore");
        }

        @Test
        @DisplayName("a start without an end is refused")
        void bothEnds() {
            assertThat(refusedPaths(payload("quietHoursStart", "22:00"))).containsExactly("quietHoursEnd");
        }

        @Test
        @DisplayName("a zone that does not exist is refused")
        void zone() {
            assertThat(refusedPaths(payload("timezone", "Mars/Olympus_Mons"))).containsExactly("timezone");
        }
    }

    @Nested
    @DisplayName("sending")
    class Sending {

        private static final Instant AFTERNOON_IN_LUSAKA = Instant.parse("2026-11-14T12:00:00Z");
        private static final Instant NIGHT_IN_LUSAKA = Instant.parse("2026-11-14T21:30:00Z");

        private static final String REMINDER = "event.reminder.24h";
        private static final String ESSENTIAL = "event.approved";

        private NotificationActivitiesImpl activitiesAt(Instant now) {
            return new NotificationActivitiesImpl(template, Mockito.mock(MessagingService.class),
                    new com.pml.identity.security.ContactCrypto(new com.pml.identity.security.FieldEncryptionService(
                            com.pml.identity.security.FieldEncryptionService.generateKey(), "k7")),
                    Clock.fixed(now, ZoneOffset.UTC));
        }

        /** Records the notification exactly as the workflow's first activity does, then asks for its channels. */
        private List<NotificationChannel> channelsFor(String templateKey, Instant now) {
            NotificationActivitiesImpl activities = activitiesAt(now);
            String id = activities.record(new NotificationWorkflow.Request(key(templateKey, now), templateKey, ALICE, null, null));
            return activities.channelsFor(id);
        }

        private Notification stored(String templateKey, Instant now) {
            return template.findById(NotificationRules.notificationId(key(templateKey, now)), Notification.class).block();
        }

        private static String key(String templateKey, Instant now) {
            return templateKey + ":" + now.getEpochSecond();
        }

        @Test
        @DisplayName("a reminder the user switched off is not sent, and is recorded as suppressed with the reason")
        void reminderOff() {
            save(ALICE, payload("eventReminders", false));

            assertThat(channelsFor(REMINDER, AFTERNOON_IN_LUSAKA)).isEmpty();
            Notification notification = stored(REMINDER, AFTERNOON_IN_LUSAKA);
            assertThat(notification.getStatus()).isEqualTo(NotificationStatus.SUPPRESSED);
            assertThat(notification.getData()).containsEntry("suppressedBecause", PreferenceGate.CATEGORY_DISABLED);
        }

        @Test
        @DisplayName("a reminder inside the user's quiet hours, in their zone, is suppressed")
        void quietHours() {
            save(ALICE, payload("quietHoursStart", "22:00", "quietHoursEnd", "07:00", "timezone", "Africa/Lusaka"));

            assertThat(channelsFor(REMINDER, NIGHT_IN_LUSAKA)).isEmpty();
            assertThat(stored(REMINDER, NIGHT_IN_LUSAKA).getData())
                    .containsEntry("suppressedBecause", PreferenceGate.QUIET_HOURS);
            assertThat(channelsFor(REMINDER, AFTERNOON_IN_LUSAKA)).isNotEmpty();
        }

        @Test
        @DisplayName("a reminder goes only by the channels the user left on")
        void channels() {
            save(ALICE, payload("whatsappEnabled", false));

            assertThat(channelsFor(REMINDER, AFTERNOON_IN_LUSAKA)).containsExactly(NotificationChannel.EMAIL);
        }

        @Test
        @DisplayName("an essential message ignores every preference")
        void essentialIgnoresPreferences() {
            save(ALICE, payload("whatsappEnabled", false, "smsEnabled", false, "eventReminders", false,
                    "quietHoursStart", "00:00", "quietHoursEnd", "23:59"));

            assertThat(channelsFor(ESSENTIAL, NIGHT_IN_LUSAKA)).isEqualTo(NotificationRules.CHAIN);
            assertThat(stored(ESSENTIAL, NIGHT_IN_LUSAKA).getStatus()).isEqualTo(NotificationStatus.PENDING);
        }

        @Test
        @DisplayName("a notification addressed by its subject, with no recipient account, is recorded")
        void subjectAddressedIsRecorded() {
            String id = activitiesAt(AFTERNOON_IN_LUSAKA).record(new NotificationWorkflow.Request(
                    "team.invitation:inv-1", "team.invitation", null, NotificationRules.TEAM_INVITATION, "inv-1"));

            assertThat(template.findById(id, Notification.class).block().getStatus()).isEqualTo(NotificationStatus.PENDING);
        }

        @Test
        @DisplayName("a user who never saved settings gets reminders on every channel")
        void defaults() {
            assertThat(channelsFor(REMINDER, AFTERNOON_IN_LUSAKA)).isEqualTo(NotificationRules.CHAIN);
        }
    }

    @Test
    @DisplayName("the stored document passes identity's own collection validator")
    void validatorAcceptsTheDocument() {
        save(ALICE, payload("quietHoursStart", "21:00", "quietHoursEnd", "06:30", "timezone", "Africa/Lusaka", "inAppEnabled", false));

        Document raw = template.getCollection(IdentityCollections.NOTIFICATION_PREFERENCES)
                .flatMap(collection -> Mono.from(collection.find(new Document("userId", ALICE)).first())).block();
        assertThat(raw).containsEntry("quietHoursStart", "21:00").containsEntry("inAppEnabled", false);
    }
}
