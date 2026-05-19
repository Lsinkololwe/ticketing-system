package com.pml.booking.infrastructure.gateway;

import com.pml.booking.infrastructure.gateway.domain.MobileNetwork;
import com.pml.booking.infrastructure.gateway.exception.NoGatewayAvailableException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Factory for selecting the appropriate MobileMoneyGateway.
 * <p>
 * Responsibilities:
 * - Select gateway based on mobile network
 * - Handle failover when primary gateway is unavailable
 * - Cache gateway availability status
 * - Provide gateway by explicit provider ID (for retries)
 * <p>
 * <strong>Pattern:</strong> Factory Pattern with Strategy selection.
 *
 * @see MobileMoneyGateway
 */
@Slf4j
@Component
public class MobileMoneyGatewayFactory {

    private final List<MobileMoneyGateway> gateways;
    private final GatewayProperties gatewayProperties;

    // Cache gateway availability to avoid repeated health checks
    private final Map<String, CachedAvailability> availabilityCache = new ConcurrentHashMap<>();

    private static final long AVAILABILITY_CACHE_TTL_MS = 30_000; // 30 seconds

    public MobileMoneyGatewayFactory(
            List<MobileMoneyGateway> gateways,
            GatewayProperties gatewayProperties
    ) {
        this.gateways = gateways;
        this.gatewayProperties = gatewayProperties;

        log.info("MobileMoneyGatewayFactory initialized with {} gateway(s): {}",
                gateways.size(),
                gateways.stream().map(MobileMoneyGateway::getProviderId).toList());
    }

    /**
     * Get the best available gateway for a mobile network.
     * <p>
     * Selection priority:
     * 1. Configured primary provider for the network (if available)
     * 2. Fallback to other providers supporting the network (by priority)
     *
     * @param network Mobile network to find gateway for
     * @return Gateway mono, or error if none available
     */
    public Mono<MobileMoneyGateway> getGateway(MobileNetwork network) {
        if (network == null) {
            return Mono.error(new NoGatewayAvailableException(
                    "Cannot select gateway: network is null"));
        }

        // Get configured primary provider for this network
        String primaryProviderId = gatewayProperties.getPrimaryProvider(network);

        return getGatewayWithFallback(network, primaryProviderId);
    }

    /**
     * Get the best available gateway for a phone number.
     * <p>
     * Auto-detects network from phone number.
     *
     * @param phoneNumber Phone number in E.164 format
     * @return Gateway mono, or error if none available
     */
    public Mono<MobileMoneyGateway> getGatewayForPhone(String phoneNumber) {
        MobileNetwork network = MobileNetwork.fromPhoneNumber(phoneNumber);
        if (network == null) {
            return Mono.error(new NoGatewayAvailableException(
                    "Cannot detect network from phone number: " + maskPhone(phoneNumber)));
        }
        return getGateway(network);
    }

    /**
     * Get gateway by explicit provider ID.
     * <p>
     * Used for retries on the same provider, or explicit provider selection.
     *
     * @param providerId Provider ID (e.g., "pawapay")
     * @return Gateway mono, or error if not found
     */
    public Mono<MobileMoneyGateway> getGatewayByProvider(String providerId) {
        return findGatewayById(providerId)
                .map(Mono::just)
                .orElseGet(() -> Mono.error(new NoGatewayAvailableException(
                        "Gateway not found: " + providerId)));
    }

    /**
     * Get all available gateways for a network.
     * <p>
     * Useful for listing options or implementing custom selection logic.
     *
     * @param network Mobile network
     * @return Flux of available gateways, sorted by priority (descending)
     */
    public Flux<MobileMoneyGateway> getAvailableGateways(MobileNetwork network) {
        return Flux.fromIterable(getGatewaysForNetwork(network))
                .flatMap(gateway -> checkAvailability(gateway)
                        .filter(available -> available)
                        .map(available -> gateway))
                .sort(Comparator.comparingInt(MobileMoneyGateway::getPriority).reversed());
    }

    /**
     * Get all registered gateways.
     */
    public List<MobileMoneyGateway> getAllGateways() {
        return List.copyOf(gateways);
    }

    /**
     * Check if any gateway is available for a network.
     */
    public Mono<Boolean> hasAvailableGateway(MobileNetwork network) {
        return getAvailableGateways(network)
                .hasElements();
    }

    /**
     * Clear availability cache (for testing or after configuration changes).
     */
    public void clearAvailabilityCache() {
        availabilityCache.clear();
        log.info("Gateway availability cache cleared");
    }

    // ==================== Private Methods ====================

    private Mono<MobileMoneyGateway> getGatewayWithFallback(MobileNetwork network, String primaryProviderId) {
        // Try primary provider first
        return findGatewayById(primaryProviderId)
                .filter(gateway -> gateway.supportsNetwork(network))
                .map(gateway -> checkAvailabilityAndReturn(gateway, network))
                .orElseGet(() -> findFallbackGateway(network, primaryProviderId));
    }

    private Mono<MobileMoneyGateway> checkAvailabilityAndReturn(MobileMoneyGateway gateway, MobileNetwork network) {
        return checkAvailability(gateway)
                .flatMap(available -> {
                    if (available) {
                        log.debug("Using primary gateway {} for network {}",
                                gateway.getProviderId(), network);
                        return Mono.just(gateway);
                    } else {
                        log.warn("Primary gateway {} unavailable for network {}, trying fallback",
                                gateway.getProviderId(), network);
                        return findFallbackGateway(network, gateway.getProviderId());
                    }
                });
    }

    private Mono<MobileMoneyGateway> findFallbackGateway(MobileNetwork network, String excludeProviderId) {
        if (!gatewayProperties.getFallback().isEnabled()) {
            return Mono.error(new NoGatewayAvailableException(
                    "No gateway available for network " + network + " (fallback disabled)",
                    network.name(), excludeProviderId));
        }

        List<MobileMoneyGateway> fallbackCandidates = getGatewaysForNetwork(network).stream()
                .filter(g -> !g.getProviderId().equals(excludeProviderId))
                .sorted(Comparator.comparingInt(MobileMoneyGateway::getPriority).reversed())
                .toList();

        if (fallbackCandidates.isEmpty()) {
            return Mono.error(new NoGatewayAvailableException(
                    "No fallback gateway available for network " + network,
                    network.name(), excludeProviderId));
        }

        // Try each fallback in priority order
        return Flux.fromIterable(fallbackCandidates)
                .flatMap(gateway -> checkAvailability(gateway)
                        .filter(available -> available)
                        .map(available -> gateway))
                .next()
                .switchIfEmpty(Mono.error(new NoGatewayAvailableException(
                        "All gateways unavailable for network " + network,
                        network.name())));
    }

    private Optional<MobileMoneyGateway> findGatewayById(String providerId) {
        if (providerId == null) return Optional.empty();
        return gateways.stream()
                .filter(g -> g.getProviderId().equalsIgnoreCase(providerId))
                .findFirst();
    }

    private List<MobileMoneyGateway> getGatewaysForNetwork(MobileNetwork network) {
        return gateways.stream()
                .filter(g -> g.supportsNetwork(network))
                .collect(Collectors.toList());
    }

    private Mono<Boolean> checkAvailability(MobileMoneyGateway gateway) {
        String providerId = gateway.getProviderId();

        // Check cache first
        CachedAvailability cached = availabilityCache.get(providerId);
        if (cached != null && !cached.isExpired()) {
            return Mono.just(cached.isAvailable());
        }

        // Perform health check
        return gateway.isAvailable()
                .doOnNext(available -> {
                    availabilityCache.put(providerId,
                            new CachedAvailability(available, System.currentTimeMillis()));
                    if (!available) {
                        log.warn("Gateway {} health check failed", providerId);
                    }
                })
                .onErrorResume(error -> {
                    log.error("Gateway {} health check error: {}", providerId, error.getMessage());
                    availabilityCache.put(providerId,
                            new CachedAvailability(false, System.currentTimeMillis()));
                    return Mono.just(false);
                });
    }

    private String maskPhone(String phoneNumber) {
        if (phoneNumber == null || phoneNumber.length() < 6) return "****";
        return phoneNumber.substring(0, 4) + "****" + phoneNumber.substring(phoneNumber.length() - 2);
    }

    // ==================== Inner Classes ====================

    private record CachedAvailability(boolean isAvailable, long cachedAt) {
        boolean isExpired() {
            return System.currentTimeMillis() - cachedAt > AVAILABILITY_CACHE_TTL_MS;
        }
    }
}
