package com.pml.identity.domain.base;

import java.time.Instant;

/**
 * Base interface for entities with creation and modification timestamps.
 *
 * <p>All timestamps use {@link Instant} for timezone-agnostic storage.
 * This is the recommended approach for distributed systems.</p>
 */
public interface Timestamped {

    /**
     * Get the creation timestamp.
     *
     * @return When the entity was created
     */
    Instant getCreatedAt();

    /**
     * Set the creation timestamp.
     *
     * @param createdAt When the entity was created
     */
    void setCreatedAt(Instant createdAt);

    /**
     * Get the last modification timestamp.
     *
     * @return When the entity was last updated
     */
    Instant getUpdatedAt();

    /**
     * Set the last modification timestamp.
     *
     * @param updatedAt When the entity was last updated
     */
    void setUpdatedAt(Instant updatedAt);
}
