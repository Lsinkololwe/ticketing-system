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
 * Province Model
 */
@Document(collection = "provinces")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Province {

    @Id
    private String id;

    @NotBlank(message = "Province name is required")
    @Indexed(unique = true)
    private String name;

    private String code;

    /**
     * Country this province belongs to
     */
    @Indexed
    private String country;

    @Builder.Default
    private boolean isActive = true;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;
}
