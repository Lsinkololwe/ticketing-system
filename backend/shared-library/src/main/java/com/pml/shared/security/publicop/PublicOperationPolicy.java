package com.pml.shared.security.publicop;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

/**
 * What a service lets a tokenless caller run, and how much of it.
 *
 * @param service          short name; part of the rate-limit key and the metric tag
 * @param rootFields       query root fields a caller without a token may select; adding one is a spec change
 * @param entityFields     federation entity types (and the leaf fields of each) the router may resolve here
 *                         for such a caller, via {@code _entities}; empty refuses {@code _entities} outright
 * @param maxBodyBytes     largest request body that is even parsed
 * @param maxDepth         deepest selection nesting, counted through fragments
 * @param maxNodes         most field selections in the operation, counted through fragments
 * @param maxFragments     most fragment definitions in the document
 * @param limitPerWindow   requests one client address may make per window
 * @param window           the fixed rate-limit window
 */
public record PublicOperationPolicy(String service,
                                    Set<String> rootFields,
                                    Map<String, Set<String>> entityFields,
                                    int maxBodyBytes,
                                    int maxDepth,
                                    int maxNodes,
                                    int maxFragments,
                                    int limitPerWindow,
                                    Duration window) {

    public PublicOperationPolicy {
        rootFields = Set.copyOf(rootFields);
        entityFields = Map.copyOf(entityFields);
    }

    /** The service's allowlist with the platform defaults for every limit. */
    public static PublicOperationPolicy of(String service, Set<String> rootFields) {
        return new PublicOperationPolicy(service, rootFields, Map.of(), 16 * 1024, 10, 300, 10, 120, Duration.ofMinutes(1));
    }

    public PublicOperationPolicy withEntities(Map<String, Set<String>> entities) {
        return new PublicOperationPolicy(service, rootFields, entities, maxBodyBytes, maxDepth, maxNodes, maxFragments,
                limitPerWindow, window);
    }

    public PublicOperationPolicy withLimit(int limit, Duration newWindow) {
        return new PublicOperationPolicy(service, rootFields, entityFields, maxBodyBytes, maxDepth, maxNodes,
                maxFragments, limit, newWindow);
    }
}
