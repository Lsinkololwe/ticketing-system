package com.pml.identity.platform;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Where the health probe looks. Each entry is a downstream service's base URL; its
 * {@code /actuator/health} is asked. Configure per environment; an empty map probes only what
 * identity itself depends on.
 */
@Data
@Component
@ConfigurationProperties(prefix = "identity.health")
public class HealthProbeProperties {

    /** Service name to base URL, e.g. {@code catalog: http://catalog-service:8085}. */
    private Map<String, String> targets = new LinkedHashMap<>();

    /** A probe that has not answered by then is DOWN. */
    private long timeoutMillis = 2000;
}
