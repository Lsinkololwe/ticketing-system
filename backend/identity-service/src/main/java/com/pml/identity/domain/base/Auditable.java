package com.pml.identity.domain.base;

/**
 * Base interface for entities that track who created and modified them.
 *
 * <p>This interface extends {@link Timestamped} to include both
 * when and who performed the operations.</p>
 */
public interface Auditable extends Timestamped {

    /**
     * Get the ID of the user who created this entity.
     *
     * @return User ID (Keycloak user ID) of the creator
     */
    String getCreatedBy();

    /**
     * Set the ID of the user who created this entity.
     *
     * @param createdBy User ID (Keycloak user ID) of the creator
     */
    void setCreatedBy(String createdBy);

    /**
     * Get the ID of the user who last modified this entity.
     *
     * @return User ID (Keycloak user ID) of the last modifier
     */
    String getUpdatedBy();

    /**
     * Set the ID of the user who last modified this entity.
     *
     * @param updatedBy User ID (Keycloak user ID) of the last modifier
     */
    void setUpdatedBy(String updatedBy);
}
