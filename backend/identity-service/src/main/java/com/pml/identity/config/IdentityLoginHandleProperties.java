package com.pml.identity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/** {@code identity.login-handle.*}: the single-use token that signs an ACTIVE account in. */
@Data
@Component
@ConfigurationProperties(prefix = "identity.login-handle")
public class IdentityLoginHandleProperties {

    private Duration ttl = Duration.ofSeconds(60);
}
