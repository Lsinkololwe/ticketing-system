package com.pml.booking.transfer;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.domain.enums.TicketTransferStatus;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.domain.model.TicketTransfer;
import com.pml.booking.infrastructure.client.dto.NotificationReceipt;
import com.pml.booking.infrastructure.client.dto.UserLookup;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.infrastructure.temporal.WorkflowIds;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.it.BookingFixture.World;
import com.pml.booking.service.Pages;
import com.pml.booking.service.TicketTransferReads;
import com.pml.booking.web.graphql.dto.InitiateTicketTransferInput;
import com.pml.booking.web.graphql.dto.InitiateTicketTransferInput.TransferChannel;
import com.pml.booking.web.graphql.dto.OffsetPaginationInput;
import com.pml.booking.workflow.transfer.TicketTransferActivitiesImpl;
import com.pml.booking.workflow.transfer.TicketTransferProcess;
import com.pml.booking.workflow.transfer.TicketTransferWorkflow;
import com.pml.booking.workflow.transfer.TicketTransferWorkflowImpl;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.event.Outbox;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TemporalDevServer;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.testing.WorkflowReplayer;
import io.temporal.worker.WorkerFactory;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import static com.pml.booking.it.BookingFixture.asAdmin;
import static com.pml.booking.it.BookingFixture.asCustomer;
import static com.pml.booking.it.BookingFixture.asOrganizer;
import static com.pml.booking.it.BookingFixture.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Ticket transfer end to end: the real workflow on a Temporal server, the real activities and
 * database, real Redis for the limits. The identity and catalog services are stubbed at their clients.
 */
@Tag("L2")
@Tag("ET-TKT-004")
@DisplayName("ET-TKT-004 · ticket transfer: only the holder offers, only the recipient answers, the ticket changes hands once")
class TicketTransferTest {

    private static final String QUEUE_NOTE = TaskQueues.CHECKOUT;
    private static MongoClient mongo;
    private static ReactiveMongoTemplate template;
    private static WorkflowServiceStubs service;
    private static WorkflowClient workflowClient;
    private static WorkerFactory workers;
    private static Outbox outbox;
    private static final Map<String, UserLookup> DIRECTORY = new ConcurrentHashMap<>();
    private static World world;

    private TicketTransferProcess process;
    private TicketTransferReads reads;
    private String eventId;
    private String holder;
    private String recipient;
    private String recipientEmail;
    private EventSummaryDto event;

    @BeforeAll
    static void start() {
        mongo = BookingFixture.newClient(MongoReplicaSet.connectionString());
        template = BookingFixture.template(mongo, "booking_transfer");
        world = new World();
        when(world.identity.lookupByContact(anyString(), anyString()))
                .thenAnswer(call -> Mono.justOrEmpty(DIRECTORY.get(call.<String>getArgument(1))));
        when(world.identity.notifyUser(anyString(), anyString(), anyString(), any()))
                .thenReturn(Mono.just(NotificationReceipt.queued("WHATSAPP", null, 1)));
        outbox = new Outbox(template, "booking_outbox", Clock.systemUTC());
        startWorker();
    }

    private static void startWorker() {
        service = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(TemporalDevServer.target()).build());
        workflowClient = WorkflowClient.newInstance(service, WorkflowClientOptions.newBuilder().setNamespace(TemporalDevServer.NAMESPACE).build());
        workers = WorkerFactory.newInstance(workflowClient);
        var worker = workers.newWorker(QUEUE_NOTE);
        worker.registerWorkflowImplementationTypes(TicketTransferWorkflowImpl.class);
        worker.registerActivitiesImplementations(new TicketTransferActivitiesImpl(template,
                TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory())), outbox,
                world.identity, Clock.systemUTC(), noCurrentEvent()));
        workers.start();
    }

    @AfterAll
    static void stop() {
        workers.shutdownNow();
        service.shutdownNow();
        mongo.close();
    }

    @BeforeEach
    void seed() {
        eventId = "ev-" + UUID.randomUUID();
        holder = "holder-" + UUID.randomUUID();
        recipient = "recipient-" + UUID.randomUUID();
        recipientEmail = recipient + "@example.com";
        event = world.event(eventId, "org-1");
        event.setStartDate(Instant.now().plus(Duration.ofDays(10)));
        event.setStatus(EventStatus.PUBLISHED);
        DIRECTORY.put(recipientEmail, new UserLookup(recipient, "Mary K.", "m***@example.com"));
        process = newProcess(Duration.ofHours(48));
        reads = new TicketTransferReads(template, world.access);
    }

    private TicketTransferProcess newProcess(Duration ttl) {
        return new TicketTransferProcess(new TemporalGateway(workflowClient), template, world.catalog, world.identity,
                BookingFixture.limiter(), Clock.systemUTC(), ttl, 5, Duration.ofHours(2));
    }

    private Ticket ticket() {
        return template.save(Ticket.builder().id(UUID.randomUUID().toString()).ticketNumber("TKT-" + UUID.randomUUID().toString().substring(0, 8))
                .eventId(eventId).eventTitle("Event").buyerId(holder).buyerName("Lazarous Sinkololwe").organizationId("org-1")
                .price(BigDecimal.TEN).status(TicketStatus.ISSUED).qrCode("QR-FIXED-" + UUID.randomUUID())
                .buyerEmail("holder@example.com").buyerPhone("+260971234567").build()).block();
    }

    private TicketTransfer offer(Ticket ticket) {
        return asCustomer(holder, process.initiate(new InitiateTicketTransferInput(ticket.getId(), TransferChannel.EMAIL, recipientEmail, "enjoy", "idem-" + System.nanoTime()), holder))
                .block();
    }

    private Ticket reload(Ticket ticket) {
        return template.findById(ticket.getId(), Ticket.class).block();
    }

    private TicketTransfer reloadTransfer(TicketTransfer transfer) {
        return template.findById(transfer.getId(), TicketTransfer.class).block();
    }

    private long transferEvents(Ticket ticket) {
        return template.count(Query.query(Criteria.where("payload.ticketId").is(ticket.getId())), Document.class, "booking_outbox").block();
    }

    // ---- happy path ----------------------------------------------------------------------------

    @Test
    @DisplayName("offer, accept: the ticket changes holder once, the QR is untouched, the payer is remembered and one event is staged")
    void offerAndAccept() {
        Ticket ticket = ticket();
        TicketTransfer transfer = offer(ticket);

        assertThat(transfer.getStatus()).isEqualTo(TicketTransferStatus.PENDING);
        assertThat(transfer.getToUserId()).isEqualTo(recipient);
        assertThat(reload(ticket).getActiveTransferId()).as("held while the offer is open").isEqualTo(transfer.getId());
        assertThat(reload(ticket).getBuyerId()).isEqualTo(holder);

        Ticket received = asCustomer(recipient, process.accept(transfer.getId(), recipient)).block();

        Ticket after = reload(ticket);
        assertThat(received.getBuyerId()).isEqualTo(recipient);
        assertThat(after.getBuyerId()).isEqualTo(recipient);
        assertThat(after.getQrCode()).as("the code is fixed for the life of the ticket").isEqualTo(ticket.getQrCode());
        assertThat(after.getTicketNumber()).isEqualTo(ticket.getTicketNumber());
        assertThat(after.getTransferCount()).isEqualTo(1);
        assertThat(after.getOriginalBuyerId()).isEqualTo(holder);
        assertThat(after.getActiveTransferId()).isNull();
        assertThat(after.getBuyerEmail()).as("the previous holder's contact is not inherited").isNull();
        assertThat(after.getBuyerPhone()).isNull();
        assertThat(after.getStatus()).isEqualTo(TicketStatus.ISSUED);
        assertThat(reloadTransfer(transfer).getStatus()).isEqualTo(TicketTransferStatus.ACCEPTED);
        assertThat(transferEvents(ticket)).isEqualTo(1);

        // the recipient asking again is told it is theirs, and nothing moves a second time
        asCustomer(recipient, process.accept(transfer.getId(), recipient)).block();
        assertThat(reload(ticket).getTransferCount()).isEqualTo(1);
        assertThat(transferEvents(ticket)).isEqualTo(1);
    }

    @Test
    @DisplayName("the stored transfer carries a masked rendering of the recipient's contact and never the contact")
    void noRawContactIsStored() {
        TicketTransfer transfer = offer(ticket());
        Document raw = template.findById(transfer.getId(), Document.class, "booking_ticket_transfers").block();
        assertThat(raw).isNotNull();
        assertThat(raw.toJson()).doesNotContain(recipientEmail).doesNotContain("holder@example.com").doesNotContain("971234567");
        assertThat(transfer.getRecipientMasked()).isEqualTo("m***@example.com");
        assertThat(transfer.getFromDisplayName()).as("first name and initial only").doesNotContain("Sinkololwe");
    }

    // ---- who may do what -----------------------------------------------------------------------

    @Test
    @DisplayName("only the holder can offer a ticket; anyone else is told the ticket is unknown, exactly as for an invented id")
    void onlyTheHolderOffers() {
        Ticket ticket = ticket();
        var input = new InitiateTicketTransferInput(ticket.getId(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime());
        var thief = refusal(asCustomer("thief", process.initiate(input, "thief")));
        var invented = refusal(asCustomer("thief", process.initiate(
                new InitiateTicketTransferInput(UUID.randomUUID().toString(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime()), "thief")));

        assertThat(thief.errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(invented.errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(thief.details()).isEqualTo(invented.details());
        assertThat(reload(ticket).getActiveTransferId()).isNull();
        assertThat(template.count(Query.query(Criteria.where("ticketId").is(ticket.getId())), TicketTransfer.class).block()).isZero();
    }

    @Test
    @DisplayName("only the recipient accepts or declines; only the sender cancels; every other attempt is 'no such transfer' and changes nothing")
    void rolesAreExclusive() {
        Ticket ticket = ticket();
        TicketTransfer transfer = offer(ticket);
        String id = transfer.getId();

        for (var attempt : List.of(
                process.accept(id, holder), process.accept(id, "third-party"), process.decline(id, holder), process.decline(id, "third-party"),
                process.cancel(id, recipient), process.cancel(id, "third-party"))) {
            assertThat(refusal(attempt).errorCode()).isEqualTo(ErrorCode.TICKET_TRANSFER_UNKNOWN);
        }
        assertThat(refusal(process.accept("no-such-transfer", recipient)).errorCode()).isEqualTo(ErrorCode.TICKET_TRANSFER_UNKNOWN);

        assertThat(reloadTransfer(transfer).getStatus()).isEqualTo(TicketTransferStatus.PENDING);
        assertThat(reload(ticket).getBuyerId()).isEqualTo(holder);
        assertThat(reload(ticket).getActiveTransferId()).isEqualTo(id);
    }

    @Test
    @DisplayName("cancel returns the ticket to the sender; a late accept is refused and the ticket stays with the sender")
    void cancelThenAccept() {
        Ticket ticket = ticket();
        TicketTransfer transfer = offer(ticket);

        TicketTransfer cancelled = asCustomer(holder, process.cancel(transfer.getId(), holder)).block();

        assertThat(cancelled.getStatus()).isEqualTo(TicketTransferStatus.CANCELLED);
        assertThat(reload(ticket).getActiveTransferId()).isNull();
        assertThat(refusal(process.accept(transfer.getId(), recipient)).errorCode()).isEqualTo(ErrorCode.TRANSFER_NOT_PENDING);
        assertThat(reload(ticket).getBuyerId()).isEqualTo(holder);
        assertThat(reload(ticket).getTransferCount()).isZero();
        assertThat(transferEvents(ticket)).isZero();
        assertThat(offer(ticket).getStatus()).as("the ticket can be offered again").isEqualTo(TicketTransferStatus.PENDING);
    }

    @Test
    @DisplayName("decline returns the ticket to the sender, and a second decline is the same answer")
    void decline() {
        Ticket ticket = ticket();
        TicketTransfer transfer = offer(ticket);

        TicketTransfer declined = asCustomer(recipient, process.decline(transfer.getId(), recipient)).block();

        assertThat(declined.getStatus()).isEqualTo(TicketTransferStatus.DECLINED);
        assertThat(reload(ticket).getActiveTransferId()).isNull();
        assertThat(reload(ticket).getBuyerId()).isEqualTo(holder);
        assertThat(refusal(process.accept(transfer.getId(), recipient)).errorCode()).isEqualTo(ErrorCode.TRANSFER_NOT_PENDING);
        assertThat(refusal(process.cancel(transfer.getId(), holder)).errorCode()).isEqualTo(ErrorCode.TRANSFER_NOT_PENDING);
        assertThat(asCustomer(recipient, process.decline(transfer.getId(), recipient)).block().getStatus())
                .as("the same answer, asked twice").isEqualTo(TicketTransferStatus.DECLINED);
    }

    // ---- refusals at offer time ----------------------------------------------------------------

    @Test
    @DisplayName("a ticket already in a transfer cannot be offered again; a non-issued ticket, a stranger contact and oneself are refused")
    void offerRefusals() {
        Ticket ticket = ticket();
        offer(ticket);
        assertThat(refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(ticket.getId(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime()), holder))).errorCode())
                .isEqualTo(ErrorCode.TICKET_STATE_INVALID);

        Ticket refunded = ticket();
        refunded.setStatus(TicketStatus.REFUNDED);
        template.save(refunded).block();
        assertThat(refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(refunded.getId(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime()), holder))).errorCode())
                .isEqualTo(ErrorCode.TICKET_STATE_INVALID);

        Ticket fresh = ticket();
        assertThat(refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(fresh.getId(), TransferChannel.EMAIL, "nobody-" + UUID.randomUUID() + "@example.com", null, "idem-" + System.nanoTime()), holder)))
                .errorCode()).isEqualTo(ErrorCode.TRANSFER_TARGET_INELIGIBLE);

        String selfEmail = holder + "@example.com";
        DIRECTORY.put(selfEmail, new UserLookup(holder, "Me M.", "m***@example.com"));
        assertThat(refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(fresh.getId(), TransferChannel.EMAIL, selfEmail, null, "idem-" + System.nanoTime()), holder))).errorCode())
                .isEqualTo(ErrorCode.TRANSFER_TO_SELF);

        assertThat(refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(fresh.getId(), TransferChannel.EMAIL, "not-an-email", null, "idem-" + System.nanoTime()), holder))).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(fresh.getId(), TransferChannel.EMAIL, recipientEmail, "n".repeat(201), "idem-" + System.nanoTime()), holder))).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(reload(fresh).getActiveTransferId()).isNull();
    }

    @Test
    @DisplayName("the cutoff, a cancelled or finished event and the chain limit each refuse the offer and hold nothing")
    void policyRefusals() {
        Ticket ticket = ticket();
        event.setStartDate(Instant.now().plus(Duration.ofMinutes(90)));
        assertThat(refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(ticket.getId(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime()), holder))).errorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_TRANSFERABLE);

        event.setStartDate(Instant.now().plus(Duration.ofDays(3)));
        event.setStatus(EventStatus.CANCELLED);
        assertThat(refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(ticket.getId(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime()), holder))).errorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_TRANSFERABLE);

        event.setStatus(EventStatus.PUBLISHED);
        ticket.setTransferCount(5);
        template.save(ticket).block();
        assertThat(refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(ticket.getId(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime()), holder))).errorCode())
                .isEqualTo(ErrorCode.TICKET_NOT_TRANSFERABLE);
        assertThat(reload(ticket).getActiveTransferId()).isNull();
        assertThat(template.count(Query.query(Criteria.where("ticketId").is(ticket.getId())), TicketTransfer.class).block()).isZero();
    }

    // ---- expiry --------------------------------------------------------------------------------

    @Test
    @DisplayName("an offer nobody answers lapses on its own timer: the ticket goes back and a late accept is refused")
    void offersExpire() {
        Ticket ticket = ticket();
        TicketTransferProcess brief = newProcess(Duration.ofSeconds(3));
        TicketTransfer transfer = asCustomer(holder, brief.initiate(
                new InitiateTicketTransferInput(ticket.getId(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime()), holder)).block();
        assertThat(transfer.getStatus()).isEqualTo(TicketTransferStatus.PENDING);

        await().atMost(Duration.ofSeconds(40)).untilAsserted(() ->
                assertThat(reloadTransfer(transfer).getStatus()).isEqualTo(TicketTransferStatus.EXPIRED));

        assertThat(reload(ticket).getActiveTransferId()).isNull();
        assertThat(reload(ticket).getBuyerId()).isEqualTo(holder);
        assertThat(reloadTransfer(transfer).getResolvedBy()).isEqualTo("SYSTEM");
        assertThat(refusal(process.accept(transfer.getId(), recipient)).errorCode()).isEqualTo(ErrorCode.TRANSFER_NOT_PENDING);
        assertThat(reload(ticket).getTransferCount()).isZero();
    }

    @Test
    @DisplayName("an offer past its expiry instant cannot be accepted even before the timer has fired")
    void acceptAfterExpiryInstantIsRefused() {
        Ticket ticket = ticket();
        TicketTransfer transfer = offer(ticket);
        template.updateFirst(Query.query(Criteria.where("_id").is(transfer.getId())),
                new org.springframework.data.mongodb.core.query.Update().set("expiresAt", Instant.now().minusSeconds(5)), TicketTransfer.class).block();

        assertThat(refusal(process.accept(transfer.getId(), recipient)).errorCode()).isEqualTo(ErrorCode.TRANSFER_NOT_PENDING);
        assertThat(reload(ticket).getBuyerId()).isEqualTo(holder);
        assertThat(reload(ticket).getTransferCount()).isZero();
    }

    // ---- races ---------------------------------------------------------------------------------

    @Test
    @DisplayName("twelve simultaneous accepts: the ticket changes holder exactly once and one event is staged")
    void concurrentAccepts() {
        Ticket ticket = ticket();
        TicketTransfer transfer = offer(ticket);

        List<Object> outcomes = race(java.util.stream.IntStream.range(0, 12)
                .mapToObj(i -> asCustomer(recipient, process.accept(transfer.getId(), recipient))).toList());

        assertThat(outcomes).as("the same recipient asking again is answered, never an error").noneMatch(o -> o instanceof Throwable);
        Ticket after = reload(ticket);
        assertThat(after.getBuyerId()).isEqualTo(recipient);
        assertThat(after.getTransferCount()).isEqualTo(1);
        assertThat(transferEvents(ticket)).isEqualTo(1);
    }

    @Test
    @DisplayName("accept racing cancel: exactly one wins and the ticket's holder agrees with the transfer's final state")
    void acceptRacesCancel() {
        for (int round = 0; round < 6; round++) {
            Ticket ticket = ticket();
            TicketTransfer transfer = offer(ticket);

            race(List.of(asCustomer(recipient, process.accept(transfer.getId(), recipient)),
                    asCustomer(holder, process.cancel(transfer.getId(), holder))));

            TicketTransfer settled = reloadTransfer(transfer);
            Ticket after = reload(ticket);
            assertThat(settled.getStatus()).isIn(TicketTransferStatus.ACCEPTED, TicketTransferStatus.CANCELLED);
            assertThat(after.getActiveTransferId()).isNull();
            if (settled.getStatus() == TicketTransferStatus.ACCEPTED) {
                assertThat(after.getBuyerId()).isEqualTo(recipient);
                assertThat(after.getTransferCount()).isEqualTo(1);
            } else {
                assertThat(after.getBuyerId()).isEqualTo(holder);
                assertThat(after.getTransferCount()).isZero();
            }
        }
    }

    @Test
    @DisplayName("five simultaneous offers of one ticket: one transfer is created and the others are refused")
    void concurrentOffers() {
        Ticket ticket = ticket();
        List<Mono<TicketTransfer>> offers = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            offers.add(asCustomer(holder, process.initiate(
                    new InitiateTicketTransferInput(ticket.getId(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime()), holder)));
        }

        List<Object> outcomes = race(offers);

        assertThat(outcomes.stream().filter(o -> o instanceof TicketTransfer)).hasSize(1);
        assertThat(template.count(Query.query(Criteria.where("ticketId").is(ticket.getId())), TicketTransfer.class).block()).isEqualTo(1);
        assertThat(outcomes.stream().filter(o -> o instanceof DomainRefusal).map(o -> ((DomainRefusal) o).errorCode()))
                .allMatch(code -> code == ErrorCode.TICKET_STATE_INVALID || code == ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    // ---- limits --------------------------------------------------------------------------------

    @Test
    @DisplayName("a ticket can be offered three times an hour; the fourth is refused with a retry hint")
    void perTicketOfferLimit() {
        Ticket ticket = ticket();
        for (int i = 0; i < 3; i++) {
            TicketTransfer transfer = offer(ticket);
            asCustomer(holder, process.cancel(transfer.getId(), holder)).block();
        }
        var refused = refusal(asCustomer(holder, process.initiate(
                new InitiateTicketTransferInput(ticket.getId(), TransferChannel.EMAIL, recipientEmail, null, "idem-" + System.nanoTime()), holder)));
        assertThat(refused.errorCode()).isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
        assertThat(refused.details()).containsKey("retryAfterSeconds");
    }

    @Test
    @DisplayName("looking up who a contact belongs to is capped at twenty an hour and returns a first name and a masked contact only")
    void lookupIsCappedAndMinimal() {
        for (int i = 0; i < 20; i++) {
            var found = asCustomer(holder, process.lookup(holder, "EMAIL", recipientEmail)).block();
            assertThat(found.displayName()).isEqualTo("Mary K.");
            assertThat(found.maskedContact()).doesNotContain(recipient);
        }
        assertThat(refusal(asCustomer(holder, process.lookup(holder, "EMAIL", recipientEmail))).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
        // enumeration: an unregistered contact answers empty without a distinguishing error, still counted
        assertThat(refusal(asCustomer(holder, process.lookup(holder, "EMAIL", "ghost@example.com"))).errorCode())
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);
    }

    // ---- reads ---------------------------------------------------------------------------------

    @Test
    @DisplayName("myTicketTransfers shows each party only their own side; the chain is for the holder and the event's attendee readers")
    void readsAreScoped() {
        Ticket ticket = ticket();
        TicketTransfer transfer = offer(ticket);
        OffsetPaginationInput page = null;

        Pages.Slice<TicketTransfer> sent = asCustomer(holder, reads.mine(TicketTransferReads.Direction.OUTGOING, null, page)).block();
        Pages.Slice<TicketTransfer> received = asCustomer(recipient, reads.mine(TicketTransferReads.Direction.INCOMING, null, page)).block();
        Pages.Slice<TicketTransfer> bystander = asCustomer("bystander", reads.mine(null, null, page)).block();
        Pages.Slice<TicketTransfer> wrongSide = asCustomer(recipient, reads.mine(TicketTransferReads.Direction.OUTGOING, null, page)).block();

        assertThat(sent.data()).extracting(TicketTransfer::getId).contains(transfer.getId());
        assertThat(received.data()).extracting(TicketTransfer::getId).contains(transfer.getId());
        assertThat(bystander.data()).isEmpty();
        assertThat(wrongSide.data()).extracting(TicketTransfer::getId).doesNotContain(transfer.getId());

        assertThat(asCustomer(holder, reads.chain(ticket.getId()).collectList()).block()).hasSize(1);
        assertThat(refusal(asCustomer("bystander", reads.chain(ticket.getId()).collectList())).errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        assertThat(refusal(asCustomer("bystander", reads.chain("no-such-ticket").collectList())).errorCode()).isEqualTo(ErrorCode.TICKET_UNKNOWN);
        world.grant("event-organizer", eventId);
        assertThat(asOrganizer("event-organizer", "org-1", reads.chain(ticket.getId()).collectList()).block()).hasSize(1);
        assertThat(asAdmin("admin-1", reads.chain(ticket.getId()).collectList(), "ROLE_ADMIN").block()).hasSize(1);
    }

    // ---- workflow durability -------------------------------------------------------------------

    @Test
    @DisplayName("a worker restart mid-offer loses nothing: the new worker replays the history and the accept still works")
    void survivesAWorkerRestart() {
        Ticket ticket = ticket();
        TicketTransfer transfer = offer(ticket);

        workers.shutdownNow();
        service.shutdownNow();
        startWorker();
        process = newProcess(Duration.ofHours(48));

        asCustomer(recipient, process.accept(transfer.getId(), recipient)).block();
        assertThat(reload(ticket).getBuyerId()).isEqualTo(recipient);
        assertThat(reload(ticket).getTransferCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the recorded history of a settled transfer replays against the current workflow code")
    void historyReplays() throws Exception {
        Ticket ticket = ticket();
        TicketTransfer transfer = offer(ticket);
        asCustomer(recipient, process.accept(transfer.getId(), recipient)).block();

        var history = workflowClient.fetchHistory(WorkflowIds.ticketTransfer(transfer.getId()));
        WorkflowReplayer.replayWorkflowExecution(history, TicketTransferWorkflowImpl.class);
        assertThat(TicketTransferWorkflow.class).isNotNull();
    }

    // ---- helpers -------------------------------------------------------------------------------

    /** Runs every call at once; each result is its value or the error it ended in. */
    private static List<Object> race(List<? extends Mono<?>> calls) {
        return Flux.fromIterable(calls)
                .flatMap(call -> call.<Object>map(v -> v).onErrorResume(e -> Mono.just(e)).defaultIfEmpty("empty"), calls.size())
                .collectList().block(Duration.ofSeconds(90));
    }

    /** Catalog knows no event, so the ticket keeps what it carries. */
    private static com.pml.booking.service.CurrentEventDetails noCurrentEvent() {
        com.pml.booking.infrastructure.client.CatalogServiceClient catalog =
                org.mockito.Mockito.mock(com.pml.booking.infrastructure.client.CatalogServiceClient.class);
        org.mockito.Mockito.when(catalog.getEventById(org.mockito.ArgumentMatchers.any())).thenReturn(reactor.core.publisher.Mono.empty());
        return new com.pml.booking.service.CurrentEventDetails(catalog, java.time.Clock.systemUTC());
    }
}
