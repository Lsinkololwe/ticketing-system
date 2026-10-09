package com.pml.shared.event;

/**
 * Marks a failure that retrying cannot fix.
 *
 * <h2>Why a consumer needs to say so</h2>
 * The retry budget exists for a provider that is briefly unreachable. It is the wrong answer to
 * an envelope missing a required key, a wire name this build does not know, or a payload whose
 * amount will not parse: those fail identically on every attempt, so retrying spends the budget
 * to arrive at the same dead letter three seconds later.
 *
 * <p>Implemented as a marker interface rather than a base class so an existing exception
 * hierarchy can adopt it without being re-parented.</p>
 */
public interface PermanentFailure {
}
