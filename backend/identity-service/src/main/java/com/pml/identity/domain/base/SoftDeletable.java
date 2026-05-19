package com.pml.identity.domain.base;

import java.time.Instant;

/**
 * Base interface for entities that support soft deletion.
 *
 * <p>Soft-deleted entities are marked as deleted but remain in the database.
 * This allows for recovery and audit purposes.</p>
 */
public interface SoftDeletable {

    /**
     * Check if the entity is soft-deleted.
     *
     * @return true if the entity is deleted
     */
    boolean isDeleted();

    /**
     * Set the deleted flag.
     *
     * @param deleted true to mark as deleted
     */
    void setDeleted(boolean deleted);

    /**
     * Get the deletion timestamp.
     *
     * @return When the entity was soft-deleted, or null if not deleted
     */
    Instant getDeletedAt();

    /**
     * Set the deletion timestamp.
     *
     * @param deletedAt When the entity was soft-deleted
     */
    void setDeletedAt(Instant deletedAt);

    /**
     * Get the ID of the user who deleted this entity.
     *
     * @return User ID of the deleter, or null if not deleted
     */
    String getDeletedBy();

    /**
     * Set the ID of the user who deleted this entity.
     *
     * @param deletedBy User ID of the deleter
     */
    void setDeletedBy(String deletedBy);
}
