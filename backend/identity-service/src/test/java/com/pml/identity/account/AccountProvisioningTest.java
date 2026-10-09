package com.pml.identity.account;

import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The MongoDB and Keycloak steps behind the ensure workflow, against a real replica set.
 *
 * <p>Each step is run twice where a retried activity would run it twice. What is asserted is the
 * property the workflow leans on: a step finds its work already done and says so, and never creates a
 * second account, a second Keycloak user or a second outbox row.
 */
@Tag("L2")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · account provisioning steps are repeatable and keep one account per contact")
class AccountProvisioningTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final String PHONE = "+260971234567";

    private static final Clock CLOCK = TestClock.frozenAt(NOW);
    private static AccountFixtures db;

    private FakeKeycloak keycloak;
    private AccountProvisioning provisioning;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures(MongoReplicaSet.connectionString(), "identity_account_provisioning", CLOCK);
    }

    @AfterAll
    static void disconnect() {
        db.close();
    }

    @BeforeEach
    void reset() {
        db.reset();
        keycloak = new FakeKeycloak();
        provisioning = new AccountProvisioning(db.template, db.transaction, db.outbox, db.proofs, keycloak,
                AccountFixtures.CRYPTO, CLOCK);
    }

    private ProofRecord whatsappProof(String proofId) {
        return db.proofFor(ContactType.WHATSAPP, PHONE, proofId);
    }

    private User account(String accountId) {
        return db.template.findById(accountId, User.class).block();
    }

    @Test
    @DisplayName("a new contact gets a PROVISIONING account and its verified contact, in one transaction, with no raw value stored")
    void claimCreatesTheAccount() {
        ProofRecord proof = whatsappProof("proof-1");

        AccountProvisioning.Claimed claimed = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block();

        assertThat(claimed.created()).isTrue();
        assertThat(claimed.state()).isEqualTo(AccountState.PROVISIONING);
        User account = account(claimed.accountId());
        assertThat(account.getStatus()).isEqualTo(AccountState.PROVISIONING);
        assertThat(account.getUsername()).as("the Keycloak username of a new account is its id").isEqualTo(claimed.accountId());
        assertThat(account.isActive()).as("not in circulation until it is finished").isFalse();
        assertThat(account.getKeycloakUserId()).isNull();
        assertThat(account.getPreferredChannel()).isEqualTo(ContactType.WHATSAPP);
        assertThat(account.getEmail()).isNull();
        assertThat(account.getPhoneNumber()).as("the number lives only in the contact").isNull();

        List<Contact> contacts = db.template.findAll(Contact.class).collectList().block();
        assertThat(contacts).hasSize(1);
        Contact contact = contacts.get(0);
        assertThat(contact.getAccountId()).isEqualTo(claimed.accountId());
        assertThat(contact.getValueHash()).isEqualTo(proof.contactKey());
        assertThat(contact.getVerifiedAt()).isEqualTo(NOW);
        assertThat(contact.isPrimary()).isTrue();
        assertThat(contact.getValueEncrypted()).doesNotContain(PHONE);
        assertThat(AccountFixtures.CRYPTO.decrypt(contact.getValueEncrypted()).block()).isEqualTo(PHONE);
        assertThat(account.getPrimaryContactId()).isEqualTo(contact.getId());
    }

    @Test
    @DisplayName("claiming twice returns the same account and creates nothing the second time")
    void claimIsRepeatable() {
        ProofRecord proof = whatsappProof("proof-1");

        AccountProvisioning.Claimed first = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block();
        AccountProvisioning.Claimed second = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block();

        assertThat(second.accountId()).isEqualTo(first.accountId());
        assertThat(second.created()).isFalse();
        assertThat(db.count("identity_users")).isEqualTo(1);
        assertThat(db.count("identity_contacts")).isEqualTo(1);
    }

    @Test
    @DisplayName("a second proof of the same contact reaches the same account; the proof is not needed once the contact is owned")
    void anOwnedContactNeedsNoProof() {
        ProofRecord proof = whatsappProof("proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        db.proofs.clear();

        assertThat(provisioning.claim("proof-expired", proof.contactKey(), ContactType.WHATSAPP).block().accountId())
                .isEqualTo(accountId);
    }

    @Test
    @DisplayName("two claims racing for one contact end with one account: the unique index picks the winner and the loser returns it")
    void aRaceMakesOneAccount() {
        ProofRecord proof = whatsappProof("proof-1");
        for (int round = 0; round < 5; round++) {
            db.reset();
            ProofRecord again = whatsappProof("proof-1");
            List<AccountProvisioning.Claimed> results = Mono.zip(
                            provisioning.claim("proof-1", again.contactKey(), ContactType.WHATSAPP),
                            provisioning.claim("proof-1", again.contactKey(), ContactType.WHATSAPP),
                            provisioning.claim("proof-1", again.contactKey(), ContactType.WHATSAPP))
                    .map(tuple -> List.of(tuple.getT1(), tuple.getT2(), tuple.getT3()))
                    .block();

            assertThat(results).extracting(AccountProvisioning.Claimed::accountId).containsOnly(results.get(0).accountId());
            assertThat(results).filteredOn(AccountProvisioning.Claimed::created).hasSizeLessThanOrEqualTo(1);
            assertThat(db.count("identity_users")).as("round " + round).isEqualTo(1);
            assertThat(db.count("identity_contacts")).isEqualTo(1);
        }
        assertThat(proof).isNotNull();
    }

    @Test
    @DisplayName("an unknown proof and a proof of another contact are refused as PROOF_INVALID, and nothing is written")
    void badProofsAreRefused() {
        ProofRecord proof = whatsappProof("proof-1");
        ProofRecord other = db.proofFor(ContactType.EMAIL, "someone@example.com", "proof-2");

        assertRefused(() -> provisioning.claim("proof-missing", proof.contactKey(), ContactType.WHATSAPP).block(), ErrorCode.PROOF_INVALID);
        assertRefused(() -> provisioning.claim("proof-2", proof.contactKey(), ContactType.WHATSAPP).block(), ErrorCode.PROOF_INVALID);
        assertThat(other).isNotNull();
        assertThat(db.count("identity_users")).isZero();
        assertThat(db.count("identity_contacts")).isZero();
    }

    @Test
    @DisplayName("the Keycloak user is created once; a user an earlier attempt made is read back, never a second one")
    void keycloakUserIsCreatedOnce() {
        ProofRecord proof = whatsappProof("proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();

        String first = provisioning.createKeycloakUser(accountId).block();
        String second = provisioning.createKeycloakUser(accountId).block();

        assertThat(second).isEqualTo(first);
        assertThat(keycloak.creates.get()).isEqualTo(1);
        assertThat(keycloak.user(accountId).roles()).containsExactly("CUSTOMER");
    }

    @Test
    @DisplayName("a Keycloak user left by a crashed attempt is adopted by username - the account's own id - and not recreated")
    void anEarlierAttemptIsReadBack() {
        ProofRecord proof = whatsappProof("proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        String leftBehind = keycloak.preexisting(accountId);

        assertThat(provisioning.createKeycloakUser(accountId).block()).isEqualTo(leftBehind);
        assertThat(keycloak.creates.get()).isZero();
    }

    @Test
    @DisplayName("an email contact is handed to Keycloak, verified; a WhatsApp contact is not")
    void attributesCarryOnlyTheEmail() {
        ProofRecord mail = db.proofFor(ContactType.EMAIL, "Buyer@Example.com", "proof-mail");
        String emailAccount = provisioning.claim("proof-mail", mail.contactKey(), ContactType.EMAIL).block().accountId();
        String emailKc = provisioning.createKeycloakUser(emailAccount).block();
        provisioning.applyAttributes(emailAccount, emailKc).block();

        KeycloakAccountPort.AccountAttributes attributes = keycloak.user(emailAccount).attributes();
        assertThat(attributes.accountId()).isEqualTo(emailAccount);
        assertThat(attributes.email()).isEqualTo("buyer@example.com");
        assertThat(attributes.emailVerified()).isTrue();

        ProofRecord phone = whatsappProof("proof-phone");
        String phoneAccount = provisioning.claim("proof-phone", phone.contactKey(), ContactType.WHATSAPP).block().accountId();
        provisioning.applyAttributes(phoneAccount, provisioning.createKeycloakUser(phoneAccount).block()).block();
        assertThat(keycloak.user(phoneAccount).attributes().email()).isNull();
        assertThat(keycloak.user(phoneAccount).roles()).contains("CUSTOMER");

        // running it again changes nothing
        provisioning.applyAttributes(emailAccount, emailKc).block();
        assertThat(keycloak.user(emailAccount).roles()).containsExactly("CUSTOMER");
    }

    @Test
    @DisplayName("activation moves PROVISIONING to ACTIVE once, with consents, the account event and one outbox row; a repeat writes nothing more")
    void activationIsOnceOnly() {
        ProofRecord proof = whatsappProof("proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        String keycloakUserId = provisioning.createKeycloakUser(accountId).block();
        List<ConsentGrant> consents = List.of(new ConsentGrant("TERMS", "2026-10"), new ConsentGrant("TERMS", "2026-10"));

        AccountProvisioning.Activated activated =
                provisioning.activate(accountId, keycloakUserId, "myticketzm-web", "Chanda", consents).block();
        provisioning.activate(accountId, keycloakUserId, "myticketzm-web", "Chanda", consents).block();
        provisioning.stageOutbox(accountId).block();
        provisioning.stageOutbox(accountId).block();

        assertThat(activated.state()).isEqualTo(AccountState.ACTIVE);
        User account = account(accountId);
        assertThat(account.getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(account.isActive()).isTrue();
        assertThat(account.getKeycloakUserId()).isEqualTo(keycloakUserId);
        assertThat(account.getProvisionedAt()).isEqualTo(NOW);
        assertThat(account.getDisplayName()).isEqualTo("Chanda");
        assertThat(db.count("identity_consents")).as("one grant however often it is sent").isEqualTo(1);
        assertThat(db.all("identity_account_events")).extracting(row -> row.getString("kind"))
                .containsExactlyInAnyOrder("PROVISIONED", "ACTIVATED");
        List<Document> outbox = db.all(AccountFixtures.OUTBOX);
        assertThat(outbox).hasSize(1);
        assertThat(outbox.get(0).getString("eventType")).isEqualTo("identity.AccountActivated");
        assertThat(outbox.get(0).get("payload", Document.class)).containsOnlyKeys("userId");
    }

    @Test
    @DisplayName("an account is not activated without its Keycloak user, and a suspended account is refused")
    void activationRefusals() {
        ProofRecord proof = whatsappProof("proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();

        assertThatThrownBy(() -> provisioning.activate(accountId, " ", "web", null, List.of()).block())
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(account(accountId).getStatus()).isEqualTo(AccountState.PROVISIONING);

        db.template.updateFirst(Query.query(Criteria.where("_id").is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().set("status", AccountState.SUSPENDED), User.class).block();
        assertRefused(() -> provisioning.activate(accountId, "kc-1", "web", null, List.of()).block(), ErrorCode.ACCOUNT_SUSPENDED);
        assertThat(account(accountId).getStatus()).isEqualTo(AccountState.SUSPENDED);
        assertThat(db.count("identity_consents")).isZero();
    }

    @Test
    @DisplayName("a transaction that fails after the status moved leaves the account PROVISIONING - the status, consents, event and row commit together or not at all")
    void activationIsAtomic() {
        ProofRecord proof = whatsappProof("proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        // an outbox row already holding the id the activation will stage makes the last write of the transaction fail
        db.template.insert(new Document("_id", "account-activated:" + accountId).append("status", "SENT"),
                AccountFixtures.OUTBOX).block();

        assertThatThrownBy(() -> provisioning.activate(accountId, "kc-1", "web", null,
                List.of(new ConsentGrant("TERMS", "2026-10"))).block()).isInstanceOf(Exception.class);

        assertThat(account(accountId).getStatus()).isEqualTo(AccountState.PROVISIONING);
        assertThat(account(accountId).getKeycloakUserId()).isNull();
        assertThat(db.count("identity_consents")).isZero();
        assertThat(db.all("identity_account_events")).extracting(row -> row.getString("kind")).containsOnly("PROVISIONED");
    }

    private static void assertRefused(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRefusal.class)
                .extracting(error -> ((DomainRefusal) error).errorCode())
                .isEqualTo(code);
    }
}
