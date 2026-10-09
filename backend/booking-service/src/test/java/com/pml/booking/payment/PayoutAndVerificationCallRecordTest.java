package com.pml.booking.payment;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.enums.PaymentAttemptStatus;
import com.pml.booking.domain.enums.PaymentAttemptType;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.PaymentAttempt;
import com.pml.booking.domain.model.PayoutRequest;
import com.pml.booking.infrastructure.gateway.MobileMoneyGateway;
import com.pml.booking.infrastructure.gateway.MobileMoneyGatewayFactory;
import com.pml.booking.infrastructure.gateway.model.PayoutResult;
import com.pml.booking.repository.PaymentAttemptRepository;
import com.pml.booking.service.AccountingService;
import com.pml.booking.service.BankVerificationService;
import com.pml.booking.service.impl.PaymentAttemptRecorderImpl;
import com.pml.booking.workflow.bank.BankVerificationActivitiesImpl;
import com.pml.booking.workflow.payout.PayoutProviderActivitiesImpl;
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
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * The payout and verification-deposit activities write their payment-attempt row before calling the
 * provider and settle it with the answer, against a MongoDB replica set with only the provider
 * gateway replaced.
 */
@Tag("L2")
@Tag("ET-FIN-003")
@DisplayName("Payouts and verification deposits leave one attempt row per provider call")
class PayoutAndVerificationCallRecordTest {

    private static final Instant NOW = Instant.parse("2026-09-18T10:00:00Z");
    private static final String PAYOUT = "payout-call-1";
    private static final String PAYOUT_REF = "5a4d2f5e-7d61-4a6e-9f0d-0c1f5b8a1e01";
    private static final String BANK = "bank-call-1";
    private static final String DEPOSIT_REF = "9b0c6f1e-2c35-4d7a-8a5b-3e4f6a7b8c02";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static PaymentAttemptRepository attempts;
    private static Clock clock;

    private MobileMoneyGateway gateway;
    private PayoutProviderActivitiesImpl payouts;
    private BankVerificationActivitiesImpl verification;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "booking_payout_calls"));
        attempts = new ReactiveMongoRepositoryFactory(template).getRepository(PaymentAttemptRepository.class);
        clock = TestClock.frozenAt(NOW);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), PaymentAttempt.class).block();
        template.remove(new Query(), PayoutRequest.class).block();
        template.remove(new Query(), BankAccount.class).block();

        template.save(BankAccount.builder()
                .id(BANK)
                .organizerId("organizer-1")
                .organizationId("org-1")
                .accountHolderName("Organizer One")
                .accountNumber("+260970000002")
                .currency("ZMW")
                .microDepositAmount(new BigDecimal("0.53"))
                .build()).block();
        template.save(PayoutRequest.builder()
                .id(PAYOUT)
                .requestId("PAY-CALL1")
                .organizerId("organizer-1")
                .organizationId("org-1")
                .eventId("event-1")
                .escrowAccountId("escrow-1")
                .bankAccountId(BANK)
                .requestedAmount(new BigDecimal("900.00"))
                .settledAmount(new BigDecimal("900.00"))
                .currency("ZMW")
                .pawaPayPayoutId(PAYOUT_REF)
                .build()).block();

        gateway = Mockito.mock(MobileMoneyGateway.class);
        MobileMoneyGatewayFactory gateways = Mockito.mock(MobileMoneyGatewayFactory.class);
        when(gateways.getGatewayForPhone(anyString())).thenReturn(Mono.just(gateway));
        PaymentAttemptRecorderImpl recorder = new PaymentAttemptRecorderImpl(attempts, clock);
        payouts = new PayoutProviderActivitiesImpl(template, gateways, recorder);
        verification = new BankVerificationActivitiesImpl(new BankVerificationService(template, clock), gateways,
                Mockito.mock(AccountingService.class), recorder);
    }

    @Test
    @DisplayName("An accepted payout is one PAYOUT row in PROCESSING, even when the activity runs twice")
    void anAcceptedPayoutIsOneRow() {
        when(gateway.initiatePayout(any())).thenReturn(Mono.just(PayoutResult.pending(PAYOUT_REF, "prov-1", "PAWAPAY", NOW)));

        payouts.initiate(PAYOUT);
        payouts.initiate(PAYOUT);

        assertThat(attempts.count().block()).isEqualTo(1);
        PaymentAttempt row = attempts.findByProviderReference(PAYOUT_REF).block();
        assertThat(row.getAttemptType()).isEqualTo(PaymentAttemptType.PAYOUT);
        assertThat(row.getPayoutRequestId()).isEqualTo(PAYOUT);
        assertThat(row.getBankAccountId()).isEqualTo(BANK);
        assertThat(row.getAmount()).isEqualByComparingTo("900.00");
        assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
    }

    @Test
    @DisplayName("A payout the provider cannot take right now leaves its row CREATED and fails the activity for a retry")
    void anUnavailableProviderLeavesTheRowCreated() {
        when(gateway.initiatePayout(any())).thenReturn(Mono.just(
                PayoutResult.failed(PAYOUT_REF, "PROVIDER_TEMPORARILY_UNAVAILABLE", "try later", true, "PAWAPAY", NOW)));

        assertThatThrownBy(() -> payouts.initiate(PAYOUT)).isInstanceOf(ApplicationFailure.class);

        assertThat(attempts.findByProviderReference(PAYOUT_REF).block().getStatus()).isEqualTo(PaymentAttemptStatus.CREATED);
    }

    @Test
    @DisplayName("A payout refused for bad account details is recorded as REJECTED with the provider's code")
    void aRefusedPayoutIsRejected() {
        when(gateway.initiatePayout(any())).thenReturn(Mono.just(
                PayoutResult.failed(PAYOUT_REF, "INVALID_RECIPIENT", "recipient unknown", false, "PAWAPAY", NOW)));

        assertThatThrownBy(() -> payouts.initiate(PAYOUT)).isInstanceOf(ApplicationFailure.class);

        PaymentAttempt row = attempts.findByProviderReference(PAYOUT_REF).block();
        assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.REJECTED);
        assertThat(row.getFailureCode()).isEqualTo("INVALID_RECIPIENT");
    }

    @Test
    @DisplayName("A verification deposit is one VERIFICATION row linked to the bank account")
    void aVerificationDepositIsOneRow() {
        when(gateway.initiatePayout(any())).thenReturn(Mono.just(PayoutResult.pending(DEPOSIT_REF, "prov-2", "PAWAPAY", NOW)));

        verification.sendDeposit(BANK, DEPOSIT_REF);
        verification.sendDeposit(BANK, DEPOSIT_REF);

        assertThat(attempts.count().block()).isEqualTo(1);
        PaymentAttempt row = attempts.findByProviderReference(DEPOSIT_REF).block();
        assertThat(row.getAttemptType()).isEqualTo(PaymentAttemptType.VERIFICATION);
        assertThat(row.getBankAccountId()).isEqualTo(BANK);
        assertThat(row.getAmount()).isEqualByComparingTo("0.53");
        assertThat(row.getStatus()).isEqualTo(PaymentAttemptStatus.PROCESSING);
    }
}
