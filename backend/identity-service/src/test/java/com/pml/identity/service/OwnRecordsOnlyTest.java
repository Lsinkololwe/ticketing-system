package com.pml.identity.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.DevicePlatform;
import com.pml.identity.domain.model.Notification;
import com.pml.identity.domain.enums.NotificationStatus;
import com.pml.identity.domain.model.UserDevice;
import com.pml.identity.repository.NotificationRepository;
import com.pml.identity.repository.UserDeviceRepository;
import com.pml.identity.service.impl.NotificationServiceImpl;
import com.pml.identity.service.impl.UserDeviceServiceImpl;
import com.pml.shared.testing.MongoReplicaSet;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
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

/**
 * A user can change only their own notifications and devices, on a real MongoDB.
 *
 * <p>These records are addressed by an id the client supplies, and the id of someone else's is as
 * easy to send as one's own. The refusal has to look like an unknown id: an answer that differs
 * would tell a caller which ids exist.</p>
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("a user's notifications and devices cannot be changed through another user's session")
class OwnRecordsOnlyTest {

    private static final String ALICE = "user-alice";
    private static final String MALLORY = "user-mallory";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static NotificationServiceImpl notifications;
    private static UserDeviceServiceImpl devices;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_own_records"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        NotificationRepository notificationRepository = factory.getRepository(NotificationRepository.class);
        UserDeviceRepository deviceRepository = factory.getRepository(UserDeviceRepository.class);
        Clock clock = Clock.fixed(Instant.parse("2026-10-09T10:00:00Z"), ZoneOffset.UTC);
        notifications = new NotificationServiceImpl(notificationRepository, clock, null, deviceRepository);
        devices = new UserDeviceServiceImpl(deviceRepository, clock);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), Notification.class).block();
        template.remove(new Query(), UserDevice.class).block();
        Notification note = new Notification();
        note.setId("note-alice");
        note.setUserId(ALICE);
        note.setStatus(NotificationStatus.SENT);
        template.save(note).block();
        template.save(UserDevice.builder().id("device-alice").userId(ALICE).deviceToken("token-a")
                .platform(DevicePlatform.IOS).isActive(true).build()).block();
    }

    private Notification note() {
        return template.findById("note-alice", Notification.class).block();
    }

    private UserDevice device() {
        return template.findById("device-alice", UserDevice.class).block();
    }

    @Test
    @DisplayName("marking someone else's notification read changes nothing and returns nothing")
    void markReadIsScoped() {
        assertThat(notifications.markAsRead(MALLORY, "note-alice").block()).isNull();
        assertThat(note().getReadAt()).isNull();
        assertThat(note().getStatus()).isEqualTo(NotificationStatus.SENT);

        assertThat(notifications.markAsRead(ALICE, "note-alice").block()).isNotNull();
        assertThat(note().getReadAt()).isNotNull();
    }

    @Test
    @DisplayName("deleting someone else's notification leaves it in place and answers as it does for an unknown id")
    void deleteIsScoped() {
        Boolean foreign = notifications.deleteNotification(MALLORY, "note-alice").block();
        Boolean unknown = notifications.deleteNotification(MALLORY, "note-does-not-exist").block();

        assertThat(foreign).isEqualTo(unknown);
        assertThat(note()).as("the notification survived").isNotNull();

        notifications.deleteNotification(ALICE, "note-alice").block();
        assertThat(note()).as("the owner can delete it").isNull();
    }

    @Test
    @DisplayName("unregistering someone else's device leaves it active and answers as it does for an unknown id")
    void unregisterIsScoped() {
        Boolean foreign = devices.unregisterDevice(MALLORY, "device-alice").block();
        Boolean unknown = devices.unregisterDevice(MALLORY, "device-does-not-exist").block();

        assertThat(foreign).isEqualTo(unknown);
        assertThat(device().isActive()).as("push delivery to Alice must not be switched off by Mallory").isTrue();

        devices.unregisterDevice(ALICE, "device-alice").block();
        assertThat(device().isActive()).isFalse();
    }
}
