package com.pml.catalog.domain.model;

import com.pml.catalog.domain.enums.MediaKind;
import com.pml.catalog.domain.enums.MediaStatus;
import com.pml.catalog.domain.enums.StockImagePurpose;
import com.pml.catalog.persistence.CatalogCollections;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * An image in the catalog: an organization's upload, or a stock image the platform offers.
 *
 * <p>The bytes live in the file store under {@link #fileKey}; this document is everything else about
 * them. The public address is computed from the key when the asset is read, so a change of store or
 * CDN changes no row.
 */
@Document(collection = CatalogCollections.MEDIA)
@TypeAlias("media")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class MediaAsset {

    @Id
    private String id;

    @Version
    private Long version;

    private MediaKind kind;

    /** The owning organization; {@code null} for a stock image. */
    private String organizationId;

    /** Who uploaded it. */
    private String uploadedBy;

    /** The event it was uploaded for, if any. */
    private String eventId;

    private String fileKey;

    private String fileName;

    private String contentType;

    private long sizeBytes;

    /** SHA-256 of the bytes, hex. */
    private String checksum;

    private String title;

    private String altText;

    @Builder.Default
    private MediaStatus status = MediaStatus.ACTIVE;

    // ---- stock images only ----

    private StockImagePurpose purpose;

    /** The reference-data code of the category a {@code CATEGORY_TILE} stands for. */
    private String categoryCode;

    /** A stock image that is switched off is not offered; it is not deleted either. */
    @Builder.Default
    private boolean active = true;

    // ---- moderation ----

    private String flaggedReason;
    private String flaggedBy;
    private Instant flaggedAt;
    private String removedReason;
    private String removedBy;
    private Instant removedAt;

    @Builder.Default
    private List<ModerationEntry> moderationLog = new ArrayList<>();

    // ---- lifecycle ----

    /** The owner deleted it. The file is gone; the row stays so the audit does. */
    @Builder.Default
    private boolean deleted = false;

    private Instant deletedAt;

    private Instant createdAt;

    private Instant updatedAt;

    /** One administrator action, kept for good. */
    public record ModerationEntry(String action, String actorId, String reason, Instant at) {
    }
}
