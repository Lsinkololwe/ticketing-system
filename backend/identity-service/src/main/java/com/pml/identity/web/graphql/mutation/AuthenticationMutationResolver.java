package com.pml.identity.web.graphql.mutation;

import com.pml.identity.web.graphql.dto.auth.AuthPayload;
import com.pml.identity.web.graphql.dto.auth.RegisterInput;
import com.pml.identity.web.graphql.dto.auth.TokenValidation;
import com.pml.identity.domain.model.User;
import com.pml.identity.infrastructure.keycloak.KeycloakAuthService;
import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.service.UserService;
import com.pml.shared.constants.UserType;
import com.pml.shared.security.SecurityContextUtils;
import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * GraphQL Mutation Resolver for Authentication operations.
 *
 * Handles user authentication via Keycloak OAuth2/OIDC:
 * - login: Authenticate user with email/password
 * - register: Create new user account
 * - refreshToken: Get new tokens using refresh token
 * - logout: Invalidate user session
 * - validateToken: Verify token validity and extract claims
 */
@Slf4j
@DgsComponent
@RequiredArgsConstructor
public class AuthenticationMutationResolver {

    private final KeycloakAuthService keycloakAuthService;
    private final KeycloakService keycloakService;
    private final UserService userService;

    /**
     * Authenticate user with email and password.
     * Returns JWT access and refresh tokens along with user details.
     *
     * @param email    User's email address
     * @param password User's password
     * @return AuthPayload containing tokens and user
     */
    @DgsMutation
    public Mono<AuthPayload> login(@InputArgument String email, @InputArgument String password) {
        log.info("Login attempt for user: {}", email);

        return keycloakAuthService.authenticate(email, password, "openid profile email phone")
                .flatMap(tokenResponse -> {
                    // Find or sync user in local database
                    return userService.findByEmail(email)
                            .switchIfEmpty(Mono.defer(() -> {
                                // User authenticated in Keycloak but doesn't exist locally
                                // This could happen if user was created directly in Keycloak
                                log.warn("User authenticated but not found locally: {}", email);
                                return keycloakService.findUserByEmail(email)
                                        .flatMap(keycloakUserOpt -> {
                                            if (keycloakUserOpt.isEmpty()) {
                                                return Mono.error(new RuntimeException("User not found"));
                                            }
                                            var keycloakUser = keycloakUserOpt.get();
                                            // Create local user record
                                            User newUser = User.builder()
                                                    .id(keycloakUser.getId())  // Use Keycloak ID as MongoDB ID
                                                    .email(email)
                                                    .username(keycloakUser.getUsername())
                                                    .firstName(keycloakUser.getFirstName())
                                                    .lastName(keycloakUser.getLastName())
                                                    .emailVerified(keycloakUser.isEmailVerified())
                                                    .roles(java.util.EnumSet.of(UserType.CUSTOMER))
                                                    .active(true)
                                                    .createdAt(Instant.now())
                                                    .updatedAt(Instant.now())
                                                    .build();
                                            return userService.createUser(newUser);
                                        });
                            }))
                            .flatMap(user -> {
                                // Update last login time
                                return userService.updateLastLogin(user.getId())
                                        .thenReturn(user);
                            })
                            .map(user -> new AuthPayload(
                                    tokenResponse.getAccessToken(),
                                    tokenResponse.getRefreshToken(),
                                    (int) tokenResponse.getExpiresIn(),
                                    user
                            ));
                })
                .doOnSuccess(payload -> log.info("User logged in successfully: {}", email))
                .doOnError(error -> log.error("Login failed for user {}: {}", email, error.getMessage()));
    }

    /**
     * Register a new user account.
     * Creates user in both Keycloak and local MongoDB database.
     *
     * @param input Registration details
     * @return Created user
     */
    @DgsMutation
    public Mono<User> register(@InputArgument RegisterInput input) {
        log.info("Registering new user: {}", input.email());

        // Check if user already exists
        return userService.findByEmail(input.email())
                .flatMap(existing -> Mono.<User>error(new RuntimeException("Email already registered")))
                .switchIfEmpty(Mono.defer(() -> {
                    // All new users get CUSTOMER role by default (via builder default)
                    User newUser = User.builder()
                            .username(input.username())
                            .email(input.email())
                            .firstName(input.firstName())
                            .lastName(input.lastName())
                            .phoneNumber(input.phoneNumber())
                            .emailVerified(false)
                            .phoneVerified(false)
                            .active(true)
                            .createdAt(Instant.now())
                            .updatedAt(Instant.now())
                            .build();

                    // First create in Keycloak
                    return keycloakService.createUser(newUser, input.password())
                            .flatMap(keycloakId -> {
                                // Set the Keycloak ID as the MongoDB ID
                                newUser.setId(keycloakId);
                                // Then save to local database
                                return userService.createUser(newUser);
                            })
                            .flatMap(user -> {
                                // Send email verification
                                return keycloakService.sendVerificationEmail(user.getId())
                                        .thenReturn(user)
                                        .onErrorResume(e -> {
                                            log.warn("Failed to send verification email: {}", e.getMessage());
                                            return Mono.just(user);
                                        });
                            });
                }))
                .doOnSuccess(user -> log.info("User registered successfully: {}", user.getEmail()))
                .doOnError(error -> log.error("Registration failed: {}", error.getMessage()));
    }

    /**
     * Refresh access token using a refresh token.
     * Returns new JWT access and refresh tokens.
     *
     * @param refreshToken The refresh token
     * @return AuthPayload containing new tokens and user
     */
    @DgsMutation
    public Mono<AuthPayload> refreshToken(@InputArgument String refreshToken) {
        log.debug("Refreshing token");

        return keycloakAuthService.refreshToken(refreshToken)
                .flatMap(tokenResponse -> {
                    // Introspect the new access token to get user info
                    return keycloakAuthService.introspectToken(tokenResponse.getAccessToken())
                            .flatMap(introspection -> {
                                if (!introspection.isActive()) {
                                    return Mono.error(new RuntimeException("Token introspection failed"));
                                }
                                String email = introspection.getEmail();
                                return userService.findByEmail(email)
                                        .map(user -> new AuthPayload(
                                                tokenResponse.getAccessToken(),
                                                tokenResponse.getRefreshToken(),
                                                (int) tokenResponse.getExpiresIn(),
                                                user
                                        ));
                            });
                })
                .doOnSuccess(payload -> log.debug("Token refreshed successfully"))
                .doOnError(error -> log.error("Token refresh failed: {}", error.getMessage()));
    }

    /**
     * Logout user and invalidate their session in Keycloak.
     * Requires the user to be authenticated to get their refresh token from context.
     *
     * @return true if logout was successful
     */
    @DgsMutation
    public Mono<Boolean> logout() {
        return SecurityContextUtils.getAuthenticationContext()
                .flatMap(ctx -> {
                    log.info("Logging out user: {}", ctx.getEmail());

                    // Get the refresh token from the session if available
                    // Note: The actual refresh token should be passed by the client
                    // This implementation logs out at the Keycloak level using session state
                    return SecurityContextUtils.getClaim("session_state")
                            .flatMap(sessionState -> keycloakService.endUserSession(sessionState)
                                    .thenReturn(true)
                                    .onErrorResume(e -> {
                                        log.warn("Session invalidation failed: {}", e.getMessage());
                                        return Mono.just(true); // Logout should not fail from user perspective
                                    })
                                    .doOnSuccess(v -> log.info("User logged out successfully: {}", ctx.getEmail())))
                            .switchIfEmpty(Mono.just(true));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    log.warn("Logout called without authentication");
                    return Mono.just(true); // No session to invalidate
                }));
    }

    /**
     * Validate a token and extract its claims.
     * Used by services to verify token validity.
     *
     * @param token The token to validate
     * @return TokenValidation with validity status and claims
     */
    @DgsMutation
    public Mono<TokenValidation> validateToken(@InputArgument String token) {
        log.debug("Validating token");

        return keycloakAuthService.introspectToken(token)
                .map(introspection -> {
                    if (!introspection.isActive()) {
                        return new TokenValidation(false, null, null, null);
                    }

                    // Extract user roles from realm roles
                    java.util.Set<String> roles = new java.util.HashSet<>();
                    if (introspection.getRealmAccess() != null &&
                        introspection.getRealmAccess().getRoles() != null) {
                        for (String role : introspection.getRealmAccess().getRoles()) {
                            // Filter to only include our defined user roles
                            if (isValidUserRole(role)) {
                                roles.add(role);
                            }
                        }
                    }
                    // Ensure CUSTOMER is always present
                    roles.add("CUSTOMER");

                    return new TokenValidation(
                            true,
                            introspection.getSub(),
                            introspection.getEmail(),
                            roles
                    );
                })
                .onErrorResume(e -> {
                    log.error("Token validation failed: {}", e.getMessage());
                    return Mono.just(new TokenValidation(false, null, null, null));
                });
    }

    /**
     * Check if a role name is a valid user role.
     */
    private boolean isValidUserRole(String role) {
        return role.equals("CUSTOMER") ||
               role.equals("ORGANIZER") ||
               role.equals("ADMIN") ||
               role.equals("SUPER_ADMIN") ||
               role.equals("SCANNER") ||
               role.equals("FINANCE");
    }
}
