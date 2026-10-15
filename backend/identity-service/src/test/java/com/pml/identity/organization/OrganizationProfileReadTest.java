package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.authorization.DecisionContextLoader;
import com.pml.identity.domain.enums.MemberStatus;
import com.pml.identity.domain.model.EventAccessGrant;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.repository.EventAccessGrantRepository;
import com.pml.identity.repository.OrganizationMemberRepository;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.OrganizationService;
import com.pml.identity.service.impl.PermissionResolutionServiceImpl;
import com.pml.identity.web.graphql.query.OrganizationQueryResolver;
import com.pml.shared.constants.OrganizationStatus;
import com.pml.shared.constants.UserType;
import com.pml.shared.testing.MongoReplicaSet;
import com.pml.shared.testing.TestClock;
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
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import reactor.core.publisher.Mono;

import java.time.Instant;
import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An organization's profile carries its tax number, registration number and business contacts, so
 * the by-id, by-slug and by-owner reads answer only the owner, a member holding the profile
 * permission and platform staff. Everyone else reads what an unknown organization reads: nothing.
 * Real repositories on a MongoDB replica set. OWASP A01:2021 · CWE-639.
 */
@Tag("L2")
@Tag("ET-PLT-013")
@DisplayName("An organization's profile is readable only by its owner, its members and platform staff")
class OrganizationProfileReadTest {

    private static final String ORG = "org-profile";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static UserRepository users;
    private static OrganizationRepository organizations;
    private static OrganizationMemberRepository members;

    private OrganizationQueryResolver resolver;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(new SimpleReactiveMongoDatabaseFactory(client, "identity_profile_read"));
        ReactiveMongoRepositoryFactory factory = new ReactiveMongoRepositoryFactory(template);
        users = factory.getRepository(UserRepository.class);
        organizations = factory.getRepository(OrganizationRepository.class);
        members = factory.getRepository(OrganizationMemberRepository.class);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void seed() {
        for (Class<?> type : List.of(User.class, Organization.class, OrganizationMember.class)) {
            template.remove(new Query(), type).block();
        }
        Organization organization = new Organization();
        organization.setId(ORG);
        organization.setName("Copperbelt Live");
        organization.setSlug("copperbelt-live");
        organization.setOwnerId("owner");
        organization.setTaxId("1002003004");
        organization.setSettings(new OrganizationSettings());
        organization.setStatus(OrganizationStatus.ACTIVE);
        organizations.save(organization).block();

        var grants = new ReactiveMongoRepositoryFactory(template).getRepository(EventAccessGrantRepository.class);
        var clock = TestClock.frozenAt(Instant.parse("2026-10-13T10:00:00Z"));
        var resolution = new PermissionResolutionServiceImpl(
                new DecisionContextLoader(users, members, organizations, grants, clock), members, grants, clock);

        OrganizationService service = Mockito.mock(OrganizationService.class);
        Mockito.when(service.findById(ORG)).thenAnswer(call -> organizations.findById(ORG));
        Mockito.when(service.findBySlug("copperbelt-live")).thenAnswer(call -> organizations.findBySlug("copperbelt-live"));
        Mockito.when(service.findByOwnerId("owner")).thenAnswer(call -> organizations.findByOwnerId("owner"));
        resolver = new OrganizationQueryResolver(service, Mockito.mock(OrganizationMemberService.class), resolution);
    }

    @Test
    @DisplayName("The owner, a contributor and platform staff read the profile by id, slug and owner")
    void entitledCallersRead() {
        member("contributor", OrganizationRole.CONTRIBUTOR, MemberStatus.ACTIVE);
        user("staff-finance", UserType.FINANCE);

        for (String caller : List.of("owner", "contributor", "staff-finance")) {
            assertThat(as(caller, resolver.organization(ORG)).block()).as("%s by id", caller).isNotNull();
            assertThat(as(caller, resolver.organizationBySlug("copperbelt-live")).block()).as("%s by slug", caller).isNotNull();
            assertThat(as(caller, resolver.organizationByOwnerId("owner")).block()).as("%s by owner", caller).isNotNull();
        }
    }

    @Test
    @DisplayName("A buyer, an unrelated organizer and a removed member read nothing, by every route")
    void everyoneElseReadsNothing() {
        user("buyer", UserType.CUSTOMER);
        member("removed", OrganizationRole.MANAGER, MemberStatus.REMOVED);

        for (String caller : List.of("buyer", "removed", "organizer-of-another-org", "unknown-user")) {
            assertThat(as(caller, resolver.organization(ORG)).block()).as("%s by id", caller).isNull();
            assertThat(as(caller, resolver.organizationBySlug("copperbelt-live")).block()).as("%s by slug", caller).isNull();
            assertThat(as(caller, resolver.organizationByOwnerId("owner")).block()).as("%s by owner", caller).isNull();
        }
    }

    @Test
    @DisplayName("A member whose profile permission is denied reads nothing")
    void deniedMemberReadsNothing() {
        OrganizationMember denied = member("denied", OrganizationRole.MANAGER, MemberStatus.ACTIVE);
        denied.setDeniedPermissions(java.util.Set.of("organization:view"));
        members.save(denied).block();

        assertThat(as("denied", resolver.organization(ORG)).block()).isNull();
    }

    private static <T> Mono<T> as(String userId, Mono<T> call) {
        var token = new JwtAuthenticationToken(Jwt.withTokenValue("t").header("alg", "none").subject(userId).build(),
                AuthorityUtils.NO_AUTHORITIES);
        return call.contextWrite(ReactiveSecurityContextHolder.withAuthentication(token));
    }

    private static OrganizationMember member(String userId, OrganizationRole role, MemberStatus status) {
        OrganizationMember member = new OrganizationMember();
        member.setUserId(userId);
        member.setOrganizationId(ORG);
        member.setRole(role);
        member.setStatus(status);
        return members.save(member).block();
    }

    private static void user(String userId, UserType role) {
        User user = new User();
        user.setId(userId);
        user.setRoles(EnumSet.of(role));
        users.save(user).block();
    }
}
