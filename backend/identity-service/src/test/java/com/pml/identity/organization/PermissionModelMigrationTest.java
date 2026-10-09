package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.migration.PermissionModelMigrationService;
import com.pml.identity.persistence.IdentityCollections;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.impl.PermissionResolutionServiceImpl;
import com.pml.shared.security.Permission;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The permission data migration against a MongoDB replica set, with documents written in the shapes
 * the service stored before the catalogue existed.
 */
@Tag("L2")
@Tag("ET-ORG-003")
@DisplayName("Existing organizations keep their team's access, and stored permission names become catalogue codes")
class PermissionModelMigrationTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    private PermissionModelMigrationService migration;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_permission_migration"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();

        insert(IdentityCollections.ORGANIZATIONS,
                new Document("_id", "org-legacy").append("settings", new Document("defaultEventVisibility", "PRIVATE")
                        .append("notifyOwnerOnMemberJoin", false)
                        .append("managersCanRequestPayouts", false)
                        .append("marketersCanViewFinancials", false)),
                new Document("_id", "org-no-settings"),
                new Document("_id", "org-new").append("settings", new Document("managersCanViewFinancials", false)
                        .append("adminsCanRequestPayouts", false)));
        insert(IdentityCollections.ORGANIZATION_MEMBERS,
                new Document("_id", "member-legacy").append("userId", "user-manager").append("organizationId", "org-legacy")
                        .append("role", "MANAGER").append("status", "ACTIVE")
                        .append("customPermissions", List.of("MEMBER_INVITE", "NOT_A_PERMISSION", "event:view"))
                        .append("deniedPermissions", List.of("EVENT_DELETE")),
                new Document("_id", "member-clean").append("userId", "user-clean").append("organizationId", "org-legacy")
                        .append("role", "CONTRIBUTOR").append("status", "ACTIVE")
                        .append("customPermissions", List.of("analytics:view")));
        insert(IdentityCollections.EVENT_ACCESS_GRANTS,
                new Document("_id", "grant-legacy").append("customPermissions", List.of("EVENT_MANAGE_ACCESS", "REFUND_ISSUE")));
        insert(IdentityCollections.PERMISSIONS, new Document("_id", "p1").append("name", "EVENT_CREATE"));
        insert(IdentityCollections.ROLE_PERMISSIONS, new Document("_id", "r1"));
        insert("identity_role_permission_changes", new Document("_id", "c1"));

        @SuppressWarnings("unchecked")
        ObjectProvider<ReactiveMongoTemplate> provider = mock(ObjectProvider.class);
        when(provider.getObject()).thenReturn(template);
        migration = new PermissionModelMigrationService(provider);
    }

    @Test
    @DisplayName("Organizations that predate the switches get both on; one created with them keeps its choice")
    void existingOrganizationsKeepAccess() {
        PermissionModelMigrationService.Result result = migration.migrate().block();

        Document legacy = find(IdentityCollections.ORGANIZATIONS, "org-legacy").get("settings", Document.class);
        assertThat(legacy.getBoolean("managersCanViewFinancials")).isTrue();
        assertThat(legacy.getBoolean("adminsCanRequestPayouts")).isTrue();
        assertThat(legacy).doesNotContainKeys("managersCanRequestPayouts", "marketersCanViewFinancials");
        assertThat(legacy.getString("defaultEventVisibility")).as("other settings are untouched").isEqualTo("PRIVATE");
        assertThat(legacy.getBoolean("notifyOwnerOnMemberJoin")).isFalse();

        Document bare = find(IdentityCollections.ORGANIZATIONS, "org-no-settings").get("settings", Document.class);
        assertThat(bare.getBoolean("managersCanViewFinancials")).isTrue();
        assertThat(bare.getBoolean("notifyOwnerOnMemberJoin")).as("a complete settings document").isTrue();

        Document created = find(IdentityCollections.ORGANIZATIONS, "org-new").get("settings", Document.class);
        assertThat(created.getBoolean("managersCanViewFinancials")).isFalse();
        assertThat(created.getBoolean("adminsCanRequestPayouts")).isFalse();
        assertThat(result.organizationsSwitchedOn()).isEqualTo(2);
    }

    @Test
    @DisplayName("After the migration, an existing organization's manager still sees the financial figures")
    void existingManagerKeepsFinancialView() {
        migration.migrate().block();

        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        PermissionResolutionServiceImpl resolution = new PermissionResolutionServiceImpl(
                factory.getRepository(UserRepository.class),
                factory.getRepository(OrganizationMemberRepository.class),
                factory.getRepository(OrganizationRepository.class),
                factory.getRepository(EventAccessGrantRepository.class),
                TestClock.frozenAt(Instant.parse("2026-09-18T10:00:00Z")));

        assertThat(resolution.hasOrganizationPermission("user-manager", "org-legacy", Permission.FINANCIAL_VIEW).block()).isTrue();
        assertThat(resolution.hasOrganizationPermission("user-manager", "org-legacy", Permission.TEAM_INVITE).block())
                .as("the translated custom permission applies").isTrue();
    }

    @Test
    @DisplayName("Stored names become catalogue codes; unknown names are removed; clean documents are not rewritten")
    void storedNamesAreTranslated() {
        PermissionModelMigrationService.Result result = migration.migrate().block();

        Document member = find(IdentityCollections.ORGANIZATION_MEMBERS, "member-legacy");
        assertThat(member.getList("customPermissions", String.class)).containsExactly("event:view", "team:invite");
        assertThat(member.getList("deniedPermissions", String.class)).containsExactly("event:delete");
        assertThat(find(IdentityCollections.EVENT_ACCESS_GRANTS, "grant-legacy").getList("customPermissions", String.class))
                .containsExactly("event_access:grant", "ticket:refund");
        assertThat(result.documentsRewritten()).isEqualTo(2);
    }

    @Test
    @DisplayName("The stored permission collections are dropped")
    void retiredCollectionsAreDropped() {
        PermissionModelMigrationService.Result result = migration.migrate().block();

        List<String> names = template.getCollectionNames().collectList().block();
        assertThat(names).doesNotContain(IdentityCollections.PERMISSIONS, IdentityCollections.ROLE_PERMISSIONS,
                "identity_role_permission_changes");
        assertThat(result.collectionsDropped()).isEqualTo(3);
    }

    @Test
    @DisplayName("Running again changes nothing, and a switch the owner turned off afterwards stays off")
    void runningAgainChangesNothing() {
        migration.migrate().block();
        template.getCollection(IdentityCollections.ORGANIZATIONS)
                .flatMap(c -> Mono.from(c.updateOne(new Document("_id", "org-legacy"),
                        new Document("$set", new Document("settings.managersCanViewFinancials", false)))))
                .block();

        PermissionModelMigrationService.Result again = migration.migrate().block();

        assertThat(again.organizationsSwitchedOn()).isZero();
        assertThat(again.documentsRewritten()).isZero();
        assertThat(again.collectionsDropped()).isZero();
        assertThat(find(IdentityCollections.ORGANIZATIONS, "org-legacy").get("settings", Document.class)
                .getBoolean("managersCanViewFinancials")).isFalse();
    }

    @Test
    @DisplayName("A migrated organization reads back into the model with both switches on")
    void migratedOrganizationReadsBack() {
        migration.migrate().block();

        Organization organization = template.findById("org-legacy", Organization.class).block();
        OrganizationMember member = template.findById("member-legacy", OrganizationMember.class).block();

        assertThat(organization.getSettings().isManagersCanViewFinancials()).isTrue();
        assertThat(member.getRole()).isEqualTo(OrganizationRole.MANAGER);
        assertThat(member.getStatus()).isEqualTo(MemberStatus.ACTIVE);
        assertThat(member.permissions(organization.getSettings())).contains(Permission.FINANCIAL_VIEW, Permission.TEAM_INVITE)
                .doesNotContain(Permission.EVENT_DELETE);
    }

    private static void insert(String collection, Document... documents) {
        template.getCollection(collection).flatMap(c -> Mono.from(c.insertMany(List.of(documents)))).block();
    }

    private static Document find(String collection, String id) {
        return template.getCollection(collection)
                .flatMap(c -> Mono.from(c.find(new Document("_id", id)).first()))
                .block();
    }
}
