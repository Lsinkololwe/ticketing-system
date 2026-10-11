package com.pml.shared.idempotency;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("a request's fingerprint changes when its meaning changes and not when its spelling does")
class FingerprintTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private static final Set<String> VARYING = Set.of("clientTimestamp", "deviceId");

    private String of(Object request) {
        return Fingerprint.of(mapper, request, VARYING);
    }

    @Test
    @DisplayName("field order does not matter at any depth")
    void orderIndependent() {
        Map<String, Object> a = new LinkedHashMap<>();
        a.put("eventId", "e1");
        a.put("selection", new LinkedHashMap<>(Map.of("tier", "t1", "quantity", 2)));
        Map<String, Object> b = new LinkedHashMap<>();
        b.put("selection", new LinkedHashMap<>(Map.of("quantity", 2, "tier", "t1")));
        b.put("eventId", "e1");

        assertThat(of(a)).isEqualTo(of(b));
    }

    @Test
    @DisplayName("the idempotency key and the fields a client may vary on retry are ignored")
    void keyAndVaryingFieldsIgnored() {
        Map<String, Object> first = Map.of("eventId", "e1", "idempotencyKey", "k1",
                "clientTimestamp", "2026-10-09T10:00:00Z", "deviceId", "phone-a");
        Map<String, Object> retry = Map.of("eventId", "e1", "idempotencyKey", "k1",
                "clientTimestamp", "2026-10-09T10:00:07Z", "deviceId", "phone-b");

        assertThat(of(first)).isEqualTo(of(retry));
    }

    @Test
    @DisplayName("any other field changes the fingerprint")
    void otherFieldsMatter() {
        assertThat(of(Map.of("eventId", "e1", "quantity", 2)))
                .isNotEqualTo(of(Map.of("eventId", "e1", "quantity", 3)))
                .isNotEqualTo(of(Map.of("eventId", "e2", "quantity", 2)))
                .isNotEqualTo(of(Map.of("eventId", "e1", "quantity", 2, "promoCode", "X")));
    }

    @Test
    @DisplayName("a varying name nested below the root is still part of the request")
    void nestedFieldsAreNotExcluded() {
        assertThat(of(Map.of("lines", List.of(Map.of("deviceId", "a")))))
                .isNotEqualTo(of(Map.of("lines", List.of(Map.of("deviceId", "b")))));
    }

    @Test
    @DisplayName("list order is meaningful and scalar types cannot collide")
    void listOrderAndTypes() {
        assertThat(of(Map.of("tiers", List.of("a", "b")))).isNotEqualTo(of(Map.of("tiers", List.of("b", "a"))));
        assertThat(of(Map.of("amount", 1))).isNotEqualTo(of(Map.of("amount", "1")));
        assertThat(of(Map.of("a", "bc"))).isNotEqualTo(of(Map.of("ab", "c")));
    }

    @Test
    @DisplayName("the client-varying allowlist names exactly clientTimestamp and deviceId")
    void clientVaryingAllowlist() {
        assertThat(Fingerprint.CLIENT_VARYING).containsExactlyInAnyOrder("clientTimestamp", "deviceId");
    }
}
