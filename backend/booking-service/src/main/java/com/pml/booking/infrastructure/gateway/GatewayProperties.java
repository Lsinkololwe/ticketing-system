package com.pml.booking.infrastructure.gateway;

import com.pml.booking.infrastructure.gateway.model.MobileNetwork;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration properties for the payment gateway abstraction layer.
 * <p>
 * Configuration prefix: {@code payment.gateway}
 * <p>
 * Example configuration:
 * <pre>
 * payment:
 *   gateway:
 *     primary-providers:
 *       MTN: pawapay
 *       AIRTEL: pawapay
 *       ZAMTEL: pawapay
 *     fallback:
 *       enabled: true
 *       max-retries: 2
 * </pre>
 */
@Data
@Component
@Validated
@ConfigurationProperties(prefix = "payment.gateway")
public class GatewayProperties {

    /**
     * Primary provider for each mobile network.
     * Key: Network name (MTN, AIRTEL, ZAMTEL)
     * Value: Provider ID (e.g., "pawapay", "flutterwave")
     */
    private Map<String, String> primaryProviders = new HashMap<>();

    /**
     * Fallback configuration when primary provider is unavailable.
     */
    private Fallback fallback = new Fallback();

    /**
     * Get the primary provider ID for a mobile network.
     *
     * @param network Mobile network
     * @return Provider ID, defaults to "pawapay" if not configured
     */
    public String getPrimaryProvider(MobileNetwork network) {
        if (network == null) return "pawapay";
        return primaryProviders.getOrDefault(network.name(), "pawapay");
    }

    @Data
    public static class Fallback {
        /**
         * Whether to attempt fallback to other providers when primary is unavailable.
         */
        private boolean enabled = true;
    }
}
