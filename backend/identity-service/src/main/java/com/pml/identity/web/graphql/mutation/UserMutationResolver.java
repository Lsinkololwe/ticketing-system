package com.pml.identity.web.graphql.mutation;

import com.netflix.graphql.dgs.DgsComponent;
import com.netflix.graphql.dgs.DgsMutation;
import com.netflix.graphql.dgs.InputArgument;
import com.pml.identity.account.AccountService;
import com.pml.identity.domain.model.User;
import com.pml.identity.exception.UserNotFoundException;
import com.pml.identity.service.UserService;
import com.pml.identity.service.UserSyncService;
import com.pml.identity.web.graphql.dto.UpdateUserInput;
import com.pml.identity.workflow.usersync.UserBackfillProcess;
import com.pml.shared.constants.UserType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.security.SecurityContextUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import reactor.core.publisher.Mono;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * GraphQL mutations on accounts: the caller's own profile, and the administrative operations.
 *
 * <p>There is nothing here for signing in, verifying a contact or changing a password. A buyer
 * proves a contact through the challenge flow and Keycloak signs them in; a contact is added or
 * changed through its own verified process; staff passwords are Keycloak's.
 */
@Slf4j
@DgsComponent
@Validated
@RequiredArgsConstructor
public class UserMutationResolver {

    private final UserService userService;
    private final AccountService accounts;
    private final UserSyncService userSyncService;
    private final UserBackfillProcess userBackfillProcess;

    // ==========================================
    // Profile
    // ==========================================

    /**
     * The caller's own profile: {@code updateMyProfile(input) AUTHENTICATED User!}.
     *
     * <p>DGS binds a resolver to its SDL field by method name, so this method's name is the
     * operation's name. The input is the typed {@code UpdateUserInput}, never an untyped map: it
     * names exactly what a user may change about themselves - names and a display name, and no
     * contact - so no field becomes writable by accident.
     *
     * <p>The subject comes from the token and there is no id argument - the CWE-639 rule
     * {@code ImplicitSubjectOperationTest} enforces for every {@code my*} operation.
     */
    @DgsMutation
    @PreAuthorize("isAuthenticated()")
    public Mono<User> updateMyProfile(@Valid @InputArgument UpdateUserInput input) {
        return SecurityContextUtils.requireCurrentUserId()
                .flatMap(userService::findBySubject)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.USER_UNKNOWN, "no account for the token")))
                .flatMap(com.pml.identity.account.AccountRequestGate::admit)
                .flatMap(account -> userService.updateProfile(account.getId(), profile(input)));
    }

    // ==========================================
    // Administration
    // ==========================================

    /**
     * Suspend an account: {@code suspendUser(id, reason) ADMIN User!}.
     *
     * <p>The SDL declares it, so it must be bound: an administrative operation the schema
     * advertises and generated clients offer, but the server does not perform, leaves a support
     * agent calling it with an error rather than a suspended account.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> suspendUser(@InputArgument String id, @InputArgument String reason) {
        return actor().flatMap(admin -> accounts.suspend(id, reason, admin))
                .doOnSuccess(user -> log.info("Account {} suspended", id));
    }

    /**
     * Lift a suspension; the counterpart of {@code suspendUser}. A suspension with no documented
     * way back is a support burden at best and a lock-out at worst, so the pair exists together.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> unsuspendUser(@InputArgument String id) {
        return actor().flatMap(admin -> accounts.unsuspend(id, admin))
                .doOnSuccess(user -> log.info("Account {} unsuspended", id));
    }

    /** Deactivation is a suspension: one state, one way in and out. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> deactivateUser(@InputArgument String id) {
        return actor().flatMap(admin -> accounts.suspend(id, "deactivated by an administrator", admin)).thenReturn(true);
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> activateUser(@InputArgument String id) {
        return actor().flatMap(admin -> accounts.unsuspend(id, admin)).thenReturn(true);
    }

    /** Locking is a suspension too; the reason is recorded. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> lockUser(@InputArgument String id, @InputArgument String reason) {
        return actor().flatMap(admin -> accounts.suspend(id, reason == null ? "locked by an administrator" : reason, admin))
                .thenReturn(true);
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<Boolean> unlockUser(@InputArgument String id) {
        return actor().flatMap(admin -> accounts.unsuspend(id, admin)).thenReturn(true);
    }

    /**
     * Create a platform staff user (admin only). The user is created in the staff realm and must
     * set a password and a second factor at first sign-in; buyers are never created this way - they
     * come into being by proving a contact.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> createUser(@InputArgument Map<String, Object> input) {
        Object role = input.get("role");
        return actor().flatMap(admin -> userService.createStaffUser(
                (String) input.get("email"),
                (String) input.get("firstName"),
                (String) input.get("lastName"),
                (String) input.get("password"),
                (String) input.get("phoneNumber"),
                role == null ? null : UserType.valueOf(role.toString()),
                admin));
    }

    /**
     * Removes the account from circulation: status DELETED, Keycloak disabled, sessions ended.
     * Nothing is erased and the call is safe to repeat; there is no hard delete.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> deleteUser(@InputArgument String id) {
        return actor().flatMap(admin -> userService.deleteUser(id, admin))
                .doOnSuccess(user -> log.info("Account {} deleted (soft)", id));
    }

    /** Update a user's names (admin only). Contacts are not editable here, and roles have their own mutations. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> updateUser(@InputArgument String id, @Valid @InputArgument UpdateUserInput input) {
        return userService.updateProfile(id, profile(input))
                .switchIfEmpty(Mono.error(new UserNotFoundException(id)));
    }

    // ==========================================
    // Roles
    // ==========================================

    /**
     * Add a role to a user. Keycloak is written first, then the stored copy.
     *
     * <p>OWASP: A01 admin-only; A04 role combinations validated; A09 the actor is recorded.
     */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> addUserRole(@InputArgument String userId, @InputArgument UserType role) {
        if (userId == null || userId.isBlank()) {
            return Mono.error(new IllegalArgumentException("User ID is required"));
        }
        if (role == null) {
            return Mono.error(new IllegalArgumentException("Role is required"));
        }
        if (role == UserType.CUSTOMER) {
            return userService.findById(userId).switchIfEmpty(Mono.error(new UserNotFoundException(userId)));
        }
        return actor().flatMap(admin -> userService.addRole(userId, role, admin));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> removeUserRole(@InputArgument String userId, @InputArgument UserType role) {
        if (userId == null || userId.isBlank()) {
            return Mono.error(new IllegalArgumentException("User ID is required"));
        }
        if (role == null) {
            return Mono.error(new IllegalArgumentException("Role is required"));
        }
        if (role == UserType.CUSTOMER) {
            return Mono.error(new IllegalArgumentException("CUSTOMER role cannot be removed - it is the base role for all users"));
        }
        return actor().flatMap(admin -> userService.removeRole(userId, role, admin));
    }

    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> setUserRoles(@InputArgument String userId, @InputArgument List<UserType> roles) {
        if (userId == null || userId.isBlank()) {
            return Mono.error(new IllegalArgumentException("User ID is required"));
        }
        if (roles == null || roles.isEmpty()) {
            return Mono.error(new IllegalArgumentException("At least one role is required"));
        }
        if (!roles.contains(UserType.CUSTOMER)) {
            return Mono.error(new IllegalArgumentException("CUSTOMER role must be included - it is the base role for all users"));
        }
        Set<UserType> roleSet = EnumSet.copyOf(roles);
        if (!UserType.isValidRoleCombination(roleSet)) {
            return Mono.error(new IllegalArgumentException("Invalid role combination"));
        }
        return actor().flatMap(admin -> userService.setRoles(userId, roleSet, admin));
    }

    // ==========================================
    // Keycloak sync (admin only)
    // ==========================================

    /** Refresh one user from Keycloak; use it to fix drift. */
    @DgsMutation
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPER_ADMIN')")
    public Mono<User> syncUserFromKeycloak(@InputArgument String userId) {
        log.info("Admin syncing user from Keycloak: {}", userId);
        return userSyncService.syncUser(null, userId);
    }

    /**
     * Re-syncs every Keycloak user, for recovery. Starts the {@code user-backfill} workflow and
     * answers {@code true} once Temporal has recorded the start; one already running is reached
     * rather than doubled.
     */
    @DgsMutation
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public Mono<Boolean> syncAllUsersFromKeycloak() {
        log.info("Super-admin initiated full user sync from Keycloak");
        return userBackfillProcess.start().thenReturn(true);
    }

    private static Mono<String> actor() {
        return SecurityContextUtils.getCurrentUserId().defaultIfEmpty("system");
    }

    private static User profile(UpdateUserInput input) {
        return User.builder()
                .firstName(input.firstName())
                .lastName(input.lastName())
                .displayName(input.displayName())
                .gender(input.gender())
                .build();
    }
}
