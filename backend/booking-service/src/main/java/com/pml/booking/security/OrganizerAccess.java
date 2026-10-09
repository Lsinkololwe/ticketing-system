package com.pml.booking.security;

import com.pml.booking.infrastructure.client.CatalogServiceClient;
import com.pml.booking.infrastructure.client.IdentityServiceClient;
import com.pml.shared.dto.EventSummaryDto;
import com.pml.shared.dto.authorization.AuthorizationRequest;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TenantBoundary;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.Permission;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.Set;

/**
 * Who may see or do what on an organization's or an event's data, decided server side.
 *
 * <p>Two locks, as everywhere in this service. The token's memberships ({@link CallerScope}) say
 * which organizations the caller belongs to at all; identity then says whether the caller's role
 * (or an event grant) holds the specific {@link Permission}. A caller who fails the first lock is
 * answered as if the organization or event did not exist, so the response never confirms a
 * resource the caller has no business knowing about; one who passes it but lacks the permission is
 * told so, because belonging to the organization already shows the resource exists.
 */
@Component
public class OrganizerAccess {

    private final CatalogServiceClient catalog;
    private final IdentityServiceClient identity;

    public OrganizerAccess(CatalogServiceClient catalog, IdentityServiceClient identity) {
        this.catalog = catalog;
        this.identity = identity;
    }

    /** The caller's authority names, for example {@code ROLE_ADMIN}. */
    public static Mono<List<String>> authorities() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication() == null ? List.<String>of()
                        : context.getAuthentication().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .defaultIfEmpty(List.of());
    }

    /** Whether the caller's platform roles alone carry {@code permission}. */
    public static Mono<Boolean> platformGrants(Permission permission) {
        return authorities().map(roles -> Permission.grantedByPlatformRoles(roles).contains(permission));
    }

    /** Whether the caller holds a platform staff role (admin, finance or super admin). */
    public static Mono<Boolean> isPlatformStaff() {
        return authorities().map(roles -> roles.stream().anyMatch(role ->
                role.equals("ROLE_ADMIN") || role.equals("ROLE_FINANCE") || role.equals("ROLE_SUPER_ADMIN")));
    }

    /** The event, once the caller is shown to hold {@code permission} on it. */
    public Mono<EventSummaryDto> requireEvent(String eventId, Permission permission) {
        if (eventId == null || eventId.isBlank()) {
            return Mono.error(TenantBoundary.refuse(ErrorCode.EVENT_UNKNOWN, "event"));
        }
        return catalog.getEventById(eventId)
                .onErrorResume(WebClientResponseException.NotFound.class, missing -> Mono.empty())
                .switchIfEmpty(Mono.error(() -> TenantBoundary.refuse(ErrorCode.EVENT_UNKNOWN, "event " + eventId)))
                .flatMap(event -> platformGrants(permission).flatMap(platform -> platform
                        ? Mono.just(event)
                        : SecurityContextUtils.requireCurrentUserId().flatMap(userId -> identity.checkAuthorization(
                                        AuthorizationRequest.builder()
                                                .userId(userId)
                                                .eventId(eventId)
                                                .organizationId(event.getOrganizationId())
                                                .requiredPermission(permission.code())
                                                .build())
                                .flatMap(result -> {
                                    if (result.isAuthorized()) {
                                        return Mono.just(event);
                                    }
                                    return Mono.error(result.getGrantingRole() != null
                                            ? new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                                                    "requires " + permission.code() + " on this event")
                                            : TenantBoundary.refuse(ErrorCode.EVENT_UNKNOWN, "event " + eventId));
                                }))));
    }

    /**
     * The organizations the caller may read as {@code permission} holder, narrowed to
     * {@code requested} when one is named. An empty set means "every organization" and is only ever
     * returned to a platform administrator who named none.
     */
    public Mono<Set<String>> requireOrganizations(String requested, Permission permission) {
        return CallerScope.organizationIds(requested, ErrorCode.ORGANIZATION_UNKNOWN)
                .flatMap(organizations -> platformGrants(permission).flatMap(platform -> platform || organizations.isEmpty()
                        ? Mono.just(organizations)
                        : SecurityContextUtils.requireCurrentUserId().flatMapMany(userId -> Flux.fromIterable(organizations)
                                        .concatMap(organizationId -> identity.checkAuthorization(AuthorizationRequest.builder()
                                                        .userId(userId)
                                                        .organizationId(organizationId)
                                                        .requiredPermission(permission.code())
                                                        .build())
                                                .filter(result -> result.isAuthorized())
                                                .map(result -> organizationId)))
                                .collect(java.util.stream.Collectors.toCollection(java.util.LinkedHashSet::new))
                                .flatMap(allowed -> allowed.isEmpty()
                                        ? Mono.<Set<String>>error(new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                                                "requires " + permission.code()))
                                        : Mono.<Set<String>>just(allowed))));
    }

    /** Whether the token names {@code organizationId} among the caller's memberships (or the caller is platform staff). */
    public static Mono<Boolean> belongsTo(String organizationId) {
        return CurrentTenantScope.get().map(scope -> scope.permits(organizationId));
    }
}
