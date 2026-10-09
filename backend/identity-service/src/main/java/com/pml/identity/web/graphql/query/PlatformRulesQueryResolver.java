package com.pml.identity.web.graphql.query;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsQuery;
import com.pml.identity.domain.model.Organization;
import com.pml.identity.domain.model.User;
import com.pml.identity.repository.OrganizationRepository;
import com.pml.identity.repository.UserRepository;
import com.pml.identity.service.OrganizationMemberService;
import com.pml.identity.service.PlatformRulesAssembler;
import com.pml.identity.web.graphql.dto.platform.PlatformRules;
import com.pml.identity.web.graphql.dto.platform.PublicPlatformRules;
import com.pml.shared.config.PlatformConfigurationReader;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.SecurityContextUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import reactor.core.publisher.Mono;

/**
 * {@code platformRules}: what the platform currently requires of organizers and buyers, readable
 * by any signed-in caller. The values are administrator-owned and live in catalog-service's
 * settings document; this reads them (read-only, through shared-library) and adds the commission
 * that applies to the caller's own organization. Nothing here is personal data.
 */
@DgsComponent
@RequiredArgsConstructor
public class PlatformRulesQueryResolver {

    private final PlatformConfigurationReader settings;
    private final OrganizationMemberService members;
    private final OrganizationRepository organizations;
    private final UserRepository users;

    @DgsQuery
    @PreAuthorize("isAuthenticated()")
    public Mono<PlatformRules> platformRules() {
        return settings.rules()
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.CONFIGURATION_KEY_UNKNOWN,
                        "platform rules are not configured; catalog-service seeds them at startup")))
                .flatMap(view -> Mono.zip(
                        callersOrganizationRate(),
                        updatedByName(view.getUpdatedBy()),
                        (rate, name) -> PlatformRulesAssembler.assemble(
                                view, rate.orElse(null), name.orElse(null))));
    }

    /**
     * The buyer-facing subset, for a signed-out caller. Deliberately carries no {@code @PreAuthorize}:
     * it is the one identity operation anonymous callers may reach (see PublicOperationFilter, which
     * admits nothing else without a token and rate limits this).
     */
    @DgsQuery
    public Mono<PublicPlatformRules> publicPlatformRules() {
        return settings.rules()
                .switchIfEmpty(Mono.error(() -> new TranslatedRefusal(ErrorCode.CONFIGURATION_KEY_UNKNOWN,
                        "platform rules are not configured; catalog-service seeds them at startup")))
                .map(PlatformRulesAssembler::assemblePublic);
    }

    /** The commission fraction of the organization the caller belongs to, when it has its own. */
    private Mono<java.util.Optional<Double>> callersOrganizationRate() {
        return SecurityContextUtils.getCurrentUserId()
                .flatMap(userId -> members.findActiveByUser(userId).next())
                .flatMap(member -> organizations.findById(member.getOrganizationId()))
                .mapNotNull((Organization org) -> org.getPayoutConfig() == null ? null
                        : org.getPayoutConfig().getCommissionRate())
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty());
    }

    /** A person's display name; an id that resolves to nobody gives no name rather than the id. */
    private Mono<java.util.Optional<String>> updatedByName(String adminId) {
        if (adminId == null || adminId.isBlank()) {
            return Mono.just(java.util.Optional.empty());
        }
        return users.findById(adminId)
                .map((User user) -> user.getDisplayName() != null && !user.getDisplayName().isBlank()
                        ? user.getDisplayName() : user.getFullName())
                .filter(name -> !name.isBlank())
                .map(java.util.Optional::of)
                .defaultIfEmpty(java.util.Optional.empty());
    }
}
