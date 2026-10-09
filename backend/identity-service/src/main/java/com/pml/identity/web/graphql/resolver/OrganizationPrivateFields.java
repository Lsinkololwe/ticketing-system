package com.pml.identity.web.graphql.resolver;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsData;
import com.netflix.graphql.dgs.DgsDataFetchingEnvironment;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.valueobject.BusinessAddress;
import com.pml.identity.domain.valueobject.OrganizationSettings;
import com.pml.identity.domain.valueobject.PayoutConfig;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import reactor.core.publisher.Mono;

import java.util.function.Function;

/**
 * An organization's business, contact, payout and configuration details, answered only to members
 * of that organization and to platform administrators.
 *
 * <p>Any signed-in user can look up an organization by id, slug or owner, and its public profile —
 * name, logo, description — stays readable to them. The fields here are resolved to null for
 * everyone else, so a tax number, a payout account or the organization's internal settings never
 * leave the organization through a lookup.
 */
@DgsComponent
public class OrganizationPrivateFields {

    @DgsData(parentType = "Organization", field = "taxId")
    public Mono<String> taxId(DgsDataFetchingEnvironment env) {
        return forMembers(env, Organization::getTaxId);
    }

    @DgsData(parentType = "Organization", field = "businessRegistrationNumber")
    public Mono<String> businessRegistrationNumber(DgsDataFetchingEnvironment env) {
        return forMembers(env, Organization::getBusinessRegistrationNumber);
    }

    @DgsData(parentType = "Organization", field = "businessPhone")
    public Mono<String> businessPhone(DgsDataFetchingEnvironment env) {
        return forMembers(env, Organization::getBusinessPhone);
    }

    @DgsData(parentType = "Organization", field = "businessEmail")
    public Mono<String> businessEmail(DgsDataFetchingEnvironment env) {
        return forMembers(env, Organization::getBusinessEmail);
    }

    @DgsData(parentType = "Organization", field = "businessAddress")
    public Mono<BusinessAddress> businessAddress(DgsDataFetchingEnvironment env) {
        return forMembers(env, Organization::getBusinessAddress);
    }

    @DgsData(parentType = "Organization", field = "payoutConfig")
    public Mono<PayoutConfig> payoutConfig(DgsDataFetchingEnvironment env) {
        return forMembers(env, Organization::getPayoutConfig);
    }

    /**
     * The schema gives settings an {@code id} and an {@code organizationId}, both non-null, but the
     * settings are embedded in the organization and carry neither; a missing one blanked every page
     * that read them. They are the organization's own id.
     */
    @DgsData(parentType = "Organization", field = "settings")
    public Mono<java.util.Map<String, Object>> settings(DgsDataFetchingEnvironment env) {
        return forMembers(env, org -> {
            OrganizationSettings settings = org.getSettings();
            if (settings == null) {
                return null;
            }
            java.util.Map<String, Object> view = new java.util.LinkedHashMap<>(
                    SETTINGS_MAPPER.convertValue(settings, new com.fasterxml.jackson.core.type.TypeReference<java.util.Map<String, Object>>() { }));
            view.put("id", org.getId());
            view.put("organizationId", org.getId());
            return view;
        });
    }

    private static final com.fasterxml.jackson.databind.ObjectMapper SETTINGS_MAPPER =
            new com.fasterxml.jackson.databind.ObjectMapper().findAndRegisterModules();

    @DgsData(parentType = "Organization", field = "keycloakGroupId")
    public Mono<String> keycloakGroupId(DgsDataFetchingEnvironment env) {
        return forMembers(env, Organization::getKeycloakGroupId);
    }

    @DgsData(parentType = "Organization", field = "rejectionReason")
    public Mono<String> rejectionReason(DgsDataFetchingEnvironment env) {
        return forMembers(env, Organization::getRejectionReason);
    }

    /** The field's value when the caller belongs to the organization or is a platform administrator. */
    static <T> Mono<T> forMembers(DgsDataFetchingEnvironment env, Function<Organization, T> field) {
        Organization organization = env.getSource();
        return visibleTo(organization, field);
    }

    /**
     * The applicant who owns the organization. Memberships are created on approval, so an applicant
     * has none, and without this rule their own draft reads back with its business details nulled.
     */
    private static boolean isOwner(com.pml.shared.security.tenancy.TenantScope scope, Organization organization) {
        return scope.subject() != null && scope.subject().equals(organization.getOwnerId());
    }

    public static <T> Mono<T> visibleTo(Organization organization, Function<Organization, T> field) {
        if (organization == null) {
            return Mono.empty();
        }
        return CurrentTenantScope.get()
                .filter(scope -> scope.permits(organization.getId()) || isOwner(scope, organization))
                .mapNotNull(scope -> field.apply(organization));
    }
}
