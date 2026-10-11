package com.pml.booking.security;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.booking.domain.model.BankAccount;
import com.pml.booking.domain.model.EventEscrowAccount;
import com.pml.booking.domain.model.PromoCode;
import com.pml.booking.domain.enums.DiscountType;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.booking.repository.BankAccountRepository;
import com.pml.booking.repository.EventEscrowAccountRepository;
import com.pml.booking.repository.PromoCodeRepository;
import com.pml.booking.service.impl.BankAccountServiceImpl;
import com.pml.booking.service.impl.EscrowServiceImpl;
import com.pml.booking.service.impl.PromoCodeServiceImpl;
import com.pml.booking.web.graphql.dto.CreatePromoCodeInput;
import com.pml.booking.web.graphql.dto.UpdateBankAccountInput;
import com.pml.shared.constants.EscrowStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
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
import java.time.ZoneOffset;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * ET-PLT-007 Phase 6 · {@code BankAccountServiceImpl}, {@code EscrowServiceImpl} and
 * {@code PromoCodeServiceImpl} each had write methods that re-fetched their record with a bare
 * {@code repository.findById(id)} — no filter of any kind — after the resolver's own
 * {@code tenantReads.*ForCaller} already proved ownership. This proves the service's own defense
 * in depth: a future caller that skips that upstream read is refused here too.
 *
 * <p>Flat {@code @Test} methods, not {@code @Nested} groups: see F-055.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@DisplayName("F-001 · bank accounts, escrow accounts and promo codes are reachable only by the organization that owns them")
class AdminFinanceOperationsTenantBoundaryTest {

    private static final String OWNER_ORG = "org-kabwe-collective";
    private static final String OTHER_ORG = "org-lusaka-live";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    private static BankAccountRepository bankAccounts;
    private static BankAccountServiceImpl bankAccountService;

    private static EventEscrowAccountRepository escrowAccounts;
    private static EscrowServiceImpl escrowService;

    private static PromoCodeRepository promoCodes;
    private static PromoCodeServiceImpl promoCodeService;

    private static final TenantScope OUTSIDER = TenantScope.of("user-outsider", Set.of(OTHER_ORG));
    private static final TenantScope ADMIN = TenantScope.platformAdministrator("user-admin", Set.of());

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "booking_admin_finance_boundary"));

        bankAccounts = new ReactiveMongoRepositoryFactory(template).getRepository(BankAccountRepository.class);
        bankAccountService = new BankAccountServiceImpl(bankAccounts, Mockito.mock(IdentityServiceClient.class), passAccess());

        escrowAccounts = new ReactiveMongoRepositoryFactory(template).getRepository(EventEscrowAccountRepository.class);
        escrowService = new EscrowServiceImpl(escrowAccounts, Clock.fixed(Instant.parse("2026-09-01T09:00:00Z"), ZoneOffset.UTC),
                Mockito.mock(TenantAccessGuard.class));

        promoCodes = new ReactiveMongoRepositoryFactory(template).getRepository(PromoCodeRepository.class);
        promoCodeService = new PromoCodeServiceImpl(promoCodes, Clock.fixed(Instant.parse("2026-09-01T09:00:00Z"), ZoneOffset.UTC));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seedOneOfEachOwnedByKabwe() {
        template.remove(new Query(), BankAccount.class).block();
        template.remove(new Query(), EventEscrowAccount.class).block();
        template.remove(new Query(), PromoCode.class).block();

        bankAccounts.save(BankAccount.builder()
                .id("bank-kabwe-1")
                .organizerId("user-" + OWNER_ORG)
                .organizationId(OWNER_ORG)
                .accountHolderName("Kabwe Collective")
                .bankName("Zanaco")
                .accountNumber("1234567890")
                .currency("ZMW")
                .build()).block();

        escrowAccounts.save(EventEscrowAccount.builder()
                .id("escrow-kabwe-1")
                .accountNumber("ESC-event-kabwe-2026")
                .eventId("event-kabwe-jazz-night")
                .organizerId("user-" + OWNER_ORG)
                .organizationId(OWNER_ORG)
                .currentBalance(BigDecimal.ZERO)
                .status(EscrowStatus.ACTIVE)
                .build()).block();

        promoCodes.save(PromoCode.builder()
                .id("promo-kabwe-1")
                .code("KABWE10")
                .eventId("event-kabwe-jazz-night")
                .organizationId(OWNER_ORG)
                .discountType(DiscountType.PERCENTAGE)
                .discountValue(BigDecimal.TEN)
                .isActive(true)
                .build()).block();
    }

    private static <T> Mono<T> as(TenantScope scope, Mono<T> operation) {
        return operation.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)));
    }

    // ── bank accounts ──────────────────────────────────────────────────────

    @Test
    @DisplayName("an outsider's bank account update is refused, and the account is unchanged")
    void outsiderCannotUpdateBankAccount() {
        UpdateBankAccountInput input = new UpdateBankAccountInput(
                "Someone Else", null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> as(OUTSIDER, bankAccountService.update("bank-kabwe-1", input)).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.BANK_ACCOUNT_UNKNOWN));

        assertThat(bankAccounts.findById("bank-kabwe-1").block().getAccountHolderName())
                .isEqualTo("Kabwe Collective");
    }

    @Test
    @DisplayName("an outsider's bank account delete is refused, and the account survives")
    void outsiderCannotDeleteBankAccount() {
        assertThatThrownBy(() -> as(OUTSIDER, bankAccountService.delete("bank-kabwe-1")).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.BANK_ACCOUNT_UNKNOWN));

        assertThat(bankAccounts.findById("bank-kabwe-1").block()).as("survives the refused delete").isNotNull();
    }

    @Test
    @DisplayName("a platform administrator's bank account update succeeds across organizations")
    void administratorCanUpdateAnyOrganizationsBankAccount() {
        UpdateBankAccountInput input = new UpdateBankAccountInput(
                "Platform Support", null, null, null, null, null, null, null, null);

        BankAccount updated = as(ADMIN, bankAccountService.update("bank-kabwe-1", input)).block();
        assertThat(updated.getAccountHolderName()).isEqualTo("Platform Support");
    }

    // ── escrow accounts ────────────────────────────────────────────────────

    @Test
    @DisplayName("an outsider's escrow lock is refused, and the account stays active")
    void outsiderCannotLockEscrow() {
        assertThatThrownBy(() -> as(OUTSIDER, escrowService.lockEscrowAccount(
                        "escrow-kabwe-1", Instant.parse("2026-12-01T00:00:00Z"), "fraud review")).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.ESCROW_ACCOUNT_UNKNOWN));

        assertThat(escrowAccounts.findById("escrow-kabwe-1").block().getStatus()).isEqualTo(EscrowStatus.ACTIVE);
    }

    @Test
    @DisplayName("a platform administrator's escrow lock succeeds across organizations")
    void administratorCanLockAnyOrganizationsEscrow() {
        EventEscrowAccount locked = as(ADMIN, escrowService.lockEscrowAccount(
                "escrow-kabwe-1", Instant.parse("2026-12-01T00:00:00Z"), "fraud review")).block();
        assertThat(locked.getStatus()).isEqualTo(EscrowStatus.HOLD);
    }

    // ── promo codes ────────────────────────────────────────────────────────

    @Test
    @DisplayName("an outsider's promo code update is refused, and the code is unchanged")
    void outsiderCannotUpdatePromoCode() {
        CreatePromoCodeInput input = new CreatePromoCodeInput(
                null, null, DiscountType.FIXED_AMOUNT, BigDecimal.valueOf(50), null, null, null, null, null, null);

        assertThatThrownBy(() -> as(OUTSIDER, promoCodeService.updatePromoCode("promo-kabwe-1", input)).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.PROMO_CODE_UNKNOWN));

        assertThat(promoCodes.findById("promo-kabwe-1").block().getDiscountType()).isEqualTo(DiscountType.PERCENTAGE);
    }

    @Test
    @DisplayName("an outsider's promo code delete is refused, and the code survives")
    void outsiderCannotDeletePromoCode() {
        assertThatThrownBy(() -> as(OUTSIDER, promoCodeService.deletePromoCode("promo-kabwe-1")).block())
                .isInstanceOfSatisfying(DomainRefusal.class, refusal ->
                        assertThat(refusal.errorCode()).isEqualTo(ErrorCode.PROMO_CODE_UNKNOWN));

        assertThat(promoCodes.findById("promo-kabwe-1").block()).as("survives the refused delete").isNotNull();
    }

    @Test
    @DisplayName("a platform administrator's promo code deactivation succeeds across organizations")
    void administratorCanDeactivateAnyOrganizationsPromoCode() {
        PromoCode deactivated = as(ADMIN, promoCodeService.deactivatePromoCode("promo-kabwe-1")).block();
        assertThat(deactivated.isActive()).isFalse();
    }

    /** Access is not what this test is about: every organization is manageable. */
    private static com.pml.booking.security.BankAccountAccess passAccess() {
        com.pml.booking.security.BankAccountAccess access = org.mockito.Mockito.mock(com.pml.booking.security.BankAccountAccess.class);
        org.mockito.Mockito.when(access.require(org.mockito.ArgumentMatchers.any()))
                .thenAnswer(call -> reactor.core.publisher.Mono.just(call.getArgument(0)));
        return access;
    }
}
