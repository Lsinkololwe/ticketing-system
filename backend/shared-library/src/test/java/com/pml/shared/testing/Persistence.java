package com.pml.shared.testing;

import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

/**
 * Asserts the corpus-wide property: <strong>a refused operation persists nothing.</strong>
 *
 * <p>Every refusal test in the corpus asserts it. It exists because the common failure is not
 * "the refusal returned the wrong code" — it is that the service wrote three documents,
 * then discovered the fourth step was not allowed, and returned a tidy error over a
 * half-applied change.
 *
 * <p>Checking the response tells you the caller was refused. Only checking the collection
 * tells you the refusal was clean.
 */
public final class Persistence {

    private Persistence() {
    }

    /** Short form, reading through the template bound by {@link Harness#bind}. */
    public static void assertNothingPersisted(String collection) {
        assertNothingPersisted(Harness.template(), collection);
    }

    public static void assertNothingPersisted(ReactiveMongoTemplate template, String collection) {
        long count = countIn(template, collection);
        if (count != 0L) {
            throw new AssertionError("""
                    %s holds %d document(s) after an operation that was refused.
                    A refusal must leave nothing behind: the caller was told no, and the \
                    database was told yes.""".formatted(collection, count));
        }
    }

    /**
     * The complement — for the paths where a refusal must leave earlier, legitimate work
     * intact (a mid-batch revocation, a compensated saga step).
     */
    public static void assertExactly(String collection, long expected) {
        assertExactly(Harness.template(), collection, expected);
    }

    public static void assertExactly(ReactiveMongoTemplate template, String collection, long expected) {
        long count = countIn(template, collection);
        if (count != expected) {
            throw new AssertionError(
                    "%s holds %d document(s), expected %d".formatted(collection, count, expected));
        }
    }

    private static long countIn(ReactiveMongoTemplate template, String collection) {
        Long count = template.count(new Query(), collection).block();
        return count == null ? 0L : count;
    }
}
