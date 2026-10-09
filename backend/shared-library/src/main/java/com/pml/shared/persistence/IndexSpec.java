package com.pml.shared.persistence;

import org.bson.Document;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * One row of the platform's index registry, as something the application can create.
 *
 * <h2>Why these are declared and not left to {@code @Indexed}</h2>
 * The annotations produce single-field indexes named after the field, and that is all they can
 * produce. Every compound index, every TTL, and the sparse-unique pairs are outside what
 * the annotation expresses — so a platform relying on annotations alone has none of them.
 *
 * <p>The gap is not a performance footnote. A unique index is a <em>constraint, not an
 * optimisation: each one is a race the application cannot win by checking first</em>. A missing unique index does not slow anything down; it permits the duplicate.
 */
public record IndexSpec(
        String collection,
        String name,
        Map<String, Integer> keys,
        boolean unique,
        boolean sparse,
        Duration expireAfter,
        boolean text,
        Document partialFilter) {

    /** {@code { field: 1 }} ascending, or {@code -1} descending. */
    public static Builder on(String collection, String name) {
        return new Builder(collection, name);
    }

    /**
     * A text index's field weights: how much a match in each field counts towards the score.
     * Empty for any other index.
     */
    public Document weights() {
        Document weights = new Document();
        if (text) {
            keys.forEach(weights::append);
        }
        return weights;
    }

    /** The {@code createIndexes} key document. */
    public Document keyDocument() {
        Document key = new Document();
        keys.forEach((field, direction) -> key.append(field, text ? "text" : (Object) direction));
        return key;
    }

    public static final class Builder {

        private final String collection;
        private final String name;
        private final Map<String, Integer> keys = new LinkedHashMap<>();
        private boolean unique;
        private boolean sparse;
        private Duration expireAfter;
        private boolean text;
        private Document partialFilter;

        private Builder(String collection, String name) {
            this.collection = collection;
            this.name = name;
        }

        public Builder asc(String field) {
            keys.put(field, 1);
            return this;
        }

        public Builder desc(String field) {
            keys.put(field, -1);
            return this;
        }

        public Builder unique() {
            this.unique = true;
            return this;
        }

        /**
         * Sparse: documents in which the field is <b>absent</b> are left out of the index.
         *
         * <h2>Sparse does not mean "ignore nulls"</h2>
         * It keys on absence, not on emptiness. A document that stores {@code phoneNumber: null}
         * <em>has</em> the field, so a sparse index still indexes it — and under {@code unique},
         * the second such document collides with the first on the key {@code null}.
         *
         * <p>That distinction is invisible until the data contains explicit nulls, which is the
         * normal result of mapping a Java object with a null field. For an optional field, use
         * {@link #partialWhereTypeIs} instead; sparse is right only when the field is genuinely
         * omitted from the document.</p>
         */
        public Builder sparse() {
            this.sparse = true;
            return this;
        }

        /**
         * Indexes only the documents matching {@code filter} — the correct tool for a unique
         * constraint on an optional field.
         *
         * <p>Unlike {@link #sparse}, a partial filter can exclude a stored {@code null}, so
         * "unique among the users who have one" is expressible.</p>
         */
        public Builder partial(Document filter) {
            this.partialFilter = filter;
            return this;
        }

        /**
         * {@code partial({field: {$type: <bsonType>}})} — the usual spelling of "where this
         * field actually holds a value".
         *
         * <p>A type test excludes both the absent field and the stored {@code null} in one
         * condition, which {@code $exists: true} on its own does not.</p>
         */
        public Builder partialWhereTypeIs(String field, String bsonType) {
            return partial(new Document(field, new Document("$type", bsonType)));
        }

        /** TTL. {@link Duration#ZERO} means "expire at the instant in the field" . */
        public Builder expireAfter(Duration duration) {
            this.expireAfter = duration;
            return this;
        }

        public Builder text(String... fields) {
            this.text = true;
            for (String field : fields) {
                keys.put(field, 1);
            }
            return this;
        }

        /** A text index field whose matches count {@code weight} times a weight-1 field's. */
        public Builder text(String field, int weight) {
            this.text = true;
            keys.put(field, weight);
            return this;
        }

        public IndexSpec build() {
            if (keys.isEmpty()) {
                throw new IllegalStateException("index " + name + " has no keys");
            }
            // Insertion order, and it is not a detail. A compound index is usable as a prefix,
            // so { userId: 1, status: 1 } serves a query on userId alone and
            // { status: 1, userId: 1 } does not — they are different indexes with the same
            // fields. Map.copyOf returns a hash-ordered map, which means the order declared
            // here would be discarded and could differ between JVM runs; the server then
            // rejects the second run with IndexKeySpecsConflict, having built the wrong index
            // on the first.
            return new IndexSpec(collection, name,
                    Collections.unmodifiableMap(new LinkedHashMap<>(keys)),
                    unique, sparse, expireAfter, text, partialFilter);
        }
    }
}
