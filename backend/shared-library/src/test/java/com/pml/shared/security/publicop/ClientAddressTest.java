package com.pml.shared.security.publicop;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;

import java.net.InetSocketAddress;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Which address a public GraphQL request is rate-limited against: only a trusted proxy may name the client. */
@Tag("L1")
@Tag("ET-PLT-007")
class ClientAddressTest {

    private static final TrustedProxies ROUTER_NET = TrustedProxies.parse("127.0.0.1, ::1, 10.0.0.0/8, 172.16.0.0/12");

    private static ServerHttpRequest from(String peer, String... forwardedFor) {
        MockServerHttpRequest.BaseBuilder<?> builder = MockServerHttpRequest.post("/graphql")
                .remoteAddress(new InetSocketAddress(peer, 4000));
        for (String value : forwardedFor) {
            builder.header("X-Forwarded-For", value);
        }
        return builder.build();
    }

    @Test
    @DisplayName("a direct caller cannot choose its bucket: X-Forwarded-For from an untrusted peer is ignored")
    void directCallerCannotSpoof() {
        assertThat(PublicGraphQlFilter.clientAddress(from("203.0.113.50", "1.2.3.4"), ROUTER_NET)).isEqualTo("203.0.113.50");
        assertThat(PublicGraphQlFilter.clientAddress(from("203.0.113.50", "10.0.0.1"), ROUTER_NET)).isEqualTo("203.0.113.50");
    }

    @Test
    @DisplayName("behind the router every anonymous visitor has their own bucket, from the address the BFF forwarded")
    void routerForwardsClient() {
        assertThat(PublicGraphQlFilter.clientAddress(from("172.18.0.1", "198.51.100.7"), ROUTER_NET)).isEqualTo("198.51.100.7");
        assertThat(PublicGraphQlFilter.clientAddress(from("172.18.0.1", "198.51.100.8"), ROUTER_NET)).isEqualTo("198.51.100.8");
    }

    @Test
    @DisplayName("the chain is read from the right, skipping our own proxies; what the caller prepended is not trusted")
    void rightmostUntrusted() {
        assertThat(PublicGraphQlFilter.clientAddress(from("10.0.0.7", "6.6.6.6, 198.51.100.7, 10.0.0.9"), ROUTER_NET))
                .isEqualTo("198.51.100.7");
        assertThat(PublicGraphQlFilter.clientAddress(from("10.0.0.7", "6.6.6.6", "198.51.100.7, 127.0.0.1"), ROUTER_NET))
                .isEqualTo("198.51.100.7");
    }

    @Test
    @DisplayName("no header, an all-internal chain or a malformed entry fall back to the peer; the key is never free text")
    void fallsBackToPeer() {
        assertThat(PublicGraphQlFilter.clientAddress(from("10.0.0.7"), ROUTER_NET)).isEqualTo("10.0.0.7");
        assertThat(PublicGraphQlFilter.clientAddress(from("10.0.0.7", "10.0.0.2, 127.0.0.1"), ROUTER_NET)).isEqualTo("10.0.0.7");
        assertThat(PublicGraphQlFilter.clientAddress(from("10.0.0.7", "evil.example.com"), ROUTER_NET)).isEqualTo("10.0.0.7");
        assertThat(PublicGraphQlFilter.clientAddress(from("10.0.0.7", "unknown"), ROUTER_NET)).isEqualTo("10.0.0.7");
    }

    @Test
    @DisplayName("with no trusted proxies configured the header is never read")
    void defaultTrustsNobody() {
        assertThat(PublicGraphQlFilter.clientAddress(from("10.0.0.7", "198.51.100.7"))).isEqualTo("10.0.0.7");
        assertThat(PublicGraphQlFilter.clientAddress(from("10.0.0.7", "198.51.100.7"), TrustedProxies.parse(" "))).isEqualTo("10.0.0.7");
    }

    @Test
    @DisplayName("blocks match by prefix, IPv4-mapped IPv6 is IPv4, and a bad entry fails at startup")
    void parsing() {
        TrustedProxies net = TrustedProxies.parse("192.168.4.0/22, fd00::/8");
        assertThat(net.trusts("192.168.7.255")).isTrue();
        assertThat(net.trusts("192.168.8.1")).isFalse();
        assertThat(net.trusts("fd12::1")).isTrue();
        assertThat(net.trusts("::ffff:192.168.5.5")).isTrue();
        assertThat(net.trusts("not-an-ip")).isFalse();
        assertThatThrownBy(() -> TrustedProxies.parse("10.0.0.0/33")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> TrustedProxies.parse("router.internal")).isInstanceOf(IllegalArgumentException.class);
    }
}
