package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.migration.ReferencePresentationStrip;
import com.pml.catalog.repository.ReferenceDataRepository;
import com.pml.catalog.service.impl.ReferenceDataServiceImpl;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.referencedata.StatusSemanticResolver;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Reference data says what a value is, never how it is drawn: no colour, icon or image on any row.
 * Against a replica set.
 */
@Tag("L2")
@Tag("ET-PLT-014")
@Tag("ET-CAT-003")
@DisplayName("Reference data carries no presentation")
class ReferenceDataPresentationTest {

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static ReferenceDataServiceImpl referenceData;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_reference_presentation");
        referenceData = new ReferenceDataServiceImpl(
                new ReactiveMongoRepositoryFactory(template).getRepository(ReferenceDataRepository.class),
                new ReferenceMetadataValidator(),
                new StatusSemanticResolver(template, Clock.systemUTC()));
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void emptyDatabase() {
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
    }

    private static ReferenceData category(String code, Map<String, Object> metadata) {
        return ReferenceData.builder().type(ReferenceType.EVENT_CATEGORY).code(code).name(code)
                .isActive(true).metadata(new HashMap<>(metadata)).build();
    }

    @Test
    @DisplayName("a category with a colour or an icon is refused, and nothing is stored")
    void presentationIsRefused() {
        assertThatThrownBy(() -> referenceData.create(category("MUSIC", Map.of("color", "#7c3aed", "iconUrl", "x.svg"))).block())
                .isInstanceOfSatisfying(ValidationRefusal.class, refused -> assertThat(refused.violations())
                        .extracting(FieldViolation::path)
                        .containsExactly("input.metadata.color", "input.metadata.iconUrl"));

        assertThat(template.count(new org.springframework.data.mongodb.core.query.Query(), ReferenceData.class).block()).isZero();
    }

    @Test
    @DisplayName("a category is a code and a name, and is accepted as one")
    void plainCategoryIsAccepted() {
        ReferenceData saved = referenceData.create(category("MUSIC", Map.of())).block();

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getMetadata()).isEmpty();
    }

    @Test
    @DisplayName("the rows seeded before the rule lose their colours, and keep everything else")
    void existingRowsAreCleared() {
        template.insert(category("SPORTS", Map.of("color", "#16a34a"))).block();
        template.insert(ReferenceData.builder().type(ReferenceType.MOBILE_MONEY_OPERATOR).code("MTN").name("MTN")
                .isActive(true).metadata(new HashMap<>(Map.of("providerCode", "MTN_MOMO_ZMB", "color", "#FFCC00"))).build()).block();

        assertThat(new ReferencePresentationStrip(template).strip().block()).isEqualTo(2);

        assertThat(template.findAll(ReferenceData.class).collectList().block())
                .allSatisfy(row -> assertThat(row.getMetadata()).doesNotContainKey("color"))
                .anySatisfy(row -> assertThat(row.getMetadata()).containsEntry("providerCode", "MTN_MOMO_ZMB"));
        assertThat(new ReferencePresentationStrip(template).strip().block()).isZero();
    }
}
