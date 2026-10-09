package com.pml.booking.it;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.config.PaymentProperties;
import com.pml.booking.domain.model.CommissionRecord;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.Ticket;
import com.pml.booking.infrastructure.client.PawaPayClient;
import com.pml.booking.infrastructure.gateway.MobileMoneyGateway;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.gateway.model.PaymentResult;
import com.pml.booking.infrastructure.gateway.model.PaymentResultStatus;
import com.pml.booking.infrastructure.temporal.TaskQueues;
import com.pml.booking.repository.ChartOfAccountsRepository;
import com.pml.booking.repository.CommissionRecordRepository;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.JournalEntryRepository;
import com.pml.booking.repository.RefundRequestRepository;
import com.pml.booking.repository.TicketRepository;
import com.pml.booking.security.TenantAccessGuard;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.ChartOfAccountsService;
import com.pml.booking.service.CommissionService;
import com.pml.booking.service.EscrowService;
import com.pml.booking.service.FinanceEscalations;
import com.pml.booking.service.JournalService;
import com.pml.booking.service.RefundService;
import com.pml.booking.service.PaymentAttemptRecorder;
import com.pml.booking.service.impl.AccountingServiceImpl;
import com.pml.booking.service.impl.ChartOfAccountsServiceImpl;
import com.pml.booking.service.impl.CommissionServiceImpl;
import com.pml.booking.service.impl.EscrowServiceImpl;
import com.pml.booking.service.impl.JournalServiceImpl;
import com.pml.booking.service.impl.RefundServiceImpl;
import com.pml.booking.workflow.refund.RefundActivitiesImpl;
import com.pml.booking.workflow.refund.RefundProcess;
import com.pml.booking.workflow.refund.RefundWorkflowImpl;
import com.pml.shared.constants.TicketStatus;
import com.pml.shared.infrastructure.temporal.TemporalGateway;
import com.pml.shared.testing.TemporalDevServer;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.WorkerFactory;
import org.mockito.Mockito;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The refund pipeline with everything booking owns real: the workflow on a Temporal server, the
 * activities, refund / commission / escrow / ledger services and repositories over a MongoDB replica
 * set. Only the provider (PawaPay) and the finance alerting are stood in for.
 */
public final class RefundHarness implements AutoCloseable {

    public final MongoClient mongo;
    public final ReactiveMongoTemplate template;
    public final Clock clock = Clock.systemUTC();
    public final RefundRequestRepository refundRepo;
    public final TicketRepository ticketRepo;
    public final CommissionRecordRepository commissionRepo;
    public final EventEscrowAccountRepository escrowRepo;
    public final JournalEntryRepository journalRepo;
    public final ChartOfAccountsRepository chartRepo;
    public final PawaPayClient pawaPay = Mockito.mock(PawaPayClient.class);
    public final MobileMoneyGateway gateway = Mockito.mock(MobileMoneyGateway.class);
    public final AtomicInteger providerRefunds = new AtomicInteger();
    public final ChartOfAccountsService chart;
    public final AccountingService accounting;
    public final CommissionService commissions;
    public final EscrowService escrow;
    public final RefundService refunds;
    public final RefundProcess process;
    private final WorkflowServiceStubs service;
    private final WorkerFactory workers;

    public RefundHarness(String mongoConnectionString) {
        mongo = BookingFixture.newClient(mongoConnectionString);
        template = BookingFixture.template(mongo, "booking_refund_money");
        var factory = new ReactiveMongoRepositoryFactory(template);
        refundRepo = factory.getRepository(RefundRequestRepository.class);
        ticketRepo = factory.getRepository(TicketRepository.class);
        commissionRepo = factory.getRepository(CommissionRecordRepository.class);
        escrowRepo = factory.getRepository(EventEscrowAccountRepository.class);
        journalRepo = factory.getRepository(JournalEntryRepository.class);
        chartRepo = factory.getRepository(ChartOfAccountsRepository.class);
        var tx = TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory()));

        BookingFixture.productionIndexes(template);
        // every service behind the transaction advice the running application puts around @Transactional methods
        chart = BookingFixture.transactional(ChartOfAccountsService.class, new ChartOfAccountsServiceImpl(chartRepo), template);
        chart.seedStandardAccounts().block();
        var journal = BookingFixture.transactional(JournalService.class, new JournalServiceImpl(journalRepo, clock, chart), template);
        accounting = BookingFixture.transactional(AccountingService.class, new AccountingServiceImpl(journal, clock, chartRepo, journalRepo), template);
        var commissionImpl = new CommissionServiceImpl(commissionRepo, accounting, clock);
        ReflectionTestUtils.setField(commissionImpl, "commissionRate", new BigDecimal("0.05"));
        commissions = BookingFixture.transactional(CommissionService.class, commissionImpl, template);
        escrow = BookingFixture.transactional(EscrowService.class,
                new EscrowServiceImpl(escrowRepo, clock, Mockito.mock(TenantAccessGuard.class)), template);

        // the provider accepts every refund it is sent and reports what it was last told to report
        when(pawaPay.initiateRefund(anyString(), anyString(), any(), anyString(), any())).thenAnswer(call -> {
            providerRefunds.incrementAndGet();
            return Mono.just(new PawaPayClient.RefundResponse(call.getArgument(0), "ACCEPTED", Instant.now(), null));
        });
        PaymentAttemptRecorder recorder = Mockito.mock(PaymentAttemptRecorder.class);
        when(recorder.beforeCall(any())).thenReturn(Mono.empty());
        when(recorder.afterCall(any(), any(), any(), any())).thenReturn(Mono.empty());
        refunds = BookingFixture.transactional(RefundService.class, new RefundServiceImpl(refundRepo, clock, ticketRepo, pawaPay,
                commissions, escrow, accounting, new PaymentProperties(), tx, template, recorder), template);

        MobileMoneyGatewayFactory gateways = Mockito.mock(MobileMoneyGatewayFactory.class);
        when(gateways.getGatewayByProvider(anyString())).thenReturn(Mono.just(gateway));
        when(gateway.checkRefundStatus(anyString())).thenAnswer(call -> Mono.just(completed(call.getArgument(0))));
        FinanceEscalations escalations = Mockito.mock(FinanceEscalations.class);
        when(escalations.escalate(any())).thenReturn(Mono.empty());
        var activities = new RefundActivitiesImpl(refunds, refundRepo, commissions, escrow, gateways, template, escalations, clock);

        service = WorkflowServiceStubs.newServiceStubs(WorkflowServiceStubsOptions.newBuilder().setTarget(TemporalDevServer.target()).build());
        var client = WorkflowClient.newInstance(service, WorkflowClientOptions.newBuilder().setNamespace(TemporalDevServer.NAMESPACE).build());
        workers = WorkerFactory.newInstance(client);
        for (String queue : new String[]{TaskQueues.FINANCE, TaskQueues.PROVIDER}) {
            var worker = workers.newWorker(queue);
            if (queue.equals(TaskQueues.FINANCE)) {
                worker.registerWorkflowImplementationTypes(RefundWorkflowImpl.class);
            }
            worker.registerActivitiesImplementations(activities);
        }
        workers.start();
        process = new RefundProcess(new TemporalGateway(client), refunds, refundRepo);
    }

    public static PaymentResult completed(String refundId) {
        return new PaymentResult(refundId, "prov-" + refundId, PaymentResultStatus.SUCCESS, "completed", false, null, null, null,
                false, Instant.now(), "pawapay");
    }

    /** An organization's event with funds in escrow for one ticket of {@code price}, 5 percent commission pending. */
    public record Sale(String eventId, String organizationId, Ticket ticket, String buyerId) { }

    public Sale sale(BigDecimal price) {
        String eventId = "ev-" + UUID.randomUUID();
        String organizationId = "org-" + UUID.randomUUID();
        String buyer = "buyer-" + UUID.randomUUID();
        Instant now = clock.instant();
        EventEscrowAccount account = EventEscrowAccount.create(eventId, "organizer-1", now.plusSeconds(86400 * 30), now);
        account.setOrganizationId(organizationId);
        account.setStatus(com.pml.shared.constants.EscrowStatus.ACTIVE);
        escrowRepo.save(account).block();
        Ticket ticket = ticketRepo.save(Ticket.builder().id(UUID.randomUUID().toString()).ticketNumber("TKT-" + UUID.randomUUID().toString().substring(0, 8))
                .eventId(eventId).eventTitle("Event").organizationId(organizationId).organizerId("organizer-1").buyerId(buyer)
                .price(price).status(TicketStatus.ISSUED).reservationId("res-" + UUID.randomUUID()).paymentReference("pi-" + UUID.randomUUID())
                .paymentInfo(Ticket.PaymentInfo.builder().transactionId("dep-" + UUID.randomUUID()).build()).build()).block();
        CommissionRecord commission = commissions.createPendingCommission(ticket.getId(), eventId, "organizer-1", organizationId, price).block();
        escrow.creditEscrow(eventId, price.subtract(commission.getAmount()), ticket.getId(), ticket.getPaymentReference(), "Sale").block();
        return new Sale(eventId, organizationId, ticket, buyer);
    }

    @Override
    public void close() {
        workers.shutdownNow();
        service.shutdownNow();
        mongo.close();
    }
}
