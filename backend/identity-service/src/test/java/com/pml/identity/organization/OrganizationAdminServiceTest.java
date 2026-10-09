package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.KybStatus;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.PayoutConfigAuditLog;
import com.pml.identity.domain.valueobject.MobileMoneyAccount;
import com.pml.identity.domain.valueobject.PayoutBankDetails;
import com.pml.identity.domain.valueobject.PayoutConfig;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.PayoutConfigAuditLogRepository;
import com.pml.identity.security.FieldEncryptionService;
import com.pml.identity.service.AdminAuditService;
import com.pml.identity.service.OrganizationAdminService;
import com.pml.identity.service.OrganizationRules.PayoutAccountStatus;
import com.pml.identity.service.PayoutAccountQueryService;
import com.pml.identity.web.graphql.dto.organization.UpdateOrganizationInput;
import com.pml.identity.web.graphql.dto.platform.PayoutAccountFilterInput;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.constants.PayoutMethod;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
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

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The organization administration operations against a real MongoDB replica set. */
@Tag("L2")
@Tag("ET-ORG-001")
@DisplayName("ET-ORG-001-R10..R12 · commission, payout-account review, profile and deletion, persisted and audited")
class OrganizationAdminServiceTest {

    private static final String ORG = "org-1";
    private static final String ADMIN = "admin-7";
    private static final Instant NOW = Instant.parse("2026-10-04T09:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static OrganizationAdminService service;
    private static PayoutAccountQueryService accounts;
    private static FieldEncryptionService encryption;
    private static OrganizationRepository organizations;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_org_admin"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        organizations = factory.getRepository(OrganizationRepository.class);
        PayoutConfigAuditLogRepository payoutAudit = factory.getRepository(PayoutConfigAuditLogRepository.class);
        TestClock clock = TestClock.frozenAt(NOW);
        service = new OrganizationAdminService(organizations, payoutAudit, new AdminAuditService(template, clock), clock);
        encryption = new FieldEncryptionService(FieldEncryptionService.generateKey());
        accounts = new PayoutAccountQueryService(organizations, encryption);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.remove(new Query(), Organization.class).block();
        template.remove(new Query(), AuditLog.class).block();
        template.remove(new Query(), PayoutConfigAuditLog.class).block();
        template.save(organization(ORG, OrganizationStatus.ACTIVE)).block();
    }

    private static Organization organization(String id, OrganizationStatus status) {
        return Organization.builder().id(id).name("Zambezi Live " + id).slug("zambezi-" + id).ownerId("owner-" + id)
                .status(status).kybStatus(KybStatus.NOT_STARTED)
                .payoutConfig(PayoutConfig.builder().preferredMethod(PayoutMethod.BANK_TRANSFER).commissionRate(0.05)
                        .bankAccount(PayoutBankDetails.builder().bankName("Zanaco")
                                .accountNumber(encrypted("0012345678901")).accountHolderName("Zambezi Live Ltd")
                                .verified(true).build())
                        .verified(true).build())
                .payoutAccountVerified(true)
                .build();
    }

    private static String encrypted(String plain) {
        return encryption == null ? plain : encryption.encrypt(plain).block();
    }

    private Organization stored(String id) {
        return template.findById(id, Organization.class).block();
    }

    private long auditRows(AuditLog.AuditAction action) {
        return template.count(Query.query(org.springframework.data.mongodb.core.query.Criteria.where("action").is(action)),
                AuditLog.class).block();
    }

    // ---- commission ----------------------------------------------------------------------------------

    @Test
    @DisplayName("an administrator's percentage is stored as the fraction, with who set it, and audited")
    void setsCommission() {
        service.setCommissionRate(ORG, 7.5, "negotiated in a meeting", ADMIN).block();

        PayoutConfig config = stored(ORG).getPayoutConfig();
        assertThat(config.getCommissionRate()).isEqualTo(0.075);
        assertThat(config.getCommissionSetBy()).isEqualTo(ADMIN);
        assertThat(config.getCommissionSetAt()).isEqualTo(NOW);
        assertThat(auditRows(AuditLog.AuditAction.ORGANIZATION_COMMISSION_CHANGED)).isEqualTo(1);
        AuditLog row = template.findOne(new Query(), AuditLog.class).block();
        assertThat(row.getPerformedBy()).isEqualTo(ADMIN);
        assertThat(row.getMetadata()).containsEntry("previousPercent", "5.0").containsEntry("newPercent", "7.5");
    }

    @Test
    @DisplayName("a rate outside 0..50 percent is refused and nothing is written")
    void refusesBadCommission() {
        assertThatThrownBy(() -> service.setCommissionRate(ORG, 51.0, null, ADMIN).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.CONFIGURATION_VALUE_INVALID));
        assertThat(stored(ORG).getPayoutConfig().getCommissionRate()).isEqualTo(0.05);
        assertThat(auditRows(AuditLog.AuditAction.ORGANIZATION_COMMISSION_CHANGED)).isZero();
    }

    @Test
    @DisplayName("an unknown organization is ORGANIZATION_UNKNOWN")
    void unknownOrganization() {
        assertThatThrownBy(() -> service.setCommissionRate("missing", 5.0, null, ADMIN).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORGANIZATION_UNKNOWN));
    }

    // ---- payout account review ------------------------------------------------------------------------

    @Test
    @DisplayName("rejecting unverifies the account, records the reason for the owner and writes both audit rows")
    void rejectsAccount() {
        service.rejectPayoutAccount(ORG, "  account holder does not match the business name ", ADMIN).block();

        Organization org = stored(ORG);
        assertThat(org.isPayoutAccountVerified()).isFalse();
        assertThat(org.getPayoutConfig().isVerified()).isFalse();
        assertThat(org.getPayoutConfig().getBankAccount().isVerified()).isFalse();
        assertThat(org.getPayoutConfig().getBankAccount().getRejectionReason())
                .isEqualTo("account holder does not match the business name");
        assertThat(PayoutAccountStatus.REJECTED).isEqualTo(com.pml.identity.service.OrganizationRules.statusOf(org.getPayoutConfig()));
        assertThat(auditRows(AuditLog.AuditAction.PAYOUT_ACCOUNT_REJECTED)).isEqualTo(1);
        assertThat(template.count(new Query(), PayoutConfigAuditLog.class).block()).isEqualTo(1);
    }

    @Test
    @DisplayName("rejecting needs a reason, and an organization without an account has nothing to reject")
    void rejectNeedsReasonAndAccount() {
        assertThatThrownBy(() -> service.rejectPayoutAccount(ORG, " ", ADMIN).block()).isInstanceOf(DomainRefusal.class);

        Organization bare = organization("org-bare", OrganizationStatus.ACTIVE);
        bare.setPayoutConfig(null);
        template.save(bare).block();
        assertThatThrownBy(() -> service.rejectPayoutAccount("org-bare", "reason", ADMIN).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.BANK_ACCOUNT_UNKNOWN));
    }

    @Test
    @DisplayName("a freeze stops payouts without removing the account, is idempotent, and reinstating lifts it")
    void suspendAndReinstate() {
        service.suspendPayoutAccount(ORG, "chargeback investigation", ADMIN).block();
        service.suspendPayoutAccount(ORG, "chargeback investigation", ADMIN).block();

        Organization frozen = stored(ORG);
        assertThat(frozen.getPayoutConfig().getBankAccount().isSuspended()).isTrue();
        assertThat(frozen.getPayoutConfig().getBankAccount().getAccountNumber()).isNotNull();
        assertThat(frozen.canReceivePayouts()).as("approved and verified, but frozen").isFalse();

        service.reinstatePayoutAccount(ORG, ADMIN).block();
        Organization restored = stored(ORG);
        assertThat(restored.getPayoutConfig().getBankAccount().isSuspended()).isFalse();
        assertThat(restored.canReceivePayouts()).isTrue();
    }

    @Test
    @DisplayName("a wallet is reviewed the same way when it is the preferred method")
    void walletReview() {
        Organization wallet = organization("org-wallet", OrganizationStatus.ACTIVE);
        wallet.setPayoutConfig(PayoutConfig.builder().preferredMethod(PayoutMethod.MOBILE_MONEY)
                .mobileMoneyAccount(MobileMoneyAccount.builder().phoneNumber("+260971234567")
                        .accountHolderName("Zambezi Live").build()).build());
        template.save(wallet).block();

        service.rejectPayoutAccount("org-wallet", "number not registered to the business", ADMIN).block();

        MobileMoneyAccount stored = stored("org-wallet").getPayoutConfig().getMobileMoneyAccount();
        assertThat(stored.getRejectionReason()).isEqualTo("number not registered to the business");
        assertThat(com.pml.identity.service.OrganizationRules.statusOf(stored)).isEqualTo(PayoutAccountStatus.REJECTED);
    }

    @Test
    @DisplayName("the review table lists one masked row per account and filters by status, method and name")
    void reviewTable() {
        template.save(organization("org-2", OrganizationStatus.ACTIVE)).block();
        service.rejectPayoutAccount("org-2", "bad details", ADMIN).block();
        Organization none = organization("org-none", OrganizationStatus.DRAFT);
        none.setPayoutConfig(null);
        template.save(none).block();

        var all = accounts.list(null).collectList().block();
        assertThat(all).extracting(r -> r.organizationId()).containsExactlyInAnyOrder(ORG, "org-2");
        assertThat(all).allSatisfy(r -> assertThat(r.accountNumberMasked()).isEqualTo("****8901"));
        assertThat(all.toString()).doesNotContain("0012345678901");

        var rejected = accounts.list(new PayoutAccountFilterInput(PayoutAccountStatus.REJECTED, null, null)).collectList().block();
        assertThat(rejected).extracting(r -> r.organizationId()).containsExactly("org-2");
        assertThat(rejected.get(0).rejectionReason()).isEqualTo("bad details");

        assertThat(accounts.list(new PayoutAccountFilterInput(null, PayoutMethod.MOBILE_MONEY, null)).collectList().block()).isEmpty();
        assertThat(accounts.list(new PayoutAccountFilterInput(null, null, "ZAMBEZI-ORG-2")).collectList().block())
                .extracting(r -> r.organizationId()).containsExactly("org-2");
    }

    // ---- profile ---------------------------------------------------------------------------------------

    @Test
    @DisplayName("an edit writes the fields given and leaves the rest alone")
    void partialProfileUpdate() {
        service.updateProfile(ORG, new UpdateOrganizationInput(null, "New description", null, null, "Live music", "https://zambezi.example",
                new com.pml.identity.web.graphql.dto.organization.OrganizationApplicationInput.SocialLinksInput(
                        "fb", null, null, null, null, null),
                null, null, null, 2019, "+260211000000", "hello@zambezi.example",
                new UpdateOrganizationInput.BusinessAddressInput("Plot 5", null, "Lusaka", "Lusaka", null, null, null))).block();

        Organization org = stored(ORG);
        assertThat(org.getName()).isEqualTo("Zambezi Live org-1");
        assertThat(org.getDescription()).isEqualTo("New description");
        assertThat(org.getTagline()).isEqualTo("Live music");
        assertThat(org.getWebsite()).isEqualTo("https://zambezi.example");
        assertThat(org.getSocialLinks().getFacebook()).isEqualTo("fb");
        assertThat(org.getYearEstablished()).isEqualTo(2019);
        assertThat(org.getBusinessAddress().getCity()).isEqualTo("Lusaka");
        assertThat(org.getBusinessAddress().getCountry()).isEqualTo("Zambia");
    }

    @Test
    @DisplayName("the tax id cannot change once the application has been reviewed, but can while it is a draft")
    void kybFieldsLockAfterReview() {
        UpdateOrganizationInput changeTpin = new UpdateOrganizationInput(null, null, null, null, null, null, null,
                null, "1002003004", null, null, null, null, null);

        assertThatThrownBy(() -> service.updateProfile(ORG, changeTpin).block())
                .isInstanceOfSatisfying(DomainRefusal.class,
                        e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ORGANIZATION_STATE_INVALID));
        assertThat(stored(ORG).getTaxId()).isNull();

        template.save(organization("org-draft", OrganizationStatus.DRAFT)).block();
        service.updateProfile("org-draft", changeTpin).block();
        assertThat(stored("org-draft").getTaxId()).isEqualTo("1002003004");
    }

    // ---- deletion --------------------------------------------------------------------------------------

    @Test
    @DisplayName("a deletion request starts a 30-day grace, cancelling restores the previous status")
    void deletionRoundTrip() {
        service.requestDeletion(ORG, "owner-org-1", "closing the business").block();

        Organization pending = stored(ORG);
        assertThat(pending.getStatus()).isEqualTo(OrganizationStatus.PENDING_DELETION);
        assertThat(pending.getStatusBeforeDeletion()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(pending.getDeletionScheduledFor()).isEqualTo(NOW.plusSeconds(30L * 24 * 3600));
        assertThat(auditRows(AuditLog.AuditAction.ORGANIZATION_DELETION_REQUESTED)).isEqualTo(1);

        assertThatThrownBy(() -> service.requestDeletion(ORG, "owner-org-1", null).block())
                .as("a second request is refused, not repeated").isInstanceOf(DomainRefusal.class);

        service.cancelDeletion(ORG, "owner-org-1").block();
        Organization restored = stored(ORG);
        assertThat(restored.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
        assertThat(restored.getDeletionRequestedAt()).isNull();
        assertThat(restored.getDeletionScheduledFor()).isNull();
        assertThat(auditRows(AuditLog.AuditAction.ORGANIZATION_DELETION_CANCELLED)).isEqualTo(1);
    }

    @Test
    @DisplayName("cancelling when nothing is open, and deleting a suspended organization, are refused")
    void deletionRefusals() {
        assertThatThrownBy(() -> service.cancelDeletion(ORG, "owner-org-1").block()).isInstanceOf(DomainRefusal.class);

        template.save(organization("org-susp", OrganizationStatus.SUSPENDED)).block();
        assertThatThrownBy(() -> service.requestDeletion("org-susp", "owner", null).block()).isInstanceOf(DomainRefusal.class);
        assertThat(stored("org-susp").getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
    }
}
