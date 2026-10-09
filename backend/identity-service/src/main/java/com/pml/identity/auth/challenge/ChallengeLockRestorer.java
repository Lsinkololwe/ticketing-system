package com.pml.identity.auth.challenge;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** After a start, puts back contact locks that Redis lost, from the MongoDB mirror. */
@Component
public class ChallengeLockRestorer {

    private static final Logger log = LoggerFactory.getLogger(ChallengeLockRestorer.class);

    private final ChallengeService challenges;

    public ChallengeLockRestorer(ChallengeService challenges) {
        this.challenges = challenges;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        challenges.restoreLocks()
                .subscribe(count -> log.info("Restored {} contact locks from the mirror", count),
                        error -> log.warn("Could not restore contact locks: {}", error.getClass().getSimpleName()));
    }
}
