package com.pml.identity.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the service logs by default. Request and token details are printed at DEBUG and TRACE, so
 * the base configuration and the production profile must be quiet; only the local profile file is
 * verbose.
 */
@Tag("L1")
@Tag("ET-IDN-001")
@DisplayName("production resolves no DEBUG or TRACE logger for the application or for Spring Security")
class IdentityLoggingConfigurationTest {

    private static Map<String, String> levels(String... files) throws IOException {
        MutablePropertySources sources = new MutablePropertySources();
        // later files win, as profile files win over the base file
        for (String file : files) {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
            for (PropertySource<?> source : loaded) {
                sources.addFirst(source);
            }
        }
        Binder binder = new Binder(ConfigurationPropertySources.from(sources));
        Map<String, String> bound = binder.bind("logging.level", Bindable.mapOf(String.class, String.class))
                .orElse(new TreeMap<>());
        return new TreeMap<>(bound);
    }

    @Test
    @DisplayName("the base file and the prod profile set application and security loggers to INFO and WARN, and nothing verbose")
    void prodIsQuiet() throws IOException {
        Map<String, String> prod = levels("application.yml", "application-prod.yml");

        assertThat(prod.getOrDefault("com.pml.identity", prod.get("root"))).isEqualTo("INFO");
        assertThat(prod.get("org.springframework.security")).isEqualTo("WARN");
        assertThat(prod.values()).as("no logger is at DEBUG or TRACE in production")
                .noneMatch(level -> level.equalsIgnoreCase("DEBUG") || level.equalsIgnoreCase("TRACE"));
    }

    @Test
    @DisplayName("only the local profile turns the application and Spring Security up to DEBUG")
    void localIsVerbose() throws IOException {
        Map<String, String> local = levels("application.yml", "application-local.yml");

        assertThat(local.get("com.pml.identity")).isEqualTo("DEBUG");
        assertThat(local.get("org.springframework.security")).isEqualTo("DEBUG");
    }
}
