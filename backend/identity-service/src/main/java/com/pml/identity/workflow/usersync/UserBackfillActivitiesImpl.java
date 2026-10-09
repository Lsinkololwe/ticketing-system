package com.pml.identity.workflow.usersync;

import com.pml.identity.infrastructure.keycloak.KeycloakService;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Change;
import com.pml.identity.workflow.usersync.UserSyncWorkflow.Kind;
import io.temporal.spring.boot.ActivityImpl;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Duration;
import java.util.List;

/**
 * The backfill activities: Keycloak's admin API for the page, {@link UserSyncProcess}
 * for the hand-off.
 */
@Component
@ActivityImpl(taskQueues = TaskQueues.ONBOARDING)
public class UserBackfillActivitiesImpl implements UserBackfillActivities {

    private static final Duration AWAIT = Duration.ofSeconds(55);

    private final KeycloakService keycloak;
    private final UserSyncProcess userSync;
    private final Clock clock;

    public UserBackfillActivitiesImpl(KeycloakService keycloak, UserSyncProcess userSync, Clock clock) {
        this.keycloak = keycloak;
        this.userSync = userSync;
        this.clock = clock;
    }

    @Override
    public List<String> page(int offset, int size) {
        return await(keycloak.getAllUsers(offset, size).map(UserRepresentation::getId).collectList());
    }

    @Override
    public void enqueue(String backfillId, List<String> keycloakUserIds) {
        long now = clock.millis();
        await(Flux.fromIterable(keycloakUserIds)
                .concatMap(id -> userSync.accept(id,
                        new Change(UserBackfillRules.eventId(backfillId, id), Kind.SYNC, false, now, null)))
                .then(Mono.just(Boolean.TRUE)));
    }

    private static <T> T await(Mono<T> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
