package com.pml.identity.account;

import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.AccountStatus;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.security.revocation.MongoRevocationStore;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.security.revocation.RevocationType;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Suspension, deletion and roles, against a real replica set and a Keycloak that can fail.
 *
 * <p>What is asserted is the order and the pairing: MongoDB first for a suspension (so nothing new
 * can start), Keycloak first for a role (so the token is what changes), and the three status fields
 * always moving together.
 */
@Tag("L2")
@Tag("ET-IDN-004")
@Tag("ET-IDN-002")
@DisplayName("ET-IDN-004 · suspend, unsuspend, delete and roles keep Mongo, Keycloak and the revocation in step")
class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final Clock CLOCK = TestClock.frozenAt(NOW);
    private static final String ADMIN = "admin-1";

    private static AccountFixtures db;

    private FakeKeycloak keycloak;
    private MongoRevocationStore revocations;
    private AccountService accounts;
    private String accountId;
    private String keycloakUserId;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures(MongoReplicaSet.connectionString(), "identity_account_service", CLOCK);
    }

    @AfterAll
    static void disconnect() {
        db.close();
    }

    @BeforeEach
    void seed() {
        db.reset();
        keycloak = new FakeKeycloak();
        revocations = Mockito.mock(MongoRevocationStore.class);
        when(revocations.revoke(any(), anyString(), anyString(), anyString())).thenReturn(Mono.empty());
        when(revocations.lift(any(), anyString())).thenReturn(Mono.empty());
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        factory.registerSingleton("revocations", revocations);
        ObjectProvider<MongoRevocationStore> provider = factory.getBeanProvider(MongoRevocationStore.class);
        accounts = new AccountService(db.template, db.transaction, db.outbox, keycloak, provider,
                new KeycloakProperties(), CLOCK, Retry.fixedDelay(2, Duration.ZERO));

        // a finished buyer: ACTIVE, linked to Keycloak, one WhatsApp contact
        AccountProvisioning provisioning = new AccountProvisioning(db.template, db.transaction, db.outbox, db.proofs,
                keycloak, AccountFixtures.CRYPTO, CLOCK);
        ProofRecord proof = db.proofFor(ContactType.WHATSAPP, "+260971234567", "proof-1");
        accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        keycloakUserId = provisioning.createKeycloakUser(accountId).block();
        provisioning.activate(accountId, keycloakUserId, "web", null, List.of()).block();
        db.template.remove(new org.springframework.data.mongodb.core.query.Query(), "identity_outbox").block();
        keycloak.calls.clear();
        keycloak.realmsUsed.clear();
    }

    private User account() {
        return db.template.findById(accountId, User.class).block();
    }

    @Test
    @DisplayName("suspending sets the state, the legacy status and the flag together, and the change survives a reload")
    void suspensionMovesAllThreeFields() {
        accounts.suspend(accountId, "chargeback abuse", ADMIN).block();

        User reloaded = account();
        assertThat(reloaded.getStatus()).isEqualTo(AccountState.SUSPENDED);
        assertThat(reloaded.getAccountStatus()).isEqualTo(AccountStatus.SUSPENDED);
        assertThat(reloaded.isActive())
                .as("an account suspended in the enum and still `active` in the flag appears in every list that filters on the flag")
                .isFalse();
        assertThat(reloaded.getUpdatedBy()).isEqualTo(ADMIN);
    }

    @Test
    @DisplayName("a suspension revokes the user's tokens, disables the Keycloak user by id and ends its sessions; audit and outbox are written")
    void suspensionIsEnforced() {
        accounts.suspend(accountId, "chargeback abuse", ADMIN).block();

        verify(revocations).revoke(eq(RevocationType.USER), eq(keycloakUserId), anyString(), eq(ADMIN));
        assertThat(keycloak.user(accountId).enabled()).isFalse();
        assertThat(keycloak.sessionsEnded).containsExactly(keycloakUserId);
        assertThat(db.all("identity_account_events")).extracting(row -> row.getString("kind")).contains("SUSPENDED");
        assertThat(db.all("identity_audit_logs")).extracting(row -> row.getString("action")).containsExactly("USER_SUSPENDED");
        List<Document> outbox = db.all("identity_outbox");
        assertThat(outbox).extracting(row -> row.getString("eventType")).containsExactly("identity.AccountSuspended");
        assertThat(outbox.get(0).get("payload", Document.class)).containsOnlyKeys("userId");
    }

    @Test
    @DisplayName("a suspension is repeatable: the second call re-enforces Keycloak and writes no second fact")
    void suspensionIsRepeatable() {
        accounts.suspend(accountId, "first", ADMIN).block();
        accounts.suspend(accountId, "second", ADMIN).block();

        assertThat(db.all("identity_outbox")).hasSize(1);
        assertThat(db.all("identity_audit_logs")).hasSize(1);
        assertThat(keycloak.callsSnapshot()).filteredOn("setEnabled:false"::equals).hasSize(2);
    }

    @Test
    @DisplayName("Keycloak down during a suspension: the status has already committed and the error reaches the caller; calling again finishes the job")
    void keycloakDownDoesNotUndoTheSuspension() {
        keycloak.down = true;

        assertThatThrownBy(() -> accounts.suspend(accountId, "abuse", ADMIN).block()).isInstanceOf(RuntimeException.class);
        assertThat(account().getStatus()).as("Mongo wins: no new sign-in can start").isEqualTo(AccountState.SUSPENDED);
        assertThat(keycloak.user(accountId).enabled()).isTrue();

        keycloak.down = false;
        accounts.suspend(accountId, "abuse", ADMIN).block();
        assertThat(keycloak.user(accountId).enabled()).isFalse();
    }

    @Test
    @DisplayName("unsuspending puts the account back in all three fields, enables Keycloak and lifts the revocation")
    void unsuspensionIsTheInverse() {
        accounts.suspend(accountId, "abuse", ADMIN).block();
        accounts.unsuspend(accountId, ADMIN).block();

        User restored = account();
        assertThat(restored.getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(restored.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(restored.isActive()).isTrue();
        assertThat(keycloak.user(accountId).enabled()).isTrue();
        verify(revocations).lift(RevocationType.USER, keycloakUserId);
    }

    @Test
    @DisplayName("a merged or deleted account cannot be suspended or unsuspended")
    void gonePeopleAreRefused() {
        db.template.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().set("status", AccountState.MERGED), User.class).block();

        assertRefused(() -> accounts.suspend(accountId, "x", ADMIN).block(), ErrorCode.ACCOUNT_NOT_ACTIVE);
        assertRefused(() -> accounts.unsuspend(accountId, ADMIN).block(), ErrorCode.ACCOUNT_NOT_ACTIVE);
        assertRefused(() -> accounts.suspend("nobody", "x", ADMIN).block(), ErrorCode.USER_UNKNOWN);
    }

    @Test
    @DisplayName("deleting marks the account DELETED, releases its contacts, disables Keycloak without deleting the user, and frees the number")
    void deletionReleasesAndNeverDeletes() {
        accounts.delete(accountId, ADMIN, true).block();

        User deleted = account();
        assertThat(deleted.getStatus()).isEqualTo(AccountState.DELETED);
        assertThat(deleted.isActive()).isFalse();
        assertThat(db.template.findAll(Contact.class).collectList().block())
                .allSatisfy(contact -> assertThat(contact.getReleasedAt()).isEqualTo(NOW));
        assertThat(keycloak.user(accountId)).as("disabled, not deleted").isNotNull();
        assertThat(keycloak.user(accountId).enabled()).isFalse();
        verify(revocations).revoke(eq(RevocationType.USER), eq(keycloakUserId), anyString(), eq(ADMIN));
        assertThat(db.all("identity_outbox")).extracting(row -> row.getString("eventType"))
                .containsExactly("identity.AccountDeleted");

        // the number can be claimed again, by a different account
        AccountProvisioning provisioning = new AccountProvisioning(db.template, db.transaction, db.outbox, db.proofs,
                keycloak, AccountFixtures.CRYPTO, CLOCK);
        ProofRecord again = db.proofFor(ContactType.WHATSAPP, "+260971234567", "proof-2");
        assertThat(provisioning.claim("proof-2", again.contactKey(), ContactType.WHATSAPP).block().accountId())
                .isNotEqualTo(accountId);
        assertThat(db.count("identity_users")).isEqualTo(2);
    }

    @Test
    @DisplayName("a delete Keycloak reported itself touches no Keycloak user, only the revocation; deleting twice is one deletion")
    void keycloakReportedDeletion() {
        accounts.delete(accountId, "keycloak", false).block();
        accounts.delete(accountId, "keycloak", false).block();

        assertThat(account().getStatus()).isEqualTo(AccountState.DELETED);
        assertThat(keycloak.calls).as("nothing to disable, it is gone").isEmpty();
        assertThat(db.all("identity_outbox")).hasSize(1);
    }

    @Test
    @DisplayName("a role is granted in Keycloak first; with Keycloak down the stored copy does not move")
    void rolesGoToKeycloakFirst() {
        keycloak.down = true;
        assertThatThrownBy(() -> accounts.addRole(accountId, UserType.ORGANIZER, ADMIN).block()).isInstanceOf(RuntimeException.class);
        assertThat(account().getRoles()).containsExactly(UserType.CUSTOMER);

        keycloak.down = false;
        User updated = accounts.addRole(accountId, UserType.ORGANIZER, ADMIN).block();

        assertThat(updated.getRoles()).containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ORGANIZER);
        assertThat(keycloak.user(accountId).roles()).contains("ORGANIZER");
        assertThat(db.all("identity_audit_logs")).extracting(row -> row.getString("action")).contains("ROLE_GRANT");
    }

    @Test
    @DisplayName("removing and replacing roles mirror to Keycloak; CUSTOMER cannot be removed")
    void roleRemovalAndReplacement() {
        accounts.addRole(accountId, UserType.ORGANIZER, ADMIN).block();
        accounts.removeRole(accountId, UserType.ORGANIZER, ADMIN).block();
        assertThat(keycloak.user(accountId).roles()).doesNotContain("ORGANIZER");
        assertThat(account().getRoles()).containsExactly(UserType.CUSTOMER);

        accounts.setRoles(accountId, EnumSet.of(UserType.CUSTOMER, UserType.FINANCE), ADMIN).block();
        assertThat(keycloak.user(accountId).roles()).contains("FINANCE");

        assertThatThrownBy(() -> accounts.removeRole(accountId, UserType.CUSTOMER, ADMIN).block())
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> accounts.setRoles(accountId, EnumSet.of(UserType.ORGANIZER), ADMIN).block())
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("an account with no Keycloak user yet cannot be given a role nothing would enforce")
    void rolesNeedAKeycloakUser() {
        db.template.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().unset("keycloakUserId"), User.class).block();

        assertRefused(() -> accounts.addRole(accountId, UserType.ORGANIZER, ADMIN).block(), ErrorCode.ACCOUNT_NOT_ACTIVE);
    }

    @Test
    @DisplayName("staff accounts are changed in the staff realm, buyers in the buyer realm")
    void theRealmFollowsTheAccount() {
        accounts.addRole(accountId, UserType.ORGANIZER, ADMIN).block();
        assertThat(keycloak.realmsUsed).containsOnly("null");

        db.template.updateFirst(org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("_id").is(accountId)),
                new org.springframework.data.mongodb.core.query.Update().set("createdVia", AccountService.STAFF_SYNC), User.class).block();
        keycloak.realmsUsed.clear();
        accounts.suspend(accountId, "left the company", ADMIN).block();

        assertThat(keycloak.realmsUsed).containsOnly(new KeycloakProperties().getStaffRealm());
    }

    @Test
    @DisplayName("a token's subject finds the account by id or by its linked Keycloak user")
    void subjectLookup() {
        assertThat(accounts.bySubject(accountId).block().getId()).isEqualTo(accountId);
        assertThat(accounts.bySubject(keycloakUserId).block().getId()).isEqualTo(accountId);
        assertThat(accounts.bySubject("someone-else").block()).isNull();
        verify(revocations, never()).revoke(any(), anyString(), anyString(), anyString());
    }

    private static void assertRefused(Runnable action, ErrorCode code) {
        assertThatThrownBy(action::run)
                .isInstanceOf(DomainRefusal.class)
                .extracting(error -> ((DomainRefusal) error).errorCode())
                .isEqualTo(code);
    }
}
