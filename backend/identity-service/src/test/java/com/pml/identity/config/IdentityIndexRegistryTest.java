package com.pml.identity.config;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.IndexRegistryAssertions;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import reactor.core.publisher.Mono;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Identity's index registry, checked on a real server.
 *
 * <h2>Three constraints that decide who someone is</h2>
 * <ul>
 *   <li>{@code (userId, organizationId)} unique — one membership per user per organization.
 *       Without it, accepting an invitation twice makes a second membership row, and the
 *       owner-count invariant membership rests on counts a person twice.</li>
 *   <li>{@code (userId, eventId)} unique — one access grant per user per event.</li>
 *   <li>{@code phoneNumber} unique <b>and sparse</b> — phone is the login identity, so a
 *       duplicate is two accounts one OTP can open.</li>
 * </ul>
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002-R3 · identity's declared indexes exist and are enforced")
class IdentityIndexRegistryTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static IndexRegistryAssertions indexes;

    @BeforeAll
    static void createIndexes() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "identity_index_registry"));
        indexes = new IndexRegistryAssertions(template);

        // The container is reused between runs, so start from an empty database and measure
        // the indexes these declarations produce.
        template.getMongoDatabase().flatMap(database -> Mono.from(database.drop())).block();

        IndexEnsurer.Report report = new IndexEnsurer(template)
                .ensure(IdentityIndexInitializer.specifications())
                .block();

        assertThat(report).isNotNull();
        assertThat(report.isClean()).as("index creation reported conflicts: %s", report).isTrue();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @Test
    @DisplayName("every §4 row for identity exists with its declared keys, uniqueness and TTL")
    void registryIsSatisfied() {
        indexes.assertAllPresent(IdentityIndexInitializer.specifications());
    }

    @Test
    @DisplayName("one account per phone number")
    void phoneNumberIsUnique() {
        indexes.assertRefusesDuplicate(IdentityCollections.USERS, "phoneNumber", "+260970000001");
    }

    @Test
    @DisplayName("sparse: many users may have no phone number at all")
    void sparseAllowsManyWithoutAPhone() {
        // What `sparse` buys, and the reason its absence is not cosmetic. A plain unique index
        // treats a missing field as a value, so the SECOND user without a phone number collides
        // with the first — one admin created by email, and no other may be created without one.
        template.insert(new Document("email", "one@example.test"), IdentityCollections.USERS).block();
        template.insert(new Document("email", "two@example.test"), IdentityCollections.USERS).block();

        Long count = template.estimatedCount(IdentityCollections.USERS).block();
        assertThat(count).isNotNull().isGreaterThanOrEqualTo(2);
    }

    @Test
    @DisplayName("one membership per user per organization")
    void membershipIsUniquePerUserAndOrganization() {
        String collection = IdentityCollections.ORGANIZATION_MEMBERS;
        template.remove(new org.springframework.data.mongodb.core.query.Query(), collection).block();

        template.insert(new Document("userId", "u-1").append("organizationId", "o-1"), collection).block();

        Throwable failure = template
                .insert(new Document("userId", "u-1").append("organizationId", "o-1"), collection)
                .then(Mono.<Throwable>empty())
                .onErrorResume(Mono::just)
                .block();

        assertThat(failure)
                .as("a second membership for the same user and organization was accepted — "
                        + "the owner-count invariant then counts one person twice")
                .isNotNull();
    }

    @Test
    @DisplayName("a person has one active membership at a time; having left another organization does not count")
    void onePersonOneActiveOrganization() {
        String collection = IdentityCollections.ORGANIZATION_MEMBERS;
        template.remove(new org.springframework.data.mongodb.core.query.Query(), collection).block();

        template.insert(new Document("userId", "u-9").append("organizationId", "o-1").append("status", "REMOVED"), collection).block();
        template.insert(new Document("userId", "u-9").append("organizationId", "o-2").append("status", "ACTIVE"), collection).block();

        Throwable failure = template
                .insert(new Document("userId", "u-9").append("organizationId", "o-3").append("status", "ACTIVE"), collection)
                .then(Mono.<Throwable>empty())
                .onErrorResume(Mono::just)
                .block();

        assertThat(failure)
                .as("a second active membership for one person was accepted: the organization derived from "
                        + "them would be ambiguous")
                .isNotNull();
    }

    @Test
    @DisplayName("expired invitations carry a TTL that will actually expire them")
    void invitationsExpire() {
        // Asserted through assertAllPresent's expireAfterSeconds check rather than by waiting:
        // MongoDB's TTL monitor runs once a minute, so observing a deletion would mean a
        // minute of wall clock per run for a property the index metadata already states.
        indexes.assertAllPresent(IdentityIndexInitializer.specifications().stream()
                .filter(spec -> spec.expireAfter() != null)
                .toList());
    }

    @Test
    @DisplayName("no index exists that §4 does not declare")
    void noUndeclaredIndexes() {
        assertThat(indexes.undeclared(IdentityIndexInitializer.specifications()))
                .as("an index created outside the registry is drift nobody chose")
                .isEmpty();
    }

    @Test
    @DisplayName("the declarations still match ET-PLT-002 §4, row for row")
    void declarationsMatchTheSpecification() {
        // Compared against the written index registry, not against the server. Asserting that the
        // server carries what the code declared cannot catch the code drifting from that registry — change
        // a declaration and the server follows it, so both move together and stay consistent
        // with each other while agreeing with nothing.
        IndexRegistryAssertions.assertMatchesRegistry(IdentityIndexInitializer.specifications(), "identity_");
    }

    @Test
    @DisplayName("ET-IDN-004 · startup builds every index except the deferred unique contact index")
    void startupLeavesTheUniqueContactIndexToItsMigration() {
        assertThat(IdentityIndexInitializer.DEFERRED).containsExactly("uniq_verified_contact");
        assertThat(IdentityIndexInitializer.startupSpecifications())
                .extracting(spec -> spec.name())
                .doesNotContain("uniq_verified_contact")
                .contains("idx_accountId", "idx_keycloakUserId", "idx_status", "idx_mergedInto");
        assertThat(IdentityIndexInitializer.specifications()).hasSize(
                IdentityIndexInitializer.startupSpecifications().size() + 1);
    }

    @Test
    @DisplayName("ET-IDN-004 · the legacy users indexes are not declared any more")
    void legacyUserIndexesAreNotDeclared() {
        assertThat(IdentityIndexInitializer.specifications())
                .filteredOn(spec -> spec.collection().equals(IdentityCollections.USERS))
                .extracting(spec -> spec.name())
                .doesNotContain("idx_userType_accountStatus")
                .doesNotContain("email");
    }

    @Test
    @DisplayName("ET-IDN-004 · uniq_verified_contact: one verified owner per contact, unverified and released rows may repeat")
    void oneVerifiedOwnerPerContact() {
        String collection = IdentityCollections.CONTACTS;
        template.remove(new org.springframework.data.mongodb.core.query.Query(), collection).block();
        java.util.Date now = new java.util.Date();

        template.insert(new Document("accountId", "a1").append("type", "EMAIL").append("valueHash", "h1")
                .append("verifiedAt", now), collection).block();

        // unverified copies and released copies may exist beside the verified one
        template.insert(new Document("accountId", "a2").append("type", "EMAIL").append("valueHash", "h1"), collection).block();
        template.insert(new Document("accountId", "a3").append("type", "EMAIL").append("valueHash", "h1"), collection).block();
        template.insert(new Document("accountId", "a4").append("type", "EMAIL").append("valueHash", "h1")
                .append("verifiedAt", now).append("releasedAt", now), collection).block();
        // the same hash under the other type is another contact
        template.insert(new Document("accountId", "a5").append("type", "WHATSAPP").append("valueHash", "h1")
                .append("verifiedAt", now), collection).block();

        for (Document second : java.util.List.of(
                new Document("accountId", "a6").append("type", "EMAIL").append("valueHash", "h1").append("verifiedAt", now),
                new Document("accountId", "a7").append("type", "EMAIL").append("valueHash", "h1").append("verifiedAt", now)
                        .append("releasedAt", null))) {
            Throwable failure = template.insert(second, collection).then(Mono.<Throwable>empty())
                    .onErrorResume(Mono::just).block();
            assertThat(failure).as("a second verified, unreleased owner was accepted: %s", second.get("accountId"))
                    .isNotNull();
        }
    }

    @Test
    @DisplayName("ET-IDN-004 · idx_keycloakUserId: unique among accounts that have one, many may have none")
    void keycloakUserIdUniqueButOptional() {
        String collection = IdentityCollections.USERS;
        template.remove(new org.springframework.data.mongodb.core.query.Query(), collection).block();
        template.insert(new Document("keycloakUserId", "kc-1"), collection).block();
        template.insert(new Document("keycloakUserId", null), collection).block();
        template.insert(new Document("keycloakUserId", null), collection).block();
        template.insert(new Document("other", 1), collection).block();
        template.insert(new Document("other", 2), collection).block();

        Throwable failure = template.insert(new Document("keycloakUserId", "kc-1"), collection)
                .then(Mono.<Throwable>empty()).onErrorResume(Mono::just).block();
        assertThat(failure).as("two accounts linked to one Keycloak user").isNotNull();
    }

    @Test
    @DisplayName("ET-IDN-004 · idx_mergedInto is partial")
    void mergedIntoIsPartial() {
        Document live = template.getCollection(IdentityCollections.USERS)
                .flatMapMany(c -> reactor.core.publisher.Flux.from(c.listIndexes()))
                .filter(index -> "idx_mergedInto".equals(index.getString("name")))
                .blockFirst();
        assertThat(live).isNotNull();
        assertThat(live.get("partialFilterExpression", Document.class))
                .isEqualTo(new Document("mergedInto", new Document("$type", "string")));
    }
}
