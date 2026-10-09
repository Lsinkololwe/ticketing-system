package com.pml.catalog.migration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.EventCategory;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.domain.model.City;
import com.pml.catalog.domain.model.Province;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.repository.ReferenceDataRepository;
import com.pml.catalog.service.ReferenceGeography;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.shared.config.MongoSchemaValidationProperties;
import com.pml.shared.testing.MongoReplicaSet;
import org.bson.Document;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * ET-PLT-014-R12 and R13 · the reference data release against a real MongoDB with the collection validator
 * applied exactly as startup applies it.
 */
@Tag("L2")
@Tag("ET-PLT-014")
@DisplayName("ET-PLT-014-R12 · the v2 release applies once, idempotently, through the real collection validator")
class ReferenceDataSeedMigrationTest {

    private static final Instant NOW = Instant.parse("2026-10-06T08:00:00Z");
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    private static MongoClient client;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private static ReferenceDataSeedMigrationService service(ReactiveMongoTemplate template) {
        return new ReferenceDataSeedMigrationService(template, CLOCK, new ObjectMapper(),
                new com.pml.catalog.config.MongoSchemaValidationConfig(template, new DefaultResourceLoader(),
                        new MongoSchemaValidationProperties()));
    }

    /** Without the validators, for the test that writes bare event documents. */
    private static ReferenceDataSeedMigrationService unvalidatedService(ReactiveMongoTemplate template) {
        MongoSchemaValidationProperties off = new MongoSchemaValidationProperties();
        off.setEnabled(false);
        return new ReferenceDataSeedMigrationService(template, CLOCK, new ObjectMapper(),
                new com.pml.catalog.config.MongoSchemaValidationConfig(template, new DefaultResourceLoader(), off));
    }

    private static long count(ReactiveMongoTemplate template, ReferenceType type) {
        return template.count(Query.query(Criteria.where("type").is(type.name())), CatalogCollections.REFERENCE_DATA).block();
    }

    private static void legacyRow(ReactiveMongoTemplate template, String type, String code, Map<String, Object> metadata, boolean system) {
        template.insert(new Document("type", type).append("code", code).append("name", code).append("isActive", true)
                .append("isSystem", system).append("displayOrder", 0).append("metadata", new Document(metadata))
                .append("createdAt", java.util.Date.from(NOW)).append("updatedAt", java.util.Date.from(NOW)), CatalogCollections.REFERENCE_DATA).block();
    }

    @Nested
    @DisplayName("against a database with the validators applied")
    class Validated {

        private static ReactiveMongoTemplate template;

        @BeforeAll
        static void prepare() {
            template = CatalogWiring.platformTemplate(client, "catalog_reference_release");
            template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
            new com.pml.catalog.config.MongoSchemaValidationConfig(template, new DefaultResourceLoader(),
                    new MongoSchemaValidationProperties()).applySchemaValidation();
            // what the database held before this release: the old document codes, and Airtel's and MTN's prefixes swapped
            legacyRow(template, "KYB_DOCUMENT_TYPE", "NRC", Map.of("appliesTo", "INDIVIDUAL"), true);
            legacyRow(template, "KYB_DOCUMENT_TYPE", "PACRA_CERT", Map.of("appliesTo", "BUSINESS"), true);
            legacyRow(template, "KYB_DOCUMENT_TYPE", "AN_ADMIN_ADDED_ONE", Map.of("appliesTo", "BOTH"), false);
            legacyRow(template, "MOBILE_MONEY_OPERATOR", "MTN",
                    Map.of("providerCode", "MTN_MOMO_ZMB", "msisdnPrefixes", List.of("26097", "26096")), true);
        }

        @Test
        @DisplayName("the whole release is accepted by the validator, and a second application changes nothing")
        void idempotent() {
            String first = service(template).applyV2().block();
            long rows = template.count(new Query(), CatalogCollections.REFERENCE_DATA).block();
            String second = service(template).applyV2().block();

            assertThat(first).startsWith("version 2:");
            assertThat(template.count(new Query(), CatalogCollections.REFERENCE_DATA).block()).isEqualTo(rows);
            assertThat(second.replaceAll("\\d+ retired", "")).isEqualTo(first.replaceAll("\\d+ retired", ""));
            assertThat(count(template, ReferenceType.COUNTRY)).isGreaterThan(230);
            // the boot-time seed owns the 21 towns with coordinates; the release adds the other 95 districts
            assertThat(count(template, ReferenceType.CITY)).isEqualTo(95);
            assertThat(count(template, ReferenceType.BUSINESS_TYPE)).isEqualTo(6);
            assertThat(count(template, ReferenceType.ORGANIZER_TYPE)).isEqualTo(7);
        }

        @Test
        @DisplayName("Zambia is a country with +260, the legal types carry their documents, MTN carries its real prefixes")
        void values() {
            service(template).applyV2().block();
            ReferenceData zambia = template.findOne(Query.query(Criteria.where("type").is("COUNTRY").and("code").is("ZM")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
            assertThat(zambia.getMetadata()).containsEntry("dialCode", "+260").containsEntry("iso3", "ZMB");
            ReferenceData limited = template.findOne(Query.query(Criteria.where("type").is("BUSINESS_TYPE").and("code").is("LIMITED_COMPANY")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
            assertThat(limited.getMetadata().get("requiredDocuments"))
                    .isEqualTo(List.of("NATIONAL_ID", "TAX_CERTIFICATE", "CERTIFICATE_OF_INCORPORATION"));
            ReferenceData mtn = template.findOne(Query.query(Criteria.where("type").is("MOBILE_MONEY_OPERATOR").and("code").is("MTN")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
            assertThat(mtn.getMetadata().get("msisdnPrefixes")).isEqualTo(List.of("26096", "26076"));
        }

        @Test
        @DisplayName("retired document codes are deactivated, never deleted, and an administrator's own row is untouched")
        void retiringDeactivates() {
            service(template).applyV2().block();
            ReferenceData nrc = template.findOne(Query.query(Criteria.where("type").is("KYB_DOCUMENT_TYPE").and("code").is("NRC")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
            ReferenceData own = template.findOne(Query.query(Criteria.where("type").is("KYB_DOCUMENT_TYPE").and("code").is("AN_ADMIN_ADDED_ONE")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
            assertThat(nrc).as("kept for the records that reference it").isNotNull();
            assertThat(nrc.isActive()).isFalse();
            assertThat(own.isActive()).isTrue();
        }

        @Test
        @DisplayName("an administrator's deactivation survives a later application of the release")
        void adminChoicesSurvive() {
            service(template).applyV2().block();
            template.updateFirst(Query.query(Criteria.where("type").is("NOTIFICATION_CHANNEL").and("code").is("SMS")),
                    new Update().set("isActive", false).set("name", "Texts"), CatalogCollections.REFERENCE_DATA).block();
            service(template).applyV2().block();
            ReferenceData sms = template.findOne(Query.query(Criteria.where("type").is("NOTIFICATION_CHANNEL").and("code").is("SMS")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
            assertThat(sms.isActive()).as("isActive is written on insert only").isFalse();
        }
    }

    @Test
    @DisplayName("a database whose validator predates the release has it replaced before the first write")
    void replacesAStaleValidatorFirst() {
        ReactiveMongoTemplate stale = CatalogWiring.platformTemplate(client, "catalog_reference_stale_validator");
        stale.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        Document old = new Document("$jsonSchema", new Document("bsonType", "object")
                .append("properties", new Document("type", new Document("enum", List.of("COUNTRY", "BANK", "CITY")))));
        stale.getMongoDatabase().flatMap(db -> Mono.from(db.createCollection(CatalogCollections.REFERENCE_DATA,
                new com.mongodb.client.model.CreateCollectionOptions().validationOptions(
                        new com.mongodb.client.model.ValidationOptions().validator(old))))).block();

        assertThat(service(stale).applyV2().block()).startsWith("version 2:");
        assertThat(count(stale, ReferenceType.ORGANIZER_TYPE)).isEqualTo(7);
    }

    @Nested
    @DisplayName("the public lists are the reference rows")
    class PublicLists {

        private static ReactiveMongoTemplate template;
        private static ReferenceGeography geography;

        @BeforeAll
        static void prepare() {
            template = CatalogWiring.platformTemplate(client, "catalog_reference_geography");
            template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
            ReferenceDataRepository repository = new ReactiveMongoRepositoryFactory(template).getRepository(ReferenceDataRepository.class);
            geography = new ReferenceGeography(repository, template);
            // a fresh database: the boot-time seed's rows, then the release
            template.insert(new Document("type", "EVENT_CATEGORY").append("code", "MUSIC").append("name", "Music").append("isActive", true)
                    .append("isSystem", true).append("displayOrder", 0), CatalogCollections.REFERENCE_DATA).block();
            template.insert(new Document("type", "PROVINCE").append("code", "LUS").append("name", "Lusaka").append("parentType", "COUNTRY")
                    .append("parentCode", "ZM").append("isActive", true).append("isSystem", true).append("displayOrder", 0), CatalogCollections.REFERENCE_DATA).block();
            template.insert(new Document("type", "CITY").append("code", "LUSAKA").append("name", "Lusaka").append("parentType", "PROVINCE")
                    .append("parentCode", "LUS").append("isActive", true).append("isSystem", true).append("displayOrder", 0), CatalogCollections.REFERENCE_DATA).block();
            template.insert(new Document("type", "CITY").append("code", "KITWE").append("name", "Kitwe").append("parentType", "PROVINCE")
                    .append("parentCode", "CB").append("isActive", true).append("isSystem", true).append("displayOrder", 1), CatalogCollections.REFERENCE_DATA).block();
            unvalidatedService(template).applyV2().block();
            template.insert(new Document("title", "Show").append("status", "PUBLISHED").append("published", true).append("isActive", true)
                    .append("isDeleted", false).append("cityId", "LUSAKA"), CatalogCollections.EVENTS).block();
            template.insert(new Document("title", "Draft").append("status", "DRAFT").append("published", false).append("isActive", true)
                    .append("isDeleted", false).append("cityId", "KITWE"), CatalogCollections.EVENTS).block();
        }

        @Test
        @DisplayName("categories, provinces and cities answer from the reference rows, with the code as the id")
        void lists() {
            List<EventCategory> categories = geography.categories().collectList().block();
            assertThat(categories).extracting(EventCategory::getId).containsExactly("MUSIC");
            List<Province> provinces = geography.provinces().collectList().block();
            assertThat(provinces).extracting(Province::getId).containsExactly("LUS");
            assertThat(provinces.get(0).getCountry()).isEqualTo("Zambia");
            List<City> inLusaka = geography.cities("LUS").collectList().block();
            assertThat(inLusaka).extracting(City::getId).contains("LUSAKA", "CHILANGA", "CHIRUNDU");
            assertThat(inLusaka).allMatch(c -> "LUS".equals(c.getProvinceId()) && "Lusaka".equals(c.getProvinceName()));
            assertThat(geography.cities(null).collectList().block().size()).isGreaterThan(90);
        }

        @Test
        @DisplayName("citiesWithEvents lists the cities with a published event, counted, and not the one with only a draft")
        void withEvents() {
            List<City> cities = geography.citiesWithEvents().collectList().block();
            assertThat(cities).extracting(City::getId).containsExactly("LUSAKA");
            assertThat(cities.get(0).getEventCount()).isEqualTo(1);
        }

        @Test
        @DisplayName("a deactivated row leaves the public list")
        void deactivated() {
            template.updateFirst(Query.query(Criteria.where("type").is("EVENT_CATEGORY").and("code").is("MUSIC")),
                    new Update().set("isActive", false), CatalogCollections.REFERENCE_DATA).block();
            assertThat(geography.categories().collectList().block()).isEmpty();
            template.updateFirst(Query.query(Criteria.where("type").is("EVENT_CATEGORY").and("code").is("MUSIC")),
                    new Update().set("isActive", true), CatalogCollections.REFERENCE_DATA).block();
        }
    }
}
