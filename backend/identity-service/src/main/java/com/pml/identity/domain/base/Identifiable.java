package com.pml.identity.domain.base;

/**
 * Base interface for all entities with an ID.
 *
 * @param <ID> The type of the identifier
 */
public interface Identifiable<ID> {

    /**
     * Get the entity's unique identifier.
     *
     * @return The entity ID
     */
    ID getId();

    /**
     * Set the entity's unique identifier.
     *
     * @param id The entity ID
     */
    void setId(ID id);
}
