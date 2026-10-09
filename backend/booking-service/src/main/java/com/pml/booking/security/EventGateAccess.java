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
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * Decides who may work an event's gate: scan its tickets, read its admissions and review its
 * scan conflicts. That is anyone holding {@code ticket:scan} on the event — through an event
 * access grant (gate staff are usually given the check-in role on one event), through membership
 * of the event's organization, or through a platform role that carries it.
 *
 * <p>The event's owning organization comes from catalog, and the decision from identity, so the
 * caller's own claims about the event are never trusted. A caller outside the organization is
 * refused exactly as if the event did not exist; a member of it who lacks the permission is told
 * so, since the event is no secret to them.
 */
@Component
public class EventGateAccess {

    private final CatalogServiceClient catalog;
    private final IdentityServiceClient identity;

    public EventGateAccess(CatalogServiceClient catalog, IdentityServiceClient identity) {
        this.catalog = catalog;
        this.identity = identity;
    }

    /** The event, once the caller is shown to hold {@code ticket:scan} on it. */
    public Mono<EventSummaryDto> requireScan(String eventId) {
        if (eventId == null || eventId.isBlank()) {
            return Mono.error(TenantBoundary.refuse(ErrorCode.EVENT_UNKNOWN, "event"));
        }
        return catalog.getEventById(eventId)
                .onErrorResume(WebClientResponseException.NotFound.class, missing -> Mono.empty())
                .switchIfEmpty(Mono.error(() -> TenantBoundary.refuse(ErrorCode.EVENT_UNKNOWN, "event " + eventId)))
                .flatMap(event -> platformGrantsScan().flatMap(platform -> platform
                        ? Mono.just(event)
                        : SecurityContextUtils.requireCurrentUserId().flatMap(userId -> identity.checkAuthorization(
                                        AuthorizationRequest.builder()
                                                .userId(userId)
                                                .eventId(eventId)
                                                .organizationId(event.getOrganizationId())
                                                .requiredPermission(Permission.TICKET_SCAN.code())
                                                .build())
                                .flatMap(result -> {
                                    if (result.isAuthorized()) {
                                        return Mono.just(event);
                                    }
                                    // A named role means the caller belongs to the organization or holds
                                    // a grant on the event, so its existence is already known to them.
                                    return Mono.error(result.getGrantingRole() != null
                                            ? new TranslatedRefusal(ErrorCode.ACTOR_NOT_PERMITTED,
                                                    "requires " + Permission.TICKET_SCAN.code() + " on this event")
                                            : TenantBoundary.refuse(ErrorCode.EVENT_UNKNOWN, "event " + eventId));
                                }))));
    }

    /** Whether the caller's platform roles alone carry {@code ticket:scan}. */
    private static Mono<Boolean> platformGrantsScan() {
        return ReactiveSecurityContextHolder.getContext()
                .map(context -> context.getAuthentication() == null ? List.<String>of()
                        : context.getAuthentication().getAuthorities().stream().map(GrantedAuthority::getAuthority).toList())
                .map(authorities -> Permission.grantedByPlatformRoles(authorities).contains(Permission.TICKET_SCAN))
                .defaultIfEmpty(false);
    }
}
