package com.pml.keycloak;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ServiceLoader;
import java.util.stream.StreamSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.keycloak.authentication.AuthenticatorFactory;
import org.keycloak.events.EventListenerProviderFactory;

@Tag("ET-IDN-001")
@Tag("layer-1-decision")
class ServiceRegistrationTest {

    @Test
    @DisplayName("ET-IDN-001-R13 · ServiceLoader finds contact-otp-authenticator and user-sync, and nothing legacy")
    void registered() {
        var auth = StreamSupport.stream(ServiceLoader.load(AuthenticatorFactory.class).spliterator(), false)
                .filter(f -> f.getClass().getName().startsWith("com.pml.keycloak")).toList();
        assertThat(auth).extracting(AuthenticatorFactory::getId).containsExactly("contact-otp-authenticator");
        var listeners = StreamSupport.stream(ServiceLoader.load(EventListenerProviderFactory.class).spliterator(), false)
                .filter(f -> f.getClass().getName().startsWith("com.pml.keycloak")).toList();
        assertThat(listeners).extracting(EventListenerProviderFactory::getId).containsExactly("user-sync");
    }

    @Test
    @DisplayName("ET-IDN-001-R13 · the authenticator factory without credentials builds a refusing authenticator")
    void authenticatorFactoryFailsClosed() {
        var factory = new com.pml.keycloak.authenticator.ContactOtpAuthenticatorFactory();
        factory.init(null);
        assertThat(factory.create(null)).isNotNull();
        assertThat(factory.isUserSetupAllowed()).isFalse();
    }
}
