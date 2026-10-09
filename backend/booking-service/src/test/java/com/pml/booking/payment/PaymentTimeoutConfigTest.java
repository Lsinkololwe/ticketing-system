package com.pml.booking.payment;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.ConfigurationPropertySources;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * How long a payment intent stays payable is set in one place, and the code reads that place.
 *
 * <p>The code read {@code payment.timeout.minutes}, which no file set, while the configuration
 * declared {@code payment.timeout} and {@code booking.payment.timeout-minutes}, which nothing read:
 * the intent always expired at the code's own default, whatever an operator configured.
 */
@Tag("L1")
@Tag("ET-PAY-001")
@DisplayName("The payment timeout an operator sets is the one intents expire by")
class PaymentTimeoutConfigTest {

    private static final String KEY = "booking.payment.timeout";

    @Test
    @DisplayName("application.yml declares booking.payment.timeout")
    void declared() throws Exception {
        var sources = new YamlPropertySourceLoader().load("application", new ClassPathResource("application.yml"));
        Duration timeout = new Binder(ConfigurationPropertySources.from(sources)).bind(KEY, Duration.class).get();
        assertThat(timeout).isEqualTo(Duration.ofMinutes(15));
    }

    @Test
    @DisplayName("PaymentServiceImpl reads exactly that key")
    void read() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/pml/booking/service/impl/PaymentServiceImpl.java"));
        assertThat(source).contains("@Value(\"${" + KEY + ":");
    }
}
