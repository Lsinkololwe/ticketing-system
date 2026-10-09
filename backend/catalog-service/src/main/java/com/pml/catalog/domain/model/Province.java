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
 * Province Model
 */
@Document(collection = CatalogCollections.PROVINCES)
@TypeAlias("provinces")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Province {

    @Id
    private String id;

    @NotBlank(message = "Province name is required")
    private String name;

    private String code;

    /**
     * Country this province belongs to
     */
    private String country;

    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    private Instant createdAt;

    @LastModifiedDate
    private Instant updatedAt;
}
