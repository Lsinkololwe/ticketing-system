package com.pml.identity.workflow.repair;

import com.pml.identity.account.AccountRepair;
import com.pml.identity.infrastructure.temporal.TaskQueues;
import com.pml.shared.workflow.Refusals;
import io.temporal.spring.boot.ActivityImpl;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;

@Component
@ActivityImpl(taskQueues = TaskQueues.ACCOUNT)
public class AccountRepairActivitiesImpl implements AccountRepairActivities {

    private static final Duration AWAIT = Duration.ofMinutes(4);

    private final AccountRepair repair;

    public AccountRepairActivitiesImpl(AccountRepair repair) {
        this.repair = repair;
    }

    @Override
    public Map<String, Long> repairAccounts() {
        return await(repair.repairAccounts());
    }

    @Override
    public Map<String, Long> repairKeycloakUsers() {
        return await(repair.repairKeycloakUsers());
    }

    @Override
    public Map<String, Long> repairStale() {
        return await(repair.repairStale());
    }

    private static Map<String, Long> await(reactor.core.publisher.Mono<Map<String, Long>> work) {
        try {
            return work.block(AWAIT);
        } catch (RuntimeException error) {
            throw Refusals.forActivity(error);
        }
    }
}
