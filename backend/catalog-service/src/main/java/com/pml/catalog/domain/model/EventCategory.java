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
 * Event Category Model
 *
 * Represents a category for events (e.g., Music, Sports, Conference).
 */
@Document(collection = CatalogCollections.CATEGORIES)
@TypeAlias("categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCategory {

    @Id
    private String id;

    @NotBlank(message = "Category name is required")
    private String name;

    /**
     * Unique code for this category (e.g., "MUSIC", "SPORTS")
     */
    private String code;

    private String description;

    private String iconUrl;

    private String color;

    @Builder.Default
    private int displayOrder = 0;

    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
