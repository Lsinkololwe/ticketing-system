package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.identity.service.UserSyncService;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.time.Duration;

/**
 * The sync activities, adapting {@link UserSyncService} to Temporal.
 *
 * <p>A change carries no profile fields — they are personal data and would sit in plain text in the
 * workflow's history — so {@link #syncIn} reads the user's current state from Keycloak by id. That
 * also makes out-of-order changes converge: whichever runs last writes what Keycloak holds.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.ONBOARDING)
public class UserSyncActivitiesImpl implements UserSyncActivities {

    private static final Duration AWAIT = Duration.ofSeconds(25);

    private final UserSyncService userSync;

    public UserSyncActivitiesImpl(UserSyncService userSync) {
        this.userSync = userSync;
    }

    @Override
    public void sync(String keycloakUserId) {
        syncIn(new Target(null, keycloakUserId));
    }

    @Override
    public void recordLogin(String keycloakUserId) {
        recordLoginIn(new Target(null, keycloakUserId));
    }

    @Override
    public void delete(String keycloakUserId) {
        deleteIn(new Target(null, keycloakUserId));
    }

    @Override
    public void syncIn(Target target) {
        await(userSync.syncUser(target.realm(), target.keycloakUserId()).map(user -> Boolean.TRUE).defaultIfEmpty(Boolean.TRUE));
    }

    @Override
    public void recordLoginIn(Target target) {
        await(userSync.updateLastLogin(target.realm(), target.keycloakUserId()).map(user -> Boolean.TRUE).defaultIfEmpty(Boolean.TRUE));
    }

    @Override
    public void deleteIn(Target target) {
        await(userSync.markDeleted(target.realm(), target.keycloakUserId()).thenReturn(Boolean.TRUE));
    }

    @Override
    public void recordFailure(Failure failure) {
        await(userSync.recordFailure(failure.realm(), failure.keycloakUserId(), failure.change(), failure.reason())
                .thenReturn(Boolean.TRUE));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
