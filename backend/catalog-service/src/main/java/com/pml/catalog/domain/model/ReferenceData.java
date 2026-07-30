package com.pml.catalog.domain.model;

import com.pml.catalog.domain.enums.ReferenceType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.CompoundIndexes;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * ReferenceData — a single polymorphic lookup/catalog record.
 *
 * <p>Every business-editable list (mobile-money operators, banks, currencies, event genres,
 * KYB document types, reason codes, tax rates…) is stored as rows in this one collection,
 * discriminated by {@link #type}. Code references rows <em>by code</em> but never branches on
 * them, so the business can grow a list without a deploy.</p>
 *
 * <p>The typed {@link #metadata} blob carries the per-type payload (e.g. MSISDN prefixes for an
 * operator, SWIFT for a bank). It is validated per type at the service layer
 * (see {@code ReferenceMetadataValidator}) and guarded at the collection level by
 * {@code reference-data-schema.json}.</p>
 */
@Document(collection = "reference_data")
@CompoundIndexes({
        // One code per type — the core uniqueness contract
        @CompoundIndex(name = "type_code_unique", def = "{'type': 1, 'code': 1}", unique = true),
        // The dropdown query: active rows of a type, ordered
        @CompoundIndex(name = "type_active_order", def = "{'type': 1, 'isActive': 1, 'displayOrder': 1}"),
        // Hierarchy lookups (cities within a province, genres within a category)
        @CompoundIndex(name = "type_parent", def = "{'type': 1, 'parentCode': 1}")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReferenceData {

    @Id
    private String id;

    /**
     * Discriminator. Determines which list this row belongs to and which metadata contract applies.
     */
    @NotNull(message = "Reference type is required")
    private ReferenceType type;

    /**
     * Stable business key, unique within {@link #type}. Uppercase/alphanumeric with underscores,
     * dots or hyphens (e.g. "MTN", "ZANACO", "Africa/Lusaka", "en").
     */
    @NotBlank(message = "Code is required")
    @Pattern(regexp = "^[A-Za-z0-9_./+-]{1,60}$", message = "Code must be 1-60 chars: letters, digits, _ . / + -")
    private String code;

    /**
     * Human-readable display name (e.g. "MTN Mobile Money").
     */
    @NotBlank(message = "Name is required")
    private String name;

    private String description;

    // ── Hierarchy (optional) ──────────────────────────────────────────────────

    /**
     * Type of the parent row, when this row belongs to a hierarchy (e.g. MUSIC_GENRE → EVENT_CATEGORY).
     */
    private ReferenceType parentType;

    /**
     * Code of the parent row within {@link #parentType} (e.g. "MUSIC", or "ZM" for a province).
     */
    private String parentCode;

    // ── Presentation & lifecycle ──────────────────────────────────────────────

    @Builder.Default
    private int displayOrder = 0;

    @Builder.Default
    private boolean isActive = true;

    /**
     * System-managed rows are seeded and protected: they cannot be deleted via admin CRUD and their
     * system-owned fields are refreshed by the seeder on boot.
     */
    @Builder.Default
    private boolean isSystem = false;

    // ── Temporal validity (optional — used by TAX_RATE etc.) ──────────────────

    private LocalDateTime effectiveFrom;

    private LocalDateTime effectiveTo;

    // ── Per-type payload ──────────────────────────────────────────────────────

    /**
     * Free-form, per-type payload validated by {@code ReferenceMetadataValidator}.
     * Examples: {providerCode, msisdnPrefixes[]} for an operator, {swift, sortCode} for a bank,
     * {symbol, decimals} for a currency, {rate, countryCode} for a tax rate.
     */
    @Builder.Default
    private Map<String, Object> metadata = new HashMap<>();

    // ── Audit ─────────────────────────────────────────────────────────────────

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    @CreatedBy
    private String createdBy;

    @LastModifiedBy
    private String updatedBy;
}
