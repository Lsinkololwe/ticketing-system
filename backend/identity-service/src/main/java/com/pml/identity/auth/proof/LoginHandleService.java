package com.pml.identity.auth.proof;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pml.identity.config.IdentityLoginHandleProperties;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * The single-use login handle that lets the Keycloak plugin sign an ACTIVE account in (CONTRACT 6).
 * {@code handle:{id}} is bound to the account and the client, lives 60 s and is read-and-deleted
 * atomically (GETDEL), so a replay or a race finds nothing.
 */
@Service
public class LoginHandleService {

    /** What a handle stands for. */
    public record Redeemed(String accountId, String clientId) {
    }

    private final ReactiveStringRedisTemplate redis;
    private final IdentityLoginHandleProperties properties;
    private final ObjectMapper json;

    public LoginHandleService(ReactiveStringRedisTemplate redis, IdentityLoginHandleProperties properties, ObjectMapper json) {
        this.redis = redis;
        this.properties = properties;
        this.json = json;
    }

    static String key(String handle) {
        return "handle:" + handle;
    }

    public Mono<String> issue(String accountId, String clientId) {
        String handle = ProofService.randomId();
        String value;
        try {
            value = json.writeValueAsString(Map.of("accountId", accountId, "clientId", clientId));
        } catch (JsonProcessingException e) {
            return Mono.error(new IllegalStateException("cannot encode handle", e));
        }
        return redis.opsForValue().set(key(handle), value, properties.getTtl()).thenReturn(handle);
    }

    /**
     * Consumes the handle. The handle is gone after the first call even when the client does not
     * match, so a stolen handle presented by the wrong client cannot be retried by the right one's
     * attacker.
     *
     * @throws TranslatedRefusal LOGIN_HANDLE_INVALID when unknown, expired, used or bound to another client
     */
    public Mono<Redeemed> redeem(String handle, String clientId) {
        if (handle == null || handle.length() < 20 || handle.length() > 128 || !handle.matches("[A-Za-z0-9_-]+")
                || clientId == null || clientId.isBlank()) {
            return Mono.error(invalid());
        }
        return redis.opsForValue().getAndDelete(key(handle))
                .map(this::parse)
                .filter(redeemed -> redeemed.clientId().equals(clientId))
                .switchIfEmpty(Mono.error(LoginHandleService::invalid));
    }

    private Redeemed parse(String value) {
        try {
            Map<?, ?> map = json.readValue(value, Map.class);
            return new Redeemed(String.valueOf(map.get("accountId")), String.valueOf(map.get("clientId")));
        } catch (JsonProcessingException e) {
            throw invalid();
        }
    }

    private static TranslatedRefusal invalid() {
        return new TranslatedRefusal(ErrorCode.LOGIN_HANDLE_INVALID, "login handle unknown, expired or used");
    }
}
