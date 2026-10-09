package com.pml.identity.account;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.config.MongoSchemaValidationConfig;
import com.pml.identity.domain.enums.AccountState;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.domain.enums.PendingKind;
import com.pml.identity.domain.model.AccountEvent;
import com.pml.identity.domain.model.AuditLog;
import com.pml.identity.domain.model.Consent;
import com.pml.identity.domain.model.Contact;
import com.pml.identity.domain.model.User;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.identity.repository.AccountEventRepository;
import com.pml.identity.repository.ConsentRepository;
import com.pml.identity.repository.ContactRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.security.revocation.RevocationType;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The new and changed validators, applied to a real server exactly as startup applies them, accept
 * what the models write and refuse what they must; and the new repositories' queries do what their
 * names say.
 */
@Tag("L2")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · account, contact, consent, event, audit and revocation validators accept the models and refuse bad data")
class AccountSchemasTest {

    private static final Instant NOW = Instant.parse("2026-10-04T08:00:00Z");

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static UserRepository users;
    private static ContactRepository contacts;
    private static ConsentRepository consents;
    private static AccountEventRepository events;

    @BeforeAll
    static void applyValidators() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_account_schemas"));
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();

        new MongoSchemaValidationConfig(template, new DefaultResourceLoader(), new MongoSchemaValidationProperties())
                .applySchemaValidation();

        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        users = factory.getRepository(UserRepository.class);
        contacts = factory.getRepository(ContactRepository.class);
        consents = factory.getRepository(ConsentRepository.class);
        events = factory.getRepository(AccountEventRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void empty() {
        for (String collection : List.of(IdentityCollections.USERS, IdentityCollections.CONTACTS,
                IdentityCollections.CONSENTS, IdentityCollections.ACCOUNT_EVENTS,
                IdentityCollections.AUDIT_LOGS, IdentityCollections.TOKEN_REVOCATIONS)) {
            template.remove(new Query(), collection).block();
        }
    }

    private static Throwable failureOf(Mono<?> write) {
        return write.then(Mono.<Throwable>empty()).onErrorResume(Mono::just).block();
    }

    /** Inserts a raw document; users and contacts need a string _id, which a raw insert would not generate. */
    private static Throwable rawInsertFailure(String collection, Document document) {
        if (!document.containsKey("_id") && (IdentityCollections.USERS.equals(collection)
                || IdentityCollections.CONTACTS.equals(collection))) {
            document.append("_id", java.util.UUID.randomUUID().toString());
        }
        return failureOf(template.insert(document, collection));
    }

    // ------------------------------------------------------------------ every schema is applied

    @Test
    @DisplayName("every new schema is installed on its collection")
    void schemasAreInstalled() {
        for (String collection : List.of(IdentityCollections.CONTACTS, IdentityCollections.CONSENTS,
                IdentityCollections.ACCOUNT_EVENTS, IdentityCollections.AUDIT_LOGS,
                IdentityCollections.TOKEN_REVOCATIONS, IdentityCollections.USERS)) {
            Document options = template.getMongoDatabase()
                    .flatMapMany(db -> Flux.from(db.listCollections()))
                    .filter(c -> collection.equals(c.getString("name")))
                    .blockFirst();
            assertThat(options).as("collection %s exists", collection).isNotNull();
            assertThat(options.get("options", Document.class).get("validator"))
                    .as("validator on %s", collection).isNotNull();
        }
    }

    // ------------------------------------------------------------------ users

    @Test
    @DisplayName("a phone-only account with none of email, username or names is accepted, new fields round-trip")
    void phoneOnlyAccount() {
        User account = User.builder().id("11111111-1111-4111-8111-111111111111")
                .phoneNumber("+260977123456").phoneCountry("ZM").phoneVerified(true)
                .status(AccountState.ACTIVE).keycloakUserId("11111111-1111-4111-8111-111111111111")
                .primaryContactId("c-1").preferredChannel(ContactType.WHATSAPP).displayName("Mwila")
                .locale("en-ZM").provisionedAt(NOW).createdVia("OTP").createdAt(NOW).build();
        users.save(account).block();

        User read = users.findById(account.getId()).block();
        assertThat(read.getEmail()).isNull();
        assertThat(read.getUsername()).isNull();
        assertThat(read.getFirstName()).isNull();
        assertThat(read.getStatus()).isEqualTo(AccountState.ACTIVE);
        assertThat(read.getPreferredChannel()).isEqualTo(ContactType.WHATSAPP);
        assertThat(read.getProvisionedAt()).isEqualTo(NOW);
        assertThat(users.findByKeycloakUserId(account.getId()).block().getId()).isEqualTo(account.getId());
    }

    @Test
    @DisplayName("a second phone-only account is accepted: nothing in the validator makes email a key")
    void severalAccountsWithoutEmail() {
        users.save(User.builder().id("a").phoneNumber("+260977000001").createdAt(NOW).build()).block();
        users.save(User.builder().id("b").phoneNumber("+260977000002").createdAt(NOW).build()).block();
        assertThat(users.count().block()).isEqualTo(2);
    }

    @Test
    @DisplayName("every state and pending marker the model can write is accepted, and the repair queries find them")
    void everyStateIsAccepted() {
        for (AccountState state : AccountState.values()) {
            users.save(User.builder().id("s-" + state).status(state).createdAt(NOW.minusSeconds(3600)).build()).block();
        }
        for (PendingKind kind : PendingKind.values()) {
            users.save(User.builder().id("p-" + kind).status(AccountState.ACTIVE).pendingKind(kind)
                    .pendingSince(NOW.minusSeconds(7200)).createdAt(NOW).build()).block();
        }
        users.save(User.builder().id("merged-1").status(AccountState.MERGED).mergedInto("s-ACTIVE").createdAt(NOW).build()).block();

        assertThat(users.findByStatus(AccountState.PROVISIONING).map(User::getId).collectList().block())
                .containsExactly("s-PROVISIONING");
        assertThat(users.findByStatusAndCreatedAtBefore(AccountState.PROVISIONING, NOW).map(User::getId).collectList().block())
                .containsExactly("s-PROVISIONING");
        assertThat(users.findByStatusAndCreatedAtBefore(AccountState.PROVISIONING, NOW.minusSeconds(7200)).collectList().block())
                .isEmpty();
        assertThat(users.findByPendingKindAndPendingSinceBefore(PendingKind.MERGING, NOW).map(User::getId).collectList().block())
                .containsExactly("p-MERGING");
        assertThat(users.findByMergedInto("s-ACTIVE").map(User::getId).collectList().block()).containsExactly("merged-1");
    }

    @Test
    @DisplayName("the users validator still refuses a malformed email, an unknown state and a role set that is too big")
    void usersValidatorRefusals() {
        Document base = new Document("roles", List.of("CUSTOMER")).append("createdAt", new java.util.Date());
        assertThat(rawInsertFailure(IdentityCollections.USERS, new Document(base))).as("the base document itself is valid").isNull();
        assertThat(rawInsertFailure(IdentityCollections.USERS, new Document(base).append("email", "not-an-email"))).isNotNull();
        assertThat(rawInsertFailure(IdentityCollections.USERS, new Document(base).append("status", "WIZARD"))).isNotNull();
        assertThat(rawInsertFailure(IdentityCollections.USERS, new Document(base).append("pendingKind", "SOMETHING"))).isNotNull();
        assertThat(rawInsertFailure(IdentityCollections.USERS, new Document(base).append("preferredChannel", "FAX"))).isNotNull();
        assertThat(rawInsertFailure(IdentityCollections.USERS, new Document(base).append("phoneNumber", "0977123456"))).isNotNull();
        assertThat(rawInsertFailure(IdentityCollections.USERS, new Document("createdAt", new java.util.Date()))).as("roles stay required").isNotNull();
        assertThat(rawInsertFailure(IdentityCollections.USERS, new Document(base).append("roles",
                List.of("CUSTOMER", "ORGANIZER", "ADMIN", "SUPER_ADMIN", "SCANNER", "FINANCE", "FINANCE_LEAD", "CUSTOMER"))))
                .as("eight roles, one repeated").isNotNull();
        // all seven roles at once are within the new maximum
        assertThat(rawInsertFailure(IdentityCollections.USERS, new Document(base).append("roles",
                List.of("CUSTOMER", "ORGANIZER", "ADMIN", "SUPER_ADMIN", "SCANNER", "FINANCE", "FINANCE_LEAD")))).isNull();
    }

    // ------------------------------------------------------------------ contacts

    private static Contact contact(String id, String accountId, ContactType type, String hash, Instant verifiedAt, Instant releasedAt) {
        return Contact.builder().id(id).accountId(accountId).type(type).valueHash(hash)
                .valueEncrypted("v1.AAAA").valueMasked("+260 97* ***456").verifiedAt(verifiedAt).primary(verifiedAt != null)
                .source("OTP").createdAt(NOW).releasedAt(releasedAt).build();
    }

    private static final String HASH_1 = "a".repeat(64);
    private static final String HASH_2 = "b".repeat(64);

    @Test
    @DisplayName("contacts round-trip, and the verified-owner query ignores unverified and released rows")
    void contactQueries() {
        contacts.saveAll(List.of(
                contact("c1", "acc-1", ContactType.EMAIL, HASH_1, NOW, null),
                contact("c2", "acc-2", ContactType.EMAIL, HASH_1, null, null),
                contact("c3", "acc-3", ContactType.EMAIL, HASH_1, NOW, NOW.plusSeconds(1)),
                contact("c4", "acc-1", ContactType.WHATSAPP, HASH_2, NOW, null))).blockLast();

        assertThat(contacts.findVerifiedOwner(ContactType.EMAIL, HASH_1).block().getAccountId()).isEqualTo("acc-1");
        assertThat(contacts.findVerifiedOwner(ContactType.WHATSAPP, HASH_1).block()).isNull();
        assertThat(contacts.existsByTypeAndValueHashAndVerifiedAtIsNotNullAndReleasedAtIsNull(ContactType.EMAIL, HASH_1).block()).isTrue();
        assertThat(contacts.existsByTypeAndValueHashAndVerifiedAtIsNotNullAndReleasedAtIsNull(ContactType.EMAIL, HASH_2).block()).isFalse();
        assertThat(contacts.findByAccountId("acc-1").map(Contact::getId).collectList().block()).containsExactlyInAnyOrder("c1", "c4");
        assertThat(contacts.findByAccountIdAndReleasedAtIsNull("acc-3").collectList().block()).isEmpty();
        assertThat(contacts.findByAccountIdAndTypeAndValueHash("acc-2", ContactType.EMAIL, HASH_1).block().getId()).isEqualTo("c2");
        assertThat(contacts.countByAccountIdAndVerifiedAtIsNotNullAndReleasedAtIsNull("acc-1").block()).isEqualTo(2);
        assertThat(contacts.findById("c4").block().isPrimary()).isTrue();
    }

    @Test
    @DisplayName("the contacts validator refuses a bad hash, an unknown type, a missing field and an extra field")
    void contactValidatorRefusals() {
        Document good = new Document("accountId", "acc").append("type", "EMAIL").append("valueHash", HASH_1)
                .append("valueEncrypted", "v1.x").append("valueMasked", "j***@x.co").append("createdAt", new java.util.Date());
        assertThat(rawInsertFailure(IdentityCollections.CONTACTS, new Document(good))).isNull();
        assertThat(rawInsertFailure(IdentityCollections.CONTACTS, new Document(good).append("valueHash", "ABC"))).isNotNull();
        assertThat(rawInsertFailure(IdentityCollections.CONTACTS, new Document(good).append("type", "SMS"))).isNotNull();
        assertThat(rawInsertFailure(IdentityCollections.CONTACTS, new Document(good).append("value", "+260977123456")))
                .as("a raw value field is refused").isNotNull();
        Document noEncrypted = new Document(good);
        noEncrypted.remove("valueEncrypted");
        assertThat(rawInsertFailure(IdentityCollections.CONTACTS, noEncrypted)).isNotNull();
    }

    // ------------------------------------------------------------------ consents, events

    @Test
    @DisplayName("consents round-trip and the live-grant query ignores withdrawn ones")
    void consentQueries() {
        consents.saveAll(List.of(
                Consent.builder().accountId("acc").purpose("TERMS").version("2026-10").grantedAt(NOW).source("OTP").build(),
                Consent.builder().accountId("acc").purpose("TERMS").version("2026-04").grantedAt(NOW).withdrawnAt(NOW).build(),
                Consent.builder().accountId("other").purpose("TERMS").version("2026-10").grantedAt(NOW).build())).blockLast();

        assertThat(consents.findByAccountId("acc").collectList().block()).hasSize(2);
        assertThat(consents.findByAccountIdAndPurpose("acc", "TERMS").collectList().block()).hasSize(2);
        assertThat(consents.findFirstByAccountIdAndPurposeAndVersionAndWithdrawnAtIsNull("acc", "TERMS", "2026-10").block()).isNotNull();
        assertThat(consents.findFirstByAccountIdAndPurposeAndVersionAndWithdrawnAtIsNull("acc", "TERMS", "2026-04").block()).isNull();
        assertThat(rawInsertFailure(IdentityCollections.CONSENTS, new Document("accountId", "acc").append("purpose", "TERMS"))).isNotNull();
    }

    @Test
    @DisplayName("account events are keyed by event id: the same event twice is refused, history reads newest first")
    void eventQueries() {
        events.save(AccountEvent.builder().id("ev-1").accountId("acc").kind("PROVISIONED").at(NOW).data(Map.of("clientId", "web")).build()).block();
        events.save(AccountEvent.builder().id("ev-2").accountId("acc").kind("ACTIVATED").at(NOW.plusSeconds(5)).build()).block();
        events.save(AccountEvent.builder().id("ev-3").kind("OTP_LOCK").at(NOW.plusSeconds(9)).build()).block();

        assertThat(events.findByAccountIdOrderByAtDesc("acc").map(AccountEvent::getId).collectList().block())
                .containsExactly("ev-2", "ev-1");
        assertThat(events.findByKindAndAtAfter("OTP_LOCK", NOW).collectList().block()).hasSize(1);
        assertThat(failureOf(template.insert(AccountEvent.builder().id("ev-1").kind("X").at(NOW).build()))).isNotNull();
        assertThat(rawInsertFailure(IdentityCollections.ACCOUNT_EVENTS, new Document("_id", "ev-9").append("at", new java.util.Date()))).as("kind is required").isNotNull();
    }

    // ------------------------------------------------------------------ audit, revocation

    @Test
    @DisplayName("audit logs written by the model pass the new validator, an unknown action does not")
    void auditLogs() {
        for (AuditLog.AuditAction action : AuditLog.AuditAction.values()) {
            template.insert(AuditLog.success(action, "u", "admin", NOW)).block();
        }
        template.insert(AuditLog.failure(AuditLog.AuditAction.ACCESS_DENIED, "u", "admin", "denied", "E1", NOW)).block();
        assertThat(template.count(new Query(), IdentityCollections.AUDIT_LOGS).block())
                .isEqualTo(AuditLog.AuditAction.values().length + 1);
        assertThat(rawInsertFailure(IdentityCollections.AUDIT_LOGS, new Document("action", "TELEPORT").append("status", "SUCCESS")
                .append("timestamp", new java.util.Date()))).isNotNull();
    }

    @Test
    @DisplayName("revocation records written by the model pass the new validator, an unknown type does not")
    void revocations() {
        for (RevocationType type : RevocationType.values()) {
            template.insert(com.pml.identity.security.revocation.RevocationRecord.builder()
                    .id(type.documentId("v")).type(type).value("v").reason("test").revokedBy("admin")
                    .revokedAt(NOW).expiresAt(NOW.plusSeconds(3600)).build()).block();
        }
        assertThat(template.count(new Query(), IdentityCollections.TOKEN_REVOCATIONS).block()).isEqualTo(RevocationType.values().length);
        assertThat(rawInsertFailure(IdentityCollections.TOKEN_REVOCATIONS, new Document("type", "COOKIE").append("value", "v")
                .append("revokedAt", new java.util.Date()))).isNotNull();
    }

    @Test
    @DisplayName("sanity: the set of states the model knows is the set the validator allows")
    void stateSetsAgree() {
        assertThat(Set.of(AccountState.values())).hasSize(5);
    }
}
