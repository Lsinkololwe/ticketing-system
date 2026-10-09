package com.pml.identity.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The field-encryption key never falls back to a value in the repository outside local development.
 *
 * <p>A default in the base file is used by every environment that forgets the variable, and a key
 * committed to git is a key anyone with the repository holds.
 */
@Tag("L1")
@Tag("ET-ORG-001")
@DisplayName("The payout-data encryption key comes from the environment, except in local development")
class EncryptionKeyConfigTest {

    private static Object keyIn(String file) throws Exception {
        List<PropertySource<?>> sources = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
        return sources.get(0).getProperty("app.security.encryption.key");
    }

    @Test
    @DisplayName("the base configuration names the variable and gives no fallback")
    void baseHasNoFallback() throws Exception {
        assertThat(keyIn("application.yml")).hasToString("${APP_SECURITY_ENCRYPTION_KEY}");
    }

    @Test
    @DisplayName("the production profile does not supply a key of its own")
    void productionSuppliesNone() throws Exception {
        assertThat(keyIn("application-prod.yml")).isNull();
    }

    @Test
    @DisplayName("the local profile's development key is a valid 256-bit key")
    void localKeyIsUsable() throws Exception {
        byte[] key = Base64.getDecoder().decode(String.valueOf(keyIn("application-local.yml")));
        assertThat(key).hasSize(32);
        new FieldEncryptionService(String.valueOf(keyIn("application-local.yml")));
    }
}
