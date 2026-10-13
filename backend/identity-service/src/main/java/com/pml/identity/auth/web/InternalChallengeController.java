package com.pml.identity.auth.web;

import com.pml.identity.auth.challenge.ChallengeService;
import com.pml.identity.auth.delivery.DeliveryChannel;
import com.pml.identity.auth.proof.ProofService;
import com.pml.identity.config.IdentityProofProperties;
import com.pml.identity.domain.enums.ContactType;
import com.pml.shared.error.ErrorCode;
import com.pml.shared.error.TranslatedRefusal;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

/**
 * The two steps of "send me a code" and "here is my code" in checkout, as the sign-in plugin and the
 * buyer app's server call them.
 *
 * <p>Asking for a code: the caller names the contact (phone or email) in the body and the IP of the
 * person at the keyboard. The IP is required because the per-IP request limit is counted on it; a
 * request without a usable IP address is refused as not well formed rather than counted in one shared
 * bucket that would let one caller throttle everyone or hide from the limit. A refusal is one clear
 * reason (contact not acceptable, wait, too many requests, locked, delivery failed). The answer is
 * 202 with a challenge id and the masked contact, and is identical whether or not the contact has an
 * account.</p>
 *
 * <p>Typing the code: a correct code returns a single-use proof of control of the contact, which the
 * ensure step turns into a signed-in account. A wrong code says how many attempts remain; an expired
 * or unknown challenge is gone; too many wrong codes lock the contact until a stated time.</p>
 *
 * <p>The contact is only ever in a body, never in a URL.</p>
 */
@RestController
@RequestMapping("/api/internal/auth/challenges")
public class InternalChallengeController {

    private final ChallengeService challenges;
    private final ProofService proofs;
    private final IdentityProofProperties proofProperties;

    public InternalChallengeController(ChallengeService challenges, ProofService proofs,
                                       IdentityProofProperties proofProperties) {
        this.challenges = challenges;
        this.proofs = proofs;
        this.proofProperties = proofProperties;
    }

    @PostMapping
    public Mono<ResponseEntity<AuthDtos.ChallengeResponse>> request(@RequestBody Mono<AuthDtos.ChallengeRequest> body) {
        return body.switchIfEmpty(Mono.error(malformed()))
                .flatMap(request -> {
                    if (request.contact() == null || request.contact().value() == null
                            || request.contact().value().isBlank() || request.contact().value().length() > 320) {
                        return Mono.error(malformed());
                    }
                    return challenges.issue(new ChallengeService.IssueCommand(
                            request.contact().value(),
                            enumOrNull(ContactType.class, request.contact().type()),
                            request.regionHint(), usableIp(request.clientIp()), request.deviceId(),
                            enumOrNull(DeliveryChannel.class, request.preferredChannel())));
                })
                .map(issued -> ResponseEntity.status(HttpStatus.ACCEPTED).body(new AuthDtos.ChallengeResponse(
                        issued.challengeId(), issued.contactType().name(), issued.maskedContact(),
                        issued.channel().name(), issued.expiresInSeconds(), issued.resendAfterSeconds())));
    }

    @PostMapping("/verify")
    public Mono<ResponseEntity<AuthDtos.VerifyResponse>> verify(@RequestBody Mono<AuthDtos.VerifyRequest> body) {
        return body.switchIfEmpty(Mono.error(malformed()))
                .flatMap(request -> challenges.verify(request.challengeId(), request.code()))
                .flatMap(verified -> proofs.create(verified.contactKey(), verified.type(),
                                verified.valueEncrypted(), verified.valueMasked())
                        .map(proof -> ResponseEntity.ok(new AuthDtos.VerifyResponse(proof, verified.type().name(),
                                verified.valueMasked(), proofProperties.getTtl().toSeconds()))));
    }

    private static final java.util.regex.Pattern IPV4 = java.util.regex.Pattern.compile(
            "(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}");

    /**
     * The caller's IP as an IPv4 or IPv6 literal, or a refusal. Only characters that can appear in a
     * literal are accepted, and an IPv4 literal is checked by pattern, so no host name is ever
     * resolved.
     */
    static String usableIp(String clientIp) {
        if (clientIp == null) {
            throw malformed();
        }
        String ip = clientIp.trim();
        if (ip.isEmpty() || ip.length() > 45 || !ip.matches("[0-9A-Fa-f:.]+")) {
            throw malformed();
        }
        if (ip.indexOf(':') < 0) {
            if (!IPV4.matcher(ip).matches()) {
                throw malformed();
            }
            return ip;
        }
        try {
            // A string with a colon is parsed as an IPv6 literal and never looked up.
            java.net.InetAddress.getByName(ip);
        } catch (java.net.UnknownHostException e) {
            throw malformed();
        }
        return ip;
    }

    static <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.trim().toUpperCase(java.util.Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw malformed();
        }
    }

    static TranslatedRefusal malformed() {
        return new TranslatedRefusal(ErrorCode.COMMAND_NOT_WELL_FORMED, "request body not well formed");
    }
}
