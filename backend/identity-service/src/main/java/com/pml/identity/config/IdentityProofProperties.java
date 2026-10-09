package com.pml.identity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** {@code identity.proof.*}: the single-use token returned after a correct code. */
@Data
@Component
@ConfigurationProperties(prefix = "identity.proof")
public class IdentityProofProperties {

    /** How long a NEW proof may wait to be used. */
    private Duration ttl = Duration.ofMinutes(2);

    /** How long a proof consumed by {@code ensure} is kept for the account workflow to read. */
    private Duration ensureHold = Duration.ofMinutes(30);
}
