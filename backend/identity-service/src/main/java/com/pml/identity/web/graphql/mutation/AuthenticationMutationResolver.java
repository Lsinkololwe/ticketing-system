package com.pml.identity.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.pml.identity.account.KeycloakUserAdminPort;
import com.pml.identity.security.revocation.MongoRevocationStore;
import com.pml.shared.security.SecurityContextUtils;
import com.pml.shared.security.revocation.RevocationType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

/**
 * The one authentication operation GraphQL keeps: {@code logout}.
 *
 * <p>Signing in is Keycloak's - the browser flow with the contact authenticator - and the app server
 * holds the tokens. The password {@code login}, {@code register}, {@code refreshToken} and
 * {@code validateToken} mutations are gone: they could not be called without a token anyway, and a
 * second way to obtain or check a token is a second thing to defend.
 */
@Slf4j
@DgsComponent
@Validated
@RequiredArgsConstructor
public class AuthenticationMutationResolver {

    private final KeycloakUserAdminPort keycloak;
    private final ObjectProvider<MongoRevocationStore> revocations;

    /**
     * Ends the caller's session: the access token's {@code jti} and the SSO session's {@code sid}
     * are recorded as revoked, so the token stops working at once in every service, and the
     * Keycloak session is ended so the next sign-in asks again.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<Boolean> logout() {
        return SecurityContextUtils.getJwt().flatMap(this::end).defaultIfEmpty(true);
    }

    private Mono<Boolean> end(Jwt jwt) {
        String jti = jwt.getId();
        String sid = jwt.getClaimAsString("sid");
        String actor = com.pml.shared.security.AccountIdentity.userIdOf(jwt);
        MongoRevocationStore store = revocations.getIfAvailable();

        Mono<Void> revoke = Mono.empty();
        if (store != null) {
            if (jti != null && !jti.isBlank()) {
                revoke = revoke.then(store.revoke(RevocationType.TOKEN, jti, "logout", actor).then());
            }
            if (sid != null && !sid.isBlank()) {
                revoke = revoke.then(store.revoke(RevocationType.SESSION, sid, "logout", actor).then());
            }
        } else {
            log.warn("Token revocation is switched off; logout can only end the Keycloak session");
        }
        Mono<Void> endSession = sid == null || sid.isBlank()
                ? Mono.empty()
                : keycloak.endSession(realmOf(jwt), sid)
                .onErrorResume(error -> {
                    // The revocation above is what stops the token; a Keycloak that cannot be reached must not fail a logout.
                    log.warn("Keycloak session {} could not be ended: {}", sid, error.toString());
                    return Mono.empty();
                });
        return revoke.then(endSession).thenReturn(true);
    }

    /** The realm that issued the token: the last segment of its issuer. Null (buyers) when unreadable. */
    private static String realmOf(Jwt jwt) {
        String issuer = jwt.getIssuer() == null ? null : jwt.getIssuer().toString();
        if (issuer == null || issuer.isBlank()) {
            return null;
        }
        return issuer.substring(issuer.lastIndexOf('/') + 1);
    }
}
