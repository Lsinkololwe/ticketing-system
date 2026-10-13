package com.pml.keycloak;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.pml.keycloak.authenticator.ContactOtpAuthenticatorFactory;
import com.pml.keycloak.eventlistener.UserSyncEventListenerFactory;
import java.util.HashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.events.EventListenerProviderFactory;

@Tag("ET-IDN-001")
@Tag("layer-1-decision")
class ServiceRegistrationTest {

    @Test
    @DisplayName("ServiceLoader finds contact-otp-authenticator and user-sync, and nothing legacy")
    void registered() {
        var auth = StreamSupport.stream(ServiceLoader.load(AuthenticatorFactory.class).spliterator(), false)
                .filter(f -> f.getClass().getName().startsWith("com.pml.keycloak")).toList();
        assertThat(auth).extracting(AuthenticatorFactory::getId).containsExactly("contact-otp-authenticator");
        var listeners = StreamSupport.stream(ServiceLoader.load(EventListenerProviderFactory.class).spliterator(), false)
                .filter(f -> f.getClass().getName().startsWith("com.pml.keycloak")).toList();
        assertThat(listeners).extracting(EventListenerProviderFactory::getId).containsExactly("user-sync");
    }

    private static Map<String, String> fullEnvironment() {
        Map<String, String> env = new HashMap<>();
        env.put("IDENTITY_BASE_URL", "http://identity.test:8083");
        env.put("IDENTITY_CLIENT_ID", "plugin-client");
        env.put("IDENTITY_CLIENT_SECRET", "super-secret-value");
        env.put("KEYCLOAK_TOKEN_URL", "http://keycloak.test/realms/r/protocol/openid-connect/token");
        return env;
    }

    @Test
    @DisplayName("the authenticator factory starts when all four identity variables are present")
    void authenticatorFactoryStartsWithAllVariables() {
        ContactOtpAuthenticatorFactory factory = new ContactOtpAuthenticatorFactory();
        factory.start(fullEnvironment());
        assertThat(factory.create(null)).isNotNull();
        assertThat(factory.isUserSetupAllowed()).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {"IDENTITY_BASE_URL", "IDENTITY_CLIENT_ID", "IDENTITY_CLIENT_SECRET", "KEYCLOAK_TOKEN_URL"})
    @DisplayName("the authenticator factory refuses to start when a required variable is missing, naming it but never a value")
    void authenticatorFactoryRefusesToStartWhenMissing(String name) {
        Map<String, String> missing = fullEnvironment();
        missing.remove(name);
        assertThatThrownBy(() -> new ContactOtpAuthenticatorFactory().start(missing))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name)
                .hasMessageNotContaining("super-secret-value")
                .hasMessageNotContaining("plugin-client");

        Map<String, String> blank = fullEnvironment();
        blank.put(name, "   ");
        assertThatThrownBy(() -> new ContactOtpAuthenticatorFactory().start(blank))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(name);
    }

    @Test
    @DisplayName("the user-sync listener factory also refuses to start without the identity variables")
    void listenerFactoryRefusesToStartWhenMissing() {
        assertThatThrownBy(() -> new UserSyncEventListenerFactory().start(Map.of()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IDENTITY_BASE_URL").hasMessageContaining("IDENTITY_CLIENT_ID")
                .hasMessageContaining("IDENTITY_CLIENT_SECRET").hasMessageContaining("KEYCLOAK_TOKEN_URL");
        UserSyncEventListenerFactory factory = new UserSyncEventListenerFactory();
        factory.start(fullEnvironment());
        factory.close();
    }
}
