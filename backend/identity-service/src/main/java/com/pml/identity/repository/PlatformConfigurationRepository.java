package com.pml.identity.repository;

import com.pml.identity.domain.model.PlatformConfigurationView;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Mono;

/**
 * Read-only repository over the shared {@code platform_configuration} document.
 *
 * <p>The document is owned/written by catalog-service; identity reads the payment defaults
 * from it. It is expected to exist (catalog seeds it at startup) — a missing document is
 * treated as a hard error at organization creation rather than silently defaulting.</p>
 */
@Repository
public interface PlatformConfigurationRepository
        extends ReactiveMongoRepository<PlatformConfigurationView, String> {

    default Mono<PlatformConfigurationView> getConfiguration() {
        return findById(PlatformConfigurationView.DEFAULT_ID);
    }
}
