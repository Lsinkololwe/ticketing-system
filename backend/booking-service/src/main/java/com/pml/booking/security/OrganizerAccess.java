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
import com.pml.shared.security.tenancy.PlatformWideAccess;
import com.pml.shared.security.tenancy.StaffRole;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
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

    /**
     * Whether the caller's platform role alone carries {@code permission}.
     *
     * <p>An administrator, a super administrator or a finance officer who holds a permission through
     * the platform role reads that organizer's data without being a member of the organization.
     * That is a reach across organizers like any other, so a {@code true} answer is recorded, with
     * the permission asked about as the operation. Only the staff roles whose own grants include the
     * permission are asked, so each role is admitted exactly where it always was, and a caller with
     * no staff role (or a role that does not carry the permission) gets {@code false} and no row.
     */
    public static Mono<Boolean> platformGrants(Permission permission) {
        Set<StaffRole> carrying = EnumSet.noneOf(StaffRole.class);
        for (StaffRole role : StaffRole.values()) {
            if (Permission.grantedByPlatformRoles(List.of(role.authority())).contains(permission)) {
                carrying.add(role);
            }
        }
        if (carrying.isEmpty()) {
            return Mono.just(false);
        }
        return PlatformWideAccess.isPlatformStaff(PlatformWideAccess.Reason.STAFF_PERMISSION_GRANT_READ,
                permission.code(), carrying);
    }

    /**
     * Whether the caller is platform staff (administrator, finance or super administrator).
     *
     * <p>Staff reading or acting on someone else's records is a reach across organizers or buyers,
     * so a {@code true} answer is recorded with the fixed {@code reason} the call site names; a
     * finance officer's use is recorded exactly as an administrator's. The question grants no
     * tenant scope, so finance still reads only what each call site separately lets it read.
     */
    public static Mono<Boolean> isPlatformStaff(PlatformWideAccess.Reason reason) {
        return PlatformWideAccess.isPlatformStaff(reason);
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
