package com.pml.catalog.domain.model;

import com.pml.catalog.persistence.CatalogCollections;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.TypeAlias;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import java.time.Instant;

/**
 * City Model
 */
@Document(collection = CatalogCollections.CITIES)
@TypeAlias("cities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class City {

    @Id
    private String id;

    @NotBlank(message = "City name is required")
    private String name;

    @NotBlank(message = "Province ID is required")
    private String provinceId;

    private String provinceName;

    /**
     * Denormalized country field for efficient queries.
     * Should be synced with the Province's country.
     */
    private String country;

    /**
     * Count of events in this city (denormalized for filtering/sorting)
     */
    @Builder.Default
    private int eventCount = 0;

    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
