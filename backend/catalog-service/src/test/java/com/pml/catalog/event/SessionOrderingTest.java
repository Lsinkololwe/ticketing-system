package com.pml.catalog.event;

import com.azure.core.amqp.AmqpRetryOptions;
import com.azure.core.amqp.AmqpTransportType;
import com.azure.messaging.servicebus.ServiceBusClientBuilder;
import com.azure.messaging.servicebus.ServiceBusMessage;
import com.azure.messaging.servicebus.ServiceBusReceivedMessage;
import com.azure.messaging.servicebus.ServiceBusReceiverClient;
import com.azure.messaging.servicebus.ServiceBusSenderClient;
import com.azure.messaging.servicebus.ServiceBusSessionReceiverClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Order is bought per entity, and only per entity.
 *
 * <h2>What this asserts that a unit test cannot</h2>
 * Ordering is a broker property. A test against an in-process fake would be asserting that the
 * fake partitions by session key, which is a fact about the fake. So this runs against the
 * Service Bus emulator, on the real {@code catalog-events} topic, through the real
 * {@code booking-sub} subscription — including its SQL filter and its {@code RequiresSession}
 * flag.
 *
 * <h2>The failure this exists to catch</h2>
 * Not "ordering is broken" — the obvious mistake is the opposite one. A platform that wants a
 * capacity change to land after the tier that it changes can get that by serialising
 * <em>everything</em>, and the result passes every ordering test while turning a topic with
 * three publishers into a queue of one. On-sale minute is exactly when that bites: five thousand
 * reservations against one event, and every message behind whichever tier is slowest.
 *
 * <p>So the assertion is two-sided. Within one {@code tierId} the sequence holds; across two
 * {@code tierId}s neither waits for the other — proven by holding one session's messages
 * unsettled and requiring the other to arrive anyway.</p>
 *
 * <h2>Skips when the emulator is not reachable, and the check is a real send</h2>
 * The emulator lives in the sibling {@code docker-resources} repository, so a CI job holding
 * only this repository must skip rather than fail — a suite that fails for reasons unrelated to
 * the code teaches people to ignore it.
 *
 * <p>The precondition is a <b>round-trip send</b>, not a socket connect, because a socket
 * connect is not evidence. On this machine {@code localhost:5672} is held by Testcontainers
 * Desktop's SSH tunnel, which accepts the connection and even answers the AMQP 1.0 protocol
 * header, while the SDK's session handshake behind it never completes. A socket-based
 * precondition passes there and every test then hangs for its full four-minute retry budget —
 * eight minutes of red for an environment problem. Sending one message with a short retry
 * budget distinguishes the two in six seconds.</p>
 */
// L1: this class stands up no container. It read as L2 only because a comment mentioned
// Testcontainers, and TestLayerLintTest did not strip comments until 2026-09-02 — so a genuine
// over-tag sat behind the same prose-counting defect the lint was written to prevent elsewhere.
@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("ET-PLT-003-R7 · ordered per entity, and not beyond it")
class SessionOrderingTest {

    /**
     * The emulator's well-known development string — not a credential. Documented by Microsoft
     * and identical in every emulator install; the real namespace comes from the environment.
     */
    private static final String EMULATOR = "Endpoint=sb://localhost:5672;"
            + "SharedAccessKeyName=RootManageSharedAccessKey;SharedAccessKey=SAS_KEY_VALUE;"
            + "UseDevelopmentEmulator=true;";

    private static final String TOPIC = "catalog-events";

    /** Session-enabled, because it carries ordered event types. */
    private static final String SUBSCRIPTION = "booking-sub";

    /** An ordered event type whose session key is {@code tierId}, and which this subscription admits. */
    private static final String ORDERED_ROW = "catalog.TicketTierCapacityChanged";

    private static ServiceBusClientBuilder builder;
    private static ServiceBusSenderClient sender;

    @BeforeAll
    static void connect() {
        builder = new ServiceBusClientBuilder()
                .connectionString(EMULATOR)
                // The emulator speaks plain AMQP; the default WebSockets transport does not
                // reach it, and the failure is a timeout rather than a refusal.
                .transportType(AmqpTransportType.AMQP)
                // Six seconds, not the default 240. Every failure here is "the emulator is not
                // there", and waiting four minutes to conclude that is what makes an
                // environment-dependent test intolerable to run.
                .retryOptions(new AmqpRetryOptions()
                        .setTryTimeout(Duration.ofSeconds(6))
                        .setMaxRetries(0));

        try {
            sender = builder.sender().topicName(TOPIC).buildClient();
            sender.sendMessage(capacityChange("reachability-probe", 0));
        } catch (Exception unreachable) {
            if (sender != null) {
                sender.close();
                sender = null;
            }
            Assumptions.abort("Service Bus emulator is not reachable over AMQP at localhost:5672 ("
                    + unreachable.getClass().getSimpleName() + "). Start it with: "
                    + "cd docker-resources && docker compose --profile servicebus up -d");
        }
    }

    @AfterAll
    static void disconnect() {
        if (sender != null) {
            sender.close();
        }
    }

    @Test
    @DisplayName("one entity's sequence arrives in order, and two entities do not block each other")
    void twoEntitiesInterleaveWithoutBlocking() {
        // Fresh ids per run: the emulator keeps its subscriptions between runs, and a leftover
        // message from a previous attempt would otherwise be read as this run's first.
        String tierA = "tier-" + UUID.randomUUID();
        String tierB = "tier-" + UUID.randomUUID();

        // Interleaved on the wire, exactly as two concurrent publishers would produce them.
        for (int seq = 1; seq <= 3; seq++) {
            sender.sendMessage(capacityChange(tierA, seq));
            sender.sendMessage(capacityChange(tierB, seq));
        }

        // --- Session A is accepted and deliberately left unsettled. ---
        try (ServiceBusSessionReceiverClient sessions = sessionReceiver();
             ServiceBusReceiverClient sessionA = sessions.acceptSession(tierA)) {

            List<String> receivedFromA = drain(sessionA, 3);

            assertThat(receivedFromA)
                    .as("""
                        Within one tier the sequence is the point: a capacity change applied \
                        before the tier it changes leaves inventory describing a tier that does \
                        not exist yet.""")
                    .containsExactly("1", "2", "3");

            // Messages from A are still held — not completed, not abandoned. If the broker were
            // serialising the subscription rather than the session, B would now be stuck behind
            // them and the next block would time out.
            try (ServiceBusSessionReceiverClient otherSessions = sessionReceiver();
                 ServiceBusReceiverClient sessionB = otherSessions.acceptSession(tierB)) {

                List<String> receivedFromB = drain(sessionB, 3);

                assertThat(receivedFromB)
                        .as("""
                            Ordering per entity, not global. A global order turns the bus into a \
                            queue of one, and at on-sale every tier waits on the slowest \
                            consumer in the platform.""")
                        .containsExactly("1", "2", "3");
            }
        }
    }

    @Test
    @DisplayName("the subscription's filter admits the ordered row it is configured for")
    void filterAdmitsTheRow() {
        String tier = "tier-" + UUID.randomUUID();
        sender.sendMessage(capacityChange(tier, 1));

        try (ServiceBusSessionReceiverClient sessions = sessionReceiver();
             ServiceBusReceiverClient session = sessions.acceptSession(tier)) {

            assertThat(drain(session, 1))
                    .as("a filter that excluded this row would leave booking's consumer waiting "
                            + "on a message the topic already accepted, and nothing would error")
                    .containsExactly("1");
        }
    }

    // --------------------------------------------------------------------- helpers

    private static ServiceBusSessionReceiverClient sessionReceiver() {
        return builder.sessionReceiver()
                .topicName(TOPIC)
                .subscriptionName(SUBSCRIPTION)
                .buildClient();
    }

    /** Reads up to {@code count} messages without settling any of them. */
    private static List<String> drain(ServiceBusReceiverClient receiver, int count) {
        List<String> sequences = new ArrayList<>();
        for (ServiceBusReceivedMessage message : receiver.receiveMessages(count, Duration.ofSeconds(10))) {
            sequences.add(String.valueOf(message.getApplicationProperties().get("sequence")));
        }
        return sequences;
    }

    private static ServiceBusMessage capacityChange(String tierId, int sequence) {
        ServiceBusMessage message = new ServiceBusMessage(
                "{\"tierId\":\"%s\",\"capacity\":%d}".formatted(tierId, 100 * sequence));
        message.setMessageId(UUID.randomUUID().toString());
        // The session id is the aggregate's id, which is how EventBridge orders a registered event type.
        message.setSessionId(tierId);
        // What the subscription's SQL filter reads.
        message.getApplicationProperties().put("eventType", ORDERED_ROW);
        message.getApplicationProperties().put("sequence", String.valueOf(sequence));
        return message;
    }

}
