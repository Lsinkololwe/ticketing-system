package com.pml.identity.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.bind.PropertySourcesPlaceholdersResolver;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.env.MutablePropertySources;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;

import java.time.Duration;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** The typed identity.* configuration binds from the shipped files and refuses bad secrets. L1: no Spring context. */
@Tag("L1")
@Tag("ET-IDN-004")
@DisplayName("ET-IDN-004 · identity configuration binds and fails fast on missing secrets")
class IdentityConfigurationTest {

    /** Properties as the given files would produce them, later files overriding earlier ones. */
    private static Binder binder(String... files) throws Exception {
        MutablePropertySources sources = new MutablePropertySources();
        for (String file : files) {
            List<PropertySource<?>> loaded = new YamlPropertySourceLoader().load(file, new ClassPathResource(file));
            loaded.forEach(sources::addFirst);
        }
        return new Binder(ConfigurationPropertySources.from(sources), new PropertySourcesPlaceholdersResolver(sources));
    }

    private record Bound(IdentityChallengeProperties challenge, IdentityProofProperties proof,
                         IdentityLoginHandleProperties loginHandle, IdentityContactProperties contact,
                         IdentityIdHashProperties idHash, IdentityLimitsProperties limits,
                         IdentityDeliveryProperties delivery) {
        List<String> validate(boolean development) {
            return IdentityConfigurationValidator.validate(development, challenge, proof, loginHandle, contact,
                    idHash, limits, delivery);
        }
    }

    private static Bound bind(String... files) throws Exception {
        Binder b = binder(files);
        return new Bound(
                b.bind("identity.challenge", IdentityChallengeProperties.class).get(),
                b.bind("identity.proof", IdentityProofProperties.class).get(),
                b.bind("identity.login-handle", IdentityLoginHandleProperties.class).get(),
                b.bind("identity.contact", IdentityContactProperties.class).get(),
                b.bind("identity.id-hash", IdentityIdHashProperties.class).get(),
                b.bind("identity.limits", IdentityLimitsProperties.class).get(),
                b.bind("identity.delivery", IdentityDeliveryProperties.class).get());
    }

    @Test
    @DisplayName("the base file binds the contract's defaults")
    void baseDefaults() throws Exception {
        Bound c = bind("application.yml");
        assertThat(c.challenge().getCodeLength()).isEqualTo(6);
        assertThat(c.challenge().getTtl()).isEqualTo(Duration.ofMinutes(5));
        assertThat(c.challenge().getMaxAttempts()).isEqualTo(5);
        assertThat(c.challenge().getLock()).isEqualTo(Duration.ofMinutes(15));
        assertThat(c.challenge().getCooldown()).isEqualTo(Duration.ofSeconds(60));
        assertThat(c.proof().getTtl()).isEqualTo(Duration.ofMinutes(2));
        assertThat(c.proof().getEnsureHold()).isEqualTo(Duration.ofMinutes(30));
        assertThat(c.loginHandle().getTtl()).isEqualTo(Duration.ofSeconds(60));
        assertThat(c.limits().getContactCodesPerDay()).isEqualTo(10);
        assertThat(c.limits().getIpCodesPerHour()).isEqualTo(10);
        assertThat(c.limits().getDeviceDistinctContactsPerDay()).isEqualTo(5);
        assertThat(c.limits().countryLimit("ZM")).isEqualTo(2000);
        assertThat(c.limits().getAllowedCountries()).containsExactly("ZM", "GB", "US", "ZA", "ZW", "MW", "TZ", "KE",
                "BW", "NA", "AE", "CA", "AU", "IE", "DE", "FR", "NL");
        assertThat(c.delivery().getWhatsapp().getTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(c.delivery().getEmail().getTimeout()).isEqualTo(Duration.ofSeconds(5));
        assertThat(c.delivery().getCapture().isEnabled()).isFalse();
    }

    @Test
    @DisplayName("the base file carries no secret: all three are blank, so production must supply them")
    void baseHasNoSecrets() throws Exception {
        Bound c = bind("application.yml");
        assertThat(c.challenge().getHmacPepper()).isNullOrEmpty();
        assertThat(c.contact().getHashKey()).isNullOrEmpty();
        assertThat(c.idHash().getKey()).isNullOrEmpty();
    }

    @Test
    @DisplayName("outside local and test, a blank secret stops startup and names the property, not a value")
    void blankSecretsRefused() throws Exception {
        List<String> problems = bind("application.yml").validate(false);
        assertThat(problems).anyMatch(p -> p.contains("identity.challenge.hmac-pepper"));
        assertThat(problems).anyMatch(p -> p.contains("identity.contact.hash-key"));
        assertThat(problems).anyMatch(p -> p.contains("identity.id-hash.key"));
        assertThat(problems).anyMatch(p -> p.contains("no delivery channel is enabled"));
    }

    @Test
    @DisplayName("a short secret and capture delivery are refused outside local and test")
    void shortSecretsAndCapture() throws Exception {
        Bound c = bind("application.yml");
        c.challenge().setHmacPepper("short");
        c.contact().setHashKey("x".repeat(40));
        c.idHash().setKey("y".repeat(40));
        c.delivery().getCapture().setEnabled(true);
        c.delivery().getEmail().setEnabled(true);
        c.delivery().getEmail().setFrom("no-reply@myticket.example");
        List<String> problems = c.validate(false);
        assertThat(problems).hasSize(2);
        assertThat(problems).anyMatch(p -> p.contains("hmac-pepper") && p.contains("at least"));
        assertThat(problems).anyMatch(p -> p.contains("capture"));
        assertThat(String.join(" ", problems)).doesNotContain("short").doesNotContain("xxxx");
    }

    @Test
    @DisplayName("enabled whatsapp delivery needs its settings")
    void whatsappNeedsSettings() throws Exception {
        Bound c = bind("application.yml");
        c.challenge().setHmacPepper("a".repeat(40));
        c.contact().setHashKey("b".repeat(40));
        c.idHash().setKey("c".repeat(40));
        c.delivery().getWhatsapp().setEnabled(true);
        assertThat(c.validate(false)).anyMatch(p -> p.contains("identity.delivery.whatsapp.phone-number-id"))
                .anyMatch(p -> p.contains("identity.delivery.whatsapp.access-token"))
                .anyMatch(p -> p.contains("identity.delivery.whatsapp.template-name"));
        c.delivery().getWhatsapp().setPhoneNumberId("1");
        c.delivery().getWhatsapp().setAccessToken("t");
        c.delivery().getWhatsapp().setTemplateName("otp");
        assertThat(c.validate(false)).isEmpty();
    }

    @Test
    @DisplayName("structural mistakes are refused even in development")
    void structuralMistakes() throws Exception {
        Bound c = bind("application.yml");
        c.challenge().setCodeLength(2);
        c.challenge().setTtl(Duration.ZERO);
        c.limits().setAllowedCountries(List.of());
        assertThat(c.validate(true)).hasSize(3);
    }

    @Test
    @DisplayName("the test profile file makes the configuration valid for tests, with no real secret")
    void testProfileIsValid() throws Exception {
        Bound c = bind("application.yml", "application-test.yml");
        assertThat(c.validate(true)).isEmpty();
        assertThat(c.delivery().getCapture().isEnabled()).isTrue();
        assertThat(c.contact().getHashKey()).contains("not-a-secret");
    }

    @Test
    @DisplayName("the local profile file makes the configuration valid for development")
    void localProfileIsValid() throws Exception {
        Bound c = bind("application.yml", "application-local.yml");
        assertThat(c.validate(true)).isEmpty();
        assertThat(c.challenge().getHmacPepper()).contains("not-a-secret");
    }

    @Test
    @DisplayName("the production file overrides none of the secrets")
    void productionOverridesNoSecrets() throws Exception {
        Bound c = bind("application.yml", "application-prod.yml");
        assertThat(c.contact().getHashKey()).isNullOrEmpty();
        assertThat(c.delivery().getCapture().isEnabled()).isFalse();
    }
}
