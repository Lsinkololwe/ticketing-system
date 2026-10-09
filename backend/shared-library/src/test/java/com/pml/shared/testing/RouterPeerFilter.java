package com.pml.shared.testing;

import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpRequestDecorator;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;
import reactor.core.publisher.Mono;

import java.net.InetAddress;
import java.net.InetSocketAddress;

/** Test-only: the mock connector has no socket, so this stamps the router's address on every request as its peer. */
public final class RouterPeerFilter implements WebFilter, Ordered {

    public static final String ROUTER = "10.0.0.7";

    @Override
    public int getOrder() {
        return -200;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        ServerHttpRequest peer = new ServerHttpRequestDecorator(exchange.getRequest()) {
            @Override
            public InetSocketAddress getRemoteAddress() {
                try {
                    return new InetSocketAddress(InetAddress.getByName(ROUTER), 40000);
                } catch (java.net.UnknownHostException e) {
                    throw new IllegalStateException(e);
                }
            }
        };
        return chain.filter(exchange.mutate().request(peer).build());
    }
}
