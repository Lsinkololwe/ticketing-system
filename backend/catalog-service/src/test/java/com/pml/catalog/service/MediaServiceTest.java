package com.pml.catalog.service;

import com.mongodb.reactivestreams.client.MongoClient;
import com.mongodb.reactivestreams.client.MongoClients;
import com.pml.catalog.config.CatalogIndexInitializer;
import com.pml.catalog.domain.enums.MediaKind;
import com.pml.catalog.domain.enums.MediaStatus;
import com.pml.catalog.domain.enums.StockImagePurpose;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.MediaAsset;
import com.pml.catalog.domain.model.ReferenceData;
import com.pml.catalog.domain.enums.ReferenceType;
import com.pml.catalog.persistence.CatalogCollections;
import com.pml.catalog.storage.LocalMediaStorage;
import com.pml.catalog.testing.CatalogWiring;
import com.pml.catalog.util.KeysetCursor;
import com.pml.catalog.web.graphql.dto.MediaFilterInput;
import com.pml.catalog.web.graphql.dto.MediaModerationFilterInput;
import com.pml.catalog.web.graphql.dto.OffsetPaginationInput;
import com.pml.catalog.web.graphql.dto.StockImageFilterInput;
import com.pml.catalog.web.graphql.dto.UpdateMediaInput;
import com.pml.catalog.web.graphql.dto.UpdateStockImageInput;
import com.pml.catalog.web.graphql.dto.UploadMediaInput;
import com.pml.catalog.web.graphql.dto.UploadStockImageInput;
import com.pml.shared.constants.EventStatus;
import com.pml.shared.error.DomainRefusal;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.persistence.IndexEnsurer;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantScope;
import com.pml.shared.testing.MongoReplicaSet;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Query;
import reactor.core.publisher.Mono;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The image library against a MongoDB replica set carrying catalog's own validators and indexes, and a
 * real directory as the file store: what an organizer may do to their own pictures and nobody else's,
 * what moderation does to what is served, and what the stock library offers.
 */
@Tag("L2")
@Tag("ET-CAT-004")
@Tag("ET-PLT-007")
@DisplayName("ET-CAT-004-R8/R9/R10 · the image library is scoped to the organization, moderated by administrators, and never serves what was removed")
class MediaServiceTest {

    private static final Instant T0 = Instant.parse("2026-10-01T10:00:00Z");
    private static final String ORG_A = "65a1b2c3d4e5f60718293a4b";
    private static final String ORG_B = "65a1b2c3d4e5f60718293a4c";
    private static final String USER_A = "user-a";
    private static final String ADMIN = "admin-1";
    private static final String BASE_URL = "http://cdn.test";
    private static final int CAP = 6;

    private static final TenantScope SCOPE_A = TenantScope.of(USER_A, Set.of(ORG_A));
    private static final TenantScope SCOPE_B = TenantScope.of("user-b", Set.of(ORG_B));

    private static MongoClient client;
    private static ReactiveMongoTemplate template;
    private static MutableClock clock;
    private static LocalMediaStorage storage;
    private static MediaService service;

    @TempDir
    static Path files;

    @BeforeAll
    static void connect() {
        client = MongoClients.create(MongoReplicaSet.connectionString());
        template = CatalogWiring.platformTemplate(client, "catalog_media_service");
        template.getMongoDatabase().flatMap(db -> Mono.from(db.drop())).block();
        // The validators as the service applies them at startup: every write below must be one the
        // production database accepts, including the new media collection's.
        new com.pml.catalog.config.MongoSchemaValidationConfig(template,
                new org.springframework.core.io.DefaultResourceLoader(),
                new com.pml.shared.config.MongoSchemaValidationProperties()).run(null);
        assertThat(new IndexEnsurer(template).ensure(CatalogIndexInitializer.specifications()).block().isClean()).isTrue();
        clock = new MutableClock(T0);
        storage = new LocalMediaStorage(files.toString(), BASE_URL);
        service = new MediaService(template, storage, clock, CatalogWiring.categories(template), CAP);
    }

    @AfterAll
    static void disconnect() {
        client.close();
    }

    @BeforeEach
    void clean() {
        template.remove(new Query(), CatalogCollections.MEDIA).block();
        template.remove(new Query(), CatalogCollections.EVENTS).block();
        template.remove(new Query(), CatalogCollections.REFERENCE_DATA).block();
        template.insertAll(List.of(
                category("MUSIC", true), category("SPORTS", true), category("RETIRED", false))).collectList().block();
        clock.set(T0);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static ReferenceData category(String code, boolean active) {
        return ReferenceData.builder().type(ReferenceType.EVENT_CATEGORY).code(code).name(code)
                .isActive(active).isSystem(true).metadata(new HashMap<>()).build();
    }

    private static <T> T as(TenantScope scope, Mono<T> call) {
        return call.contextWrite(ctx -> CurrentTenantScope.seed(ctx, Mono.just(scope))).block();
    }

    private static String b64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    private static UploadMediaInput upload(String name, String eventId) {
        return new UploadMediaInput(name, "image/png", b64(MediaRulesTest.PNG), "Poster", "A poster", eventId);
    }

    private static MediaAsset uploadAs(String org, String name) {
        clock.advance(Duration.ofSeconds(1));
        return service.upload(upload(name, null), "uploader", org).block();
    }

    private static Event event(String org, EventStatus status, String banner) {
        return template.save(Event.builder()
                .organizationId(org).organizerId("organizer").title("Jazz night").status(status)
                .published(status == EventStatus.PUBLISHED).isActive(true)
                .bannerImageUrl(banner).thumbnailImageUrl(banner)
                .galleryImages(banner == null ? null : List.of(banner, "https://other.example/1.jpg"))
                .eventDateTime(T0.plusSeconds(86_400)).endDateTime(T0.plusSeconds(90_000)).totalCapacity(10)
                .createdAt(T0).build()).block();
    }

    private static Event reload(Event event) {
        return template.findById(event.getId(), Event.class).block();
    }

    private static DomainRefusal refused(Runnable call) {
        try {
            call.run();
        } catch (DomainRefusal refusal) {
            return refusal;
        }
        throw new AssertionError("expected a refusal");
    }

    private static List<String> violationPaths(DomainRefusal refusal) {
        return ((ValidationRefusal) refusal).violations().stream().map(FieldViolation::path).toList();
    }

    private static long count(String collection) {
        return template.count(new Query(), collection).block();
    }

    // ── organizer ────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("an organizer's upload")
    class Upload {

        @Test
        @DisplayName("is stored in the file store and recorded against the caller's organization")
        void stored() {
            MediaAsset asset = uploadAs(ORG_A, "poster.png");

            assertThat(asset.getId()).isNotBlank();
            assertThat(asset.getKind()).isEqualTo(MediaKind.ORGANIZER_UPLOAD);
            assertThat(asset.getOrganizationId()).isEqualTo(ORG_A);
            assertThat(asset.getUploadedBy()).isEqualTo("uploader");
            assertThat(asset.getStatus()).isEqualTo(MediaStatus.ACTIVE);
            assertThat(asset.getSizeBytes()).isEqualTo(MediaRulesTest.PNG.length);
            assertThat(asset.getChecksum()).hasSize(64);
            assertThat(asset.getFileKey()).startsWith("media/" + ORG_A + "/").endsWith("poster.png");
            assertThat(storage.read(asset.getFileKey()).block()).isEqualTo(MediaRulesTest.PNG);
            assertThat(service.urlOf(asset)).isEqualTo(BASE_URL + "/media/files/" + asset.getFileKey());
            assertThat(template.findById(asset.getId(), MediaAsset.class).block().getTitle()).isEqualTo("Poster");
        }

        @Test
        @DisplayName("is refused when the bytes are not an image, and nothing is stored")
        void notAnImage() {
            UploadMediaInput script = new UploadMediaInput("poster.png", "image/png",
                    b64("<script>alert(1)</script>".getBytes()), null, null, null);

            DomainRefusal refusal = refused(() -> service.upload(script, "uploader", ORG_A).block());

            assertThat(refusal.errorCode()).isEqualTo(ErrorCode.COMMAND_NOT_WELL_FORMED);
            assertThat(violationPaths(refusal)).containsExactly("input.contentBase64");
            assertThat(count(CatalogCollections.MEDIA)).isZero();
        }

        @Test
        @DisplayName("is refused when the base64 is not base64")
        void notBase64() {
            UploadMediaInput junk = new UploadMediaInput("poster.png", "image/png", "%%%", null, null, null);

            assertThat(violationPaths(refused(() -> service.upload(junk, "uploader", ORG_A).block())))
                    .containsExactly("input.contentBase64");
        }

        @Test
        @DisplayName("may name an event of the caller's organization, and only that")
        void eventMustBeTheirs() {
            Event mine = event(ORG_A, EventStatus.DRAFT, null);
            Event theirs = event(ORG_B, EventStatus.DRAFT, null);

            assertThat(service.upload(upload("a.png", mine.getId()), "u", ORG_A).block().getEventId()).isEqualTo(mine.getId());
            DomainRefusal refusal = refused(() -> service.upload(upload("b.png", theirs.getId()), "u", ORG_A).block());

            assertThat(violationPaths(refusal)).containsExactly("input.eventId");
            assertThat(count(CatalogCollections.MEDIA)).isEqualTo(1);
        }

        @Test
        @DisplayName("stops at the organization's library cap, counting its own images only")
        void libraryCap() {
            for (int i = 0; i < CAP; i++) {
                uploadAs(ORG_A, "p" + i + ".png");
            }

            DomainRefusal refusal = refused(() -> service.upload(upload("one-more.png", null), "u", ORG_A).block());

            assertThat(violationPaths(refusal)).containsExactly("input");
            assertThat(uploadAs(ORG_B, "other-org.png")).as("another organization's library is its own").isNotNull();
        }

        @Test
        @DisplayName("a name with a path in it cannot choose where the file lands")
        void pathInNameIsHarmless() {
            MediaAsset asset = uploadAs(ORG_A, "../../etc/passwd.png");

            assertThat(asset.getFileKey()).startsWith("media/" + ORG_A + "/").doesNotContain("..");
            assertThat(asset.getFileName()).isEqualTo("passwd.png");
            assertThat(Files.exists(files.resolve(asset.getFileKey()))).isTrue();
        }
    }

    @Nested
    @DisplayName("renaming and deleting")
    class Manage {

        @Test
        @DisplayName("the owner renames an image and files it under one of their events")
        void ownerUpdates() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");
            Event mine = event(ORG_A, EventStatus.DRAFT, null);

            MediaAsset updated = as(SCOPE_A, service.update(asset.getId(), new UpdateMediaInput("New title", "New alt", mine.getId())));

            assertThat(updated.getTitle()).isEqualTo("New title");
            assertThat(updated.getAltText()).isEqualTo("New alt");
            assertThat(updated.getEventId()).isEqualTo(mine.getId());
            assertThat(template.findById(asset.getId(), MediaAsset.class).block().getTitle()).isEqualTo("New title");
        }

        @Test
        @DisplayName("another organization's image is MEDIA_UNKNOWN, exactly as an id never issued is")
        void updateIsTenantScoped() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");

            DomainRefusal foreign = refused(() -> as(SCOPE_B, service.update(asset.getId(), new UpdateMediaInput("x", null, null))));
            DomainRefusal invented = refused(() -> as(SCOPE_B, service.update("65a1b2c3d4e5f60718293aff", new UpdateMediaInput("x", null, null))));

            assertThat(foreign.errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
            assertThat(invented.errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
            assertThat(foreign.getMessage().replace(asset.getId(), "ID")).isEqualTo(invented.getMessage().replace("65a1b2c3d4e5f60718293aff", "ID"));
            assertThat(template.findById(asset.getId(), MediaAsset.class).block().getTitle()).isEqualTo("Poster");
        }

        @Test
        @DisplayName("an event of another organization cannot be named")
        void updateEventMustBeTheirs() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");
            Event theirs = event(ORG_B, EventStatus.DRAFT, null);

            assertThat(violationPaths(refused(() ->
                    as(SCOPE_A, service.update(asset.getId(), new UpdateMediaInput(null, null, theirs.getId()))))))
                    .containsExactly("input.eventId");
        }

        @Test
        @DisplayName("delete removes the file, keeps the row for the audit, and takes the image off every event")
        void deleteDetaches() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");
            String url = service.urlOf(asset);
            Event shows = event(ORG_A, EventStatus.PUBLISHED, url);
            Event unrelated = event(ORG_A, EventStatus.PUBLISHED, "https://other.example/banner.jpg");

            assertThat(as(SCOPE_A, service.delete(asset.getId()))).isEqualTo(asset.getId());

            assertThat(storage.read(asset.getFileKey()).block()).as("the file is gone").isNull();
            MediaAsset row = template.findById(asset.getId(), MediaAsset.class).block();
            assertThat(row.isDeleted()).isTrue();
            assertThat(row.getDeletedAt()).isEqualTo(clock.instant());
            Event after = reload(shows);
            assertThat(after.getBannerImageUrl()).isNull();
            assertThat(after.getThumbnailImageUrl()).isNull();
            assertThat(after.getGalleryImages()).containsExactly("https://other.example/1.jpg");
            assertThat(reload(unrelated).getBannerImageUrl()).isEqualTo("https://other.example/banner.jpg");
            assertThat(service.servable(asset.getFileKey()).block()).isNull();
        }

        @Test
        @DisplayName("another organization cannot delete it, and deleting twice is MEDIA_UNKNOWN the second time")
        void deleteIsScopedAndOnce() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");

            assertThat(refused(() -> as(SCOPE_B, service.delete(asset.getId()))).errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
            assertThat(storage.read(asset.getFileKey()).block()).as("still there").isNotNull();

            as(SCOPE_A, service.delete(asset.getId()));
            assertThat(refused(() -> as(SCOPE_A, service.delete(asset.getId()))).errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
        }

        @Test
        @DisplayName("a platform administrator reaches any organization's image")
        void administratorReachesAll() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");

            MediaAsset updated = as(TenantScope.platformAdministrator(ADMIN, Set.of()),
                    service.update(asset.getId(), new UpdateMediaInput("Edited by ops", null, null)));

            assertThat(updated.getTitle()).isEqualTo("Edited by ops");
        }
    }

    @Nested
    @DisplayName("an organization's library")
    class Library {

        @Test
        @DisplayName("lists only the caller's organizations' images, newest first, and pages by cursor without repeats")
        void pagesNewestFirst() {
            List<String> mine = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                mine.add(uploadAs(ORG_A, "a" + i + ".png").getId());
            }
            uploadAs(ORG_B, "b0.png");
            // Two uploads in the same millisecond still order and page deterministically.
            clock.set(T0.plusSeconds(100));
            String tie1 = service.upload(upload("t1.png", null), "u", ORG_A).block().getId();
            String tie2 = service.upload(upload("t2.png", null), "u", ORG_A).block().getId();

            List<String> seen = new ArrayList<>();
            String after = null;
            MediaService.Page page;
            do {
                page = as(SCOPE_A, service.mine(null, after, 3));
                page.assets().forEach(asset -> seen.add(asset.getId()));
                MediaAsset last = page.assets().get(page.assets().size() - 1);
                after = KeysetCursor.encode(last.getCreatedAt(), last.getId());
            } while (page.hasNext());

            assertThat(seen).hasSize(6).doesNotHaveDuplicates();
            assertThat(seen.subList(0, 2)).containsExactlyInAnyOrder(tie1, tie2);
            assertThat(seen.subList(2, 6)).containsExactly(mine.get(3), mine.get(2), mine.get(1), mine.get(0));
        }

        @Test
        @DisplayName("a team member of two organizations sees both; a caller with none sees nothing")
        void scope() {
            uploadAs(ORG_A, "a.png");
            uploadAs(ORG_B, "b.png");

            assertThat(as(TenantScope.of("both", Set.of(ORG_A, ORG_B)), service.mine(null, null, 10)).assets()).hasSize(2);
            assertThat(as(TenantScope.denyAll("nobody"), service.mine(null, null, 10)).assets()).isEmpty();
            assertThat(as(SCOPE_B, service.mine(null, null, 10)).assets()).extracting(MediaAsset::getOrganizationId)
                    .containsOnly(ORG_B);
        }

        @Test
        @DisplayName("filters by event and by text, and a text with regex characters is only text")
        void filters() {
            Event e = event(ORG_A, EventStatus.DRAFT, null);
            service.upload(new UploadMediaInput("a.png", "image/png", b64(MediaRulesTest.PNG), "Summer (2026)", null, e.getId()), "u", ORG_A).block();
            uploadAs(ORG_A, "other.png");

            assertThat(as(SCOPE_A, service.mine(new MediaFilterInput(e.getId(), null), null, 10)).assets()).hasSize(1);
            assertThat(as(SCOPE_A, service.mine(new MediaFilterInput(null, "summer (2026)"), null, 10)).assets()).hasSize(1);
            assertThat(as(SCOPE_A, service.mine(new MediaFilterInput(null, ".*"), null, 10)).assets()).isEmpty();
        }

        @Test
        @DisplayName("a deleted image is not listed; a removed one is, with its status")
        void deletedAndRemoved() {
            MediaAsset gone = uploadAs(ORG_A, "gone.png");
            MediaAsset removed = uploadAs(ORG_A, "removed.png");
            as(SCOPE_A, service.delete(gone.getId()));
            service.remove(removed.getId(), "off-brand", ADMIN).block();

            List<MediaAsset> listed = as(SCOPE_A, service.mine(null, null, 10)).assets();

            assertThat(listed).extracting(MediaAsset::getId).containsExactly(removed.getId());
            assertThat(listed.get(0).getStatus()).isEqualTo(MediaStatus.REMOVED);
            assertThat(listed.get(0).getRemovedReason()).isEqualTo("off-brand");
        }

        @Test
        @DisplayName("a cursor the list did not issue is refused")
        void forgedCursor() {
            assertThat(violationPaths(refused(() -> as(SCOPE_A, service.mine(null, "forged", 10)))))
                    .containsExactly("pagination.after");
        }
    }

    // ── moderation ───────────────────────────────────────────────────────────

    @Nested
    @DisplayName("moderation")
    class Moderation {

        @Test
        @DisplayName("flagging keeps the image served and records who and why")
        void flag() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");

            MediaAsset flagged = service.flag(asset.getId(), "possibly offensive", ADMIN).block();

            assertThat(flagged.getStatus()).isEqualTo(MediaStatus.FLAGGED);
            assertThat(flagged.getFlaggedReason()).isEqualTo("possibly offensive");
            assertThat(flagged.getFlaggedBy()).isEqualTo(ADMIN);
            assertThat(flagged.getFlaggedAt()).isEqualTo(clock.instant());
            assertThat(service.servable(asset.getFileKey()).block()).isNotNull();
            assertThat(flagged.getModerationLog()).singleElement().satisfies(entry -> {
                assertThat(entry.action()).isEqualTo("FLAG");
                assertThat(entry.actorId()).isEqualTo(ADMIN);
                assertThat(entry.reason()).isEqualTo("possibly offensive");
            });
        }

        @Test
        @DisplayName("flagging twice is the same flag, not a second entry")
        void flagIsIdempotent() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");

            service.flag(asset.getId(), "r", ADMIN).block();
            MediaAsset again = service.flag(asset.getId(), "r2", "admin-2").block();

            assertThat(again.getModerationLog()).hasSize(1);
            assertThat(again.getFlaggedBy()).isEqualTo(ADMIN);
        }

        @Test
        @DisplayName("removal stops serving the image, takes it off every event, and keeps the file for a restore")
        void remove() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");
            Event shows = event(ORG_A, EventStatus.PUBLISHED, service.urlOf(asset));

            MediaAsset removed = service.remove(asset.getId(), "copyright claim", ADMIN).block();

            assertThat(removed.getStatus()).isEqualTo(MediaStatus.REMOVED);
            assertThat(removed.getRemovedBy()).isEqualTo(ADMIN);
            assertThat(service.servable(asset.getFileKey()).block()).as("not served").isNull();
            assertThat(storage.read(asset.getFileKey()).block()).as("but kept").isNotNull();
            assertThat(reload(shows).getBannerImageUrl()).isNull();
            assertThat(reload(shows).getGalleryImages()).containsExactly("https://other.example/1.jpg");
        }

        @Test
        @DisplayName("restore serves it again and clears the flag and the removal, leaving the trail")
        void restore() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");
            service.flag(asset.getId(), "look", ADMIN).block();
            service.remove(asset.getId(), "copyright", ADMIN).block();

            MediaAsset restored = service.restore(asset.getId(), "admin-2").block();

            assertThat(restored.getStatus()).isEqualTo(MediaStatus.ACTIVE);
            assertThat(restored.getRemovedReason()).isNull();
            assertThat(restored.getFlaggedReason()).isNull();
            assertThat(service.servable(asset.getFileKey()).block()).isNotNull();
            assertThat(restored.getModerationLog()).extracting(MediaAsset.ModerationEntry::action)
                    .containsExactly("FLAG", "REMOVE", "RESTORE");
            assertThat(restored.getModerationLog()).extracting(MediaAsset.ModerationEntry::actorId)
                    .containsExactly(ADMIN, ADMIN, "admin-2");
        }

        @Test
        @DisplayName("a removed image cannot be flagged, and a reason is required")
        void rules() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");

            assertThat(violationPaths(refused(() -> service.flag(asset.getId(), " ", ADMIN).block()))).containsExactly("reason");
            assertThat(violationPaths(refused(() -> service.remove(asset.getId(), null, ADMIN).block()))).containsExactly("reason");
            service.remove(asset.getId(), "copyright", ADMIN).block();

            DomainRefusal refusal = refused(() -> service.flag(asset.getId(), "again", ADMIN).block());
            assertThat(refusal.errorCode()).isEqualTo(ErrorCode.MEDIA_STATE_INVALID);
            assertThat(refusal.details()).containsEntry("currentStatus", "REMOVED");
        }

        @Test
        @DisplayName("an unknown or stock image is MEDIA_UNKNOWN to the moderation queue")
        void unknown() {
            assertThat(refused(() -> service.flag("65a1b2c3d4e5f60718293aff", "r", ADMIN).block()).errorCode())
                    .isEqualTo(ErrorCode.MEDIA_UNKNOWN);
            MediaAsset stock = service.uploadStock(new UploadStockImageInput("s.png", "image/png", b64(MediaRulesTest.PNG),
                    StockImagePurpose.EVENT_COVER, null, null, null), ADMIN).block();
            assertThat(refused(() -> service.remove(stock.getId(), "r", ADMIN).block()).errorCode())
                    .isEqualTo(ErrorCode.MEDIA_UNKNOWN);
        }

        @Test
        @DisplayName("the queue filters by status and organization and pages by offset")
        void queue() {
            MediaAsset a1 = uploadAs(ORG_A, "a1.png");
            MediaAsset a2 = uploadAs(ORG_A, "a2.png");
            MediaAsset b1 = uploadAs(ORG_B, "b1.png");
            service.flag(a1.getId(), "r", ADMIN).block();
            service.remove(b1.getId(), "r", ADMIN).block();

            assertThat(service.moderationQueue(new MediaModerationFilterInput(MediaStatus.FLAGGED, null, null, null), null)
                    .block().content()).extracting(MediaAsset::getId).containsExactly(a1.getId());
            assertThat(service.moderationQueue(new MediaModerationFilterInput(null, ORG_B, null, null), null)
                    .block().content()).extracting(MediaAsset::getId).containsExactly(b1.getId());

            MediaService.MediaPageResult first = service.moderationQueue(null,
                    new OffsetPaginationInput(0, 2, "createdAt", OffsetPaginationInput.SortDirection.DESC)).block();
            MediaService.MediaPageResult second = service.moderationQueue(null,
                    new OffsetPaginationInput(1, 2, "createdAt", OffsetPaginationInput.SortDirection.DESC)).block();
            assertThat(first.totalElements()).isEqualTo(3);
            assertThat(first.totalPages()).isEqualTo(2);
            assertThat(first.hasNext()).isTrue();
            assertThat(second.hasNext()).isFalse();
            assertThat(first.content()).extracting(MediaAsset::getId).containsExactly(b1.getId(), a2.getId());
            assertThat(second.content()).extracting(MediaAsset::getId).containsExactly(a1.getId());
        }

        @Test
        @DisplayName("a banner override puts an organization's image on that organization's event, with the reason on the image")
        void overrideBanner() {
            MediaAsset asset = uploadAs(ORG_A, "a.png");
            as(SCOPE_A, service.update(asset.getId(), new UpdateMediaInput(null, "Crowd at sunset", null)));
            Event e = event(ORG_A, EventStatus.PUBLISHED, "https://bad.example/banner.jpg");

            Event after = service.overrideBanner(e.getId(), asset.getId(), "the old banner was not ours", ADMIN).block();

            assertThat(after.getBannerImageUrl()).isEqualTo(service.urlOf(asset));
            assertThat(after.getBannerAltText()).isEqualTo("Crowd at sunset");
            assertThat(after.getUpdatedBy()).isEqualTo(ADMIN);
            assertThat(template.findById(asset.getId(), MediaAsset.class).block().getModerationLog())
                    .singleElement().satisfies(entry -> {
                        assertThat(entry.action()).isEqualTo("BANNER_OVERRIDE");
                        assertThat(entry.reason()).contains(e.getId()).contains("the old banner was not ours");
                    });
        }

        @Test
        @DisplayName("a banner override refuses another organization's image, a removed one and an event that is over")
        void overrideRefusals() {
            MediaAsset others = uploadAs(ORG_B, "b.png");
            MediaAsset removed = uploadAs(ORG_A, "r.png");
            service.remove(removed.getId(), "copyright", ADMIN).block();
            Event e = event(ORG_A, EventStatus.PUBLISHED, null);
            Event over = event(ORG_A, EventStatus.CANCELLED, null);
            MediaAsset fine = uploadAs(ORG_A, "f.png");

            assertThat(refused(() -> service.overrideBanner(e.getId(), others.getId(), "r", ADMIN).block()).errorCode())
                    .isEqualTo(ErrorCode.MEDIA_UNKNOWN);
            assertThat(refused(() -> service.overrideBanner(e.getId(), removed.getId(), "r", ADMIN).block()).errorCode())
                    .isEqualTo(ErrorCode.MEDIA_UNKNOWN);
            assertThat(refused(() -> service.overrideBanner(over.getId(), fine.getId(), "r", ADMIN).block()).errorCode())
                    .isEqualTo(ErrorCode.EVENT_STATE_INVALID);
            assertThat(refused(() -> service.overrideBanner("65a1b2c3d4e5f60718293aff", fine.getId(), "r", ADMIN).block()).errorCode())
                    .isEqualTo(ErrorCode.EVENT_UNKNOWN);
            assertThat(violationPaths(refused(() -> service.overrideBanner(e.getId(), fine.getId(), " ", ADMIN).block())))
                    .containsExactly("reason");
            assertThat(reload(e).getBannerImageUrl()).isNull();
        }

        @Test
        @DisplayName("a banner override with no image clears the banner")
        void overrideClears() {
            Event e = event(ORG_A, EventStatus.PUBLISHED, "https://bad.example/banner.jpg");

            Event after = service.overrideBanner(e.getId(), null, "inappropriate image", ADMIN).block();

            assertThat(after.getBannerImageUrl()).isNull();
            assertThat(reload(e).getBannerImageUrl()).isNull();
        }

        @Test
        @DisplayName("a stock image can be the banner of any event")
        void overrideWithStock() {
            MediaAsset stock = service.uploadStock(new UploadStockImageInput("s.png", "image/png", b64(MediaRulesTest.PNG),
                    StockImagePurpose.EVENT_COVER, null, "Stage", "A stage"), ADMIN).block();
            Event e = event(ORG_B, EventStatus.APPROVED, null);

            Event after = service.overrideBanner(e.getId(), stock.getId(), "organizer asked support for a cover", ADMIN).block();

            assertThat(after.getBannerImageUrl()).isEqualTo(service.urlOf(stock));
        }
    }

    // ── stock ────────────────────────────────────────────────────────────────

    @Nested
    @DisplayName("the stock library")
    class Stock {

        private MediaAsset tile(String category) {
            clock.advance(Duration.ofSeconds(1));
            return service.uploadStock(new UploadStockImageInput("t.png", "image/png", b64(MediaRulesTest.PNG),
                    StockImagePurpose.CATEGORY_TILE, category, "Tile", "A tile"), ADMIN).block();
        }

        @Test
        @DisplayName("a category tile needs an active category; a cover refuses one")
        void purposeRules() {
            assertThat(violationPaths(refused(() -> service.uploadStock(new UploadStockImageInput("t.png", "image/png",
                    b64(MediaRulesTest.PNG), StockImagePurpose.CATEGORY_TILE, null, null, null), ADMIN).block())))
                    .containsExactly("input.categoryCode");
            assertThat(violationPaths(refused(() -> tile("NO_SUCH"))))
                    .containsExactly("input.categoryCode");
            assertThat(violationPaths(refused(() -> tile("RETIRED")))).containsExactly("input.categoryCode");
            assertThat(violationPaths(refused(() -> service.uploadStock(new UploadStockImageInput("c.png", "image/png",
                    b64(MediaRulesTest.PNG), StockImagePurpose.EVENT_COVER, "MUSIC", null, null), ADMIN).block())))
                    .containsExactly("input.categoryCode");
            assertThat(count(CatalogCollections.MEDIA)).isZero();
        }

        @Test
        @DisplayName("the tile for a category is the newest active one, and none when there is none")
        void categoryTile() {
            assertThat(service.categoryTileUrl("MUSIC").block()).isNull();

            MediaAsset older = tile("MUSIC");
            MediaAsset newer = tile("MUSIC");
            tile("SPORTS");

            assertThat(service.categoryTileUrl("MUSIC").block()).isEqualTo(service.urlOf(newer));

            service.updateStock(newer.getId(), new UpdateStockImageInput(null, null, null, null, false)).block();
            assertThat(service.categoryTileUrl("MUSIC").block()).isEqualTo(service.urlOf(older));

            service.deleteStock(older.getId()).block();
            assertThat(service.categoryTileUrl("MUSIC").block()).isNull();
        }

        @Test
        @DisplayName("an organizer is offered the active images; an administrator may ask for all")
        void listing() {
            MediaAsset on = tile("MUSIC");
            MediaAsset off = tile("SPORTS");
            service.updateStock(off.getId(), new UpdateStockImageInput(null, null, null, null, false)).block();
            uploadAs(ORG_A, "not-stock.png");

            assertThat(service.stockImages(null, false, null, 10).block().assets()).extracting(MediaAsset::getId)
                    .containsExactly(on.getId());
            assertThat(service.stockImages(new StockImageFilterInput(null, null, true), false, null, 10).block().assets())
                    .as("an organizer asking for inactive ones is not an administrator").hasSize(1);
            assertThat(service.stockImages(new StockImageFilterInput(null, null, true), true, null, 10).block().assets())
                    .hasSize(2);
            assertThat(service.stockImages(new StockImageFilterInput(StockImagePurpose.CATEGORY_TILE, "MUSIC", null), true, null, 10)
                    .block().assets()).extracting(MediaAsset::getId).containsExactly(on.getId());
        }

        @Test
        @DisplayName("changing a tile into a cover drops its category; into a tile needs one")
        void updateRules() {
            MediaAsset tile = tile("MUSIC");

            MediaAsset cover = service.updateStock(tile.getId(),
                    new UpdateStockImageInput(null, null, StockImagePurpose.EVENT_COVER, null, null)).block();
            assertThat(cover.getPurpose()).isEqualTo(StockImagePurpose.EVENT_COVER);
            assertThat(cover.getCategoryCode()).isNull();

            assertThat(violationPaths(refused(() -> service.updateStock(tile.getId(),
                    new UpdateStockImageInput(null, null, StockImagePurpose.CATEGORY_TILE, null, null)).block())))
                    .containsExactly("input.categoryCode");
            MediaAsset back = service.updateStock(tile.getId(),
                    new UpdateStockImageInput(null, null, StockImagePurpose.CATEGORY_TILE, "SPORTS", null)).block();
            assertThat(back.getCategoryCode()).isEqualTo("SPORTS");
        }

        @Test
        @DisplayName("deleting a stock image removes the file and detaches it from events")
        void delete() {
            MediaAsset cover = service.uploadStock(new UploadStockImageInput("c.png", "image/png", b64(MediaRulesTest.PNG),
                    StockImagePurpose.EVENT_COVER, null, null, null), ADMIN).block();
            Event shows = event(ORG_A, EventStatus.PUBLISHED, service.urlOf(cover));

            assertThat(service.deleteStock(cover.getId()).block()).isEqualTo(cover.getId());

            assertThat(storage.read(cover.getFileKey()).block()).isNull();
            assertThat(reload(shows).getBannerImageUrl()).isNull();
            assertThat(refused(() -> service.deleteStock(cover.getId()).block()).errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
        }

        @Test
        @DisplayName("an organizer's upload is not a stock image to the stock operations")
        void notStock() {
            MediaAsset org = uploadAs(ORG_A, "a.png");

            assertThat(refused(() -> service.deleteStock(org.getId()).block()).errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
            assertThat(refused(() -> service.updateStock(org.getId(), new UpdateStockImageInput("x", null, null, null, null)).block())
                    .errorCode()).isEqualTo(ErrorCode.MEDIA_UNKNOWN);
        }
    }

    // ── serving ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("the file endpoint's lookup serves an active or flagged image and nothing else")
    void serving() {
        MediaAsset asset = uploadAs(ORG_A, "a.png");
        AtomicReference<MediaAsset> found = new AtomicReference<>();

        found.set(service.servable(asset.getFileKey()).block());
        assertThat(found.get()).isNotNull();
        assertThat(service.bytes(found.get()).block()).isEqualTo(MediaRulesTest.PNG);

        assertThat(service.servable("media/" + ORG_A + "/never-issued.png").block()).isNull();
        service.remove(asset.getId(), "r", ADMIN).block();
        assertThat(service.servable(asset.getFileKey()).block()).isNull();
    }

    /** A clock a test moves by hand. */
    static final class MutableClock extends Clock {
        private volatile Instant now;

        MutableClock(Instant start) {
            this.now = start;
        }

        void set(Instant instant) {
            this.now = instant;
        }

        void advance(Duration by) {
            this.now = now.plus(by);
        }

        @Override
        public java.time.ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(java.time.ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }
    }
}
