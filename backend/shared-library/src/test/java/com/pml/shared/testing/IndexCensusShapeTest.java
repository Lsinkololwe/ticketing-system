package com.pml.shared.testing;

import com.pml.shared.persistence.IndexSpec;
import org.bson.Document;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** How the census compares indexes: by behaviour, with field order significant and names ignored. Pure values. */
@Tag("L1")
@Tag("ET-PLT-002")
@DisplayName("The index census compares indexes by shape")
class IndexCensusShapeTest {

    @Test
    @DisplayName("Field order is part of an index; the name is not")
    void orderMattersNameDoesNot() {
        IndexCensus.Shape ab = IndexCensus.of("c", new Document("name", "one").append("key", new Document("a", 1).append("b", 1)));
        IndexCensus.Shape abRenamed = IndexCensus.of("c", new Document("name", "two").append("key", new Document("a", 1).append("b", 1)));
        IndexCensus.Shape ba = IndexCensus.of("c", new Document("name", "one").append("key", new Document("b", 1).append("a", 1)));

        assertThat(ab).isEqualTo(abRenamed).isNotEqualTo(ba);
    }

    @Test
    @DisplayName("1, 1.0 and a 64-bit 1 are the same direction; options that change behaviour are not ignored")
    void numbersNormaliseOptionsCount() {
        IndexCensus.Shape plain = IndexCensus.of("c", new Document("key", new Document("a", 1)));
        assertThat(IndexCensus.of("c", new Document("key", new Document("a", 1.0)))).isEqualTo(plain);
        assertThat(IndexCensus.of("c", new Document("key", new Document("a", 1L)))).isEqualTo(plain);
        assertThat(IndexCensus.of("c", new Document("key", new Document("a", 1)).append("unique", true))).isNotEqualTo(plain);
        assertThat(IndexCensus.of("c", new Document("key", new Document("a", 1)).append("expireAfterSeconds", 0))).isNotEqualTo(plain);
        assertThat(IndexCensus.of("c", new Document("key", new Document("a", -1)))).isNotEqualTo(plain);
    }

    @Test
    @DisplayName("A registry entry has the shape the server reports once it is built, text indexes included")
    void specShapeMatchesLiveShape() {
        IndexSpec ttl = IndexSpec.on("c", "idx").asc("expiresAt").expireAfter(Duration.ZERO).build();
        assertThat(IndexCensus.of(ttl)).isEqualTo(IndexCensus.of("c", new Document("key", new Document("expiresAt", 1))
                .append("expireAfterSeconds", 0)));

        IndexSpec text = IndexSpec.on("c", "search").text("title", "description").build();
        assertThat(IndexCensus.of(text)).isEqualTo(IndexCensus.of("c", new Document("key",
                new Document("_fts", "text").append("_ftsx", 1))
                .append("weights", new Document("description", 1).append("title", 1))));
    }

    @Test
    @DisplayName("Missing lists what the expected set has and the actual set lacks, and nothing else")
    void missingIsADifference() {
        IndexCensus.Shape a = IndexCensus.of("c", new Document("key", new Document("a", 1)));
        IndexCensus.Shape b = IndexCensus.of("c", new Document("key", new Document("b", 1)));
        IndexCensus.Shape extra = IndexCensus.of("c", new Document("key", new Document("z", 1)));

        assertThat(IndexCensus.missing(List.of(a, b), List.of(a, extra))).containsExactly(b);
        assertThat(IndexCensus.missing(List.of(a), List.of(a))).isEmpty();
    }
}
