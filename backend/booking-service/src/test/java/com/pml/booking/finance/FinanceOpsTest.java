package com.pml.booking.finance;

import com.mongodb.reactivestreams.client.MongoClient;
import com.pml.booking.domain.enums.PlatformAccountType;
import com.pml.booking.domain.enums.RecoveryAction;
import com.pml.booking.domain.enums.RecoveryProposalStatus;
import com.pml.booking.domain.model.JournalEntry;
import com.pml.booking.domain.model.PlatformTransfer;
import com.pml.booking.domain.model.RecoveryProposal;
import com.pml.booking.it.BookingFixture;
import com.pml.booking.repository.ChartOfAccountsRepository;
import com.pml.booking.repository.JournalEntryRepository;
import com.pml.booking.repository.PlatformAccountRepository;
import com.pml.booking.service.ChargebackService;
import com.pml.booking.service.DualControlService;
import com.pml.booking.service.PaymentOutcomeService;
import com.pml.booking.service.PlatformTransferService;
import com.pml.booking.service.impl.ChartOfAccountsServiceImpl;
import com.pml.booking.service.impl.JournalServiceImpl;
import com.pml.booking.service.impl.PlatformAccountServiceImpl;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.TestClock;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.mongodb.ReactiveMongoTransactionManager;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.transaction.reactive.TransactionalOperator;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static com.pml.booking.it.BookingFixture.as;
import static com.pml.booking.it.BookingFixture.refusal;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Moving the platform's own money, and the two-person rule in front of the big moves: balanced double
 * entry, an idempotency key that makes a repeat the same move, no loss under simultaneous transfers,
 * and a proposal nobody can confirm for themselves, after it expired, or without the role.
 */
@Tag("L2")
@Tag("ET-ADM-003")
@DisplayName("ET-ADM-003 · platform transfers are balanced and idempotent, and dual control is a second person, in time, in role")
class FinanceOpsTest {

    private static final BigDecimal LIMIT = new BigDecimal("1000.00");
    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static PlatformAccountServiceImpl accounts;
    private static PlatformTransferService transfers;
    private static JournalEntryRepository journalRepo;
    private static DualControlService dual;
    private static ChargebackService chargebacks;
    private static Clock clock;
    private static PaymentOutcomeService outcomes;
    private static com.pml.booking.web.graphql.query.AdminOpsResolver resolver;

    @BeforeAll
    static void start() {
        client = BookingFixture.newClient(MongoReplicaSet.connectionString());
        template = BookingFixture.template(client, "booking_finance_ops");
        BookingFixture.productionIndexes(template);
        var factory = new ReactiveMongoRepositoryFactory(template);
        journalRepo = factory.getRepository(JournalEntryRepository.class);
        ChartOfAccountsRepository chartRepo = factory.getRepository(ChartOfAccountsRepository.class);
        clock = Clock.systemUTC();
        var chart = new ChartOfAccountsServiceImpl(chartRepo);
        chart.seedStandardAccounts().block();
        var journal = new JournalServiceImpl(journalRepo, clock, chart);
        accounts = new PlatformAccountServiceImpl(factory.getRepository(PlatformAccountRepository.class));
        var tx = TransactionalOperator.create(new ReactiveMongoTransactionManager(template.getMongoDatabaseFactory()));
        transfers = new PlatformTransferService(accounts, journal, template, tx, clock, LIMIT);
        chargebacks = Mockito.mock(ChargebackService.class);
        outcomes = Mockito.mock(PaymentOutcomeService.class);
        when(outcomes.resume(anyString())).thenReturn(Mono.empty());
        dual = new DualControlService(template, clock, outcomes, chargebacks, transfers);
        var risk = new com.pml.booking.service.PaymentRiskService(template, clock);
        resolver = new com.pml.booking.web.graphql.query.AdminOpsResolver(new com.pml.booking.service.AdminFinanceReads(template),
                new com.pml.booking.service.PaymentOperations(template, outcomes, risk, clock), risk, dual, transfers,
                Mockito.mock(com.pml.booking.service.ChargebackRecoveryOps.class), clock);
    }

    @AfterAll
    static void stop() {
        client.close();
    }

    @BeforeEach
    void reset() {
        template.remove(new Query(), PlatformTransfer.class).block();
        template.remove(new Query(), RecoveryProposal.class).block();
        template.remove(new Query(), JournalEntry.class).block();
        template.remove(new Query(), com.pml.booking.domain.model.PlatformAccount.class).block();
        accounts.credit(PlatformAccountType.OPERATING, new BigDecimal("5000.00"), "seed", "seed").block();
        accounts.credit(PlatformAccountType.RESERVE, new BigDecimal("200.00"), "seed", "seed").block();
    }

    private static BigDecimal balance(PlatformAccountType type) {
        return accounts.getBalance(type).block();
    }

    private static <T> Mono<T> asFinance(String user, Mono<T> call) {
        return as(user, com.pml.shared.security.tenancy.TenantScope.platformAdministrator(user, java.util.Set.of()), call, "ROLE_FINANCE");
    }

    private static <T> Mono<T> asRole(String user, String role, Mono<T> call) {
        return as(user, com.pml.shared.security.tenancy.TenantScope.platformAdministrator(user, java.util.Set.of()), call, role);
    }

    private PlatformTransfer move(String key, String amount) {
        return transfers.execute(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, new BigDecimal(amount),
                "month end reserve top up", key, "fin-1", null).block();
    }

    // ---- single transfers ----------------------------------------------------------------------

    @Test
    @DisplayName("a transfer moves exactly the amount, records one balanced entry, and total platform money is unchanged")
    void aTransferBalances() {
        BigDecimal before = balance(PlatformAccountType.OPERATING).add(balance(PlatformAccountType.RESERVE));

        PlatformTransfer transfer = move("k-" + UUID.randomUUID(), "300.00");

        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("4700.00");
        assertThat(balance(PlatformAccountType.RESERVE)).isEqualByComparingTo("500.00");
        assertThat(balance(PlatformAccountType.OPERATING).add(balance(PlatformAccountType.RESERVE))).isEqualByComparingTo(before);
        JournalEntry entry = journalRepo.findById(transfer.getJournalEntryId()).block();
        assertThat(entry.getTotalDebits()).isEqualByComparingTo("300.00");
        assertThat(entry.isBalanced()).isTrue();
        assertThat(entry.getLines()).hasSize(2);
    }

    @Test
    @DisplayName("the same idempotency key is the same transfer: no second movement, no second entry")
    void repeatedKeyIsOneMove() {
        String key = "k-" + UUID.randomUUID();
        PlatformTransfer first = move(key, "100.00");
        PlatformTransfer again = move(key, "100.00");

        assertThat(again.getId()).isEqualTo(first.getId());
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("4900.00");
        assertThat(journalRepo.count().block()).isEqualTo(1L);
    }

    @Test
    @DisplayName("eight simultaneous requests under one key move the money once")
    void simultaneousRepeatsMoveOnce() {
        String key = "k-" + UUID.randomUUID();
        List<Object> outcomes = Flux.range(0, 8)
                .flatMap(i -> transfers.execute(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, new BigDecimal("100.00"),
                        "month end reserve top up", key, "fin-1", null).<Object>map(t -> t).onErrorResume(e -> Mono.just(e)), 8)
                .collectList().block();

        assertThat(template.count(new Query(), PlatformTransfer.class).block()).as("outcomes %s", outcomes).isEqualTo(1);
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("4900.00");
        assertThat(balance(PlatformAccountType.RESERVE)).isEqualByComparingTo("300.00");
        assertThat(journalRepo.count().block()).isEqualTo(1L);
    }

    @Test
    @DisplayName("more than the account holds is refused and leaves nothing: no record, no entry, balances untouched")
    void insufficientFundsLeavesNothing() {
        assertThatThrownBy(() -> transfers.execute(PlatformAccountType.RESERVE, PlatformAccountType.OPERATING,
                new BigDecimal("250.00"), "emergency draw down", "k-" + UUID.randomUUID(), "fin-1", null).block())
                .hasMessageContaining("Insufficient");
        assertThat(balance(PlatformAccountType.RESERVE)).isEqualByComparingTo("200.00");
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("5000.00");
        assertThat(template.count(new Query(), PlatformTransfer.class).block()).isZero();
        assertThat(journalRepo.count().block()).isZero();
    }

    @Test
    @DisplayName("simultaneous transfers with different keys never create or destroy money")
    void simultaneousTransfersConserveMoney() {
        List<Object> outcomes = Flux.range(0, 12)
                .flatMap(i -> transfers.execute(PlatformAccountType.RESERVE, PlatformAccountType.OPERATING, new BigDecimal("100.00"),
                        "draw down number " + i, "k-" + UUID.randomUUID(), "fin-1", null).<Object>map(t -> t).onErrorResume(e -> Mono.just(e)), 12)
                .collectList().block();

        long moved = template.count(new Query(), PlatformTransfer.class).block();
        BigDecimal total = balance(PlatformAccountType.OPERATING).add(balance(PlatformAccountType.RESERVE));
        assertThat(total).as("outcomes %s", outcomes).isEqualByComparingTo("5200.00");
        assertThat(balance(PlatformAccountType.RESERVE)).isEqualByComparingTo(new BigDecimal("200.00").subtract(new BigDecimal("100.00").multiply(BigDecimal.valueOf(moved))));
        assertThat(moved).as("only what the reserve could cover").isLessThanOrEqualTo(2);
        assertThat(journalRepo.count().block()).isEqualTo(moved);
    }

    @Test
    @DisplayName("malformed moves are refused before anything is written")
    void malformed() {
        assertThat(PlatformTransferService.check(PlatformAccountType.OPERATING, PlatformAccountType.OPERATING, BigDecimal.TEN, "a good reason here")).isNotNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.TAX_HOLDING, PlatformAccountType.OPERATING, BigDecimal.TEN, "a good reason here")).isNotNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, new BigDecimal("10.005"), "a good reason here")).isNotNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, BigDecimal.ZERO, "a good reason here")).isNotNull();
        assertThat(PlatformTransferService.check(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, BigDecimal.TEN, "short")).isNotNull();
        assertThat(refusal(transfers.execute(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, BigDecimal.TEN, "a good reason here", " ", "fin-1", null))
                .errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(transfers.needsSecondPerson(new BigDecimal("1000.00"))).isFalse();
        assertThat(transfers.needsSecondPerson(new BigDecimal("1000.01"))).isTrue();
        assertThat(template.count(new Query(), PlatformTransfer.class).block()).isZero();
    }

    // ---- dual control --------------------------------------------------------------------------

    private RecoveryProposal proposeTransfer(String maker, String amount) {
        return asFinance(maker, dual.proposeTransfer(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE, new BigDecimal(amount),
                "quarterly reserve funding approved by the board")).block();
    }

    @Test
    @DisplayName("a proposal moves nothing; the maker cannot confirm it; a different FINANCE user does, and the money moves once")
    void makerCannotCheck() {
        RecoveryProposal proposal = proposeTransfer("maker-1", "2500.00");
        assertThat(proposal.getStatus()).isEqualTo(RecoveryProposalStatus.PENDING);
        assertThat(balance(PlatformAccountType.OPERATING)).as("proposing moves nothing").isEqualByComparingTo("5000.00");

        assertThat(refusal(asFinance("maker-1", dual.confirm(proposal.getId(), "I confirm my own proposal, honest"))).errorCode())
                .isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("5000.00");

        RecoveryProposal confirmed = asFinance("checker-1", dual.confirm(proposal.getId(), "Checked the board minutes, approved")).block();
        assertThat(confirmed.getStatus()).isEqualTo(RecoveryProposalStatus.CONFIRMED);
        assertThat(confirmed.getConfirmedById()).isEqualTo("checker-1");
        assertThat(confirmed.getOutcome()).contains("Moved 2500.00");
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("2500.00");
        assertThat(balance(PlatformAccountType.RESERVE)).isEqualByComparingTo("2700.00");

        assertThat(refusal(asFinance("checker-2", dual.confirm(proposal.getId(), "Confirming it a second time as well"))).errorCode())
                .isEqualTo(ErrorCode.TRANSACTION_NOT_RECOVERABLE);
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("2500.00");
    }

    @Test
    @DisplayName("six checkers confirming at once: one wins, the transfer runs once")
    void concurrentConfirmations() {
        RecoveryProposal proposal = proposeTransfer("maker-1", "1500.00");
        List<Object> outcomes = Flux.range(0, 6)
                .flatMap(i -> asFinance("checker-" + i, dual.confirm(proposal.getId(), "Confirming number " + i + " of the board decision"))
                        .<Object>map(p -> p).onErrorResume(e -> Mono.just(e)), 6)
                .collectList().block();

        assertThat(outcomes.stream().filter(o -> o instanceof RecoveryProposal)).as("%s", outcomes).hasSize(1);
        assertThat(template.count(new Query(), PlatformTransfer.class).block()).isEqualTo(1);
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("3500.00");
    }

    @Test
    @DisplayName("a proposal confirmed after its two hours applies nothing and no longer shows in anyone's queue")
    void expiry() {
        RecoveryProposal proposal = proposeTransfer("maker-1", "1500.00");
        template.updateFirst(Query.query(Criteria.where("_id").is(proposal.getId())),
                new Update().set("expiresAt", Instant.now().minus(Duration.ofMinutes(1))), RecoveryProposal.class).block();

        DomainRefusal refused = refusal(asFinance("checker-1", dual.confirm(proposal.getId(), "Confirming but far too late now")));
        assertThat(refused.errorCode()).isEqualTo(ErrorCode.TRANSACTION_NOT_RECOVERABLE);
        assertThat(refused.details()).containsEntry("currentStatus", "EXPIRED");
        assertThat(asFinance("checker-1", dual.queue().collectList()).block()).extracting(RecoveryProposal::getId).doesNotContain(proposal.getId());
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("5000.00");
    }

    @Test
    @DisplayName("roles: a customer or organizer cannot propose; force-complete needs SUPER_ADMIN to propose and to confirm; ADMIN covers FINANCE")
    void roles() {
        assertThat(refusal(asRole("org-1", "ROLE_ORGANIZER", dual.proposeTransfer(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE,
                new BigDecimal("2000.00"), "organizers must never be able to do this"))).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(refusal(asRole("admin-1", "ROLE_ADMIN", dual.propose(RecoveryAction.FORCE_COMPLETE_PAYMENT_ATTEMPTS, List.of("dep-1"), null,
                Map.of(), "provider confirmed payment but it is stuck"))).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        RecoveryProposal forced = asRole("super-1", "ROLE_SUPER_ADMIN", dual.propose(RecoveryAction.FORCE_COMPLETE_PAYMENT_ATTEMPTS,
                List.of("dep-1"), null, Map.of(), "provider confirmed payment but it is stuck")).block();
        assertThat(refusal(asRole("admin-1", "ROLE_ADMIN", dual.confirm(forced.getId(), "An admin may not confirm a force complete"))).errorCode())
                .isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(refusal(asRole("super-1", "ROLE_SUPER_ADMIN", dual.confirm(forced.getId(), "Confirming my own force complete here"))).errorCode())
                .isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);

        RecoveryProposal bigTransfer = proposeTransfer("maker-1", "2000.00");
        asRole("admin-9", "ROLE_ADMIN", dual.confirm(bigTransfer.getId(), "Admin standing in for finance, approved")).block();
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("3000.00");
    }

    @Test
    @DisplayName("the queue lists others' proposals the caller could confirm, never their own, and hides what their role cannot confirm")
    void queue() {
        RecoveryProposal mine = proposeTransfer("maker-1", "1200.00");
        RecoveryProposal theirs = proposeTransfer("maker-2", "1300.00");
        RecoveryProposal superOnly = asRole("super-1", "ROLE_SUPER_ADMIN", dual.propose(RecoveryAction.FORCE_COMPLETE_PAYMENT_ATTEMPTS,
                List.of("dep-9"), null, Map.of(), "provider confirmed payment but it is stuck")).block();

        List<String> makerSees = asFinance("maker-1", dual.queue().collectList()).block().stream().map(RecoveryProposal::getId).toList();
        assertThat(makerSees).contains(theirs.getId()).doesNotContain(mine.getId(), superOnly.getId());
        List<String> superSees = asRole("super-2", "ROLE_SUPER_ADMIN", dual.queue().collectList()).block().stream().map(RecoveryProposal::getId).toList();
        assertThat(superSees).contains(mine.getId(), theirs.getId(), superOnly.getId());
    }

    @Test
    @DisplayName("withdraw: only the proposer, only while pending; a short reason is refused")
    void withdrawAndReasons() {
        RecoveryProposal proposal = proposeTransfer("maker-1", "1200.00");
        assertThat(refusal(asFinance("maker-2", dual.withdraw(proposal.getId()))).errorCode()).isEqualTo(ErrorCode.ACTOR_NOT_PERMITTED);
        assertThat(asFinance("maker-1", dual.withdraw(proposal.getId())).block().getStatus()).isEqualTo(RecoveryProposalStatus.WITHDRAWN);
        assertThat(refusal(asFinance("checker-1", dual.confirm(proposal.getId(), "Confirming a withdrawn proposal now"))).errorCode())
                .isEqualTo(ErrorCode.TRANSACTION_NOT_RECOVERABLE);
        assertThat(refusal(asFinance("checker-1", dual.confirm(proposal.getId(), "ok"))).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(asFinance("maker-1", dual.proposeTransfer(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE,
                new BigDecimal("2000.00"), "too short"))).errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
        assertThat(refusal(asFinance("maker-1", dual.confirm("no-such-proposal", "Confirming something that is not there"))).errorCode())
                .isEqualTo(ErrorCode.RECOVERY_PROPOSAL_UNKNOWN);
    }

    @Test
    @DisplayName("a confirmed action the system then refuses applies nothing and leaves the proposal FAILED with the reason")
    void failedActionIsRecorded() {
        when(chargebacks.writeOff(anyString(), any(), anyString(), anyString()))
                .thenReturn(Mono.error(new IllegalStateException("the chargeback was already recovered")));
        RecoveryProposal proposal = asFinance("maker-1", dual.propose(RecoveryAction.WRITE_OFF_CHARGEBACK, List.of("cb-1"),
                new BigDecimal("40.00"), Map.of(), "customer unreachable, unrecoverable loss")).block();

        RecoveryProposal result = asFinance("checker-1", dual.confirm(proposal.getId(), "Reviewed the file, write it off")).block();

        assertThat(result.getStatus()).isEqualTo(RecoveryProposalStatus.FAILED);
        assertThat(result.getFailureReason()).contains("already recovered");
        verify(chargebacks, org.mockito.Mockito.times(1)).writeOff(anyString(), any(), anyString(), anyString());
        verify(chargebacks, never()).startRecovery(anyString());
    }

    // ---- the operations as the resolver composes them ------------------------------------------

    private static com.pml.booking.web.graphql.dto.PlatformTransferInput transferInput(String amount, String key) {
        return new com.pml.booking.web.graphql.dto.PlatformTransferInput(PlatformAccountType.OPERATING, PlatformAccountType.RESERVE,
                new BigDecimal(amount), "month end reserve top up", key);
    }

    @Test
    @DisplayName("transferBetweenPlatformAccounts: up to the limit it moves now, once per key; above it only a proposal is made and nothing moves")
    void transferEntryPoint() {
        String key = "k-" + UUID.randomUUID();
        var executed = asFinance("fin-1", resolver.transferBetweenPlatformAccounts(transferInput("1000.00", key))).block();
        var repeated = asFinance("fin-1", resolver.transferBetweenPlatformAccounts(transferInput("1000.00", key))).block();
        assertThat(executed.executed()).isTrue();
        assertThat(repeated.transfer().getId()).isEqualTo(executed.transfer().getId());
        assertThat(balance(PlatformAccountType.OPERATING)).isEqualByComparingTo("4000.00");

        var proposed = asFinance("fin-1", resolver.transferBetweenPlatformAccounts(transferInput("1000.01", "k-" + UUID.randomUUID()))).block();
        assertThat(proposed.executed()).isFalse();
        assertThat(proposed.requiresSecondApprover()).isTrue();
        assertThat(proposed.proposal().getStatus()).isEqualTo(RecoveryProposalStatus.PENDING);
        assertThat(balance(PlatformAccountType.OPERATING)).as("a proposal moves nothing").isEqualByComparingTo("4000.00");

        assertThat(refusal(asFinance("fin-1", resolver.transferBetweenPlatformAccounts(new com.pml.booking.web.graphql.dto.PlatformTransferInput(
                PlatformAccountType.TAX_HOLDING, PlatformAccountType.RESERVE, BigDecimal.TEN, "month end reserve top up", "k")))).errorCode())
                .isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
    }

    private void seedAttempt(String deposit, String reservation) {
        template.insert(com.pml.booking.domain.model.PaymentAttempt.builder().depositId(deposit).attemptNumber("PA-" + deposit).reservationId(reservation)
                .attemptType(com.pml.booking.domain.enums.PaymentAttemptType.COLLECT)
                .status(com.pml.booking.domain.enums.PaymentAttemptStatus.PROCESSING).amount(BigDecimal.TEN).createdAt(Instant.now()).build()).block();
    }

    @Test
    @DisplayName("forceCompletePaymentAttempts: two people; the provider's own answer is applied to each attempt, and nothing is invented for the rest")
    void forceCompleteAppliesOnlyConfirmedAnswers() {
        String confirmed = "dep-" + UUID.randomUUID();
        String silent = "dep-" + UUID.randomUUID();
        seedAttempt(confirmed, "res-1");
        seedAttempt(silent, "res-2");
        when(outcomes.verifyAndApply(confirmed)).thenAnswer(call -> template.updateFirst(
                Query.query(Criteria.where("depositId").is(confirmed)),
                new Update().set("status", com.pml.booking.domain.enums.PaymentAttemptStatus.COMPLETED), com.pml.booking.domain.model.PaymentAttempt.class)
                .then(Mono.empty()));
        when(outcomes.verifyAndApply(silent)).thenReturn(Mono.empty());

        RecoveryProposal proposal = asRole("super-1", "ROLE_SUPER_ADMIN", resolver.forceCompletePaymentAttempts(List.of(confirmed, silent),
                "provider confirmed the first payment but the callback was lost")).block();
        assertThat(proposal.getStatus()).isEqualTo(RecoveryProposalStatus.PENDING);
        assertThat(template.findOne(Query.query(Criteria.where("depositId").is(confirmed)), com.pml.booking.domain.model.PaymentAttempt.class).block().getStatus())
                .as("proposing completes nothing").isEqualTo(com.pml.booking.domain.enums.PaymentAttemptStatus.PROCESSING);

        RecoveryProposal done = asRole("super-2", "ROLE_SUPER_ADMIN", dual.confirm(proposal.getId(), "Checked with the provider dashboard, confirmed")).block();
        assertThat(done.getStatus()).isEqualTo(RecoveryProposalStatus.CONFIRMED);
        assertThat(done.getOutcome()).isEqualTo("Completed 1 of 2 payment attempts");
        assertThat(template.findOne(Query.query(Criteria.where("depositId").is(silent)), com.pml.booking.domain.model.PaymentAttempt.class).block().getStatus())
                .isEqualTo(com.pml.booking.domain.enums.PaymentAttemptStatus.PROCESSING);

        RecoveryProposal none = asRole("super-1", "ROLE_SUPER_ADMIN", resolver.forceCompletePaymentAttempts(List.of(silent),
                "provider says nothing about this one, trying anyway")).block();
        RecoveryProposal failed = asRole("super-2", "ROLE_SUPER_ADMIN", dual.confirm(none.getId(), "Trying to force a payment nobody confirmed")).block();
        assertThat(failed.getStatus()).isEqualTo(RecoveryProposalStatus.FAILED);
        assertThat(failed.getFailureReason()).contains("has not confirmed");
    }

    @SuppressWarnings("unused")
    private static final SimpleGrantedAuthority UNUSED = null;
}
