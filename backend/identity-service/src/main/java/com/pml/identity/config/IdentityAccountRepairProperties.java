package com.pml.identity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** {@code identity.account.repair.*}: when the repair schedule treats an account as stuck. */
@Data
@Component
@ConfigurationProperties(prefix = "identity.account.repair")
public class IdentityAccountRepairProperties {

    /** Whether this instance creates the {@code identity-account-repair} Schedule at boot (on in prod). */
    private boolean enabled = false;
    private Duration interval = Duration.ofMinutes(15);
    private Duration provisioningMaxAge = Duration.ofMinutes(10);
    private Duration mergingMaxAge = Duration.ofHours(2);
    private Duration changingMaxAge = Duration.ofHours(48);
}
