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
        // prepared here, in order, rather than in separate @BeforeAll methods whose relative order
        // is not defined and which both need the client above
        validatedPrepare();
        publicListsPrepare();
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

    // ── against a database with the validators applied ────────────────────────────────────────────

    private static ReactiveMongoTemplate validatedTemplate;

    private static void validatedPrepare() {
        validatedTemplate = CatalogWiring.platformTemplate(client, "catalog_reference_release");
        validatedTemplate.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new com.pml.catalog.config.MongoSchemaValidationConfig(validatedTemplate, new DefaultResourceLoader(),
                new MongoSchemaValidationProperties()).applySchemaValidation();
        // what the database held before this release: the old document codes, and Airtel's and MTN's prefixes swapped
        legacyRow(validatedTemplate, "KYB_DOCUMENT_TYPE", "NRC", Map.of("appliesTo", "INDIVIDUAL"), true);
        legacyRow(validatedTemplate, "KYB_DOCUMENT_TYPE", "PACRA_CERT", Map.of("appliesTo", "BUSINESS"), true);
        legacyRow(validatedTemplate, "KYB_DOCUMENT_TYPE", "AN_ADMIN_ADDED_ONE", Map.of("appliesTo", "BOTH"), false);
        legacyRow(validatedTemplate, "MOBILE_MONEY_OPERATOR", "MTN",
                Map.of("providerCode", "MTN_MOMO_ZMB", "msisdnPrefixes", List.of("26097", "26096")), true);
    }

    @Test
    @DisplayName("against a database with the validators applied · the whole release is accepted by the validator, and a second application changes nothing")
    void validated_idempotent() {
        String first = service(validatedTemplate).applyV2().block();
        long rows = validatedTemplate.count(new Query(), CatalogCollections.REFERENCE_DATA).block();
        String second = service(validatedTemplate).applyV2().block();

        assertThat(first).startsWith("version 2:");
        assertThat(validatedTemplate.count(new Query(), CatalogCollections.REFERENCE_DATA).block()).isEqualTo(rows);
        assertThat(second.replaceAll("\\d+ retired", "")).isEqualTo(first.replaceAll("\\d+ retired", ""));
        assertThat(count(validatedTemplate, ReferenceType.COUNTRY)).isGreaterThan(230);
        // the boot-time seed owns the 21 towns with coordinates; the release adds the other 95 districts
        assertThat(count(validatedTemplate, ReferenceType.CITY)).isEqualTo(95);
        assertThat(count(validatedTemplate, ReferenceType.BUSINESS_TYPE)).isEqualTo(6);
        assertThat(count(validatedTemplate, ReferenceType.ORGANIZER_TYPE)).isEqualTo(7);
    }

    @Test
    @DisplayName("against a database with the validators applied · Zambia is a country with +260, the legal types carry their documents, MTN carries its real prefixes")
    void validated_values() {
        service(validatedTemplate).applyV2().block();
        ReferenceData zambia = validatedTemplate.findOne(Query.query(Criteria.where("type").is("COUNTRY").and("code").is("ZM")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
        assertThat(zambia.getMetadata()).containsEntry("dialCode", "+260").containsEntry("iso3", "ZMB");
        ReferenceData limited = validatedTemplate.findOne(Query.query(Criteria.where("type").is("BUSINESS_TYPE").and("code").is("LIMITED_COMPANY")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
        assertThat(limited.getMetadata().get("requiredDocuments"))
                .isEqualTo(List.of("NATIONAL_ID", "TAX_CERTIFICATE", "CERTIFICATE_OF_INCORPORATION"));
        ReferenceData mtn = validatedTemplate.findOne(Query.query(Criteria.where("type").is("MOBILE_MONEY_OPERATOR").and("code").is("MTN")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
        assertThat(mtn.getMetadata().get("msisdnPrefixes")).isEqualTo(List.of("26096", "26076"));
    }

    @Test
    @DisplayName("against a database with the validators applied · retired document codes are deactivated, never deleted, and an administrator's own row is untouched")
    void validated_retiringDeactivates() {
        service(validatedTemplate).applyV2().block();
        ReferenceData nrc = validatedTemplate.findOne(Query.query(Criteria.where("type").is("KYB_DOCUMENT_TYPE").and("code").is("NRC")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
        ReferenceData own = validatedTemplate.findOne(Query.query(Criteria.where("type").is("KYB_DOCUMENT_TYPE").and("code").is("AN_ADMIN_ADDED_ONE")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
        assertThat(nrc).as("kept for the records that reference it").isNotNull();
        assertThat(nrc.isActive()).isFalse();
        assertThat(own.isActive()).isTrue();
    }

    @Test
    @DisplayName("against a database with the validators applied · an administrator's deactivation survives a later application of the release")
    void validated_adminChoicesSurvive() {
        service(validatedTemplate).applyV2().block();
        validatedTemplate.updateFirst(Query.query(Criteria.where("type").is("NOTIFICATION_CHANNEL").and("code").is("SMS")),
                new Update().set("isActive", false).set("name", "Texts"), CatalogCollections.REFERENCE_DATA).block();
        service(validatedTemplate).applyV2().block();
        ReferenceData sms = validatedTemplate.findOne(Query.query(Criteria.where("type").is("NOTIFICATION_CHANNEL").and("code").is("SMS")), ReferenceData.class, CatalogCollections.REFERENCE_DATA).block();
        assertThat(sms.isActive()).as("isActive is written on insert only").isFalse();
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

    // ── the public lists are the reference rows ───────────────────────────────────────────────────

    private static ReactiveMongoTemplate publicListsTemplate;
    private static ReferenceGeography geography;

    private static void publicListsPrepare() {
        publicListsTemplate = CatalogWiring.platformTemplate(client, "catalog_reference_geography");
        publicListsTemplate.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        ReferenceDataRepository repository = new ReactiveMongoRepositoryFactory(publicListsTemplate).getRepository(ReferenceDataRepository.class);
        geography = new ReferenceGeography(repository, publicListsTemplate);
        // a fresh database: the boot-time seed's rows, then the release
        publicListsTemplate.insert(new Document("type", "EVENT_CATEGORY").append("code", "MUSIC").append("name", "Music").append("isActive", true)
                .append("isSystem", true).append("displayOrder", 0), CatalogCollections.REFERENCE_DATA).block();
        publicListsTemplate.insert(new Document("type", "PROVINCE").append("code", "LUS").append("name", "Lusaka").append("parentType", "COUNTRY")
                .append("parentCode", "ZM").append("isActive", true).append("isSystem", true).append("displayOrder", 0), CatalogCollections.REFERENCE_DATA).block();
        publicListsTemplate.insert(new Document("type", "CITY").append("code", "LUSAKA").append("name", "Lusaka").append("parentType", "PROVINCE")
                .append("parentCode", "LUS").append("isActive", true).append("isSystem", true).append("displayOrder", 0), CatalogCollections.REFERENCE_DATA).block();
        publicListsTemplate.insert(new Document("type", "CITY").append("code", "KITWE").append("name", "Kitwe").append("parentType", "PROVINCE")
                .append("parentCode", "CB").append("isActive", true).append("isSystem", true).append("displayOrder", 1), CatalogCollections.REFERENCE_DATA).block();
        unvalidatedService(publicListsTemplate).applyV2().block();
        publicListsTemplate.insert(new Document("title", "Show").append("status", "PUBLISHED").append("published", true).append("isActive", true)
                .append("isDeleted", false).append("cityId", "LUSAKA"), CatalogCollections.EVENTS).block();
        publicListsTemplate.insert(new Document("title", "Draft").append("status", "DRAFT").append("published", false).append("isActive", true)
                .append("isDeleted", false).append("cityId", "KITWE"), CatalogCollections.EVENTS).block();
    }

    @Test
    @DisplayName("the public lists are the reference rows · categories, provinces and cities answer from the reference rows, with the code as the id")
    void publicLists_lists() {
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
    @DisplayName("the public lists are the reference rows · citiesWithEvents lists the cities with a published event, counted, and not the one with only a draft")
    void publicLists_withEvents() {
        List<City> cities = geography.citiesWithEvents().collectList().block();
        assertThat(cities).extracting(City::getId).containsExactly("LUSAKA");
        assertThat(cities.get(0).getEventCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("the public lists are the reference rows · a deactivated row leaves the public list")
    void publicLists_deactivated() {
        publicListsTemplate.updateFirst(Query.query(Criteria.where("type").is("EVENT_CATEGORY").and("code").is("MUSIC")),
                new Update().set("isActive", false), CatalogCollections.REFERENCE_DATA).block();
        assertThat(geography.categories().collectList().block()).isEmpty();
        publicListsTemplate.updateFirst(Query.query(Criteria.where("type").is("EVENT_CATEGORY").and("code").is("MUSIC")),
                new Update().set("isActive", true), CatalogCollections.REFERENCE_DATA).block();
    }
}
