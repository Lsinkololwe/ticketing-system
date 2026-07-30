package com.pml.catalog.config;

import com.pml.catalog.repository.PlatformConfigurationRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ensures the single {@code platform_configuration} document exists at startup.
 *
 * <p>Catalog owns this document. Its repository seeds a default lazily on first read, but
 * other services (e.g. identity, when creating an organization) depend on it being present.
 * This runner warms it up at boot so the seeded document — including the payment/payout/
 * commission defaults — is guaranteed to exist before it is consumed.</p>
 *
 * <p>Idempotent: {@code getConfiguration()} only creates the document when it is absent.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PlatformConfigurationInitializer implements ApplicationRunner {

    private final PlatformConfigurationRepository configurationRepository;

    @Override
    public void run(ApplicationArguments args) {
        configurationRepository.getConfiguration()
                .doOnSuccess(config -> log.info(
                        "Platform configuration ready (id={}, commissionRate={})",
                        config.getId(),
                        config.getPayment() != null ? config.getPayment().getCommissionRate() : null))
                .doOnError(e -> log.error("Failed to initialize platform configuration: {}", e.getMessage()))
                .block();
    }
}
