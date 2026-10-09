package com.pml.identity.auth.proof;

import com.pml.identity.account.ProofLookup;
import com.pml.identity.account.ProofRecord;
import com.pml.identity.config.IdentityProofProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.security.SecureRandom;
import java.util.Base64;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The single-use proof of contact control returned after a correct code (CONTRACT 6).
 *
 * <p>{@code proof:{id}} holds the contact key, type, encrypted value and mask. The id is 256
 * random bits, so the key neither contains nor can be guessed from a contact. A NEW proof lives
 * {@code identity.proof.ttl}; {@link #markConsumed} moves it to CONSUMED and extends it to
 * {@code identity.proof.ensure-hold} so the account workflow can still read the encrypted contact.
 * Presenting a CONSUMED proof again is allowed (it is how {@code ensure} stays idempotent) until
 * the hold runs out.</p>
 */
@Service
public class ProofService implements ProofLookup {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** KEYS: proof. ARGV: holdMs. Moves NEW to CONSUMED and extends the TTL once; returns 0 if absent. */
    private static final RedisScript<Long> CONSUME = script("""
            if redis.call('EXISTS', KEYS[1]) == 0 then return 0 end
            if redis.call('HGET', KEYS[1], 'state') == 'NEW' then
              redis.call('HSET', KEYS[1], 'state', 'CONSUMED')
              redis.call('PEXPIRE', KEYS[1], ARGV[1])
            end
            return 1
            """);

    private final ReactiveStringRedisTemplate redis;
    private final IdentityProofProperties properties;

    public ProofService(ReactiveStringRedisTemplate redis, IdentityProofProperties properties) {
        this.redis = redis;
        this.properties = properties;
    }

    static String key(String proofId) {
        return "proof:" + proofId;
    }

    /** Creates a NEW proof bound to one verified contact and returns its id. */
    public Mono<String> create(String contactKey, ContactType type, String valueEncrypted, String valueMasked) {
        String id = randomId();
        Map<String, String> fields = new HashMap<>();
        fields.put("contactKey", contactKey);
        fields.put("type", type.name());
        fields.put("valueEncrypted", valueEncrypted);
        fields.put("valueMasked", valueMasked);
        fields.put("state", "NEW");
        return redis.opsForHash().putAll(key(id), fields)
                .then(redis.expire(key(id), properties.getTtl()))
                .thenReturn(id);
    }

    /**
     * Marks the proof consumed by {@code ensure} and returns it. Idempotent: a proof already
     * CONSUMED is returned unchanged.
     *
     * @throws TranslatedRefusal PROOF_INVALID when the proof is unknown, expired or malformed
     */
    public Mono<ProofRecord> markConsumed(String proofId) {
        if (!plausible(proofId)) {
            return Mono.error(invalid());
        }
        return redis.execute(CONSUME, List.of(key(proofId)), List.of(String.valueOf(properties.getEnsureHold().toMillis())))
                .next()
                .filter(ok -> ok == 1L)
                .switchIfEmpty(Mono.error(ProofService::invalid))
                .then(find(proofId))
                .switchIfEmpty(Mono.error(ProofService::invalid));
    }

    /** Records the account a CONSUMED proof led to, without changing its TTL. */
    public Mono<Void> recordAccount(String proofId, String accountId) {
        return redis.opsForHash().put(key(proofId), "accountId", accountId).then();
    }

    @Override
    public Mono<ProofRecord> find(String proofId) {
        if (!plausible(proofId)) {
            return Mono.empty();
        }
        return redis.<String, String>opsForHash().entries(key(proofId))
                .collectMap(Map.Entry::getKey, Map.Entry::getValue)
                .filter(fields -> !fields.isEmpty())
                .map(fields -> new ProofRecord(proofId, fields.get("contactKey"),
                        ContactType.valueOf(fields.get("type")), fields.get("valueEncrypted"),
                        fields.get("valueMasked"), fields.getOrDefault("state", "NEW"), fields.get("accountId")));
    }

    private static boolean plausible(String id) {
        return id != null && id.length() >= 20 && id.length() <= 128 && id.matches("[A-Za-z0-9_-]+");
    }

    static String randomId() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static TranslatedRefusal invalid() {
        return new TranslatedRefusal(ErrorCode.PROOF_INVALID, "proof unknown, expired or already used up");
    }

    private static RedisScript<Long> script(String lua) {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptText(lua);
        script.setResultType(Long.class);
        return script;
    }
}
