package com.pml.shared.testing;

import com.pml.shared.persistence.IndexSpec;
import org.bson.Document;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Describes indexes by what they do rather than what they are called, so two sources of indexes —
 * a live database and a registry — can be compared.
 *
 * <p>A {@link Shape} is the collection, the key fields in order with their direction, and the
 * options that change behaviour: unique, sparse, TTL, partial filter and text weights. Names are
 * ignored; the same index under another name is the same index.
 */
public final class IndexCensus {

    private IndexCensus() {
    }

    /** One index as it behaves. {@code key} is canonical JSON, so field order is part of equality. */
    public record Shape(String collection, String key, boolean unique, boolean sparse, Long expireAfterSeconds,
                        String partialFilter, String weights) {

        public String describe() {
            StringBuilder out = new StringBuilder(collection).append(' ').append(key);
            if (unique) out.append(" unique");
            if (sparse) out.append(" sparse");
            if (expireAfterSeconds != null) out.append(" ttl=").append(expireAfterSeconds).append('s');
            if (partialFilter != null) out.append(" partial=").append(partialFilter);
            if (weights != null) out.append(" weights=").append(weights);
            return out.toString();
        }

        /** One line of the census file. */
        public Document toDocument() {
            Document document = new Document("collection", collection).append("key", Document.parse(key))
                    .append("unique", unique).append("sparse", sparse);
            if (expireAfterSeconds != null) document.append("expireAfterSeconds", expireAfterSeconds);
            if (partialFilter != null) document.append("partialFilterExpression", Document.parse(partialFilter));
            if (weights != null) document.append("weights", Document.parse(weights));
            return document;
        }
    }

    /** The shape of one {@code listIndexes} entry. */
    public static Shape of(String collection, Document index) {
        Number ttl = (Number) index.get("expireAfterSeconds");
        Document partial = index.get("partialFilterExpression", Document.class);
        Document weights = index.get("weights", Document.class);
        return new Shape(collection, canonical(index.get("key", Document.class)),
                Boolean.TRUE.equals(index.get("unique")), Boolean.TRUE.equals(index.get("sparse")),
                ttl == null ? null : ttl.longValue(),
                partial == null ? null : canonical(partial),
                weights == null ? null : canonical(weights));
    }

    /** The shape a registry entry produces once created. */
    public static Shape of(IndexSpec spec) {
        String key;
        String weights = null;
        if (spec.text()) {
            key = canonical(new Document("_fts", "text").append("_ftsx", 1));
            Document weightDocument = new Document();
            spec.keys().keySet().stream().sorted().forEach(field -> weightDocument.append(field, 1));
            weights = canonical(weightDocument);
        } else {
            key = canonical(spec.keyDocument());
        }
        return new Shape(spec.collection(), key, spec.unique(), spec.sparse(),
                spec.expireAfter() == null ? null : spec.expireAfter().toSeconds(),
                spec.partialFilter() == null ? null : canonical(spec.partialFilter()), weights);
    }

    /** Every index in the database except each collection's {@code _id_}, with its name. */
    public static Mono<Map<Shape, String>> live(ReactiveMongoTemplate template) {
        return template.getCollectionNames()
                .concatMap(collection -> template.getCollection(collection)
                        .flatMapMany(c -> Flux.from(c.listIndexes()))
                        .filter(index -> !"_id_".equals(index.getString("name")))
                        .map(index -> Map.entry(of(collection, index), index.getString("name"))))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue, java.util.LinkedHashMap::new);
    }

    /** The shapes in {@code expected} that {@code actual} does not have. */
    public static List<Shape> missing(Collection<Shape> expected, Collection<Shape> actual) {
        Set<Shape> present = new LinkedHashSet<>(actual);
        return expected.stream().filter(shape -> !present.contains(shape))
                .sorted(Comparator.comparing(Shape::describe)).toList();
    }

    public static void write(Path file, Collection<Shape> shapes) throws IOException {
        List<String> lines = shapes.stream().sorted(Comparator.comparing(Shape::describe))
                .map(shape -> shape.toDocument().toJson()).toList();
        Files.createDirectories(file.getParent());
        Files.write(file, lines);
    }

    public static List<Shape> read(Path file) throws IOException {
        List<Shape> shapes = new ArrayList<>();
        for (String line : Files.readAllLines(file)) {
            if (line.isBlank()) {
                continue;
            }
            Document document = Document.parse(line);
            shapes.add(of(document.getString("collection"), document));
        }
        return shapes;
    }

    /**
     * JSON with every number written as an integer and keys in their stored order, so {@code 1},
     * {@code 1.0} and {@code NumberInt(1)} compare equal while {@code {a:1,b:1}} and {@code {b:1,a:1}}
     * do not.
     */
    static String canonical(Document document) {
        return normalise(document).toJson();
    }

    private static Document normalise(Document document) {
        Document out = new Document();
        document.forEach((field, value) -> out.append(field, normaliseValue(value)));
        return out;
    }

    private static Object normaliseValue(Object value) {
        if (value instanceof Number number && !(value instanceof Double d && d % 1 != 0)) {
            return number.intValue();
        }
        if (value instanceof Document nested) {
            return normalise(nested);
        }
        if (value instanceof List<?> list) {
            return list.stream().map(IndexCensus::normaliseValue).toList();
        }
        return value;
    }
}
