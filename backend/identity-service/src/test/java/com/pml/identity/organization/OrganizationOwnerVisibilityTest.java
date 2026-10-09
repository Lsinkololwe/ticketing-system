package com.pml.identity.organization;

import com.pml.identity.domain.model.Organization;
import com.pml.identity.web.graphql.resolver.OrganizationPrivateFields;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An applicant has no membership until approval, yet must read the business details they entered:
 * the onboarding review step reported every field as missing because the private fields resolved
 * to null for the organization's own owner.
 */
@Tag("L1")
@Tag("ET-ORG-002")
@DisplayName("ET-ORG-002 · the owner of an organization reads its private fields before any membership exists")
class OrganizationOwnerVisibilityTest {

    private static final String OWNER = "dc1415d9-d020-45f0-9f97-32cb721ad830";

    private static Organization organization() {
        Organization org = new Organization();
        org.setId("6ac424255e2b6cdaab5ce55d");
        org.setOwnerId(OWNER);
        org.setBusinessEmail("events@example.test");
        return org;
    }

    private static String emailAs(TenantScope scope) {
        return OrganizationPrivateFields.visibleTo(organization(), Organization::getBusinessEmail)
                .contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope)))
                .block();
    }

    @Test
    @DisplayName("the owner with no memberships reads it")
    void ownerReads() {
        assertThat(emailAs(TenantScope.of(OWNER, Set.of()))).isEqualTo("events@example.test");
    }

    @Test
    @DisplayName("a stranger with no memberships still gets nothing")
    void strangerDoesNot() {
        assertThat(emailAs(TenantScope.of("someone-else", Set.of()))).isNull();
    }
}
