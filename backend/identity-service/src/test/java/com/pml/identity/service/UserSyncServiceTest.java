package com.pml.identity.service;

import com.pml.identity.account.AccountFixtures;
import com.pml.identity.account.AccountProvisioning;
import com.pml.identity.account.AccountService;
import com.pml.identity.account.FakeKeycloak;
import com.pml.identity.account.KeycloakUserAdminPort.KeycloakUserView;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.service.impl.UserSyncServiceImpl;
import com.pml.shared.constants.UserType;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;

import java.lang.reflect.RecordComponent;
import java.time.Clock;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Keycloak users become accounts only where Keycloak is the authority - platform staff - and never
 * on the strength of an attribute a user could edit.
 */
@Tag("L2")
@Tag("ET-IDN-004")
@Tag("ET-IDN-002")
@DisplayName("ET-IDN-004 · user sync adopts or creates staff, trusts realm roles only, never writes contacts, never hard-deletes")
class UserSyncServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-04T10:00:00Z");
    private static final Clock CLOCK = TestClock.frozenAt(NOW);
    private static final KeycloakProperties PROPERTIES = new KeycloakProperties();

    private static AccountFixtures db;

    private FakeKeycloak keycloak;
    private UserSyncServiceImpl sync;

    @BeforeAll
    static void connect() {
        db = new AccountFixtures(MongoReplicaSet.connectionString(), "identity_user_sync", CLOCK);
    }

    @AfterAll
    static void disconnect() {
        db.close();
    }

    @BeforeEach
    void seed() {
        db.reset();
        keycloak = new FakeKeycloak();
        var revocations = new DefaultListableBeanFactory()
                .getBeanProvider(com.pml.identity.security.revocation.MongoRevocationStore.class);
        AccountService accounts = new AccountService(db.template, db.transaction, db.outbox, keycloak, revocations,
                PROPERTIES, CLOCK);
        sync = new UserSyncServiceImpl(db.template, keycloak, accounts, PROPERTIES, CLOCK);
    }

    private KeycloakUserView staff(String id, boolean enabled, String... roles) {
        KeycloakUserView view = new KeycloakUserView(id, "ops@example.org", "ops@example.org", "Ops", "Admin", enabled,
                true, Set.of(roles));
        keycloak.staff.put(id, view);
        return view;
    }

    private User account(String id) {
        return db.template.findById(id, User.class).block();
    }

    @Test
    @DisplayName("a staff user with no account gets one: ACTIVE, roles from realm roles only, linked to its Keycloak user")
    void staffAreCreated() {
        staff("kc-staff-1", true, "ADMIN", "offline_access", "default-roles-myticketzm-admin", "not-a-platform-role");

        User created = sync.syncUser("myticketzm-admin", "kc-staff-1").block();

        assertThat(created.getId()).isEqualTo("kc-staff-1");
        assertThat(created.getKeycloakUserId()).isEqualTo("kc-staff-1");
        assertThat(created.getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(created.isActive()).isTrue();
        assertThat(created.getRoles()).containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.ADMIN);
        assertThat(created.getCreatedVia()).isEqualTo(AccountService.STAFF_SYNC);
        assertThat(created.getEmail()).isEqualTo("ops@example.org");
        assertThat(db.count("identity_contacts")).as("sync never writes contacts").isZero();
    }

    @Test
    @DisplayName("a user attribute cannot grant a role: the Keycloak view carries no attributes at all")
    void attributesAreNotRead() {
        assertThat(Arrays.stream(KeycloakUserView.class.getRecordComponents()).map(RecordComponent::getName))
                .as("roles and accountType attributes are user-editable in some flows; the sync reads realm roles")
                .doesNotContain("attributes", "roles", "accountType")
                .contains("realmRoles");
    }

    @Test
    @DisplayName("syncing twice keeps one account; a disabled staff user is suspended and an enabled one restored")
    void staffStandingFollowsKeycloak() {
        staff("kc-staff-1", true, "FINANCE");
        sync.syncUser("myticketzm-admin", "kc-staff-1").block();
        staff("kc-staff-1", false, "FINANCE");
        sync.syncUser("myticketzm-admin", "kc-staff-1").block();

        User suspended = account("kc-staff-1");
        assertThat(db.count("identity_users")).isEqualTo(1);
        assertThat(suspended.getStatus()).isEqualTo(AccountState.SUSPENDED);
        assertThat(suspended.isActive()).isFalse();

        staff("kc-staff-1", true, "FINANCE", "ADMIN");
        sync.syncUser("myticketzm-admin", "kc-staff-1").block();
        User restored = account("kc-staff-1");
        assertThat(restored.getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(restored.getRoles()).containsExactlyInAnyOrder(UserType.CUSTOMER, UserType.FINANCE, UserType.ADMIN);
    }

    @Test
    @DisplayName("a legacy account takes its roles from Keycloak's realm roles: a role Keycloak no longer holds is dropped")
    void legacyRolesFollowKeycloak() {
        db.template.save(User.builder().id("legacy-1").keycloakUserId("legacy-1").username("legacy")
                .roles(EnumSet.of(UserType.CUSTOMER, UserType.ORGANIZER)).createdAt(NOW).build()).block();
        keycloak.staff.put("legacy-1", new KeycloakUserView("legacy-1", "legacy", null, null, null, true, false, Set.of("CUSTOMER")));

        User synced = sync.syncUser(null, "legacy-1").block();

        assertThat(synced.getRoles()).containsExactly(UserType.CUSTOMER);
    }

    @Test
    @DisplayName("a buyer-realm user nobody recognises is recorded as an orphan and no account is created")
    void buyerOrphansAreRecordedNotCreated() {
        keycloak.staff.put("kc-stray", new KeycloakUserView("kc-stray", "someone", "someone@example.com", null, null, true, true, Set.of("CUSTOMER")));

        assertThat(sync.syncUser(null, "kc-stray").block()).isNull();
        assertThat(db.count("identity_users")).isZero();
        assertThat(db.all("identity_account_events")).extracting(row -> row.getString("kind"))
                .containsExactly("ORPHAN_KEYCLOAK_USER");
    }

    @Test
    @DisplayName("a buyer-realm user whose username is an unlinked account's id is linked to it; an account born of a contact keeps its own roles and standing")
    void buyersAreLinkedNotOverwritten() {
        AccountProvisioning provisioning = new AccountProvisioning(db.template, db.transaction, db.outbox, db.proofs,
                keycloak, AccountFixtures.CRYPTO, CLOCK);
        ProofRecord proof = db.proofFor(ContactType.WHATSAPP, "+260971234567", "proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        keycloak.staff.put("kc-1", new KeycloakUserView("kc-1", accountId, null, null, null, false, false,
                Set.of("CUSTOMER", "ADMIN")));

        User linked = sync.syncUser(null, "kc-1").block();

        assertThat(linked.getId()).isEqualTo(accountId);
        assertThat(linked.getKeycloakUserId()).isEqualTo("kc-1");

        // later events must not turn a buyer into an administrator or switch the account off
        User again = sync.syncUser(null, "kc-1").block();
        assertThat(again.getRoles()).containsExactly(UserType.CUSTOMER);
        assertThat(again.getStatus()).isEqualTo(AccountState.PROVISIONING);
        assertThat(db.count("identity_contacts")).isEqualTo(1);
    }

    @Test
    @DisplayName("a Keycloak delete marks the account DELETED and releases its contacts - the document and the contact rows remain")
    void deleteNeverHardDeletes() {
        AccountProvisioning provisioning = new AccountProvisioning(db.template, db.transaction, db.outbox, db.proofs,
                keycloak, AccountFixtures.CRYPTO, CLOCK);
        ProofRecord proof = db.proofFor(ContactType.WHATSAPP, "+260971234567", "proof-1");
        String accountId = provisioning.claim("proof-1", proof.contactKey(), ContactType.WHATSAPP).block().accountId();
        db.template.updateFirst(Query.query(Criteria.where("_id").is(accountId)),
                new Update().set("keycloakUserId", "kc-1"), User.class).block();

        sync.markDeleted(null, "kc-1").block();
        sync.markDeleted(null, "kc-1").block();

        assertThat(account(accountId)).isNotNull();
        assertThat(account(accountId).getStatus()).isEqualTo(AccountState.DELETED);
        assertThat(account(accountId).isActive()).isFalse();
        assertThat(db.template.findAll(Contact.class).collectList().block())
                .hasSize(1).allSatisfy(contact -> assertThat(contact.getReleasedAt()).isNotNull());
        assertThat(db.all("identity_outbox")).hasSize(1);
        sync.markDeleted(null, "kc-unknown").block();
    }

    @Test
    @DisplayName("a login stamps lastLoginAt; an unknown user is ignored")
    void loginIsRecorded() {
        staff("kc-staff-1", true, "ADMIN");
        sync.syncUser("myticketzm-admin", "kc-staff-1").block();

        assertThat(sync.updateLastLogin("myticketzm-admin", "kc-staff-1").block().getLastLoginAt()).isEqualTo(NOW);
        assertThat(sync.updateLastLogin(null, "nobody").block()).isNull();
    }

    @Test
    @DisplayName("a change that could not be applied is written down as a fact with its kind and reason")
    void failuresAreRecorded() {
        sync.recordFailure(null, "kc-1", "SYNC", "ConnectException").block();

        var rows = db.all("identity_account_events");
        assertThat(rows).hasSize(1);
        assertThat(rows.get(0).getString("kind")).isEqualTo("SYNC_FAILED");
        assertThat(rows.get(0).get("data", org.bson.Document.class)).containsEntry("change", "SYNC").containsEntry("reason", "ConnectException");
    }

    @Test
    @DisplayName("Keycloak holds no such user: nothing is created")
    void goneUsersAreSkipped() {
        assertThat(sync.syncUser("myticketzm-admin", "kc-missing").block()).isNull();
        assertThat(db.count("identity_users")).isZero();
    }
}
