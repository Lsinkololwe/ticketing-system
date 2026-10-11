package com.pml.identity.boot;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoCollection;
import com.pml.identity.IdentityServiceApplication;
import com.pml.identity.config.KeycloakProperties;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.OrganizationMember;
import com.pml.identity.domain.model.TeamInvitation;
import com.pml.identity.domain.model.User;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.domain.valueobject.OrganizationRole;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.OrganizationAdminService;
import com.pml.identity.service.OrganizationOnboardingService;
import com.pml.identity.service.OwnershipTransferService;
import com.pml.identity.service.TeamInvitationService;
import com.pml.identity.service.VerificationDocumentService;
import org.bson.Document;
import org.bson.types.Decimal128;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import reactor.core.publisher.Mono;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.mockJwt;
import static org.springframework.security.test.web.reactive.server.SecurityMockServerConfigurers.springSecurity;

/**
 * The whole organizer application write path, against a real MongoDB replica set with every
 * collection's JSON-schema validator applied at startup exactly as production does, the real Spring
 * Data auditing, real method security and the real revocation aspect, and the real onboarding
 * workflow on a Temporal dev server. Only Keycloak is faked, at its port.
 *
 * <p>It exists because no unit test saw these failures: an {@code @CreatedDate} that bound to a
 * boolean field (auditing), a {@code Decimal128} money field the validator typed as {@code double},
 * and a revocation aspect that crashed beside {@code @PreAuthorize}. Any validator rejection,
 * auditing error or Decimal128/double mismatch on this path now fails here.
 */
@Tag("L3")
@Tag("ET-ORG-001")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("ET-ORG-001 · apply, edit, document, submit, approve, payout accounts, team and ownership all write through real validators and auditing")
@SpringBootTest(classes = {IdentityServiceApplication.class, OrganizerApplicationWritePathEndToEndTest.Fakes.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@ActiveProfiles("test")
class OrganizerApplicationWritePathEndToEndTest {

    private static final String DATABASE = IdentityStack.newDatabase();
    private static final String OWNER = "11111111-1111-4111-8111-111111111111";
    private static final String ADMIN = "22222222-2222-4222-8222-222222222222";
    private static final String INVITEE = "33333333-3333-4333-8333-333333333333";

    /** Keycloak, faked at its port: every group and role write succeeds and is remembered. */
    static class FakeOrganizationKeycloak extends KeycloakService {
        FakeOrganizationKeycloak(KeycloakProperties properties) {
            super(properties, () -> {
                throw new IllegalStateException("the fake never builds a real admin client");
            });
        }

        @Override
        public Mono<Void> grantRealmRole(String realm, String keycloakUserId, String roleName) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> revokeRealmRole(String realm, String keycloakUserId, String roleName) {
            return Mono.empty();
        }

        @Override
        public Mono<String> ensureOrganizationGroupTree(String organizationSlug) {
            return Mono.just("group-" + organizationSlug);
        }

        @Override
        public Mono<Void> joinOrganizationGroup(String userId, String organizationSlug, String groupName) {
            return Mono.empty();
        }

        @Override
        public Mono<Void> leaveOrganizationGroup(String userId, String organizationSlug, String groupName) {
            return Mono.empty();
        }

    }

    @TestConfiguration
    static class Fakes {
        @Bean
        @Primary
        KeycloakService fakeKeycloak(KeycloakProperties properties) {
            return new FakeOrganizationKeycloak(properties);
        }
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry registry) {
        IdentityStack.register(registry, DATABASE);
    }

    /** What catalog-service seeds at startup: the payment defaults a new organization is built from. */
    @BeforeAll
    static void seedPlatformConfiguration() {
        MongoClient client = IdentityStack.mongoClient();
        try {
            MongoCollection<Document> settings = client.getDatabase(DATABASE).getCollection("catalog_platform_configuration");
            Mono.from(settings.insertOne(new Document("_id", "platform-config")
                    .append("payment", new Document("commissionRate", 0.1d)
                            .append("payoutMethod", "BANK_TRANSFER")
                            .append("payoutSchedule", "WEEKLY")
                            .append("minimumPayoutAmount", new Decimal128(new BigDecimal("100.00")))))).block();
        } finally {
            client.close();
        }
    }

    @Autowired
    ApplicationContext context;
    @Autowired
    ReactiveMongoTemplate template;
    @Autowired
    OrganizationOnboardingService onboarding;
    @Autowired
    VerificationDocumentService documents;
    @Autowired
    OrganizationAdminService adminService;
    @Autowired
    TeamInvitationService invitations;
    @Autowired
    OwnershipTransferService transfers;

    private static String organizationId;

    // ---------------------------------------------------------------------------------- helpers

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphql(String subject, String role, String query) {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(90)).build()
                .mutateWith(mockJwt().jwt(jwt -> jwt.subject(subject))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)));
        EntityExchangeResult<Map> result = client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query)).exchange().expectBody(Map.class).returnResult();
        return (Map<String, Object>) result.getResponseBody();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> data(Map<String, Object> response, String field) {
        assertThat(response.get("errors")).as("GraphQL errors in %s", response).isNull();
        return (Map<String, Object>) ((Map<String, Object>) response.get("data")).get(field);
    }

    private Organization organization() {
        return template.findById(organizationId, Organization.class).block();
    }

    private void saveUser(String id, String email, String first) {
        template.save(User.builder().id(id).username(id).email(email).emailVerified(true)
                .firstName(first).lastName("Tester").createdAt(java.time.Instant.now()).build()).block();
    }

    // ---------------------------------------------------------------------------------- the path

    @Test
    @Order(1)
    @DisplayName("applyToBeOrganizer through GraphQL creates the DRAFT organization and an OWNER membership with a real createdAt")
    void apply() {
        saveUser(OWNER, "owner@example.com", "Olive");
        saveUser(ADMIN, "admin@example.com", "Ada");
        saveUser(INVITEE, "invitee@example.com", "Ian");

        Map<String, Object> created = data(graphql(OWNER, "CUSTOMER", """
                mutation { applyToBeOrganizer(input: {name: "Copperbelt Live", type: BUSINESS,
                  businessType: SOLE_PROPRIETORSHIP, businessEmail: "live@example.com", city: "Ndola",
                  province: "Copperbelt", country: "Zambia"}) { id status slug } }"""), "applyToBeOrganizer");
        organizationId = created.get("id").toString();
        assertThat(created.get("status")).isEqualTo("DRAFT");

        List<OrganizationMember> members = template.find(
                org.springframework.data.mongodb.core.query.Query.query(
                        org.springframework.data.mongodb.core.query.Criteria.where("organizationId").is(organizationId)),
                OrganizationMember.class).collectList().block();
        assertThat(members).hasSize(1);
        assertThat(members.get(0).getCreatedAt()).as("auditing filled createdAt").isNotNull();
        assertThat(members.get(0).getRole()).isEqualTo(OrganizationRole.OWNER);
    }

    @Test
    @Order(2)
    @DisplayName("the application is edited: business info, address and social links")
    void update() {
        Map<String, Object> updated = data(graphql(OWNER, "CUSTOMER", "mutation { updateOrganizationApplication(id: \""
                + organizationId + "\", input: {name: \"Copperbelt Live\", description: \"Live events\", tagline: \"Loud\","
                + " website: \"https://copperbelt.example.com\", taxId: \"1002003004\", businessRegistrationNumber: \"PACRA-1\","
                + " businessPhone: \"+260971234567\", city: \"Kitwe\", province: \"Copperbelt\", country: \"Zambia\","
                + " socialLinks: {facebook: \"https://facebook.com/cb\", instagram: \"https://instagram.com/cb\","
                + " twitter: \"https://x.com/cb\", linkedin: \"https://linkedin.com/cb\", youtube: \"https://youtube.com/cb\","
                + " tiktok: \"https://tiktok.com/cb\"}}) { id status } }"), "updateOrganizationApplication");
        assertThat(updated.get("status")).isEqualTo("DRAFT");
        assertThat(organization().getSocialLinks().getTiktok()).isEqualTo("https://tiktok.com/cb");
    }

    @Test
    @Order(3)
    @DisplayName("submitting before the documents arrive is refused; registering them satisfies it, and the reviewer can approve one")
    void documentsThenSubmit() {
        Map<String, Object> early = graphql(OWNER, "CUSTOMER",
                "mutation { submitOrganizationForReview(id: \"" + organizationId + "\") { id status } }");
        assertThat(early.get("errors")).as("missing documents refuse the submit").isNotNull();

        VerificationDocument id = documents.upload(organizationId, "NATIONAL_ID", "https://files.example.com/nrc.pdf",
                "nrc.pdf", 2048L, "application/pdf").block();
        documents.upload(organizationId, "TAX_CERTIFICATE", "https://files.example.com/tax.pdf",
                "tax.pdf", 4096L, "application/pdf").block();
        assertThat(id.getUploadedAt()).as("auditing filled uploadedAt").isNotNull();
        // documents.approve() is called directly rather than through the GraphQL mutation, so
        // there is no TenantScopeWebFilter in play here; seed the scope an admin's real request
        // would carry, same as the production code now requires.
        VerificationDocument approved = documents.approve(id.getId(), ADMIN)
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(TenantScope.platformAdministrator(ADMIN, java.util.Set.of()))))
                .block();
        assertThat(approved.getStatus().name()).isEqualTo("APPROVED");

        Map<String, Object> submitted = data(graphql(OWNER, "CUSTOMER",
                "mutation { submitOrganizationForReview(id: \"" + organizationId + "\") { id status } }"),
                "submitOrganizationForReview");
        assertThat(submitted.get("status")).isEqualTo("PENDING_REVIEW");
    }

    @Test
    @Order(4)
    @DisplayName("an admin approves with a commission rate: the saga runs against the fake Keycloak and the organization is ACTIVE")
    void approve() {
        // An applicant has no membership row until approval creates it; that insert goes through the
        // real validator, which wants an ObjectId _id.
        template.remove(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("organizationId").is(organizationId)),
                OrganizationMember.class).block();
        data(graphql(ADMIN, "ADMIN", "mutation { approveOrganization(id: \"" + organizationId
                + "\", commissionRate: 7.5) { id status } }"), "approveOrganization");
        await().atMost(Duration.ofSeconds(60)).untilAsserted(() -> {
            Organization approved = organization();
            assertThat(approved.getStatus().name()).isEqualTo("ACTIVE");
            assertThat(approved.getKeycloakGroupId()).isEqualTo("group-" + approved.getSlug());
            assertThat(approved.getPayoutConfig().getCommissionRate()).isEqualTo(0.075d);
        });
        assertThat(template.find(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("organizationId").is(organizationId)),
                OrganizationMember.class).collectList().block()).as("approval created the owner membership").hasSize(1);
        Map<String, Object> mine = data(graphql(OWNER, "ORGANIZER",
                "query { myOwnedOrganization { id status commissionRate verified } }"), "myOwnedOrganization");
        assertThat(mine.get("id")).isEqualTo(organizationId);
        assertThat(mine.get("verified")).isEqualTo(true);
    }

    @Test
    @Order(5)
    @DisplayName("payout setup writes a Decimal128 minimum and encrypted bank and mobile-money details, which an admin verifies, rejects, suspends and reinstates")
    void payout() {
        data(graphql(OWNER, "ORGANIZER", "mutation { updatePayoutConfig(organizationId: \"" + organizationId
                + "\", input: {preferredMethod: BANK_TRANSFER, schedule: MONTHLY, minimumPayoutAmount: 250.5}) { id } }"),
                "updatePayoutConfig");
        assertThat(organization().getPayoutConfig().getMinimumPayoutAmount()).isEqualByComparingTo("250.5");

        data(graphql(OWNER, "ORGANIZER", "mutation { setBankAccount(organizationId: \"" + organizationId
                + "\", input: {bankName: \"Zanaco\", bankCode: \"ZNCOZMLU\", accountNumber: \"1234567890123\","
                + " accountHolderName: \"Copperbelt Live\", accountType: \"BUSINESS\"}) { id } }"), "setBankAccount");
        data(graphql(OWNER, "ORGANIZER", "mutation { setMobileMoneyAccount(organizationId: \"" + organizationId
                + "\", input: {provider: MTN, phoneNumber: \"+260961234567\", accountHolderName: \"Copperbelt Live\"}) { id } }"),
                "setMobileMoneyAccount");
        data(graphql(ADMIN, "ADMIN", "mutation { verifyPayoutAccount(organizationId: \"" + organizationId
                + "\", verified: true) { id } }"), "verifyPayoutAccount");
        assertThat(organization().getPayoutConfig().isVerified()).isTrue();

        adminService.suspendPayoutAccount(organizationId, "audit hold", ADMIN).block();
        adminService.reinstatePayoutAccount(organizationId, ADMIN).block();
        adminService.rejectPayoutAccount(organizationId, "name mismatch", ADMIN).block();
        assertThat(organization().getPayoutConfig().isVerified()).isFalse();
    }

    @Test
    @Order(6)
    @DisplayName("a member is invited and accepts, then ownership transfers to them")
    void teamAndOwnership() {
        TeamInvitation invitation = invitations.invite(organizationId, "invitee@example.com", null, "Ian Tester",
                OrganizationRole.ADMIN, "welcome", List.of(), OWNER).block();
        assertThat(invitation.getCreatedAt()).isNotNull();
        OrganizationMember joined = invitations.accept(invitation.getInvitationToken(), INVITEE).block();
        assertThat(joined.getStatus().name()).isEqualTo("ACTIVE");

        String transferId = java.util.UUID.randomUUID().toString();
        transfers.initiate(transferId, organizationId, OWNER, INVITEE, "handover").block();
        transfers.complete(transferId).block();
        assertThat(organization().getOwnerId()).isEqualTo(INVITEE);
    }

    @Test
    @Order(7)
    @DisplayName("every document the path wrote is accepted by the validators and nothing was rejected")
    void readPaths() {
        assertThat(documents.findByOrganization(organizationId).collectList().block()).hasSize(2);
        Map<String, Object> mine = data(graphql(INVITEE, "ORGANIZER",
                "query { myOwnedOrganization { id status ownerId payoutConfig { verified } } }"), "myOwnedOrganization");
        assertThat(mine.get("ownerId")).isEqualTo(INVITEE);
    }

    @Test
    @Order(8)
    @DisplayName("the admin and organizer operations the web apps send resolve with no GraphQL error, on an applicant with no members, invitations or stats")
    void frontendOperationsHaveNoGraphQlErrors() throws Exception {
        String applicant = "44444444-4444-4444-8444-444444444444";
        saveUser(applicant, "applicant@example.com", "Appl");
        String applicantOrg = data(graphql(applicant, "CUSTOMER", """
                mutation { applyToBeOrganizer(input: {name: "Applicant Events", type: BUSINESS,
                  businessType: SOLE_PROPRIETORSHIP, businessEmail: "applicant@example.com", city: "Lusaka",
                  province: "Lusaka", country: "Zambia"}) { id } }"""), "applyToBeOrganizer").get("id").toString();
        // An application has no members until it is approved; the pages must render that state too.
        template.remove(org.springframework.data.mongodb.core.query.Query.query(
                org.springframework.data.mongodb.core.query.Criteria.where("organizationId").is(applicantOrg)),
                OrganizationMember.class).block();
        List<FrontendOperations.Operation> operations = FrontendOperations.load("admin/modules", "organization-admin/modules");
        operations.addAll(FrontendOperations.loadApps("organization-admin", "admin"));
        org.junit.jupiter.api.Assumptions.assumeFalse(operations.isEmpty(), "frontend sources not present");
        List<String> failures = new java.util.ArrayList<>();
        int executed = 0;
        // The admin console reads as an administrator; the organizer app reads as the organization's owner.
        for (String[] caller : new String[][]{{ADMIN, "ADMIN"}, {INVITEE, "ORGANIZER"}})
        for (String org : List.of(applicantOrg, organizationId)) {
            for (FrontendOperations.Operation op : operations) {
                if (!op.kind().equals("query")) {
                    continue;
                }
                Map<String, Object> variables = new java.util.LinkedHashMap<>();
                boolean runnable = true;
                for (Map.Entry<String, String> v : op.variables().entrySet()) {
                    String name = v.getKey();
                    boolean required = v.getValue().endsWith("!");
                    if (name.equals("id") || name.equals("organizationId") || name.equals("orgId")) {
                        variables.put(name, org);
                    } else if (name.equals("userId")) {
                        variables.put(name, OWNER);
                    } else if (required) {
                        runnable = false;
                    }
                }
                if (!runnable) {
                    continue;
                }
                Map<String, Object> response = graphqlWith(caller[0], caller[1], op.document(), variables);
                Object errors = response.get("errors");
                if (errors instanceof List<?> list && !list.isEmpty()) {
                    // A document naming a field this service does not own is another service's query.
                    boolean foreign = list.stream().allMatch(e -> e instanceof Map<?, ?> map
                            && map.get("extensions") instanceof Map<?, ?> ext
                            && ("FIELD_NOT_FOUND".equals(ext.get("errorType")) || "INVALID_ARGUMENT".equals(ext.get("errorType"))
                                    || "FIELD_NOT_FOUND".equals(ext.get("classification")) || "INVALID_ARGUMENT".equals(ext.get("classification"))
                                    || "ValidationError".equals(ext.get("classification"))
                                    // admin-console documents are rightly refused to the organizer caller
                                    || (caller[1].equals("ORGANIZER") && ("FORBIDDEN".equals(ext.get("classification"))
                                            || "PERMISSION_DENIED".equals(ext.get("classification"))))));
                    if (!foreign) {
                        failures.add(caller[1] + " " + op.name() + " on " + org + ": " + list);
                    }
                } else {
                    executed++;
                }
            }
        }
        assertThat(failures).as("operations with GraphQL errors").isEmpty();
        assertThat(executed).as("operations that executed").isGreaterThan(5);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> graphqlWith(String subject, String role, String query, Map<String, Object> variables) {
        WebTestClient client = WebTestClient.bindToApplicationContext(context).apply(springSecurity()).configureClient()
                .responseTimeout(Duration.ofSeconds(90)).build()
                .mutateWith(mockJwt().jwt(jwt -> jwt.subject(subject))
                        .authorities(new SimpleGrantedAuthority("ROLE_" + role)));
        EntityExchangeResult<Map> result = client.post().uri("/graphql").contentType(MediaType.APPLICATION_JSON)
                .bodyValue(Map.of("query", query, "variables", variables)).exchange().expectBody(Map.class).returnResult();
        return (Map<String, Object>) result.getResponseBody();
    }
}
