package com.pml.booking.security;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
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
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Escrow transactions are readable by organizers, so the read carries an ownership boundary.
 * OWASP A01:2021 · CWE-639.
 *
 * <h2>Why the boundary is not optional</h2>
 * {@code escrowTransactions(escrowAccountId, page)} is open to ORGANIZER as well as ADMIN, so the
 * organizer whose money it is can read it.
 *
 * <p>That wider audience is the dangerous part. {@code escrowAccountId} comes from the client.
 * ORGANIZER access without a filter would hand every organizer on the platform a competitor's
 * complete money history by id — every ticket sale, every refund, every payout, with amounts. A
 * role annotation alone would make that look deliberate.
 *
 * <p>This asserts the filter that accompanies the wider audience, on the real derived query. Each
 * refusal case first proves the row is present and reachable unscoped, so a passing test is
 * never just an empty collection.
 */
@Tag("L2")
@Tag("ET-PLT-007")
    // The test iterates ids across two organizations and asserts the response is identical
    // for a non-existent id and for another tenant's id. That property belongs to the error
    // contract as much as to the tenant boundary — one decides who may read, the other that
    // the refusal discloses nothing — so the class carries both tags and either group runs it.
@Tag("ET-PLT-005")
@DisplayName("O-4 · an escrow account is reachable only from the organization that owns it")
class EscrowTransactionScopeTest {

    private static final String OWNER_ORG = "org-kabwe-collective";
    private static final String OTHER_ORG = "org-lusaka-live";
    private static final String THE_ACCOUNT = "escrow-kabwe-jazz";
    private static final String NEVER_ISSUED = "escrow-that-was-never-issued";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static EventEscrowAccountRepository accounts;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "booking_escrow_scope"));
        accounts = new ReactiveMongoRepositoryFactory(template)
                .getRepository(EventEscrowAccountRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOneAccountPerOrganization() {
        template.remove(new Query(), EventEscrowAccount.class).block();
        save(THE_ACCOUNT, OWNER_ORG);
        save("escrow-lusaka-live", OTHER_ORG);
    }

    private static void save(String id, String organizationId) {
        EventEscrowAccount account = new EventEscrowAccount();
        account.setId(id);
        account.setOrganizationId(organizationId);
        account.setEventId("event-for-" + id);
        account.setCurrentBalance(new BigDecimal("48250.00"));
        template.save(account).block();
    }

    @Test
    @DisplayName("ET-PLT-007 · the owning organization reads its own escrow account")
    void theOwnerReadsIt() {
        assertThat(visibleTo(TenantScope.of("u", Set.of(OWNER_ORG))).getId()).isEqualTo(THE_ACCOUNT);
    }

    @Test
    @DisplayName("ET-PLT-007 · another organizer cannot read it, which is what the widening risked")
    void anotherOrganizerCannot() {
        assertThat(accounts.findById(THE_ACCOUNT).block())
                .as("the account must exist, or this passes against an empty collection")
                .isNotNull();

        assertThatThrownBy(() -> visibleTo(TenantScope.of("u", Set.of(OTHER_ORG))))
                .as("""
                    before O-4 this query was ADMIN-only. Taking §4's ORGANIZER audience without \
                    this filter would publish every organization's money movements to every \
                    organizer holding an account id.""")
                .isInstanceOf(DomainRefusal.class)
                .satisfies(refused -> assertThat(((DomainRefusal) refused).errorCode())
                        .isEqualTo(ErrorCode.ESCROW_ACCOUNT_UNKNOWN));
    }

    @Test
    @DisplayName("ET-PLT-007 · the refusal is indistinguishable from an id that was never issued")
    void crossTenantLooksLikeNotFound() {
        DomainRefusal onTheirs = refusalFor(THE_ACCOUNT);
        DomainRefusal onNothing = refusalFor(NEVER_ISSUED);

        // Escrow account ids are the one thing worth enumerating here: knowing an id is real
        // tells you an event exists and has taken money.
        assertThat(onTheirs.errorCode()).isEqualTo(onNothing.errorCode());
        assertThat(onTheirs.details()).isEqualTo(onNothing.details()).isEmpty();
    }

    @Test
    @DisplayName("ET-PLT-007 · finance and platform administrators still read across organizations")
    void administratorsAreUnscoped() {
        // The audience widened; it did not narrow. Reconciliation and payout review read every
        // account, and closing that would trade one defect for another.
        assertThat(visibleTo(TenantScope.platformAdministrator("ops", Set.of())).getId())
                .isEqualTo(THE_ACCOUNT);
    }

    // ── the boundary under test, exactly as the resolver composes it ────────

    private static EventEscrowAccount visibleTo(TenantScope scope) {
        return locate(scope, THE_ACCOUNT);
    }

    private static EventEscrowAccount locate(TenantScope scope, String accountId) {
        return CurrentTenantScope.get()
                .flatMap(current -> TenantGuard.locate(
                        current,
                        accounts.findById(accountId),
                        organizationIds -> accounts.findByIdAndOrganizationIdIn(accountId, organizationIds),
                        ErrorCode.ESCROW_ACCOUNT_UNKNOWN,
                        "escrow account " + accountId))
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .block();
    }

    private static DomainRefusal refusalFor(String accountId) {
        try {
            locate(TenantScope.of("u", Set.of(OTHER_ORG)), accountId);
        } catch (DomainRefusal refused) {
            return refused;
        }
        throw new AssertionError("expected a refusal for " + accountId);
    }
}
