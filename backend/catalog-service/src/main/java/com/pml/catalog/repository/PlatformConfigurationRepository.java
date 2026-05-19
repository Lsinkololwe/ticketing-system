package com.pml.catalog.repository;

import com.pml.catalog.domain.model.PlatformConfiguration;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Repository for PlatformConfiguration.
 *
 * This is a singleton document - there should only be one configuration.
 */
@Repository
public interface PlatformConfigurationRepository extends ReactiveMongoRepository<PlatformConfiguration, String> {

    /**
     * Get the platform configuration (singleton)
     */
    default Mono<PlatformConfiguration> getConfiguration() {
        return findById(PlatformConfiguration.DEFAULT_ID)
                .switchIfEmpty(Mono.defer(() -> save(PlatformConfiguration.createDefault())));
    }
}
