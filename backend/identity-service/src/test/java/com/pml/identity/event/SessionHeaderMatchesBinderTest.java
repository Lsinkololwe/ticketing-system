package com.pml.identity.event;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.spring.messaging.servicebus.support.ServiceBusMessageHeaders;
import com.pml.shared.event.EventBridge;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

@Tag("L1")
@Tag("ET-PLT-003")
@DisplayName("the session key header the outbox sets is the one the Azure binder turns into a Service Bus session id")
class SessionHeaderMatchesBinderTest {

    @Test
    @DisplayName("a differently named header would send every event with no session id")
    void sameHeaderName() {
        assertThat(EventBridge.SESSION_ID_HEADER).isEqualTo(ServiceBusMessageHeaders.SESSION_ID);
    }
}
