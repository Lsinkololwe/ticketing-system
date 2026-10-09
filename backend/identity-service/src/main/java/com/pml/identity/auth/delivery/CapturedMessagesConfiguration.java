package com.pml.identity.auth.delivery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The in-memory capture, only when capture is switched on. */
@Configuration
@ConditionalOnProperty(name = "identity.delivery.capture.enabled", havingValue = "true")
class CapturedMessagesConfiguration {

    @Bean
    CapturedMessages capturedMessages() {
        return new CapturedMessages();
    }
}
