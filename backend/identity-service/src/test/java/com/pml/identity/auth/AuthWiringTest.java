package com.pml.identity.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.identity.account.AccountEnsurer;
import com.pml.identity.account.AccountStatusLookup;
import com.pml.identity.auth.challenge.ChallengeService;
import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.identity.auth.delivery.CapturingProvider;
import com.pml.identity.auth.delivery.DeliveryOrchestrator;
import com.pml.identity.auth.proof.ProofService;
import com.pml.identity.auth.web.InternalChallengeController;
import com.pml.identity.auth.web.InternalEnsureController;
import com.pml.identity.auth.web.InternalHandleController;
import com.pml.identity.config.IdentityChallengeProperties;
import com.pml.identity.config.IdentityContactProperties;
import com.pml.identity.config.IdentityDeliveryProperties;
import com.pml.identity.config.IdentityLimitsProperties;
import com.pml.identity.config.IdentityLoginHandleProperties;
import com.pml.identity.config.IdentityProofProperties;
import com.pml.identity.repository.AccountEventRepository;
import com.pml.identity.security.ContactCrypto;
import com.pml.identity.security.ContactHasher;
import com.pml.identity.security.FieldEncryptionService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.web.reactive.function.client.WebClient;

import java.time.Clock;

import static org.assertj.core.api.Assertions.assertThat;

/** The auth beans wire together from the same collaborators the service provides, and capture is opt-in. */
@Tag("L3")
@Tag("ET-IDN-001")
@DisplayName("ET-IDN-001 · the auth components wire up, and the in-memory capture exists only when switched on")
class AuthWiringTest {

    @Configuration
    @EnableConfigurationProperties
    @Import({IdentityChallengeProperties.class, IdentityLimitsProperties.class, IdentityProofProperties.class,
            IdentityLoginHandleProperties.class, IdentityDeliveryProperties.class, IdentityContactProperties.class})
    @ComponentScan("com.pml.identity.auth")
    static class Collaborators {
        @Bean
        ReactiveStringRedisTemplate redis() {
            return Mockito.mock(ReactiveStringRedisTemplate.class);
        }

        @Bean
        MeterRegistry meters() {
            return new SimpleMeterRegistry();
        }

        @Bean
        Clock clock() {
            return Clock.systemUTC();
        }

        @Bean
        WebClient.Builder webClientBuilder() {
            return WebClient.builder();
        }

        @Bean
        ObjectMapper objectMapper() {
            return new ObjectMapper();
        }

        @Bean
        ContactHasher contactHasher() {
            return new ContactHasher("wiring-test-hash-key");
        }

        @Bean
        ContactCrypto contactCrypto() {
            return new ContactCrypto(new FieldEncryptionService(FieldEncryptionService.generateKey()));
        }

        @Bean
        AccountEventRepository accountEvents() {
            return Mockito.mock(AccountEventRepository.class);
        }

        @Bean
        AccountEnsurer ensurer() {
            return Mockito.mock(AccountEnsurer.class);
        }

        @Bean
        AccountStatusLookup statusLookup() {
            return Mockito.mock(AccountStatusLookup.class);
        }
    }

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(Collaborators.class)
            .withPropertyValues("identity.challenge.hmac-pepper=wiring-test-pepper");

    @Test
    @DisplayName("everything wires, with the real providers and no capture by default")
    void defaultWiring() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(ChallengeService.class).hasSingleBean(DeliveryOrchestrator.class)
                    .hasSingleBean(ProofService.class).hasSingleBean(InternalChallengeController.class)
                    .hasSingleBean(InternalEnsureController.class).hasSingleBean(InternalHandleController.class);
            assertThat(context).doesNotHaveBean(CapturedMessages.class).doesNotHaveBean(CapturingProvider.class);
        });
    }

    @Test
    @DisplayName("identity.delivery.capture.enabled=true adds the capture and nothing else changes")
    void captureOptIn() {
        runner.withPropertyValues("identity.delivery.capture.enabled=true").run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).hasSingleBean(CapturedMessages.class).hasSingleBean(CapturingProvider.class);
        });
    }
}
