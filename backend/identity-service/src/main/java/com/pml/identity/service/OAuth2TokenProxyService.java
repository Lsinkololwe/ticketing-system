package com.pml.identity.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.BodyInserters;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * OAuth2 Token Proxy Service.
 *
 * Proxies token requests to the Authorization Server during migration.
 * This allows the Identity Service to maintain backward compatibility
 * with existing clients while transitioning to OAuth2.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuth2TokenProxyService {

    private final WebClient.Builder webClientBuilder;

    @Value("${services.authorization-server.url:http://localhost:8084}")
    private String authServerUrl;

    @Value("${spring.security.oauth2.client.registration.identity-service.client-id:identity-service}")
    private String clientId;

    @Value("${spring.security.oauth2.client.registration.identity-service.client-secret:identity-service-secret}")
    private String clientSecret;

    /**
     * Request an OAuth2 token using Resource Owner Password Credentials grant.
     * Note: This is provided for migration purposes. New clients should use
     * the Authorization Server directly with authorization_code flow.
     *
     * @param username The username
     * @param password The password
     * @return Token response from Authorization Server
     */
    public Mono<Map<String, Object>> requestToken(String username, String password) {
        WebClient webClient = webClientBuilder.build();

        return webClient.post()
                .uri(authServerUrl + "/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .body(BodyInserters
                        .fromFormData("grant_type", "password")
                        .with("username", username)
                        .with("password", password)
                        .with("scope", "openid profile email"))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (Map<String, Object>) response)
                .doOnSuccess(response -> log.debug("Token obtained for user: {}", username))
                .doOnError(error -> log.error("Failed to obtain token for user: {}", username, error));
    }

    /**
     * Refresh an OAuth2 token.
     *
     * @param refreshToken The refresh token
     * @return Token response from Authorization Server
     */
    public Mono<Map<String, Object>> refreshToken(String refreshToken) {
        WebClient webClient = webClientBuilder.build();

        return webClient.post()
                .uri(authServerUrl + "/oauth2/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .body(BodyInserters
                        .fromFormData("grant_type", "refresh_token")
                        .with("refresh_token", refreshToken))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (Map<String, Object>) response)
                .doOnSuccess(response -> log.debug("Token refreshed successfully"))
                .doOnError(error -> log.error("Failed to refresh token", error));
    }

    /**
     * Revoke an OAuth2 token.
     *
     * @param token The token to revoke
     * @return Void when revocation is complete
     */
    public Mono<Void> revokeToken(String token) {
        WebClient webClient = webClientBuilder.build();

        return webClient.post()
                .uri(authServerUrl + "/oauth2/revoke")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .body(BodyInserters.fromFormData("token", token))
                .retrieve()
                .bodyToMono(Void.class)
                .doOnSuccess(v -> log.debug("Token revoked successfully"))
                .doOnError(error -> log.error("Failed to revoke token", error));
    }

    /**
     * Introspect an OAuth2 token.
     *
     * @param token The token to introspect
     * @return Token introspection response
     */
    public Mono<Map<String, Object>> introspectToken(String token) {
        WebClient webClient = webClientBuilder.build();

        return webClient.post()
                .uri(authServerUrl + "/oauth2/introspect")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .headers(headers -> headers.setBasicAuth(clientId, clientSecret))
                .body(BodyInserters.fromFormData("token", token))
                .retrieve()
                .bodyToMono(Map.class)
                .map(response -> (Map<String, Object>) response)
                .doOnSuccess(response -> log.debug("Token introspected: active={}", response.get("active")))
                .doOnError(error -> log.error("Failed to introspect token", error));
    }
}
