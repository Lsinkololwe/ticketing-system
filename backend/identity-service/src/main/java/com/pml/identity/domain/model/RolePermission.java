package com.pml.identity.domain.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import jakarta.validation.constraints.NotBlank;
import java.time.LocalDateTime;

/**
 * Role Permission Model
 *
 * Maps roles to permissions. Each role can have multiple permissions.
 */
@Document(collection = "role_permissions")
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
@CompoundIndex(name = "role_permission_unique", def = "{'roleId': 1, 'permissionId': 1}", unique = true)
public class RolePermission {

    @Id
    private String id;

    @NotBlank(message = "Role ID is required")
    @Indexed
    private String roleId;

    @NotBlank(message = "Permission ID is required")
    @Indexed
    private String permissionId;

    @CreatedDate
    private LocalDateTime createdAt;

    @CreatedBy
    private String createdBy;

    @Field("isActive")
    @Builder.Default
    private boolean isActive = true;
}
