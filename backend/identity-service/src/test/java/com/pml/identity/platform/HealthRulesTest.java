package com.pml.identity.platform;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.net.ConnectException;
import java.net.UnknownHostException;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-ADM-005")
@DisplayName("ET-ADM-005-R9 · a health answer is UP only when it says so; failures carry a category, never an address")
class HealthRulesTest {

    @Test
    void actuatorUpOnlyWhenTheBodySaysUp() {
        assertThat(HealthRules.ofActuator(200, "{\"status\":\"UP\"}")).isEqualTo(HealthRules.Status.UP);
        assertThat(HealthRules.ofActuator(200, "{ \"status\" : \"UP\" }")).isEqualTo(HealthRules.Status.UP);
        assertThat(HealthRules.ofActuator(200, "{\"status\":\"DOWN\"}")).isEqualTo(HealthRules.Status.DOWN);
        assertThat(HealthRules.ofActuator(503, "{\"status\":\"UP\"}")).isEqualTo(HealthRules.Status.DOWN);
        assertThat(HealthRules.ofActuator(200, null)).isEqualTo(HealthRules.Status.DOWN);
    }

    @Test
    void failureCategoriesNameNoHost() {
        assertThat(HealthRules.category(new TimeoutException("Did not observe any item within 2000ms for http://catalog:8085")))
                .isEqualTo("timeout");
        assertThat(HealthRules.category(new RuntimeException(new ConnectException("catalog:8085")))).isEqualTo("connection refused");
        assertThat(HealthRules.category(new RuntimeException(new UnknownHostException("catalog")))).isEqualTo("unreachable");
        assertThat(HealthRules.category(new IllegalStateException("boom http://x"))).isEqualTo("unavailable");
    }

    @Test
    void alertKeyIsStablePerService() {
        assertThat(HealthRules.alertKey("catalog")).isEqualTo("service-down:catalog");
    }
}
