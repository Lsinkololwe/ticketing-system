package com.pml.identity.web.graphql.dto;

import jakarta.validation.constraints.Size;

/** The {@code ResendContactCodeInput} GraphQL type: a challenge, or a change with a target (NEW or CURRENT). */
public record ResendContactCodeInput(@Size(max = 64) String challengeId, @Size(max = 64) String changeId,
                                     ContactCodeTarget target) {
    /** The {@code ContactCodeTarget} GraphQL enum. */
    public enum ContactCodeTarget {
        NEW, CURRENT
    }
}
