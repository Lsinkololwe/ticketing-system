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
                .switchIfEmpty(Mono.defer(() -> save(PlatformConfiguration.createDefault())))
                .flatMap(config -> {
                    // A document written before the rules section existed gets the documented
                    // starting values once; after that an administrator owns them.
                    if (config.getRules() == null) {
                        config.setRules(com.pml.shared.config.model.PlatformRulesSection.defaults());
                        return save(config);
                    }
                    return Mono.just(config);
                });
    }
}
