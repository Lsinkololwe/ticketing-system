package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.config.CatalogIndexInitializer;
import com.pml.catalog.domain.enums.ExportFormat;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.repository.EventRepository;
import com.pml.catalog.repository.ReferenceDataRepository;
import com.pml.catalog.service.impl.ExportServiceImpl;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.web.graphql.dto.EventFilterInput;
import com.pml.catalog.web.graphql.dto.ReportExportDto;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.repository.support.ReactiveMongoRepositoryFactory;
import org.springframework.test.util.ReflectionTestUtils;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The administrator's event export, against a MongoDB replica set: every field of the filter
 * narrows it, the country filter reads the reference data, and nothing an organizer types can run
 * as a formula when the file is opened in a spreadsheet.
 */
@Tag("L2")
@Tag("ET-CAT-001")
@Tag("ET-PLT-007")
@DisplayName("An event export applies every filter field, and its cells are text")
class EventExportFilterTest {

    private static final Instant NOW = Instant.parse("2026-09-19T10:00:00Z");
    private static final String ORG = "65a1b2c3d4e5f60718293a4b";
    private static final Clock CLOCK = Clock.fixed(NOW, ZoneOffset.UTC);

    @TempDir
    static Path exports;

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static ExportServiceImpl exporter;

    @BeforeAll
    static void seed() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_event_export");
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        new IndexEnsurer(template).ensure(CatalogIndexInitializer.specifications()).block();
        template.insertAll(List.of(
                geo(ReferenceType.COUNTRY, "ZM", "Zambia", null), geo(ReferenceType.COUNTRY, "MW", "Malawi", null),
                geo(ReferenceType.PROVINCE, "LUS", "Lusaka", "ZM"), geo(ReferenceType.PROVINCE, "CB", "Copperbelt", "ZM"),
                geo(ReferenceType.PROVINCE, "SOUTH_MW", "Southern", "MW"),
                geo(ReferenceType.CITY, "LUSAKA", "Lusaka", "LUS"), geo(ReferenceType.CITY, "KITWE", "Kitwe", "CB"),
                geo(ReferenceType.CITY, "BLANTYRE", "Blantyre", "SOUTH_MW"))).collectList().block();
        template.insertAll(List.of(
                event("Lusaka Jazz Night", EventStatus.PUBLISHED, "LUSAKA", 40),
                event("Kitwe Rock", EventStatus.APPROVED, "KITWE", 5),
                event("Blantyre Gala", EventStatus.PUBLISHED, "BLANTYRE", 10),
                event("=HYPERLINK(\"http://evil.example\",\"click\")", EventStatus.DRAFT, "LUSAKA", 0)))
                .collectList().block();

        ReactiveMongoRepositoryFactory repositories = new ReactiveMongoRepositoryFactory(template);
        exporter = new ExportServiceImpl(repositories.getRepository(EventRepository.class), CLOCK,
                new EventAdminFilter(repositories.getRepository(ReferenceDataRepository.class), CLOCK), template);
        ReflectionTestUtils.setField(exporter, "exportDirectory", exports.toString());
        ReflectionTestUtils.setField(exporter, "exportBaseUrl", "https://exports.example");
        ReflectionTestUtils.setField(exporter, "expiryHours", 24);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    private static ReferenceData geo(ReferenceType type, String code, String name, String parent) {
        return ReferenceData.builder().type(type).code(code).name(name)
                .parentType(type == ReferenceType.CITY ? ReferenceType.PROVINCE : type == ReferenceType.PROVINCE ? ReferenceType.COUNTRY : null)
                .parentCode(parent).isActive(true).isSystem(true).metadata(new HashMap<>(Map.of())).build();
    }

    private static Event event(String title, EventStatus status, String city, int approvedDaysAgo) {
        return Event.builder().title(title).description("An event").status(status).cityId(city)
                .organizationId(ORG).organizerId("user-1").published(status == EventStatus.PUBLISHED).isActive(true)
                .eventDateTime(NOW.plusSeconds(30 * 86_400L)).endDateTime(NOW.plusSeconds(30 * 86_400L + 3600))
                .approvedAt(status == EventStatus.APPROVED || status == EventStatus.PUBLISHED
                        ? NOW.minusSeconds(approvedDaysAgo * 86_400L) : null)
                .createdAt(NOW.minusSeconds(60 * 86_400L)).build();
    }

    private static List<String> rows(EventFilterInput filter) throws Exception {
        ReportExportDto export = exporter.exportEventsReport(filter, ExportFormat.CSV).block();
        List<String> lines = Files.readAllLines(exports.resolve(export.getFileName()));
        return lines.subList(1, lines.size());
    }

    @Test
    @DisplayName("statuses and the search text both narrow the export")
    void statusesAndSearch() throws Exception {
        EventFilterInput filter = EventFilterInput.builder()
                .statuses(List.of(EventStatus.PUBLISHED, EventStatus.APPROVED)).searchQuery("jazz").build();

        assertThat(rows(filter)).singleElement().asString().contains("Lusaka Jazz Night");
    }

    @Test
    @DisplayName("a country selects the events in its cities, through the reference data")
    void country() throws Exception {
        EventFilterInput filter = EventFilterInput.builder().country("Zambia")
                .statuses(List.of(EventStatus.PUBLISHED, EventStatus.APPROVED)).build();

        assertThat(rows(filter)).hasSize(2).noneMatch(row -> row.contains("Blantyre"));
    }

    @Test
    @DisplayName("days since approval bound the approval date")
    void daysSinceApproval() throws Exception {
        EventFilterInput filter = EventFilterInput.builder().daysSinceApprovalMin(7).daysSinceApprovalMax(30).build();

        assertThat(rows(filter)).singleElement().asString().contains("Blantyre Gala");
    }

    @Test
    @DisplayName("an organizer's title that starts a formula is written as text")
    void formulaIsText() throws Exception {
        List<String> drafts = rows(EventFilterInput.builder().status(EventStatus.DRAFT).build());

        assertThat(drafts).singleElement().asString().contains("\"'=HYPERLINK(");
    }

    @Test
    @DisplayName("an invalid filter is refused, not turned into an empty file")
    void invalidFilterIsRefused() {
        try {
            exporter.exportEventsReport(EventFilterInput.builder().searchQuery("ja").build(), ExportFormat.CSV).block();
            throw new AssertionError("expected a refusal");
        } catch (ValidationRefusal refused) {
            assertThat(refused.violations()).extracting(FieldViolation::path).containsExactly("filter.searchQuery");
        }
    }
}
