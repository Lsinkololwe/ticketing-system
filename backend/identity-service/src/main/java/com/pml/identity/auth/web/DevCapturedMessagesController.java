package com.pml.identity.auth.web;

import com.pml.identity.auth.delivery.CapturedMessages;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * DEV ONLY: reads the codes the in-memory capture kept, so a person driving the apps can type them back.
 * Exists only when {@code identity.delivery.capture.enabled=true} (profiles local and test only; startup fails
 * elsewhere). {@code GET /api/internal/auth/dev/captured} is covered by the existing rule for
 * {@code GET /api/internal/auth/**}, so it needs a token with scope {@code internal-read}, and it answers
 * only to a loopback caller. The code is returned in the body and never logged.
 */
@RestController
@RequestMapping("/api/internal/auth/dev")
@ConditionalOnProperty(name = "identity.delivery.capture.enabled", havingValue = "true")
public class DevCapturedMessagesController {

    private final CapturedMessages captured;

    public DevCapturedMessagesController(CapturedMessages captured) {
        this.captured = captured;
    }

    /** Newest first. {@code recipient} (exact) and {@code limit} (default 20) are optional filters. */
    @GetMapping("/captured")
    public Mono<ResponseEntity<List<Map<String, Object>>>> captured(
            ServerWebExchange exchange,
            @RequestParam(required = false) String recipient,
            @RequestParam(defaultValue = "20") int limit) {
        InetSocketAddress remote = exchange.getRequest().getRemoteAddress();
        if (remote == null || !remote.getAddress().isLoopbackAddress()
                || exchange.getRequest().getHeaders().containsKey("X-Forwarded-For")) {
            return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
        }
        List<CapturedMessages.Message> all = captured.all();
        List<Map<String, Object>> out = new java.util.ArrayList<>();
        for (int i = all.size() - 1; i >= 0 && out.size() < Math.max(1, limit); i--) {
            CapturedMessages.Message m = all.get(i);
            if (recipient != null && !recipient.equals(m.recipient())) {
                continue;
            }
            out.add(Map.of("channel", m.channel().name(), "recipient", m.recipient(),
                    "code", m.code(), "at", m.at().toString()));
        }
        return Mono.just(ResponseEntity.ok(out));
    }
}
