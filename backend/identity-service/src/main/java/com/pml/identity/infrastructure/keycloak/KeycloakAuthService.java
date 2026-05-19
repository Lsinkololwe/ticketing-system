package com.pml.identity.infrastructure.keycloak;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.pml.identity.config.KeycloakProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

/**
 * Service for Keycloak authentication operations.
 * Handles token exchange, refresh, and introspection.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KeycloakAuthService {

    private final KeycloakProperties keycloakProperties;
    private final WebClient.Builder webClientBuilder;

    /**
     * Authenticate user with username/email and password via Resource Owner Password Grant.
     * Note: This grant type should only be used for trusted first-party applications.
     *
     * @param username The username or email
     * @param password The password
     * @param scopes   Space-separated scopes to request
     * @return Mono with TokenResponse containing access and refresh tokens
     */
    public Mono<TokenResponse> authenticate(String username, String password, String scopes) {
        log.info("Authenticating user via Keycloak: {}", username);

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "password");
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("username", username);
        formData.add("password", password);
        if (scopes != null && !scopes.isBlank()) {
            formData.add("scope", scopes);
        }

        return webClientBuilder.build()
                .post()
                .uri(keycloakProperties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                                log.error("Authentication failed: {}", body);
                                return Mono.error(new RuntimeException("Invalid credentials"));
                            });
                })
                .bodyToMono(TokenResponse.class)
                .doOnSuccess(token -> log.info("User authenticated successfully: {}", username))
                .doOnError(error -> log.error("Authentication failed for user {}: {}", username, error.getMessage()));
    }

    /**
     * Refresh access token using a refresh token.
     *
     * @param refreshToken The refresh token
     * @return Mono with TokenResponse containing new access and refresh tokens
     */
    public Mono<TokenResponse> refreshToken(String refreshToken) {
        log.debug("Refreshing token via Keycloak");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "refresh_token");
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("refresh_token", refreshToken);

        return webClientBuilder.build()
                .post()
                .uri(keycloakProperties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .onStatus(status -> status.is4xxClientError(), response -> {
                    return response.bodyToMono(String.class)
                            .flatMap(body -> {
                                log.error("Token refresh failed: {}", body);
                                return Mono.error(new RuntimeException("Invalid refresh token"));
                            });
                })
                .bodyToMono(TokenResponse.class)
                .doOnSuccess(token -> log.debug("Token refreshed successfully"))
                .doOnError(error -> log.error("Token refresh failed: {}", error.getMessage()));
    }

    /**
     * Get a service account token for service-to-service communication.
     *
     * @param scopes Space-separated scopes to request
     * @return Mono with TokenResponse
     */
    public Mono<TokenResponse> getServiceToken(String scopes) {
        log.debug("Getting service token from Keycloak");

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("grant_type", "client_credentials");
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        if (scopes != null && !scopes.isBlank()) {
            formData.add("scope", scopes);
        }

        return webClientBuilder.build()
                .post()
                .uri(keycloakProperties.getTokenUri())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(TokenResponse.class)
                .doOnSuccess(token -> log.debug("Service token obtained successfully"));
    }

    /**
     * Introspect a token to check if it's valid.
     *
     * @param token The token to introspect
     * @return Mono with TokenIntrospectionResponse
     */
    public Mono<TokenIntrospectionResponse> introspectToken(String token) {
        log.debug("Introspecting token via Keycloak");

        String introspectionUri = keycloakProperties.getServerUrl() +
                "/realms/" + keycloakProperties.getRealm() +
                "/protocol/openid-connect/token/introspect";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("token", token);

        return webClientBuilder.build()
                .post()
                .uri(introspectionUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(TokenIntrospectionResponse.class)
                .doOnSuccess(response -> log.debug("Token introspection completed, active: {}", response.isActive()));
    }

    /**
     * Revoke a token (access or refresh).
     *
     * @param token     The token to revoke
     * @param tokenType "access_token" or "refresh_token"
     * @return Mono signaling completion
     */
    public Mono<Void> revokeToken(String token, String tokenType) {
        log.info("Revoking {} via Keycloak", tokenType);

        String revocationUri = keycloakProperties.getServerUrl() +
                "/realms/" + keycloakProperties.getRealm() +
                "/protocol/openid-connect/revoke";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("token", token);
        formData.add("token_type_hint", tokenType);

        return webClientBuilder.build()
                .post()
                .uri(revocationUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("Token revoked successfully"));
    }

    /**
     * Logout user from Keycloak (end session).
     *
     * @param refreshToken The user's refresh token
     * @return Mono signaling completion
     */
    public Mono<Void> logout(String refreshToken) {
        log.info("Logging out user via Keycloak");

        String logoutUri = keycloakProperties.getServerUrl() +
                "/realms/" + keycloakProperties.getRealm() +
                "/protocol/openid-connect/logout";

        MultiValueMap<String, String> formData = new LinkedMultiValueMap<>();
        formData.add("client_id", keycloakProperties.getClientId());
        formData.add("client_secret", keycloakProperties.getClientSecret());
        formData.add("refresh_token", refreshToken);

        return webClientBuilder.build()
                .post()
                .uri(logoutUri)
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(BodyInserters.fromFormData(formData))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.info("User logged out successfully"))
                .doOnError(e -> log.warn("Logout failed: {}", e.getMessage()))
                .onErrorResume(e -> Mono.empty()); // Logout failures shouldn't block the flow
    }

    /**
     * Token response from Keycloak.
     */
    @Data
    public static class TokenResponse {
        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        @JsonProperty("token_type")
        private String tokenType;

        @JsonProperty("expires_in")
        private long expiresIn;

        @JsonProperty("refresh_expires_in")
        private long refreshExpiresIn;

        @JsonProperty("scope")
        private String scope;

        @JsonProperty("id_token")
        private String idToken;

        @JsonProperty("not-before-policy")
        private int notBeforePolicy;

        @JsonProperty("session_state")
        private String sessionState;
    }

    /**
     * Token introspection response from Keycloak.
     */
    @Data
    public static class TokenIntrospectionResponse {
        private boolean active;

        private String sub;

        @JsonProperty("preferred_username")
        private String preferredUsername;

        private String email;

        @JsonProperty("email_verified")
        private boolean emailVerified;

        private String scope;

        @JsonProperty("client_id")
        private String clientId;

        private String username;

        @JsonProperty("token_type")
        private String tokenType;

        private long exp;

        private long iat;

        private String iss;

        private String aud;

        @JsonProperty("realm_access")
        private RealmAccess realmAccess;

        @Data
        public static class RealmAccess {
            private String[] roles;
        }
    }
}
