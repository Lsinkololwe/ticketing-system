package com.pml.shared.migration;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.SimpleReactiveMongoDatabaseFactory;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A database written by an older build is brought up to date.
 *
 * <h2>What this is really testing</h2>
 * Not the update operators — those are MongoDB's. It is the claim that the migration set is
 * <em>complete</em>: a document missing {@code currency}, {@code version} and its type alias
 * ends up carrying all three, with the rest of its data untouched. Every one of those absences
 * is invisible until something reads the document, which is why each is asserted rather than
 * assumed.
 */
@Tag("L2")
@Tag("ET-PLT-002")
@DisplayName("ET-PLT-002 · legacy documents are brought to the shape the code now expects")
class DocumentBackfillTest {

    private static final String COLLECTION = "backfill_probe";

    private static MongoClient client;
    private static ReactiveMongoTemplate template;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = new ReactiveMongoTemplate(
                new SimpleReactiveMongoDatabaseFactory(client, "backfill_harness"));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void reset() {
        template.dropCollection(COLLECTION).block();
    }

    @Test
    @DisplayName("a document lacking currency, version and an alias gains all three")
    void legacyDocumentIsBroughtForward() {
        // The shape a stored document has when its class carried no @TypeAlias, no @Version and
        // no currency field: a fully-qualified _class and neither of the other two keys.
        template.insert(new Document("_id", "legacy-1")
                .append("_class", "com.pml.booking.domain.model.PromoCode")
                .append("code", "EARLYBIRD")
                .append("currentUses", 3), COLLECTION).block();

        DocumentBackfill.Result result = new DocumentBackfill(template)
                .rewriteClassTo(COLLECTION, "com.pml.booking.domain.model.PromoCode", "promo_codes")
                .setWhereMissing(COLLECTION, "version", 0L)
                .setWhereMissing(COLLECTION, "currency", "ZMW")
                .run()
                .block();

        assertThat(result).isNotNull();
        assertThat(result.total()).isEqualTo(3);

        Document after = stored("legacy-1");
        assertThat(after.getString("_class"))
                .as("a stored fully-qualified name stops deserialising the day the class moves")
                .isEqualTo("promo_codes");
        assertThat(after.getLong("version"))
                .as("@Version with no stored version makes Spring Data treat the document as new")
                .isZero();
        assertThat(after.getString("currency"))
                .as("@Builder.Default runs on build, not on read — an existing document has null")
                .isEqualTo("ZMW");
        assertThat(after.getInteger("currentUses"))
                .as("the backfill must not disturb the data it is not there for")
                .isEqualTo(3);
    }

    @Test
    @DisplayName("a document already in the new shape is left alone")
    void currentDocumentsAreNotRewritten() {
        template.insert(new Document("_id", "current-1")
                .append("_class", "promo_codes")
                .append("version", 7L)
                .append("currency", "ZMW"), COLLECTION).block();

        DocumentBackfill.Result result = new DocumentBackfill(template)
                .rewriteClassTo(COLLECTION, "com.pml.booking.domain.model.PromoCode", "promo_codes")
                .setWhereMissing(COLLECTION, "version", 0L)
                .setWhereMissing(COLLECTION, "currency", "ZMW")
                .run()
                .block();

        assertThat(result.total())
                .as("every operation filters on the absence of what it sets, so a second run "
                        + "reports zero — which is what makes the count worth reading")
                .isZero();

        assertThat(stored("current-1").getLong("version"))
                .as("resetting a live version to 0 would hand every in-flight writer a stale "
                        + "document that the server then accepts")
                .isEqualTo(7L);
    }

    @Test
    @DisplayName("running the backfill twice changes nothing the second time")
    void isIdempotent() {
        template.insert(new Document("_id", "legacy-2")
                .append("_class", "com.pml.booking.domain.model.PromoCode"), COLLECTION).block();

        DocumentBackfill backfill = new DocumentBackfill(template)
                .rewriteClassTo(COLLECTION, "com.pml.booking.domain.model.PromoCode", "promo_codes")
                .setWhereMissing(COLLECTION, "currency", "ZMW");

        assertThat(backfill.run().block().total()).isEqualTo(2);

        DocumentBackfill second = new DocumentBackfill(template)
                .rewriteClassTo(COLLECTION, "com.pml.booking.domain.model.PromoCode", "promo_codes")
                .setWhereMissing(COLLECTION, "currency", "ZMW");
        assertThat(second.run().block().total()).isZero();
    }

    @Test
    @DisplayName("only the named _class is rewritten, so a sibling subtype survives")
    void aDifferentSubtypeIsUntouched() {
        // Rewriting every _class starting with com.pml would collapse two shapes stored in one
        // collection into one, and nothing would report it until a read returned the wrong type.
        template.insert(new Document("_id", "other")
                .append("_class", "com.pml.booking.domain.model.SomethingElse"), COLLECTION).block();

        new DocumentBackfill(template)
                .rewriteClassTo(COLLECTION, "com.pml.booking.domain.model.PromoCode", "promo_codes")
                .run().block();

        assertThat(stored("other").getString("_class"))
                .isEqualTo("com.pml.booking.domain.model.SomethingElse");
    }

    @Test
    @DisplayName("a money field stored as a double is converted, not walked past")
    void doublesAreConvertedToDecimal() {
        // The filter this exercises previously matched only strings. A Java Double lands in BSON
        // as a double, so the conversion reported zero and looked like "nothing to do".
        template.insert(new Document("_id", "money-1").append("amount", 100.0), COLLECTION).block();

        Mono<?> converted = template.getMongoDatabase().flatMap(database -> Mono.from(
                database.getCollection(COLLECTION).updateMany(
                        new Document("amount", new Document("$type", List.of("string", "double", "int", "long"))),
                        List.of(new Document("$set",
                                new Document("amount", new Document("$toDecimal", "$amount")))))));
        converted.block();

        Object amount = stored("money-1").get("amount");
        assertThat(amount.getClass().getSimpleName())
                .as("a binary double cannot hold K0.10 exactly, and the error compounds over a ledger")
                .isEqualTo("Decimal128");
    }

    private static Document stored(String id) {
        return template.findOne(Query.query(Criteria.where("_id").is(id)), Document.class, COLLECTION).block();
    }
}
