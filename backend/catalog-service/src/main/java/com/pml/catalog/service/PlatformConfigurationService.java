package com.pml.catalog.service;

import com.pml.catalog.domain.model.PlatformConfiguration;
import reactor.core.publisher.Mono;

/**
 * Service for managing platform configuration.
 */
public interface PlatformConfigurationService {

    /**
     * Get the current platform configuration.
     * If no configuration exists, creates one with default values.
     */
    Mono<PlatformConfiguration> getConfiguration();

    /**
     * Update the platform configuration.
     *
     * @param config the updated configuration
     * @param updatedBy the admin who updated the configuration
     * @return the updated configuration
     */
    Mono<PlatformConfiguration> updateConfiguration(PlatformConfiguration config, String updatedBy);
}
