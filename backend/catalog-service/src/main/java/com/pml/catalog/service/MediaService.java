package com.pml.catalog.service;

import com.pml.catalog.domain.enums.MediaKind;
import com.pml.catalog.domain.enums.MediaStatus;
import com.pml.catalog.domain.enums.StockImagePurpose;
import com.pml.catalog.domain.model.Event;
import com.pml.catalog.domain.model.MediaAsset;
import com.pml.catalog.domain.model.MediaAsset.ModerationEntry;
import com.pml.catalog.storage.MediaStorage;
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
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.FieldViolation;
import com.pml.shared.error.TranslatedRefusal;
import com.pml.shared.error.ValidationRefusal;
import com.pml.shared.security.tenancy.CurrentTenantScope;
import com.pml.shared.security.tenancy.TenantGuard;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.ReactiveMongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * The image library: organizers' uploads, administrators' moderation, and the stock images the
 * platform offers.
 *
 * <p>Every organizer operation finds its asset through {@link TenantGuard}, so another
 * organization's image answers {@code MEDIA_UNKNOWN}, exactly as an id never issued does. Every
 * administrator action appends an entry to the asset's {@code moderationLog}, which is never
 * rewritten.
 *
 * <p>An image that stops being served — deleted by its owner, removed by moderation, or deleted from
 * the stock library — is also taken off every event that points at it, so no event page shows a
 * broken picture.
 */
@Slf4j
@Service
public class MediaService {

    /** A page never holds more than this; cursor pages refuse above it. */
    static final Set<MediaStatus> SERVED = Set.of(MediaStatus.ACTIVE, MediaStatus.FLAGGED);

    private final ReactiveMongoTemplate mongo;
    private final MediaStorage storage;
    private final Clock clock;
    private final EventCategories categories;
    private final int maxAssetsPerOrganization;

    public MediaService(ReactiveMongoTemplate mongo, MediaStorage storage, Clock clock, EventCategories categories,
                        @Value("${catalog.media.max-assets-per-organization:500}") int maxAssetsPerOrganization) {
        this.mongo = mongo;
        this.storage = storage;
        this.clock = clock;
        this.categories = categories;
        this.maxAssetsPerOrganization = maxAssetsPerOrganization;
    }

    /** A page of assets and where the next one starts. */
    public record Page(List<MediaAsset> assets, boolean hasNext, boolean hasPrevious) {
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Organizer
    // ═══════════════════════════════════════════════════════════════════════════

    /** Stores an organization's picture; the organization comes from the caller's permission check, never from input. */
    public Mono<MediaAsset> upload(UploadMediaInput input, String userId, String organizationId) {
        return uploadBytes(input.fileName(), input.contentType(), MediaRules.decode(input.contentBase64()).orElse(null),
                "input.contentBase64", input.title(), input.altText(), input.eventId(), userId, organizationId);
    }

    /**
     * The same upload when the bytes already arrived as bytes — the multipart endpoint's path. The
     * checks are identical: the bytes decide, not the name or the declared type.
     *
     * @param bytesPath where a problem with the bytes is reported
     */
    public Mono<MediaAsset> uploadBytes(String fileName, String contentType, byte[] bytes, String bytesPath,
                                        String title, String altText, String eventId, String userId,
                                        String organizationId) {
        List<FieldViolation> violations = new ArrayList<>(MediaRules.check(fileName, contentType, bytes, bytesPath));
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }
        UploadMediaInput input = new UploadMediaInput(fileName, contentType, "", title, altText, eventId);
        Mono<Boolean> eventOwned = input.eventId() == null
                ? Mono.just(true)
                : mongo.exists(Query.query(Criteria.where("id").is(input.eventId())
                .and("organizationId").is(organizationId)), Event.class);
        return eventOwned
                .flatMap(owned -> owned
                        ? mongo.count(Query.query(Criteria.where("organizationId").is(organizationId)
                        .and("kind").is(MediaKind.ORGANIZER_UPLOAD).and("deleted").is(false)), MediaAsset.class)
                        : Mono.<Long>error(new ValidationRefusal(List.of(
                        new FieldViolation("input.eventId", "is not an event of your organization")))))
                .flatMap(count -> count >= maxAssetsPerOrganization
                        ? Mono.<MediaAsset>error(new ValidationRefusal(List.of(new FieldViolation("input",
                        "the media library holds at most " + maxAssetsPerOrganization + " images; delete some first"))))
                        : save(bytes, input.contentType(), input.fileName(), organizationId, MediaAsset.builder()
                        .kind(MediaKind.ORGANIZER_UPLOAD)
                        .organizationId(organizationId)
                        .uploadedBy(userId)
                        .eventId(input.eventId())
                        .title(blankToNull(input.title()))
                        .altText(blankToNull(input.altText()))
                        .build()));
    }

    public Mono<MediaAsset> update(String id, UpdateMediaInput input) {
        return owned(id).flatMap(asset -> {
            Mono<Boolean> eventOk = input.eventId() == null
                    ? Mono.just(true)
                    : mongo.exists(Query.query(Criteria.where("id").is(input.eventId())
                    .and("organizationId").is(asset.getOrganizationId())), Event.class);
            return eventOk.flatMap(ok -> {
                if (!ok) {
                    return Mono.<MediaAsset>error(new ValidationRefusal(List.of(
                            new FieldViolation("input.eventId", "is not an event of this image's organization"))));
                }
                if (input.title() != null) asset.setTitle(blankToNull(input.title()));
                if (input.altText() != null) asset.setAltText(blankToNull(input.altText()));
                if (input.eventId() != null) asset.setEventId(input.eventId());
                asset.setUpdatedAt(clock.instant());
                return mongo.save(asset);
            });
        });
    }

    /** The owner deletes an image: the file goes, the row stays for the audit, and no event keeps pointing at it. */
    public Mono<String> delete(String id) {
        return owned(id).flatMap(this::purge).thenReturn(id);
    }

    /** The caller's organizations' images, newest first, with a cursor that survives new uploads. */
    public Mono<Page> mine(MediaFilterInput filter, String after, int limit) {
        return CurrentTenantScope.get().flatMap(scope -> {
            if (scope.organizationIds().isEmpty()) {
                return Mono.just(new Page(List.of(), false, false));
            }
            Criteria criteria = Criteria.where("organizationId").in(scope.organizationIds())
                    .and("kind").is(MediaKind.ORGANIZER_UPLOAD)
                    .and("deleted").is(false);
            if (filter != null && filter.eventId() != null) {
                criteria.and("eventId").is(filter.eventId());
            }
            return keyset(criteria, filter == null ? null : filter.search(), after, limit);
        });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Administrator · moderation
    // ═══════════════════════════════════════════════════════════════════════════

    /** Every organization's uploads, for the moderation queue. */
    public Mono<MediaPageResult> moderationQueue(MediaModerationFilterInput filter, OffsetPaginationInput pagination) {
        Criteria criteria = Criteria.where("kind").is(MediaKind.ORGANIZER_UPLOAD).and("deleted").is(false);
        if (filter != null) {
            if (filter.status() != null) criteria.and("status").is(filter.status());
            if (filter.organizationId() != null) criteria.and("organizationId").is(filter.organizationId());
            if (filter.eventId() != null) criteria.and("eventId").is(filter.eventId());
        }
        Query query = new Query(criteria);
        if (filter != null && filter.search() != null && !filter.search().isBlank()) {
            query.addCriteria(searchCriteria(filter.search()));
        }
        OffsetPaginationInput page = pagination != null ? pagination
                : new OffsetPaginationInput(0, 20, "createdAt", OffsetPaginationInput.SortDirection.DESC);
        Sort.Direction direction = page.sortDirection() == OffsetPaginationInput.SortDirection.ASC
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        String sortBy = Set.of("createdAt", "flaggedAt", "removedAt", "sizeBytes").contains(page.sortBy())
                ? page.sortBy() : "createdAt";
        return mongo.count(query, MediaAsset.class).flatMap(total -> mongo.find(
                        query.with(Sort.by(direction, sortBy).and(Sort.by(direction, "id")))
                                .skip(page.getOffset()).limit(page.getLimit()), MediaAsset.class)
                .collectList()
                .map(items -> new MediaPageResult(items, page.page(), page.getLimit(), total.intValue())));
    }

    /** One offset page of assets. */
    public record MediaPageResult(List<MediaAsset> content, int pageNumber, int pageSize, int totalElements) {
        public int totalPages() {
            return totalElements == 0 ? 0 : (int) Math.ceil((double) totalElements / pageSize);
        }

        public boolean hasNext() {
            return (long) (pageNumber + 1) * pageSize < totalElements;
        }

        public boolean hasPrevious() {
            return pageNumber > 0;
        }
    }

    public Mono<MediaAsset> flag(String id, String reason, String actorId) {
        return moderated(id).flatMap(asset -> {
            if (asset.getStatus() == MediaStatus.FLAGGED) {
                return Mono.just(asset);
            }
            if (asset.getStatus() == MediaStatus.REMOVED) {
                return Mono.<MediaAsset>error(stateRefusal(asset, "a removed image cannot be flagged"));
            }
            Instant now = clock.instant();
            asset.setStatus(MediaStatus.FLAGGED);
            asset.setFlaggedReason(requireReason(reason));
            asset.setFlaggedBy(actorId);
            asset.setFlaggedAt(now);
            asset.setUpdatedAt(now);
            log(asset, "FLAG", actorId, reason, now);
            return mongo.save(asset);
        });
    }

    public Mono<MediaAsset> remove(String id, String reason, String actorId) {
        return moderated(id).flatMap(asset -> {
            if (asset.getStatus() == MediaStatus.REMOVED) {
                return Mono.just(asset);
            }
            Instant now = clock.instant();
            asset.setStatus(MediaStatus.REMOVED);
            asset.setRemovedReason(requireReason(reason));
            asset.setRemovedBy(actorId);
            asset.setRemovedAt(now);
            asset.setUpdatedAt(now);
            log(asset, "REMOVE", actorId, reason, now);
            return mongo.save(asset).flatMap(saved -> detachFromEvents(saved).thenReturn(saved));
        });
    }

    public Mono<MediaAsset> restore(String id, String actorId) {
        return moderated(id).flatMap(asset -> {
            if (asset.getStatus() == MediaStatus.ACTIVE) {
                return Mono.just(asset);
            }
            Instant now = clock.instant();
            asset.setStatus(MediaStatus.ACTIVE);
            asset.setFlaggedReason(null);
            asset.setFlaggedBy(null);
            asset.setFlaggedAt(null);
            asset.setRemovedReason(null);
            asset.setRemovedBy(null);
            asset.setRemovedAt(null);
            asset.setUpdatedAt(now);
            log(asset, "RESTORE", actorId, null, now);
            return mongo.save(asset);
        });
    }

    /**
     * Replaces an event's banner with an uploaded or stock image of an administrator's choosing, or
     * clears it when {@code mediaId} is null. The reason lands in the image's audit trail; clearing
     * has no image to carry it, so it goes to the audit log.
     */
    public Mono<Event> overrideBanner(String eventId, String mediaId, String reason, String actorId) {
        return mongo.findOne(Query.query(Criteria.where("id").is(eventId).and("isDeleted").ne(true)), Event.class)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.EVENT_UNKNOWN, "event not found")))
                .flatMap(event -> {
                    String why = requireReason(reason);
                    if (event.getStatus() == EventStatus.CANCELLED || event.getStatus() == EventStatus.COMPLETED) {
                        return Mono.<Event>error(new TranslatedRefusal(ErrorCode.EVENT_STATE_INVALID,
                                "the banner of a " + event.getStatus() + " event is not changed",
                                Map.of("currentStatus", event.getStatus().name())));
                    }
                    Instant now = clock.instant();
                    if (mediaId == null) {
                        log.info("audit: administrator {} cleared the banner of event {}: {}", actorId, eventId, why);
                        event.setBannerImageUrl(null);
                        event.setBannerAltText(null);
                        event.setUpdatedAt(now);
                        event.setUpdatedBy(actorId);
                        return mongo.save(event);
                    }
                    return mongo.findOne(Query.query(Criteria.where("id").is(mediaId).and("deleted").is(false)), MediaAsset.class)
                            .filter(asset -> asset.getStatus() != MediaStatus.REMOVED)
                            .filter(asset -> asset.getKind() == MediaKind.STOCK
                                    || (asset.getOrganizationId() != null
                                    && asset.getOrganizationId().equals(event.getOrganizationId())))
                            .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.MEDIA_UNKNOWN,
                                    "image not found, removed, or not this event's organization's")))
                            .flatMap(asset -> {
                                log(asset, "BANNER_OVERRIDE", actorId, "event " + eventId + ": " + why, now);
                                asset.setUpdatedAt(now);
                                event.setBannerImageUrl(storage.publicUrl(asset.getFileKey()));
                                event.setBannerAltText(asset.getAltText());
                                event.setUpdatedAt(now);
                                event.setUpdatedBy(actorId);
                                return mongo.save(asset).then(mongo.save(event));
                            });
                });
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Stock images
    // ═══════════════════════════════════════════════════════════════════════════

    public Mono<MediaAsset> uploadStock(UploadStockImageInput input, String actorId) {
        return uploadStockBytes(input.fileName(), input.contentType(), MediaRules.decode(input.contentBase64()).orElse(null),
                "input.contentBase64", input.purpose(), input.categoryCode(), input.title(), input.altText(), actorId);
    }

    /** The same upload when the bytes already arrived as bytes — the multipart endpoint's path. */
    public Mono<MediaAsset> uploadStockBytes(String fileName, String contentType, byte[] bytes, String bytesPath,
                                             StockImagePurpose purpose, String categoryCode, String title,
                                             String altText, String actorId) {
        UploadStockImageInput input = new UploadStockImageInput(fileName, contentType, "", purpose, categoryCode, title, altText);
        List<FieldViolation> violations = new ArrayList<>(MediaRules.check(fileName, contentType, bytes, bytesPath));
        String code = blankToNull(input.categoryCode());
        if (input.purpose() == null) {
            violations.add(new FieldViolation("input.purpose", "is required"));
        }
        if (input.purpose() == StockImagePurpose.CATEGORY_TILE && code == null) {
            violations.add(new FieldViolation("input.categoryCode", "is required for a category tile"));
        }
        if (input.purpose() == StockImagePurpose.EVENT_COVER && code != null) {
            violations.add(new FieldViolation("input.categoryCode", "belongs to a category tile, not an event cover"));
        }
        if (!violations.isEmpty()) {
            return Mono.error(new ValidationRefusal(violations));
        }
        Mono<Boolean> known = code == null ? Mono.just(true) : categories.isSelectable(code);
        return known.flatMap(ok -> ok
                ? save(bytes, input.contentType(), input.fileName(), "stock", MediaAsset.builder()
                .kind(MediaKind.STOCK)
                .uploadedBy(actorId)
                .purpose(input.purpose())
                .categoryCode(code)
                .title(blankToNull(input.title()))
                .altText(blankToNull(input.altText()))
                .active(true)
                .build())
                : Mono.<MediaAsset>error(new ValidationRefusal(List.of(
                new FieldViolation("input.categoryCode", "is not an active event category")))));
    }

    public Mono<MediaAsset> updateStock(String id, UpdateStockImageInput input) {
        return stock(id).flatMap(asset -> {
            StockImagePurpose purpose = input.purpose() != null ? input.purpose() : asset.getPurpose();
            String code = input.categoryCode() != null ? blankToNull(input.categoryCode()) : asset.getCategoryCode();
            if (purpose == StockImagePurpose.EVENT_COVER && input.purpose() != null) {
                code = input.categoryCode() != null ? blankToNull(input.categoryCode()) : null;
            }
            List<FieldViolation> violations = new ArrayList<>();
            if (purpose == StockImagePurpose.CATEGORY_TILE && code == null) {
                violations.add(new FieldViolation("input.categoryCode", "is required for a category tile"));
            }
            if (purpose == StockImagePurpose.EVENT_COVER && code != null) {
                violations.add(new FieldViolation("input.categoryCode", "belongs to a category tile, not an event cover"));
            }
            if (!violations.isEmpty()) {
                return Mono.<MediaAsset>error(new ValidationRefusal(violations));
            }
            String finalCode = code;
            boolean codeChanged = finalCode != null && !finalCode.equals(asset.getCategoryCode());
            return (codeChanged ? categories.isSelectable(finalCode) : Mono.just(true)).flatMap(ok -> {
                if (!ok) {
                    return Mono.<MediaAsset>error(new ValidationRefusal(List.of(
                            new FieldViolation("input.categoryCode", "is not an active event category"))));
                }
                if (input.title() != null) asset.setTitle(blankToNull(input.title()));
                if (input.altText() != null) asset.setAltText(blankToNull(input.altText()));
                if (input.active() != null) asset.setActive(input.active());
                asset.setPurpose(purpose);
                asset.setCategoryCode(finalCode);
                asset.setUpdatedAt(clock.instant());
                return mongo.save(asset);
            });
        });
    }

    public Mono<String> deleteStock(String id) {
        return stock(id).flatMap(this::purge).thenReturn(id);
    }

    /** Stock images: only the active ones unless an administrator asks for the rest. */
    public Mono<Page> stockImages(StockImageFilterInput filter, boolean administrator, String after, int limit) {
        Criteria criteria = Criteria.where("kind").is(MediaKind.STOCK).and("deleted").is(false)
                .and("status").in(SERVED);
        if (!(administrator && filter != null && Boolean.TRUE.equals(filter.includeInactive()))) {
            criteria.and("active").is(true);
        }
        if (filter != null) {
            if (filter.purpose() != null) criteria.and("purpose").is(filter.purpose());
            if (filter.categoryCode() != null) criteria.and("categoryCode").is(filter.categoryCode());
        }
        return keyset(criteria, null, after, limit);
    }

    /** The tile the library holds for a category: the newest active one, if there is one. */
    public Mono<String> categoryTileUrl(String categoryCode) {
        if (categoryCode == null) {
            return Mono.empty();
        }
        return mongo.findOne(Query.query(Criteria.where("kind").is(MediaKind.STOCK)
                                .and("purpose").is(StockImagePurpose.CATEGORY_TILE)
                                .and("categoryCode").is(categoryCode)
                                .and("active").is(true).and("deleted").is(false)
                                .and("status").in(SERVED))
                        .with(Sort.by(Sort.Direction.DESC, "createdAt")), MediaAsset.class)
                .map(asset -> storage.publicUrl(asset.getFileKey()));
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // Serving
    // ═══════════════════════════════════════════════════════════════════════════

    /** The asset behind a file key, when it may be served: not deleted, not removed by moderation. */
    public Mono<MediaAsset> servable(String fileKey) {
        return mongo.findOne(Query.query(Criteria.where("fileKey").is(fileKey)
                .and("deleted").is(false).and("status").in(SERVED)), MediaAsset.class);
    }

    public Mono<byte[]> bytes(MediaAsset asset) {
        return storage.read(asset.getFileKey());
    }

    public String urlOf(MediaAsset asset) {
        return storage.publicUrl(asset.getFileKey());
    }

    // ═══════════════════════════════════════════════════════════════════════════
    // internals
    // ═══════════════════════════════════════════════════════════════════════════

    private Mono<MediaAsset> save(byte[] bytes, String contentType, String fileName, String scope, MediaAsset draft) {
        return storage.store(bytes, contentType.toLowerCase(java.util.Locale.ROOT), scope, fileName)
                .flatMap(stored -> {
                    Instant now = clock.instant();
                    MediaAsset asset = draft.toBuilder()
                            .fileKey(stored.fileKey())
                            .fileName(sanitizeName(fileName))
                            .contentType(contentType.toLowerCase(java.util.Locale.ROOT))
                            .sizeBytes(stored.sizeBytes())
                            .checksum(stored.checksum())
                            .status(MediaStatus.ACTIVE)
                            .deleted(false)
                            .createdAt(now)
                            .updatedAt(now)
                            .build();
                    return mongo.insert(asset)
                            // The row is what makes the file reachable; without it the file is litter.
                            .onErrorResume(error -> storage.delete(stored.fileKey()).then(Mono.error(error)));
                });
    }

    /** An uploaded image of the caller's organization, or {@code MEDIA_UNKNOWN}. */
    private Mono<MediaAsset> owned(String id) {
        Criteria base = Criteria.where("id").is(id).and("kind").is(MediaKind.ORGANIZER_UPLOAD).and("deleted").is(false);
        return CurrentTenantScope.get().flatMap(scope -> TenantGuard.locate(
                scope,
                mongo.findOne(Query.query(base), MediaAsset.class),
                organizationIds -> mongo.findOne(Query.query(Criteria.where("id").is(id)
                        .and("kind").is(MediaKind.ORGANIZER_UPLOAD).and("deleted").is(false)
                        .and("organizationId").in(organizationIds)), MediaAsset.class),
                ErrorCode.MEDIA_UNKNOWN,
                "media " + id));
    }

    private Mono<MediaAsset> moderated(String id) {
        return mongo.findOne(Query.query(Criteria.where("id").is(id).and("kind").is(MediaKind.ORGANIZER_UPLOAD)
                        .and("deleted").is(false)), MediaAsset.class)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.MEDIA_UNKNOWN, "image not found")));
    }

    private Mono<MediaAsset> stock(String id) {
        return mongo.findOne(Query.query(Criteria.where("id").is(id).and("kind").is(MediaKind.STOCK)
                        .and("deleted").is(false)), MediaAsset.class)
                .switchIfEmpty(Mono.error(new TranslatedRefusal(ErrorCode.MEDIA_UNKNOWN, "stock image not found")));
    }

    /** Takes the file away and marks the row deleted, then detaches the image from every event. */
    private Mono<MediaAsset> purge(MediaAsset asset) {
        Instant now = clock.instant();
        asset.setDeleted(true);
        asset.setDeletedAt(now);
        asset.setUpdatedAt(now);
        return storage.delete(asset.getFileKey())
                .then(mongo.save(asset))
                .flatMap(saved -> detachFromEvents(saved).thenReturn(saved));
    }

    /** No event keeps showing an image that is no longer served. */
    private Mono<Void> detachFromEvents(MediaAsset asset) {
        String url = storage.publicUrl(asset.getFileKey());
        Instant now = clock.instant();
        Mono<Long> banner = mongo.updateMulti(Query.query(Criteria.where("bannerImageUrl").is(url)),
                        new Update().unset("bannerImageUrl").unset("bannerAltText").set("updatedAt", now), Event.class)
                .map(result -> result.getModifiedCount());
        Mono<Long> thumb = mongo.updateMulti(Query.query(Criteria.where("thumbnailImageUrl").is(url)),
                        new Update().unset("thumbnailImageUrl").set("updatedAt", now), Event.class)
                .map(result -> result.getModifiedCount());
        Mono<Long> gallery = mongo.updateMulti(Query.query(Criteria.where("galleryImages").is(url)),
                        new Update().pull("galleryImages", url).set("updatedAt", now), Event.class)
                .map(result -> result.getModifiedCount());
        return Mono.when(banner, thumb, gallery);
    }

    /** Newest first by {@code (createdAt, id)}; one extra row says whether another page follows. */
    private Mono<Page> keyset(Criteria base, String search, String after, int limit) {
        Query query = new Query(base);
        if (search != null && !search.isBlank()) {
            query.addCriteria(searchCriteria(search));
        }
        boolean paged = false;
        var position = KeysetCursor.decode(after);
        if (after != null && !after.isBlank() && position.isEmpty()) {
            return Mono.error(new ValidationRefusal(List.of(
                    new FieldViolation("pagination.after", "is not a cursor this query issued"))));
        }
        if (position.isPresent()) {
            paged = true;
            query.addCriteria(new Criteria().orOperator(
                    Criteria.where("createdAt").lt(position.get().createdAt()),
                    Criteria.where("createdAt").is(position.get().createdAt()).and("id").lt(position.get().id())));
        }
        boolean hasPrevious = paged;
        query.with(Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))).limit(limit + 1);
        return mongo.find(query, MediaAsset.class).collectList().map(found -> new Page(
                found.size() > limit ? found.subList(0, limit) : found, found.size() > limit, hasPrevious));
    }

    private static Criteria searchCriteria(String search) {
        String escaped = Pattern.quote(search.trim());
        return new Criteria().orOperator(
                Criteria.where("title").regex(escaped, "i"),
                Criteria.where("fileName").regex(escaped, "i"),
                Criteria.where("altText").regex(escaped, "i"));
    }

    private static void log(MediaAsset asset, String action, String actorId, String reason, Instant at) {
        if (asset.getModerationLog() == null) {
            asset.setModerationLog(new ArrayList<>());
        }
        asset.getModerationLog().add(new ModerationEntry(action, actorId, reason, at));
    }

    private static String requireReason(String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ValidationRefusal(List.of(new FieldViolation("reason", "is required")));
        }
        return reason.trim();
    }

    private static TranslatedRefusal stateRefusal(MediaAsset asset, String why) {
        return new TranslatedRefusal(ErrorCode.MEDIA_STATE_INVALID, why,
                Map.of("currentStatus", asset.getStatus().name()));
    }

    private static String sanitizeName(String fileName) {
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        String base = slash >= 0 ? fileName.substring(slash + 1) : fileName;
        return base.length() > 255 ? base.substring(base.length() - 255) : base;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
