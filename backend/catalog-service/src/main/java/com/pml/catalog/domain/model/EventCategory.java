package com.pml.catalog.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * Event Category Model
 *
 * Represents a category for events (e.g., Music, Sports, Conference).
 */
@Document(collection = "event_categories")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventCategory {

    @Id
    private String id;

    @NotBlank(message = "Category name is required")
    @Indexed(unique = true)
    private String name;

    /**
     * Unique code for this category (e.g., "MUSIC", "SPORTS")
     */
    @Indexed(unique = true)
    private String code;

    private String description;

    private String iconUrl;

    private String color;

    @Builder.Default
    private int displayOrder = 0;

    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
