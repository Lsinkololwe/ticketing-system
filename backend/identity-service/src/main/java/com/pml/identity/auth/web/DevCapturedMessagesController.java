package com.pml.identity.auth.web;

import com.pml.identity.auth.delivery.CapturedMessages;
import com.pml.shared.graphql.PageSize;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * Development helper: lets a person driving the apps on their own machine read the sign-in codes
 * the in-memory capture kept instead of sending, so they can type them back.
 *
 * <p>The controller exists only when {@code identity.delivery.capture.enabled=true}, which the
 * configuration validator allows in the local and test profiles only. The caller must hold the
 * internal write scope (the security chain enforces it for every POST under
 * {@code /api/internal/auth}) and must be on the loopback interface: a request that carries any
 * forwarding header ({@code X-Forwarded-For}, {@code Forwarded}, {@code X-Real-IP}) has come
 * through a proxy and is refused, even when the proxy itself is local. The contact travels in the
 * JSON body, never in the URL, so it cannot end up in an access log. The code is returned in the
 * body and never logged.</p>
 */
@RestController
@RequestMapping("/api/internal/auth/dev")
@ConditionalOnProperty(name = "identity.delivery.capture.enabled", havingValue = "true")
public class DevCapturedMessagesController {

    static final int DEFAULT_LIMIT = 20;
    static final int MAX_LIMIT = 100;

    /** Optional filters: the exact (normalised) {@code recipient} and how many newest messages to return. */
    public record Query(String recipient, Integer limit) {
        @Override
        public String toString() {
            return "Query[limit=" + limit + "]";
        }
    }

    private final CapturedMessages captured;

    public DevCapturedMessagesController(CapturedMessages captured) {
        this.captured = captured;
    }

    /** Newest first. An empty body returns the newest {@value #DEFAULT_LIMIT} messages; a limit above {@value #MAX_LIMIT} is refused. */
    @PostMapping("/captured/search")
    public Mono<ResponseEntity<List<Map<String, Object>>>> captured(
            ServerWebExchange exchange, @RequestBody(required = false) Mono<Query> body) {
        if (!fromThisMachineDirectly(exchange)) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        return body.defaultIfEmpty(new Query(null, null)).map(query -> {
            int limit = PageSize.require(query.limit(), MAX_LIMIT, DEFAULT_LIMIT);
            List<CapturedMessages.Message> all = captured.all();
            List<Map<String, Object>> out = new ArrayList<>();
            for (int i = all.size() - 1; i >= 0 && out.size() < limit; i--) {
                CapturedMessages.Message m = all.get(i);
                if (query.recipient() != null && !query.recipient().equals(m.recipient())) {
                    continue;
                }
                out.add(Map.of("channel", m.channel().name(), "recipient", m.recipient(),
                        "code", m.code(), "at", m.at().toString()));
            }
            return ResponseEntity.ok(out);
        });
    }

    private static boolean fromThisMachineDirectly(ServerWebExchange exchange) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || remote.getAddress() == null || !remote.getAddress().isLoopbackAddress()) {
            return false;
        }
        HttpHeaders headers = exchange.getRequest().getHeaders();
        return !headers.containsKey("X-Forwarded-For") && !headers.containsKey("Forwarded")
                && !headers.containsKey("X-Real-IP");
    }
}
