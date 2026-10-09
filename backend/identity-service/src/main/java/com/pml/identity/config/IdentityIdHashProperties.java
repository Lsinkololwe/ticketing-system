package com.pml.identity.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/** {@code identity.id-hash.*}: the key behind identifiers derived from contacts (workflow ids). */
@Data
@Component
@ConfigurationProperties(prefix = "identity.id-hash")
public class IdentityIdHashProperties {

    private String key;
}
