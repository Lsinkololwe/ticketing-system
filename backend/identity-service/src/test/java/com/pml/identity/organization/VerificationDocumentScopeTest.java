package com.pml.identity.organization;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.identity.domain.model.VerificationDocument;
import com.pml.identity.repository.VerificationDocumentRepository;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.GraphQlErrors;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A KYC document is readable only from the organization that owns it.
 *
 * <h2>What is at stake</h2>
 * {@code verificationDocument(id)} is {@code @PreAuthorize("isAuthenticated()")}. Without scoping
 * at the lookup, <b>any account that can sign in can read any organizer's verification document by
 * id</b>: its type ({@code ID_DOCUMENT}, {@code BUSINESS_LICENSE},
 * {@code TAX_CERT}), filename, review status, rejection reason, and {@code documentUrl} — the
 * location of the file itself.
 *
 * <p>An account on this platform costs a phone number.</p>
 *
 * <h2>The sibling comparison, again</h2>
 * Three methods below it, {@code myVerificationDocuments} resolves the caller's organizations and
 * filters to them. A list query that filters beside a by-id read that does not is a recurring
 * shape: the filter gets written once, on the operation somebody thought about, and the by-id
 * lookup is added by someone who assumes the guard is elsewhere.
 */
@Tag("L2")
@Tag("ET-PLT-007")
@Tag("ET-PLT-005")
@DisplayName("F-029 · a verification document is readable only inside its organization")
class VerificationDocumentScopeTest {

    private static final String OWNING_ORG = "org-owner";
    private static final String OTHER_ORG = "org-outsider";
    private static final String THE_DOCUMENT = "doc-kyc-1";
    private static final String NEVER_ISSUED = "doc-never-issued";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static VerificationDocumentRepository documents;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "document_scope"));
        documents = new ReactiveMongoRepositoryFactory(template)
                .getRepository(VerificationDocumentRepository.class);

        template.remove(new Query(), VerificationDocument.class).block();
        documents.save(VerificationDocument.builder()
                .id(THE_DOCUMENT)
                .organizationId(OWNING_ORG)
                .documentType("BUSINESS_LICENSE")
                .documentUrl("s3://kyc/org-owner/licence.pdf")
                .fileName("licence.pdf")
                .build()).block();
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private static TenantScope member(String organizationId) {
        return new TenantScope("caller-1", Set.of(organizationId), false);
    }

    private static TenantScope platformAdmin() {
        return new TenantScope("admin-1", Set.of(), true);
    }

    /** The rule exactly as the resolver applies it. */
    private static VerificationDocument visibleTo(TenantScope scope, String id) {
        return documents.findById(id)
                .flatMap(document -> CurrentTenantScope.get()
                        .flatMap(current -> current.platformAdmin()
                                || current.permits(document.getOrganizationId())
                                ? Mono.just(document)
                                : Mono.<VerificationDocument>error(TenantBoundary.refuse(
                                        ErrorCode.DOCUMENT_UNKNOWN, "document " + id))))
                .switchIfEmpty(Mono.error(TenantBoundary.refuse(
                        ErrorCode.DOCUMENT_UNKNOWN, "document " + id + " does not exist")))
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .block();
    }

    @Nested
    @DisplayName("the organization that owns the document")
    class Owner {

        @Test
        @DisplayName("reads it, which is the point of the query")
        void theOwnerReadsIt() {
            // Asserted first and deliberately: without it, every refusal below would also pass
            // against a fixture that never wrote the row, or a misspelled id.
            assertThat(documents.findById(THE_DOCUMENT).block())
                    .as("the document must exist, or this class proves nothing")
                    .isNotNull();

            assertThat(visibleTo(member(OWNING_ORG), THE_DOCUMENT))
                    .isNotNull()
                    .satisfies(d -> assertThat(d.getDocumentUrl()).isNotBlank());
        }
    }

    @Nested
    @DisplayName("a member of a different organization")
    class Outsider {

        @Test
        @DisplayName("cannot read the document")
        void cannotRead() {
            assertThatThrownBy(() -> visibleTo(member(OTHER_ORG), THE_DOCUMENT))
                    .isInstanceOf(DomainRefusal.class)
                    .satisfies(refusal -> assertThat(((DomainRefusal) refusal).errorCode())
                            .isEqualTo(ErrorCode.DOCUMENT_UNKNOWN));
        }

        @Test
        @DisplayName("cannot tell it apart from an id that was never issued")
        void theRefusalIsIndistinguishable() {
            // The requirement that makes this more than a lock. If the two answers differ in
            // anything the caller can observe, the error is an oracle and anyone holding a list of
            // candidate ids learns which are real without any access at all.
            DomainRefusal onSomeoneElses = refusalFrom(member(OTHER_ORG), THE_DOCUMENT);
            DomainRefusal onNeverIssued = refusalFrom(member(OTHER_ORG), NEVER_ISSUED);

            assertThat(onSomeoneElses.errorCode()).isEqualTo(onNeverIssued.errorCode());
            assertThat(GraphQlErrors.refusalMessage(onSomeoneElses.errorCode()))
                    .isEqualTo(GraphQlErrors.refusalMessage(onNeverIssued.errorCode()));
            assertThat(onSomeoneElses.details()).isEqualTo(onNeverIssued.details()).isEmpty();
        }
    }

    @Nested
    @DisplayName("an account with no organizations")
    class Unaffiliated {

        @Test
        @DisplayName("reads nothing — this is the account the defect was reachable from")
        void readsNothing() {
            // The defect's actual audience: a customer, signed in with a phone number, holding no
            // membership anywhere. Before the fix this account read every organizer's KYC.
            assertThatThrownBy(() -> visibleTo(new TenantScope("customer-1", Set.of(), false),
                    THE_DOCUMENT))
                    .isInstanceOf(DomainRefusal.class);
        }
    }

    @Nested
    @DisplayName("a platform administrator")
    class Administrator {

        @Test
        @DisplayName("still reads across organizations, because review requires it")
        void adminReadsAcross() {
            // The other bound. A fix that refused everybody would pass every test above and break
            // the approvals workbench, where an admin reviews documents for organizations they are
            // deliberately not a member of.
            assertThat(visibleTo(platformAdmin(), THE_DOCUMENT)).isNotNull();
        }
    }

    private static DomainRefusal refusalFrom(TenantScope scope, String id) {
        try {
            visibleTo(scope, id);
            throw new AssertionError("expected a refusal for " + id);
        } catch (DomainRefusal refusal) {
            return refusal;
        }
    }
}
