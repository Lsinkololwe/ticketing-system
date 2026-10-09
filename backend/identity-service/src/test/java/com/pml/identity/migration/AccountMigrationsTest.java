package com.pml.identity.migration;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.config.IdentityIndexInitializer;
import com.pml.identity.domain.enums.ContactType;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.identity.security.ContactCrypto;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.security.FieldEncryptionService;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * The ET-IDN-004 data migrations against a real replica set, with accounts shaped like the ones in
 * production: phone-only accounts holding an {@code @phone.local} placeholder, an account whose id
 * is not a Keycloak id, emails that differ only by case, and a clean account.
 */
@Tag("L2")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · account and contact migrations are idempotent, resumable, and never merge")
class AccountMigrationsTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-04T09:00:00Z"), ZoneOffset.UTC);
    private static final Date CREATED = Date.from(Instant.parse("2026-01-10T10:00:00Z"));
    private static final Date UPDATED = Date.from(Instant.parse("2026-03-01T10:00:00Z"));

    private static final String CLEAN = "00000000-0000-4000-8000-000000000001";
    private static final String PHONE_ONLY = "00000000-0000-4000-8000-000000000002";
    private static final String PHONE_PENDING = "00000000-0000-4000-8000-000000000003";
    private static final String ORPHAN = "ba-9f8e7d6c5b4a";
    private static final String DUP_A = "00000000-0000-4000-8000-0000000000a1";
    private static final String DUP_B = "00000000-0000-4000-8000-0000000000b2";
    private static final String LOCKED = "00000000-0000-4000-8000-000000000006";
    private static final String DELETING = "00000000-0000-4000-8000-000000000007";
    private static final String REAL_USER = "00000000-0000-4000-8000-000000000008";
    private static final String INACTIVE = "00000000-0000-4000-8000-000000000009";

    private static final String HASH_KEY = "account-migrations-test-hash-key";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static ContactHasher hasher;
    private static ContactCrypto crypto;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_account_migrations"));
        hasher = new ContactHasher(HASH_KEY);
        crypto = new ContactCrypto(new FieldEncryptionService(FieldEncryptionService.generateKey(), "v1"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();

        insert(user(CLEAN).append("username", "clean").append("email", "Clean@Example.com").append("emailVerified", true)
                .append("firstName", "Clean").append("lastName", "Person").append("accountStatus", "ACTIVE")
                .append("active", true).append("locked", false).append("phoneNumber", "+260977000001").append("phoneVerified", true));
        insert(user(PHONE_ONLY).append("username", "user_77000002").append("email", "260977000002@phone.local")
                .append("emailVerified", true).append("firstName", "Phone").append("lastName", "User")
                .append("accountStatus", "ACTIVE").append("active", true).append("phoneNumber", "+260977000002")
                .append("phoneVerified", true));
        insert(user(PHONE_PENDING).append("username", "user_77000003").append("email", "260977000003@Phone.Local")
                .append("firstName", "Phone").append("lastName", "User").append("accountStatus", "PENDING_VERIFICATION")
                .append("phoneNumber", "0977000003").append("phoneVerified", false));
        insert(user(ORPHAN).append("email", "orphan@example.com").append("emailVerified", false)
                .append("accountStatus", "ACTIVE").append("firstName", "Better").append("lastName", "Auth"));
        insert(user(DUP_A).append("email", "Dup@Example.com").append("emailVerified", true).append("accountStatus", "ACTIVE"));
        insert(user(DUP_B).append("email", "dup@example.com").append("emailVerified", true).append("accountStatus", "ACTIVE"));
        insert(user(LOCKED).append("email", "locked@example.com").append("emailVerified", true)
                .append("accountStatus", "ACTIVE").append("locked", true));
        insert(user(DELETING).append("email", "bye@example.com").append("emailVerified", true)
                .append("accountStatus", "PENDING_DELETION"));
        insert(user(REAL_USER).append("firstName", "Ada").append("lastName", "User").append("email", "ada@example.com")
                .append("accountStatus", "ACTIVE").append("active", true));
        insert(user(INACTIVE).append("email", "gone@example.com").append("accountStatus", "INACTIVE").append("active", false));
    }

    // ------------------------------------------------------------------ helpers

    private static Document user(String id) {
        return new Document("_id", id).append("_class", "users").append("roles", List.of("CUSTOMER"))
                .append("createdAt", CREATED).append("updatedAt", UPDATED);
    }

    private static void insert(Document user) {
        template.insert(user, IdentityCollections.USERS).block();
    }

    private static Document userDoc(String id) {
        return template.getCollection(IdentityCollections.USERS)
                .flatMap(c -> Mono.from(c.find(new Document("_id", id)).first())).block();
    }

    private static List<Document> all(String collection) {
        return template.getCollection(collection).flatMapMany(c -> Flux.from(c.find())).collectList().block();
    }

    private static List<Document> contactsOf(String accountId) {
        return all(IdentityCollections.CONTACTS).stream().filter(c -> accountId.equals(c.getString("accountId"))).toList();
    }

    private static Document contactOf(String accountId, ContactType type) {
        return contactsOf(accountId).stream().filter(c -> type.name().equals(c.getString("type"))).findFirst().orElse(null);
    }

    private final AccountPreflightReportMigrationService preflight = new AccountPreflightReportMigrationService(template());
    private final UserAccountFieldsBackfillMigrationService fields = new UserAccountFieldsBackfillMigrationService(template());
    private final UserLegacyIndexRetirementMigrationService legacyIndexes = new UserLegacyIndexRetirementMigrationService(template());
    private final ContactsUniqueIndexMigrationService uniqueIndex = new ContactsUniqueIndexMigrationService(template(), CLOCK);

    private static ReactiveMongoTemplate template() {
        return template;
    }

    private static ContactsBackfillMigrationService contactsBackfill(int batchSize, boolean dryRun) {
        return new ContactsBackfillMigrationService(template, hasher, crypto, CLOCK, batchSize, dryRun);
    }

    // ------------------------------------------------------------------ legacy indexes

    @Test
    @DisplayName("users-legacy-indexes-drop removes the plain unique email index and the userType index, keeps the partial one, and is idempotent")
    void legacyIndexesDrop() {
        template.getCollection(IdentityCollections.USERS).flatMap(c -> Mono.from(c.createIndex(new Document("email", 1),
                new com.mongodb.client.model.IndexOptions().name("email").unique(true)))).block();
        template.getCollection(IdentityCollections.USERS).flatMap(c -> Mono.from(c.createIndex(
                new Document("userType", 1).append("accountStatus", 1),
                new com.mongodb.client.model.IndexOptions().name("idx_userType_accountStatus")))).block();
        template.getCollection(IdentityCollections.USERS).flatMap(c -> Mono.from(c.createIndex(new Document("email", 1),
                new com.mongodb.client.model.IndexOptions().name("idx_email").unique(true)
                        .partialFilterExpression(new Document("email", new Document("$type", "string")))))).block();

        assertThat(legacyIndexes.migrate().block()).contains("email").contains("idx_userType_accountStatus");
        assertThat(indexNames(IdentityCollections.USERS)).contains("idx_email").doesNotContain("email", "idx_userType_accountStatus");
        assertThat(legacyIndexes.migrate().block()).isEqualTo("nothing to drop");
    }

    private static List<String> indexNames(String collection) {
        return template.getCollection(collection).flatMapMany(c -> Flux.from(c.listIndexes()))
                .map(i -> i.getString("name")).collectList().block();
    }

    // ------------------------------------------------------------------ preflight

    @Test
    @DisplayName("account-preflight-report counts the problems, changes nothing, and its report holds no values")
    void preflightReport() {
        // two different numbers that share their last eight digits
        insert(user("00000000-0000-4000-8000-0000000000c1").append("phoneNumber", "+99977123456"));
        insert(user("00000000-0000-4000-8000-0000000000c2").append("phoneNumber", "+88877123456"));
        // the same number stored two ways: the same person on two accounts
        insert(user("00000000-0000-4000-8000-0000000000c3").append("phoneNumber", "0977555555"));
        insert(user("00000000-0000-4000-8000-0000000000c4").append("phoneNumber", "+260977555555"));
        List<Document> before = all(IdentityCollections.USERS);

        AccountPreflightReportMigrationService.Report report = preflight.migrate().block();

        assertThat(report.users()).isEqualTo(14);
        assertThat(report.placeholderEmails()).isEqualTo(2);
        assertThat(report.caseCollidingEmailGroups()).isEqualTo(1);
        assertThat(report.orphans()).isEqualTo(1);
        assertThat(report.phoneDerivedUsernameCollisions()).isEqualTo(1);
        assertThat(report.samePersonOnSeveralAccounts()).isEqualTo(2); // dup email + dup phone
        assertThat(report.toString()).doesNotContain("@").doesNotContain("phone.local").doesNotContain("0000-4000");
        assertThat(all(IdentityCollections.USERS)).isEqualTo(before);
        assertThat(preflight.migrate().block()).isEqualTo(report);
    }

    // ------------------------------------------------------------------ account fields

    @Test
    @DisplayName("users-account-fields-backfill sets status, keycloakUserId and provisionedAt, handles orphans, blanks placeholder names, keeps old fields")
    void accountFieldsBackfill() {
        UserAccountFieldsBackfillMigrationService.Result result = fields.migrate().block();

        assertThat(result.orphans()).isEqualTo(1);
        assertThat(result.accounts()).isEqualTo(9);
        assertThat(result.placeholderNames()).isEqualTo(2);

        Document clean = userDoc(CLEAN);
        assertThat(clean.getString("status")).isEqualTo("ACTIVE");
        assertThat(clean.getString("keycloakUserId")).isEqualTo(CLEAN);
        assertThat(clean.getDate("provisionedAt")).isEqualTo(CREATED);
        assertThat(clean.getString("accountStatus")).as("old fields stay").isEqualTo("ACTIVE");
        assertThat(clean.getString("firstName")).isEqualTo("Clean");

        Document phoneOnly = userDoc(PHONE_ONLY);
        assertThat(phoneOnly.getString("status")).isEqualTo("ACTIVE");
        assertThat(phoneOnly).doesNotContainKeys("firstName", "lastName");
        assertThat(phoneOnly.getString("email")).as("the placeholder email is left for later cleanup").isEqualTo("260977000002@phone.local");

        Document pending = userDoc(PHONE_PENDING);
        assertThat(pending.getString("status")).isEqualTo("PROVISIONING");
        assertThat(pending.getString("keycloakUserId")).isEqualTo(PHONE_PENDING);
        assertThat(pending).doesNotContainKey("provisionedAt");

        Document orphan = userDoc(ORPHAN);
        assertThat(orphan.getString("status")).isEqualTo("PROVISIONING");
        assertThat(orphan.get("keycloakUserId")).isNull();
        assertThat(orphan).doesNotContainKey("provisionedAt");
        assertThat(orphan.getString("firstName")).as("only the Phone/User pair is blanked").isEqualTo("Better");

        assertThat(userDoc(LOCKED).getString("status")).isEqualTo("SUSPENDED");
        assertThat(userDoc(INACTIVE).getString("status")).isEqualTo("SUSPENDED");
        Document deleting = userDoc(DELETING);
        assertThat(deleting.getString("status")).isEqualTo("ACTIVE");
        assertThat(deleting.getString("pendingKind")).isEqualTo("DELETION_REQUESTED");
        assertThat(deleting.getDate("pendingSince")).isEqualTo(UPDATED);
        assertThat(userDoc(REAL_USER).getString("lastName")).as("a real surname 'User' survives").isEqualTo("User");
    }

    @Test
    @DisplayName("users-account-fields-backfill run twice changes nothing the second time")
    void accountFieldsBackfillIsIdempotent() {
        fields.migrate().block();
        List<Document> afterFirst = all(IdentityCollections.USERS);

        UserAccountFieldsBackfillMigrationService.Result second = fields.migrate().block();

        assertThat(second.orphans()).isZero();
        assertThat(second.accounts()).isZero();
        assertThat(second.placeholderNames()).isZero();
        assertThat(all(IdentityCollections.USERS)).isEqualTo(afterFirst);
    }

    // ------------------------------------------------------------------ contacts

    @Test
    @DisplayName("contacts-backfill creates hashed, encrypted, masked contacts; skips placeholders; verifies only what was verified")
    void contactsBackfill() {
        fields.migrate().block();

        ContactsBackfillMigrationService.Result result = contactsBackfill(3, false).migrate().block();

        // clean: email + phone; phoneOnly: phone only; phonePending: phone (unverified); orphan: email;
        // dupA, dupB, locked, deleting, real, inactive: email each
        assertThat(result.accountsScanned()).isEqualTo(10);
        assertThat(result.skippedPlaceholder()).isEqualTo(2);
        assertThat(result.skippedInvalid()).isZero();
        assertThat(result.written()).isEqualTo(11);
        assertThat(result.alreadyPresent()).isZero();
        assertThat(all(IdentityCollections.CONTACTS)).hasSize(11);

        Document cleanEmail = contactOf(CLEAN, ContactType.EMAIL);
        assertThat(cleanEmail.getString("valueHash")).isEqualTo(hasher.hash(ContactType.EMAIL, "clean@example.com"));
        assertThat(cleanEmail.getString("valueMasked")).isEqualTo("c***@example.com");
        assertThat(crypto.decrypt(cleanEmail.getString("valueEncrypted")).block()).isEqualTo("clean@example.com");
        assertThat(cleanEmail.getDate("verifiedAt")).isNotNull();
        assertThat(cleanEmail.getBoolean("primary")).isFalse();
        assertThat(cleanEmail.getString("source")).isEqualTo("MIGRATION");

        Document cleanPhone = contactOf(CLEAN, ContactType.WHATSAPP);
        assertThat(crypto.decrypt(cleanPhone.getString("valueEncrypted")).block()).isEqualTo("+260977000001");
        assertThat(cleanPhone.getString("valueMasked")).isEqualTo("+260 97* ***001");
        assertThat(cleanPhone.getBoolean("primary")).isTrue();
        assertThat(userDoc(CLEAN).getString("primaryContactId")).isEqualTo(cleanPhone.getString("_id"));

        assertThat(contactsOf(PHONE_ONLY)).hasSize(1);
        assertThat(contactOf(PHONE_ONLY, ContactType.WHATSAPP).getDate("verifiedAt")).isNotNull();

        Document pendingPhone = contactOf(PHONE_PENDING, ContactType.WHATSAPP);
        assertThat(crypto.decrypt(pendingPhone.getString("valueEncrypted")).block()).as("0977000003 normalised").isEqualTo("+260977000003");
        assertThat(pendingPhone).as("phone not verified").doesNotContainKey("verifiedAt");
        assertThat(userDoc(PHONE_PENDING)).as("no verified contact, no primary").doesNotContainKey("primaryContactId");

        assertThat(contactOf(ORPHAN, ContactType.EMAIL)).as("unverified email still becomes an unverified contact")
                .isNotNull().doesNotContainKey("verifiedAt");

        // the two case variants hash to the same contact key
        assertThat(contactOf(DUP_A, ContactType.EMAIL).getString("valueHash"))
                .isEqualTo(contactOf(DUP_B, ContactType.EMAIL).getString("valueHash"));

        // no plain value anywhere in the collection
        String everything = all(IdentityCollections.CONTACTS).stream().map(Document::toJson).collect(Collectors.joining());
        assertThat(everything).doesNotContain("clean@example.com").doesNotContain("orphan@example.com").doesNotContain("dup@example.com")
                .doesNotContain("phone.local")
                .doesNotContain("260977000001").doesNotContain("0977000003");
    }

    @Test
    @DisplayName("contacts-backfill run twice writes nothing new, and a run after a partial loss restores only what is missing")
    void contactsBackfillIsIdempotentAndResumable() {
        fields.migrate().block();
        contactsBackfill(4, false).migrate().block();
        List<Document> first = all(IdentityCollections.CONTACTS);

        ContactsBackfillMigrationService.Result second = contactsBackfill(4, false).migrate().block();
        assertThat(second.written()).isZero();
        assertThat(second.alreadyPresent()).isEqualTo(11);
        assertThat(all(IdentityCollections.CONTACTS)).containsExactlyInAnyOrderElementsOf(first);

        // simulate a crash that lost the later batches
        List<Object> lost = new ArrayList<>(first.stream().skip(7).map(d -> d.get("_id")).toList());
        template.remove(new Query(org.springframework.data.mongodb.core.query.Criteria.where("_id").in(lost)),
                IdentityCollections.CONTACTS).block();
        assertThat(all(IdentityCollections.CONTACTS)).hasSize(7);

        ContactsBackfillMigrationService.Result resumed = contactsBackfill(2, false).migrate().block();
        assertThat(resumed.written()).isEqualTo(4);
        assertThat(resumed.alreadyPresent()).isEqualTo(7);
        // a recreated row is encrypted afresh (new IV), so compare everything but the ciphertext
        assertThat(all(IdentityCollections.CONTACTS).stream().map(AccountMigrationsTest::withoutCipher).toList())
                .containsExactlyInAnyOrderElementsOf(first.stream().map(AccountMigrationsTest::withoutCipher).toList());
        assertThat(all(IdentityCollections.CONTACTS)).allSatisfy(c ->
                assertThat(crypto.decrypt(c.getString("valueEncrypted")).block()).isNotBlank());
    }

    private static Document withoutCipher(Document contact) {
        Document copy = new Document(contact);
        copy.remove("valueEncrypted");
        return copy;
    }

    @Test
    @DisplayName("contacts-backfill in dry-run mode counts what it would write and writes nothing")
    void contactsBackfillDryRun() {
        fields.migrate().block();
        List<Document> usersBefore = all(IdentityCollections.USERS);

        ContactsBackfillMigrationService.Result dry = contactsBackfill(5, true).migrate().block();

        assertThat(dry.dryRun()).isTrue();
        assertThat(dry.written()).isEqualTo(11);
        assertThat(dry.toString()).startsWith("DRY-RUN");
        assertThat(all(IdentityCollections.CONTACTS)).isEmpty();
        assertThat(all(IdentityCollections.USERS)).isEqualTo(usersBefore);
        // the explicit override works on a service configured for real runs
        assertThat(contactsBackfill(5, false).migrate(true).block().dryRun()).isTrue();
        assertThat(all(IdentityCollections.CONTACTS)).isEmpty();
    }

    // ------------------------------------------------------------------ unique index

    private static boolean uniqueIndexExists() {
        return indexNames(IdentityCollections.CONTACTS).contains(IdentityIndexInitializer.UNIQUE_VERIFIED_CONTACT);
    }

    @Test
    @DisplayName("contacts-unique-index refuses while one verified contact belongs to two accounts: leaves a report, builds nothing, merges nothing")
    void uniqueIndexRefusesDuplicates() {
        fields.migrate().block();
        contactsBackfill(100, false).migrate().block();
        long contactsBefore = all(IdentityCollections.CONTACTS).size();

        assertThatThrownBy(() -> uniqueIndex.migrate().block())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refused")
                .hasMessageContaining("1 verified contact(s)")
                .hasMessageContaining("Nothing was merged")
                .hasMessageNotContaining("example.com");

        assertThat(uniqueIndexExists()).isFalse();
        assertThat(all(IdentityCollections.CONTACTS)).hasSize((int) contactsBefore);
        assertThat(userDoc(DUP_A).getString("status")).isEqualTo("ACTIVE");
        assertThat(userDoc(DUP_B).getString("status")).isEqualTo("ACTIVE");

        List<Document> report = all(IdentityCollections.ACCOUNT_EVENTS);
        assertThat(report).hasSize(2);
        assertThat(report).allSatisfy(event -> {
            assertThat(event.getString("kind")).isEqualTo("CONTACT_DUPLICATE_REPORT");
            assertThat(event.getString("accountId")).isIn(DUP_A, DUP_B);
            assertThat(event.get("data", Document.class).getString("type")).isEqualTo("EMAIL");
        });
        assertThat(report.stream().map(Document::toJson).collect(Collectors.joining()))
                .as("the report holds no contact value and no contact key")
                .doesNotContain("dup@").doesNotContain(hasher.hash(ContactType.EMAIL, "dup@example.com"));

        // a re-run reports the same rows rather than adding more
        assertThatThrownBy(() -> uniqueIndex.migrate().block()).isInstanceOf(IllegalStateException.class);
        assertThat(all(IdentityCollections.ACCOUNT_EVENTS)).hasSize(2);
    }

    @Test
    @DisplayName("contacts-unique-index builds uniq_verified_contact once the duplicates are resolved, and a second run is harmless")
    void uniqueIndexBuilds() {
        fields.migrate().block();
        contactsBackfill(100, false).migrate().block();
        assertThatThrownBy(() -> uniqueIndex.migrate().block()).isInstanceOf(IllegalStateException.class);

        // an operator resolves the duplicate by releasing one of the two contacts
        template.getCollection(IdentityCollections.CONTACTS).flatMap(c -> Mono.from(c.updateOne(
                new Document("accountId", DUP_B).append("type", "EMAIL"),
                new Document("$set", new Document("releasedAt", Date.from(CLOCK.instant())))))).block();

        assertThat(uniqueIndex.migrate().block()).startsWith("uniq_verified_contact");
        assertThat(uniqueIndexExists()).isTrue();
        assertThat(uniqueIndex.migrate().block()).startsWith("uniq_verified_contact");

        Document live = template.getCollection(IdentityCollections.CONTACTS).flatMapMany(c -> Flux.from(c.listIndexes()))
                .filter(i -> IdentityIndexInitializer.UNIQUE_VERIFIED_CONTACT.equals(i.getString("name"))).blockFirst();
        assertThat(live.getBoolean("unique")).isTrue();
        assertThat(live.get("key", Document.class).keySet()).containsExactly("type", "valueHash");
        assertThat(live.get("partialFilterExpression", Document.class)).containsKeys("verifiedAt", "releasedAt");

        // and it enforces: another verified owner of an existing contact is refused
        Document another = new Document("accountId", "intruder").append("type", "EMAIL")
                .append("valueHash", hasher.hash(ContactType.EMAIL, "clean@example.com"))
                .append("valueEncrypted", "v1.x").append("valueMasked", "c***").append("verifiedAt", new Date());
        Throwable failure = template.insert(another, IdentityCollections.CONTACTS).then(Mono.<Throwable>empty())
                .onErrorResume(Mono::just).block();
        assertThat(failure).isNotNull();
    }

    @Test
    @DisplayName("contacts-unique-index succeeds straight away on an empty contacts collection")
    void uniqueIndexOnEmpty() {
        assertThat(uniqueIndex.migrate().block()).startsWith("uniq_verified_contact");
        assertThat(uniqueIndexExists()).isTrue();
    }

    // ------------------------------------------------------------------ wiring

    @Test
    @DisplayName("the runner executes the new steps, in dependency order, after the existing ones")
    void runnerOrder() {
        IdentityMigrationRunner runner = new IdentityMigrationRunner(template, CLOCK,
                mock(IdentityCollectionRenameMigrationService.class), mock(IdentityRegistryConformanceMigrationService.class),
                mock(OrganizationStatusSemanticMigrationService.class), mock(UserFieldCleanupMigrationService.class),
                mock(PermissionModelMigrationService.class),
                legacyIndexes, preflight, fields, new UserUsernameNormalizationMigrationService(template), contactsBackfill(10, false), uniqueIndex);

        Map<String, ?> steps = runner.steps();
        List<String> names = new ArrayList<>(steps.keySet());
        int last = names.indexOf("index-registry-conformance-4");
        assertThat(names.subList(last + 1, names.size())).containsExactly(
                "users-legacy-indexes-drop", "account-preflight-report", "users-account-fields-backfill",
                "users-username-normalize", "contacts-backfill", "contacts-unique-index");
    }
}
