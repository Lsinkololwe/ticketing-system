package com.pml.identity.account;

import com.pml.identity.infrastructure.keycloak.KeycloakWriteFailed;
import com.pml.shared.constants.UserType;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * An in-memory Keycloak behind both account ports. It reproduces the two behaviours the account
 * process depends on - a create with an existing username reads that user back, and the server may
 * be unreachable - and records what was asked of it.
 */
public class FakeKeycloak implements KeycloakAccountPort, KeycloakUserAdminPort {

    public record Stored(String id, String username, boolean enabled, Set<String> roles, AccountAttributes attributes) {
    }

    public final Map<String, Stored> byUsername = new ConcurrentHashMap<>();
    public final Map<String, KeycloakUserView> staff = new ConcurrentHashMap<>();
    public final List<String> sessionsEnded = new CopyOnWriteArrayList<>();
    public final List<String> calls = new CopyOnWriteArrayList<>();
    public final List<String> realmsUsed = new CopyOnWriteArrayList<>();
    public final AtomicInteger creates = new AtomicInteger();
    public volatile boolean down;
    /** Fail the next N calls of any kind, then recover. */
    public final AtomicInteger failNext = new AtomicInteger();

    private <T> Mono<T> guard(String call, java.util.function.Supplier<T> work) {
        return Mono.fromCallable(() -> {
            calls.add(call);
            if (down || failNext.getAndUpdate(n -> n > 0 ? n - 1 : 0) > 0) {
                throw new KeycloakWriteFailed("Keycloak is unreachable");
            }
            return work.get();
        });
    }

    /** A Keycloak user that exists before the account process meets it (an earlier, half-finished attempt). */
    public String preexisting(String accountId) {
        String id = UUID.randomUUID().toString();
        byUsername.put(accountId, new Stored(id, accountId, true, new TreeSet<>(), null));
        return id;
    }

    public Stored user(String accountId) {
        return byUsername.get(accountId);
    }

    private Stored byId(String id) {
        return byUsername.values().stream().filter(user -> user.id().equals(id)).findFirst()
                .orElseThrow(() -> new KeycloakWriteFailed("no Keycloak user " + id));
    }

    @Override
    public Mono<String> createUser(String accountId, boolean enabled, UserType role) {
        return guard("createUser", () -> {
            Stored existing = byUsername.get(accountId);
            if (existing != null) {
                return existing.id();
            }
            creates.incrementAndGet();
            Set<String> roles = new TreeSet<>();
            if (role != null) {
                roles.add(role.name());
            }
            String id = UUID.randomUUID().toString();
            byUsername.put(accountId, new Stored(id, accountId, enabled, roles, null));
            return id;
        });
    }

    @Override
    public Mono<Void> applyAttributesAndRoles(String keycloakUserId, AccountAttributes attributes, Set<UserType> roles) {
        return guard("applyAttributesAndRoles", () -> {
            Stored user = byId(keycloakUserId);
            Set<String> merged = new TreeSet<>(user.roles());
            roles.forEach(role -> merged.add(role.name()));
            byUsername.put(user.username(), new Stored(user.id(), user.username(), user.enabled(), merged, attributes));
            return (Void) null;
        });
    }

    /** The email last written by {@code setEmail}, by Keycloak user id (null value = cleared). */
    public final Map<String, String> emails = new ConcurrentHashMap<>();

    @Override
    public Mono<Void> setEmail(String keycloakUserId, String email, boolean emailVerified) {
        return guard("setEmail", () -> {
            byId(keycloakUserId);
            if (email == null) {
                emails.remove(keycloakUserId);
            } else {
                emails.put(keycloakUserId, email);
            }
            return (Void) null;
        });
    }

    @Override
    public Mono<KeycloakAccount> findByUsername(String username) {
        return guard("findByUsername", () -> byUsername.get(username))
                .map(user -> new KeycloakAccount(user.id(), user.username(), user.enabled(), false));
    }

    @Override
    public Mono<Void> setEnabled(String keycloakUserId, boolean enabled) {
        return setEnabled(null, keycloakUserId, enabled);
    }

    @Override
    public Mono<Void> setEnabled(String realm, String keycloakUserId, boolean enabled) {
        realmsUsed.add(String.valueOf(realm));
        return guard("setEnabled:" + enabled, () -> {
            Stored user = byId(keycloakUserId);
            byUsername.put(user.username(), new Stored(user.id(), user.username(), enabled, user.roles(), user.attributes()));
            return (Void) null;
        });
    }

    @Override
    public Mono<Void> endSessions(String realm, String keycloakUserId) {
        return guard("endSessions", () -> {
            sessionsEnded.add(keycloakUserId);
            return (Void) null;
        });
    }

    @Override
    public Mono<Void> endSession(String realm, String sessionId) {
        return guard("endSession", () -> (Void) null);
    }

    @Override
    public Mono<Void> grantRealmRole(String realm, String keycloakUserId, String roleName) {
        realmsUsed.add(String.valueOf(realm));
        return guard("grant:" + roleName, () -> {
            Stored user = byId(keycloakUserId);
            Set<String> roles = new TreeSet<>(user.roles());
            roles.add(roleName);
            byUsername.put(user.username(), new Stored(user.id(), user.username(), user.enabled(), roles, user.attributes()));
            return (Void) null;
        });
    }

    @Override
    public Mono<Void> revokeRealmRole(String realm, String keycloakUserId, String roleName) {
        return guard("revoke:" + roleName, () -> {
            Stored user = byId(keycloakUserId);
            Set<String> roles = new TreeSet<>(user.roles());
            roles.remove(roleName);
            byUsername.put(user.username(), new Stored(user.id(), user.username(), user.enabled(), roles, user.attributes()));
            return (Void) null;
        });
    }

    @Override
    public Mono<KeycloakUserView> readUser(String realm, String keycloakUserId) {
        return guard("readUser", () -> staff.get(keycloakUserId));
    }

    @Override
    public Mono<Optional<String>> readAttribute(String realm, String keycloakUserId, String name) {
        return guard("readAttribute", () -> Optional.<String>empty());
    }

    @Override
    public Mono<String> createStaffUser(String username, String email, String firstName, String lastName,
                                        String temporaryPassword) {
        return guard("createStaffUser", () -> {
            String id = UUID.randomUUID().toString();
            staff.put(id, new KeycloakUserView(id, username, email, firstName, lastName, true, false, Set.of()));
            return id;
        });
    }

    public List<String> callsSnapshot() {
        return new ArrayList<>(calls);
    }
}
