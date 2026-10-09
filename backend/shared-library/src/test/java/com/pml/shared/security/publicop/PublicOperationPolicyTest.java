package com.pml.shared.security.publicop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Tag("L1")
@Tag("ET-PLT-007")
@DisplayName("ET-PLT-007-R9 · a policy defaults to the platform limits and cannot be changed after it is built")
class PublicOperationPolicyTest {

    @Test
    @DisplayName("defaults: 16 KiB body, depth 10, 300 fields, 10 fragments, 120 a minute, no entities")
    void defaults() {
        PublicOperationPolicy policy = PublicOperationPolicy.of("svc", Set.of("a"));
        assertThat(policy.maxBodyBytes()).isEqualTo(16 * 1024);
        assertThat(policy.maxDepth()).isEqualTo(10);
        assertThat(policy.maxNodes()).isEqualTo(300);
        assertThat(policy.maxFragments()).isEqualTo(10);
        assertThat(policy.limitPerWindow()).isEqualTo(120);
        assertThat(policy.window()).isEqualTo(Duration.ofMinutes(1));
        assertThat(policy.entityFields()).isEmpty();
    }

    @Test
    @DisplayName("the allowlists are copies and immutable")
    void immutable() {
        java.util.HashSet<String> roots = new java.util.HashSet<>(Set.of("a"));
        PublicOperationPolicy policy = PublicOperationPolicy.of("svc", roots).withEntities(Map.of("Org", Set.of("n")));
        roots.add("b");
        assertThat(policy.rootFields()).containsExactly("a");
        assertThatThrownBy(() -> policy.rootFields().add("c")).isInstanceOf(UnsupportedOperationException.class);
        assertThat(policy.withLimit(5, Duration.ofSeconds(10)).limitPerWindow()).isEqualTo(5);
    }
}
